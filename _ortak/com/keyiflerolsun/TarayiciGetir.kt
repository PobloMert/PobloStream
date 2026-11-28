package com.keyiflerolsun

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Cloudflare "Just a moment" sayfasını yalnızca tarayıcı olmayan istemcilere gösteren siteler için:
 * sayfayı görünmez bir WebView'de (gerçek tarayıcı) açar ve HTML'ini okur. Doğrulama atlanmaz;
 * sayfayı tarayıcı normal bir ziyaretçi gibi alır.
 *
 * Hız için:
 *  - Doğrulama isteyen site hatırlanır; sonraki isteklerde önce doğrudan deneme yapılmaz.
 *  - Site tarayıcıda bir kez açıldıktan sonra diğer sayfalar tarayıcının fetch() işleviyle alınır
 *    (resim/betik yüklenmez, istekler aynı anda gidebilir). Tam sayfa açılışı yalnızca ilk seferde
 *    ya da fetch sonuç vermezse yapılır.
 *  - Aynı sayfa 2 dakika içinde tekrar istenirse önbellekten verilir.
 *
 * Kullanım: istekte `interceptor = TarayiciGetir.Yakalayici()`.
 * Bağlam için Plugin.load() içinde WebViewVekili.kur(context) çağrılmış olmalı.
 * ! _ortak/ klasöründedir; kök build.gradle.kts her eklentiye ekler.
 */
object TarayiciGetir {
    private const val ETIKET = "TarayiciGetir"
    private const val ONBELLEK_MS = 120_000L
    private val ana = Handler(Looper.getMainLooper())
    private var wv: WebView? = null
    private val sonHata = AtomicReference<String?>(null)
    /** Sayfa açılışı (gezinme) yazma kilidi, fetch okuma kilidi alır: fetch'ler aynı anda gidebilir. */
    private val kilit = ReentrantReadWriteLock()
    internal val dogrulamali = ConcurrentHashMap.newKeySet<String>() // testte "doğrulanamadı" ayrımı için
    private val onbellek = ConcurrentHashMap<String, Pair<Long, String>>()
    private val sayac = AtomicInteger()
    /** Aynı anda en çok 6 fetch: çok kategorili ana sayfada TV'nin tarayıcısını boğmasın. */
    private val esZamanli = java.util.concurrent.Semaphore(6)
    /** Bu iş parçacığındaki son tarayıcı isteği HTTP hata koduyla mı döndü (site engeli)? */
    private val sonEngelKodu = ThreadLocal<Int?>()
    private val ENGEL_KODLARI = setOf(401, 403, 429, 503)
    /** Site (host sonu) → tarayıcıda atılacak öğelerin CSS seçicisi. Bkz. [kirp]. */
    private val kirpmalar = ConcurrentHashMap<String, String>()

    /**
     * Eklentinin kullanmadığı büyük sayfa bölümlerini tarayıcı tarafında atar: sayfa eklentiye
     * (evaluateJavascript ile) aktarılmadan önce küçülür, TV'de belirgin hız kazancı.
     * Örn. DiziBox her sayfaya ~1,6 MB'lık arama listesi gömüyor: kirp("dizibox.live", "li.search-target").
     * Yalnızca tarayıcı yolundan (Cloudflare) gelen GET sayfalarına uygulanır.
     */
    fun kirp(hostSonu: String, secici: String) { kirpmalar[hostSonu.lowercase()] = secici }

    private fun kirpmaSecicisi(url: String): String? {
        val host = Regex("""^https?://([^/:]+)""").find(url)?.groupValues?.get(1)?.lowercase() ?: return null
        return kirpmalar.entries.firstOrNull { host == it.key || host.endsWith("." + it.key) }?.value
    }

    /** Site, doğrulamayı geçen tarayıcıyı da reddettiğinde: CloudStream bu mesajı ekranda gösterir. */
    class SiteEngeli(mesaj: String) : java.io.IOException(mesaj)

    class Yakalayici : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val istek = chain.request()
            val host = istek.url.host
            val get = istek.method == "GET"

            if (get) onbellek[istek.url.toString()]?.let { (zaman, html) ->
                if (System.currentTimeMillis() - zaman < ONBELLEK_MS) return yanit(istek, html)
            }

