package com.keyiflerolsun

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ProxyInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Uygulamanın WebView'i (ör. CloudflareKiller doğrulaması) engelli bir siteye ulaşamaz:
 * WebView eklentinin OkHttp istemcisini değil, cihazın normal bağlantısını kullanır.
 *
 * Bu nesne:
 *  1. 127.0.0.1 üzerinde küçük bir HTTP CONNECT vekili açar. Vekil hedef adı DoH ile çözer ve
 *     tarayıcının ilk TLS kaydını (ClientHello) EngelAsici ile aynı şekilde böler. TLS uçtan uca
 *     tarayıcı ile site arasındadır; vekil trafiği çözmez ya da değiştirmez.
 *  2. Yalnızca [ile] bloğu süresince uygulamanın WebView'ini bu vekile yönlendirir (Chromium'un
 *     dinlediği "proxy değişti" bildirimi süreç içinde kendi alıcısına iletilir), sonra geri alır.
 *
 * Doğrulamayı yine gerçek tarayıcı çözer; bu sınıf yalnızca tarayıcının siteye ulaşmasını sağlar.
 * Herhangi bir adım başarısız olursa sessizce eski davranışa döner.
 *
 * ! _ortak/ klasöründedir; kök build.gradle.kts her eklentiye ekler.
 */
object WebViewVekili {
    private const val ETIKET = "WebViewVekili"
    @Volatile var baglam: Context? = null
    @Volatile private var sunucu: ServerSocket? = null
    private val havuz = Executors.newCachedThreadPool { r -> Thread(r, ETIKET).apply { isDaemon = true } }

    /** Eklentinin Plugin.load(context) içinde çağırması yeterli. */
    fun kur(context: Context) { baglam = context.applicationContext }

    /** [blok] süresince WebView trafiğini yerel vekilden geçirir. */
    fun <T> ile(blok: () -> T): T {
        val acildi = runCatching { ayarla(true) }.onFailure { Log.w(ETIKET, "vekil açılamadı: ${it.message}") }.getOrDefault(false)
        try {
            return blok()
        } finally {
            if (acildi) runCatching { ayarla(false) }.onFailure { Log.w(ETIKET, "vekil kapatılamadı: ${it.message}") }
        }
    }

    // ---------------------------------------------------------------- yerel CONNECT vekili

    @Synchronized
    private fun port(): Int {
        sunucu?.takeIf { !it.isClosed }?.let { return it.localPort }
        val s = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        sunucu = s
        Thread({
            while (!s.isClosed) {
                val b = runCatching { s.accept() }.getOrNull() ?: continue
                havuz.execute { runCatching { isle(b) }.onFailure { runCatching { b.close() } } }
            }
        }, "$ETIKET-dinleyici").apply { isDaemon = true }.start()
        Log.d(ETIKET, "yerel vekil 127.0.0.1:${s.localPort} açıldı")
        return s.localPort
    }

