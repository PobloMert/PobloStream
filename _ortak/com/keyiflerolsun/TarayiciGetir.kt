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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Cloudflare "Just a moment" sayfasını yalnızca tarayıcı olmayan istemcilere gösteren siteler için:
 * sayfayı görünmez bir WebView'de (gerçek tarayıcı) açar ve HTML'ini okur. Doğrulama atlanmaz;
 * sayfayı tarayıcı normal bir ziyaretçi gibi alır.
 *
 * Kullanım: istekte `interceptor = TarayiciGetir.Yakalayici()`; yanıt "Just a moment" ise
 * aynı istek tarayıcıyla yapılır ve eklentiye normal 200 yanıtı olarak verilir.
 *
 * Bağlam için Plugin.load() içinde WebViewVekili.kur(context) çağrılmış olmalı.
 * ! _ortak/ klasöründedir; kök build.gradle.kts her eklentiye ekler.
 */
object TarayiciGetir {
    private const val ETIKET = "TarayiciGetir"
    private val ana = Handler(Looper.getMainLooper())
    private var wv: WebView? = null
    private val sonHata = AtomicReference<String?>(null)

    class Yakalayici : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val istek = chain.request()
            val yanit = chain.proceed(istek)
            if (!dogrulamaMi(yanit)) return yanit
            yanit.close()
            Log.d(ETIKET, "Cloudflare sayfası, tarayıcıyla alınıyor: ${istek.url}")

            val govde = istek.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }
            val tur = istek.body?.contentType()?.toString()
            val metin = synchronized(TarayiciGetir) {
                if (istek.method == "GET") sayfa(istek.url.toString())
                else fetch(istek.url.toString(), istek.method, istek.headers.toMultimap().mapValues { it.value.first() }, govde, tur)
            } ?: return chain.proceed(istek) // tarayıcı da alamadı: eski davranış

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

    /** Sayfayı tarayıcıda açıp hazır olunca HTML'ini döndürür. Ulaşamazsa yerel vekille bir kez dener. */
    fun sayfa(url: String, sureMs: Long = 35_000): String? {
        val dogrudan = sayfaYukle(url, sureMs)
        if (dogrudan != null) return dogrudan
        if (sonHata.get() == null) return null
        Log.d(ETIKET, "tarayıcı siteye ulaşamadı (${sonHata.get()}), yerel vekille deneniyor")
        return WebViewVekili.ile { sayfaYukle(url, sureMs) }
    }

    /** Sitenin bir sayfası açıkken tarayıcının fetch() işleviyle istek yapar (ör. arama API'si). */
    fun fetch(url: String, yontem: String, basliklar: Map<String, String>, govde: String?, tur: String?): String? {
        val koken = Regex("""^(https?://[^/]+)""").find(url)?.value ?: return null
        if (calistir("location.origin") != koken) sayfa("$koken/") ?: return null
        val h = JSONObject().apply {
            basliklar.filterKeys { it.lowercase() !in setOf("host", "content-length", "cookie", "user-agent", "accept-encoding", "connection", "referer", "origin") && !it.lowercase().startsWith("sec-") }
                .forEach { (k, v) -> put(k, v) }
            if (tur != null) put("Content-Type", tur)
        }
        val gov = if (govde != null) JSONObject.quote(govde) else "undefined"
        calistir("""window.__tg=undefined;fetch(${JSONObject.quote(url)},{method:${JSONObject.quote(yontem)},headers:$h,body:$gov,credentials:'include'})
            .then(r=>r.text()).then(t=>window.__tg=t).catch(e=>window.__tg='__HATA__'+e);1""")
        val bitis = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < bitis) {
            val s = calistir("window.__tg===undefined?null:window.__tg")
            if (s != null) return if (s.startsWith("__HATA__")) null.also { Log.w(ETIKET, "fetch: $s") } else s
            Thread.sleep(300)
        }
        return null
    }

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
        Thread.sleep(800)
        while (System.currentTimeMillis() < bitis) {
            if (sonHata.get() != null) return null
            val durum = calistir("document.readyState+'|'+document.title+'|'+location.href") ?: ""
            val (hazirlik, baslik) = durum.split("|").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
            if (hazirlik == "complete" && !baslik.contains("Just a moment", true) && !baslik.contains("Bir dakika", true)) {
                val html = calistir("document.documentElement.outerHTML")
                if (html != null && !html.contains("challenge-platform")) {
                    Log.d(ETIKET, "sayfa alındı (${html.length} karakter): $url")
                    return html
                }
            }
            Thread.sleep(500)
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