            // Her istek önce doğrudan denenir: bir sitede yalnızca bazı sayfalar doğrulama ister
            // (ör. HDFilmCehennemi'de ana sayfa ister, /load/ uç noktaları istemez). Tarayıcı yalnızca
            // doğrulama gelen istekte kullanılır.
            val y = chain.proceed(istek)
            if (!dogrulamaMi(y)) return y
            y.close()
            dogrulamali += host
            Log.d(ETIKET, "Cloudflare sayfası, tarayıcıyla alınıyor: ${istek.url}")

            val govde = istek.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }
            val tur = istek.body?.contentType()?.toString()
            val basliklar = istek.headers.toMultimap().mapValues { it.value.first() }
            sonEngelKodu.remove()
            val metin = if (get) getir(istek.url.toString(), basliklar)
                else fetch(istek.url.toString(), istek.method, basliklar, govde, tur)
            if (metin == null) {
                // Tarayıcı da HTTP hata kodu aldıysa (ör. 403 "Bu uygulama desteklenmiyor") anlaşılır mesaj ver;
                // zaman aşımı gibi durumlarda eski davranış (sitenin kendi yanıtı)
                sonEngelKodu.get()?.let { kod ->
                    sonEngelKodu.remove()
                    throw SiteEngeli("$host şu an bu uygulamayı engelliyor (Cloudflare, HTTP $kod). " +
                        "Bu sitenin kendi koruması; eklenti hatası değil. Daha sonra tekrar deneyin.")
                }
                return chain.proceed(istek)
            }
            if (get) onbellek[istek.url.toString()] = System.currentTimeMillis() to metin
            return yanit(istek, metin)
        }

        private fun yanit(istek: okhttp3.Request, metin: String): Response {
            val icerik = if (istek.method == "GET") "text/html; charset=utf-8" else "application/json; charset=utf-8"
            return Response.Builder().request(istek).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", icerik).body(metin.toResponseBody(icerik.toMediaType())).build()
        }

        private fun dogrulamaMi(y: Response): Boolean {
            if (y.code != 403 && y.code != 503 && y.code != 429) return false
            val bas = runCatching { y.peekBody(64 * 1024).string() }.getOrDefault("")
            return bas.contains("Just a moment") || bas.contains("challenge-platform") || y.header("cf-mitigated") == "challenge"
        }
    }

    /** GET: site tarayıcıda açıksa fetch ile (hızlı), değilse ya da sonuç dönmezse tam sayfa açarak. */
    fun getir(url: String, basliklar: Map<String, String> = emptyMap()): String? {
        val koken = koken(url) ?: return null
        if (calistir("location.origin") != koken) {
            // Site henüz açık değil: tarayıcıda yalnızca sitenin ana sayfasını aç (doğrulama ve çerezler
            // için); aynı anda gelen diğer istekler bekler. İstenen sayfanın kendisi her zaman fetch ile
            // ham HTML olarak alınır: eklentiler sunucunun HTML'ine göre yazılmıştır, JavaScript'in
            // değiştirdiği DOM'a göre değil (ör. DiziBox'ta kartlar sonradan yeniden düzenleniyor).
            kilit.write { if (calistir("location.origin") != koken) sayfa("$koken/") ?: return sayfa(url) }
        }
        val html = fetch(url, "GET", basliklar, null, null)
        Log.d(ETIKET, "fetch: $url (${html?.length ?: "yok"} karakter)")
        if (html != null && !dogrulamaSayfasi(html)) return html
        Log.d(ETIKET, "fetch sonuç vermedi, sayfa açılıyor: $url")
        return sayfa(url)
    }

    /** Sayfayı tarayıcıda açıp hazır olunca HTML'ini döndürür. Ulaşamazsa yerel vekille bir kez dener. */
    fun sayfa(url: String, sureMs: Long = 35_000): String? = kilit.write {
        val dogrudan = sayfaYukle(url, sureMs)
        if (dogrudan != null) return@write dogrudan
        if (sonHata.get() == null) return@write null
        Log.d(ETIKET, "tarayıcı siteye ulaşamadı (${sonHata.get()}), yerel vekille deneniyor")
        WebViewVekili.ile { sayfaYukle(url, sureMs) }
    }

    /** Sitenin bir sayfası açıkken tarayıcının fetch() işleviyle istek yapar (ör. arama API'si). */
    fun fetch(url: String, yontem: String, basliklar: Map<String, String>, govde: String?, tur: String?, veri: Boolean = false): String? {
        val koken = koken(url) ?: return null
        if (calistir("location.origin") != koken) sayfa("$koken/") ?: return null
        // fetch() Cookie başlığı gönderemez: eklentinin istekle verdiği çerezleri tarayıcıya yaz
        // (ör. DiziBox'ın giriş ekranını atlatan LockUser/isTrustedUser çerezleri)
        basliklar.entries.firstOrNull { it.key.equals("cookie", true) }?.value?.let { cerezYaz(koken, it) }
        esZamanli.acquire()
        try { return kilit.read {
            val h = JSONObject().apply {
                basliklar.filterKeys { it.lowercase() !in setOf("host", "content-length", "cookie", "user-agent", "accept-encoding", "connection", "referer", "origin") && !it.lowercase().startsWith("sec-") }
                    .forEach { (k, v) -> put(k, v) }
                if (tur != null) put("Content-Type", tur)
            }
            val gov = if (govde != null) JSONObject.quote(govde) else "undefined"
            val no = "k" + sayac.incrementAndGet()
            val kirpma = if (yontem == "GET" && !veri) kirpmaSecicisi(url) else null
            // ! Tek ifade olmalı (calistir "return (...)" içine koyar): noktalı virgül değil virgül
            calistir("""((window.__tg=window.__tg||{})['$no']=undefined, fetch(${JSONObject.quote(url)},{method:${JSONObject.quote(yontem)},headers:$h,body:$gov,credentials:'include'})
                .then(r=>r.status>=400?'__HATA__HTTP '+r.status:${when {
                    veri -> VERI_JS
                    kirpma != null -> "r.text().then(t=>{try{const d=new DOMParser().parseFromString(t,'text/html');d.querySelectorAll(${JSONObject.quote(kirpma)}).forEach(e=>e.remove());return '<!DOCTYPE html>'+d.documentElement.outerHTML}catch(e){return t}})"
                    else -> "r.text()"
                }}).then(t=>{window.__tg['$no']=t}).catch(e=>{window.__tg['$no']='__HATA__'+e}), '1')""")
            val bitis = System.currentTimeMillis() + 20_000
            while (System.currentTimeMillis() < bitis) {
                val s = calistir("(function(){var t=window.__tg&&window.__tg['$no'];if(t===undefined)return null;delete window.__tg['$no'];return t;})()")
                if (s != null) return@read if (s.startsWith("__HATA__")) null.also {
                    Log.w(ETIKET, "fetch: $s")
                    s.removePrefix("__HATA__HTTP ").toIntOrNull()?.takeIf { it in ENGEL_KODLARI }?.let { kod -> sonEngelKodu.set(kod) }
                } else s
                Thread.sleep(150)
            }
            null
        } } finally { esZamanli.release() }
    }

    /** Yanıtı "data:<tür>;base64,..." metnine çevirir (resim gibi ikili içerik için). */
    private const val VERI_JS = "r.blob().then(b=>new Promise(res=>{const f=new FileReader();f.onload=()=>res(f.result);f.onerror=()=>res('__HATA__okuma');f.readAsDataURL(b)}))"

    /**
     * Resmi (ya da başka ikili içeriği) tarayıcının fetch() işleviyle alır: Cloudflare'in uygulamanın
     * resim yükleyicisini reddettiği sitelerde afiş için. Dönen: (içerik türü, baytlar).
     */
    fun resim(url: String): Pair<String, ByteArray>? {
        val koken = koken(url) ?: return null
        if (calistir("location.origin") != koken) kilit.write { if (calistir("location.origin") != koken) sayfa("$koken/") ?: return null }
        val veri = fetch(url, "GET", emptyMap(), null, null, veri = true) ?: return null
        if (!veri.startsWith("data:")) return null
        val tur = veri.substringAfter("data:").substringBefore(";").ifBlank { "image/jpeg" }
        return runCatching { tur to android.util.Base64.decode(veri.substringAfter(","), android.util.Base64.DEFAULT) }.getOrNull()
    }

    /** "a=1; b=2" biçimindeki çerezleri tarayıcının çerez deposuna yazar (eksik olanları). */
    private fun cerezYaz(koken: String, cerezler: String) {
        val bitti = CountDownLatch(1)
        ana.post {
            runCatching {
                val cm = android.webkit.CookieManager.getInstance()
                val mevcut = cm.getCookie(koken).orEmpty()
                cerezler.split(";").map { it.trim() }.filter { it.contains("=") }.forEach { c ->
                    if (!mevcut.contains(c)) cm.setCookie(koken, "$c; path=/")
                }
                cm.flush()
            }
            bitti.countDown()
        }
        bitti.await(3, TimeUnit.SECONDS)
    }

    private fun koken(url: String) = Regex("""^(https?://[^/]+)""").find(url)?.value

    private fun dogrulamaSayfasi(html: String) =
        html.contains("challenge-platform") || html.contains("<title>Just a moment", true)

    @SuppressLint("SetJavaScriptEnabled")
    private fun sayfaYukle(url: String, sureMs: Long): String? {
        val ctx = WebViewVekili.baglam ?: return null.also { Log.w(ETIKET, "bağlam yok") }
        sonHata.set(null)
        val hazir = CountDownLatch(1)
        ana.post {
            runCatching {
                val w = wv ?: WebView(ctx).also { wv = it }
                w.settings.javaScriptEnabled = true
                w.settings.domStorageEnabled = true
                w.settings.blockNetworkImage = true // görünmez tarayıcıda resim gerekmez; sayfa daha hızlı hazır olur
                w.webViewClient = object : WebViewClient() {
                    override fun onReceivedError(v: WebView?, r: WebResourceRequest?, e: WebResourceError?) {
                        if (r?.isForMainFrame == true) sonHata.set(e?.description?.toString() ?: "hata")
                    }
                }
                w.loadUrl(url)
            }.onFailure { sonHata.set(it.message ?: "webview"); Log.w(ETIKET, "webview açılamadı: ${it.message}") }
            hazir.countDown()
        }
        hazir.await(5, TimeUnit.SECONDS)

        // Sayfa yüklenip Cloudflare doğrulaması (varsa) tarayıcıda tamamlanana kadar bekle
        val bitis = System.currentTimeMillis() + sureMs
        Thread.sleep(500)
        while (System.currentTimeMillis() < bitis) {
            if (sonHata.get() != null) return null
            val durum = calistir("document.readyState+'|'+document.title+'|'+location.href") ?: ""
            val kod = calistir("String((performance.getEntriesByType('navigation')[0]||{}).responseStatus||0)")?.toIntOrNull() ?: 0
            val (hazirlik, baslik) = durum.split("|").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
            val adres = durum.split("|").lastOrNull() ?: ""
            // İlk açılışta tarayıcı bir an boş sayfada (about:blank) "complete" görünür: hedef sayfaya geçmeden okuma
            if (hazirlik == "complete" && adres.startsWith("http") && !baslik.contains("Just a moment", true) && !baslik.contains("Bir dakika", true)) {
                // JSON/düz metin yanıtı (ör. sitenin /load/ uç noktaları) tarayıcıda <pre> ile sarılır: ham metni ver
                // Doğrulama bittiği hâlde sayfa hata koduyla geldiyse (ör. "Bu uygulama desteklenmiyor" 403) başarısız say
                if (kod >= 400) { Log.w(ETIKET, "tarayıcı da HTTP $kod aldı: $url"); if (kod in ENGEL_KODLARI) sonEngelKodu.set(kod); return null }
                val html = calistir("/html|xml/.test(document.contentType)?document.documentElement.outerHTML:document.body.innerText")
                if (html != null && !html.contains("challenge-platform")) {
                    Log.d(ETIKET, "sayfa alındı (${html.length} karakter): $url")
                    return html
                }
            }
            Thread.sleep(300)
        }
        Log.w(ETIKET, "zaman aşımı: $url")
        return null
    }

    /** WebView'de JavaScript çalıştırıp sonucu (dize olarak) döndürür. */
    private fun calistir(js: String): String? {
        val sonuc = AtomicReference<String?>(null)
        val bitti = CountDownLatch(1)
        ana.post {
            val w = wv ?: return@post bitti.countDown()
            runCatching {
                w.evaluateJavascript("(function(){try{return ($js);}catch(e){return null;}})()") { ham ->
                    sonuc.set(runCatching { JSONTokener(ham).nextValue() as? String }.getOrNull())
                    bitti.countDown()
                }
            }.onFailure { bitti.countDown() }
        }
        bitti.await(10, TimeUnit.SECONDS)
        return sonuc.get()
    }
}