    private fun satirOku(giris: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = giris.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
        }
    }

    private fun isle(istemci: Socket) {
        val giris = BufferedInputStream(istemci.getInputStream())
        val cikis = istemci.getOutputStream()
        val istekSatiri = satirOku(giris) ?: return istemci.close()
        val basliklar = mutableListOf<String>()
        while (true) { val s = satirOku(giris) ?: break; if (s.isEmpty()) break; basliklar += s }

        val (yontem, hedef) = istekSatiri.split(" ").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
        val sunucuSoket: Socket
        if (yontem.equals("CONNECT", true)) {
            val host = hedef.substringBeforeLast(":"); val p = hedef.substringAfterLast(":").toIntOrNull() ?: 443
            sunucuSoket = baglan(host, p)
            cikis.write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray(Charsets.ISO_8859_1)); cikis.flush()
            aktar(istemci, giris, sunucuSoket, EngelAsici.BolenCikis(sunucuSoket.getOutputStream()))
        } else if (hedef.startsWith("/_baslat")) {
            // Ağ katmanını başlatmak için açılan görünmez WebView'in isteği (doğrudan bize gelir)
            cikis.write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            cikis.flush(); istemci.close(); return
        } else {
            // Düz HTTP: "GET http://host/yol HTTP/1.1" → host:80'e olduğu gibi ilet
            val url = java.net.URL(hedef)
            sunucuSoket = baglan(url.host, if (url.port > 0) url.port else 80)
            val so = sunucuSoket.getOutputStream()
            val yol = (url.file ?: "/").ifEmpty { "/" }
            so.write(("$yontem $yol ${istekSatiri.substringAfterLast(" ")}\r\n" +
                basliklar.filterNot { it.startsWith("Proxy-", true) }.joinToString("") { "$it\r\n" } + "\r\n")
                .toByteArray(Charsets.ISO_8859_1))
            so.flush()
            aktar(istemci, giris, sunucuSoket, so)
        }
    }

    private fun baglan(host: String, port: Int): Socket {
        val ip = EngelAsici.DohDns.lookup(host).first()
        return Socket().apply { tcpNoDelay = true; connect(InetSocketAddress(ip, port), 15_000) }
    }

    /** İki yönlü aktarım: istemci → sunucu (ilk kayıt bölünerek), sunucu → istemci (aynen). */
    private fun aktar(istemci: Socket, istemciGiris: InputStream, sunucu: Socket, sunucuCikis: OutputStream) {
        val bitti = CountDownLatch(2)
        fun kopyala(g: InputStream, c: OutputStream) = havuz.execute {
            runCatching {
                val tampon = ByteArray(32 * 1024)
                while (true) { val n = g.read(tampon); if (n < 0) break; c.write(tampon, 0, n); c.flush() }
            }
            runCatching { istemci.shutdownOutput() }; runCatching { sunucu.shutdownOutput() }
            bitti.countDown()
        }
        kopyala(istemciGiris, sunucuCikis)
        kopyala(sunucu.getInputStream(), istemci.getOutputStream())
        bitti.await(10, TimeUnit.MINUTES)
        runCatching { istemci.close() }; runCatching { sunucu.close() }
    }

    // ---------------------------------------------------------------- WebView yönlendirmesi

    /**
     * Chromium WebView, sistemin PROXY_CHANGE bildirimini kendi ProxyChangeListener alıcısıyla dinler.
     * Bildirim, uygulama süreci içindeki o alıcıya doğrudan iletilir (cihazın ayarları değişmez,
     * başka uygulamalar etkilenmez).
     */
    private fun ayarla(ac: Boolean): Boolean {
        val ctx = baglam ?: return false.also { Log.w(ETIKET, "bağlam yok (kur() çağrılmadı)") }
        val port = if (ac) port() else 0
        // Not: System.setProperty("https.proxyHost") KULLANILMAZ; süreçteki tüm OkHttp istemcilerini de
        // vekile yönlendirirdi. Chromium yalnızca bildirimdeki ProxyInfo'yu okur.
        var alicilar = proxyAlicilari(ctx)
        if (alicilar.isEmpty() && ac) {
            // Chromium alıcıyı ağ katmanı ilk kez çalışınca kaydeder: görünmez bir WebView ile
            // yerel vekile zararsız bir istek attırıp katmanı başlat, sonra tekrar bak.
            agKatmaniniBaslat(ctx, port)
            alicilar = proxyAlicilari(ctx)
        }
        if (alicilar.isEmpty()) return false.also { Log.w(ETIKET, "WebView proxy alıcısı bulunamadı") }

        val intent = Intent("android.intent.action.PROXY_CHANGE")
        if (ac) intent.putExtra("android.intent.extra.PROXY_INFO", ProxyInfo.buildDirectProxy("127.0.0.1", port))
        val tamam = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            alicilar.forEach { runCatching { it.onReceive(ctx, intent) } }
            tamam.countDown()
        }
        tamam.await(5, TimeUnit.SECONDS)
        Thread.sleep(300) // Chromium'un yeni ayarı uygulaması için kısa bekleme
        Log.d(ETIKET, if (ac) "WebView → 127.0.0.1:$port (${alicilar.size} alıcı)" else "WebView vekili kapatıldı")
        return true
    }

    private fun agKatmaniniBaslat(ctx: Context, port: Int) {
        val bitti = CountDownLatch(1)
        var wv: android.webkit.WebView? = null
        Handler(Looper.getMainLooper()).post {
            runCatching {
                wv = android.webkit.WebView(ctx).apply {
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) { bitti.countDown() }
                    }
                    // Yerel vekil düz HTTP isteğine kısa bir yanıt verir (aşağıda "/_baslat")
                    loadUrl("http://127.0.0.1:$port/_baslat")
                }
            }.onFailure { bitti.countDown() }
        }
        bitti.await(8, TimeUnit.SECONDS)
        // Alıcı kaydı ağ katmanı başladıktan kısa süre sonra olur
        for (i in 0 until 20) { if (proxyAlicilari(ctx).isNotEmpty()) break; Thread.sleep(150) }
        Handler(Looper.getMainLooper()).post { runCatching { wv?.stopLoading(); wv?.destroy() } }
        Log.d(ETIKET, "ağ katmanı başlatıldı, alıcı: ${proxyAlicilari(ctx).size}")
    }

    private fun proxyAlicilari(ctx: Context): List<BroadcastReceiver> {
        val app = ctx.applicationContext
        val yuklu = Class.forName("android.app.Application").getDeclaredField("mLoadedApk").apply { isAccessible = true }.get(app)
        val alicilar = Class.forName("android.app.LoadedApk").getDeclaredField("mReceivers").apply { isAccessible = true }.get(yuklu) as Map<*, *>
        return alicilar.values.flatMap { (it as? Map<*, *>)?.keys.orEmpty() }
            .filterIsInstance<BroadcastReceiver>()
            .filter { it.javaClass.name.contains("ProxyChangeListener") }
    }
}
