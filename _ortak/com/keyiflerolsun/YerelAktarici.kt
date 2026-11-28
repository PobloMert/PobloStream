package com.keyiflerolsun

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import android.util.Base64
import java.util.concurrent.Executors

/**
 * Engelli video sunucuları için yerel aktarıcı.
 *
 * Videoyu eklenti değil uygulamanın oynatıcısı indirir; oynatıcı EngelAsici'yi kullanamaz.
 * Bu sınıf 127.0.0.1 üzerinde küçük bir HTTP sunucusu açar: oynatıcı buraya bağlanır, istek
 * EngelAsici.client ile (DoH + bölünmüş ClientHello) asıl sunucuya iletilir.
 *  - HLS çalma listelerindeki (m3u8) adresler yeniden yazılır, parçalar da aktarıcıdan geçer.
 *  - Diğer içerik (ts/mp4/vtt…) Range başlığıyla birlikte olduğu gibi aktarılır.
 *
 * Kullanım:  url = YerelAktarici.adres(gercekUrl, mapOf("Referer" to "https://site/"))
 *
 * ! _ortak/ klasöründedir; kök build.gradle.kts her eklentiye ekler.
 */
object YerelAktarici {
    private const val AYRAC_KV = '\u0001'
    private const val AYRAC_SATIR = '\u0002'

    @Volatile private var sunucu: ServerSocket? = null
    private val havuz = Executors.newCachedThreadPool { r -> Thread(r, "YerelAktarici").apply { isDaemon = true } }

    // android.util.Base64: java.util.Base64 eski Android sürümlerinde (API < 26) yok
    private const val B64 = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    private fun b64(s: String) = Base64.encodeToString(s.toByteArray(Charsets.UTF_8), B64)
    private fun b64coz(s: String) = String(Base64.decode(s, B64), Charsets.UTF_8)

    @Synchronized
    private fun port(): Int {
        sunucu?.takeIf { !it.isClosed }?.let { return it.localPort }
        val s = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        sunucu = s
        Thread({
            while (!s.isClosed) {
                val baglanti = runCatching { s.accept() }.getOrNull() ?: continue
                havuz.execute { runCatching { isle(baglanti) }; runCatching { baglanti.close() } }
            }
        }, "YerelAktarici-dinleyici").apply { isDaemon = true }.start()
        return s.localPort
    }

    /** Gerçek adresi, oynatıcının bağlanacağı yerel adrese çevirir. */
    fun adres(url: String, basliklar: Map<String, String> = emptyMap()): String {
        val h = basliklar.entries.joinToString(AYRAC_SATIR.toString()) { "${it.key}$AYRAC_KV${it.value}" }
        val uzanti = url.substringBefore("?").substringAfterLast("/").substringAfterLast(".", "").take(5)
        // Uzantıyı sona koymak oynatıcının biçimi tahmin etmesine yardım eder
        return "http://127.0.0.1:${port()}/a/${b64(url)}/${b64(h)}/v.${uzanti.ifBlank { "bin" }}"
    }

    /**
     * İçeriği uygulamanın görünmez tarayıcısıyla alan aktarıcı adresi. Cloudflare'in uygulamanın resim
     * yükleyicisine 403 verdiği sitelerde afiş için: `posterUrl = YerelAktarici.tarayiciIle(afis)`.
     * Plugin.load() içinde WebViewVekili.kur(context) çağrılmış olmalı.
     */
    fun tarayiciIle(url: String): String =
        "http://127.0.0.1:${port()}/t/${b64(url)}/v.${url.substringBefore("?").substringAfterLast(".", "jpg").take(4)}"

    private val yoklamaIstemcisi by lazy {
        com.lagradost.cloudstream3.app.baseClient.newBuilder()
            .callTimeout(6, java.util.concurrent.TimeUnit.SECONDS).followRedirects(false).build()
    }

    /**
     * Adrese oynatıcının kendi yolundan (sistem DNS'i, düz TLS) ulaşılabiliyorsa adresi aynen,
     * ulaşılamıyorsa (engel) aktarıcı adresini döndürür. Herhangi bir HTTP yanıtı "ulaşılabilir" sayılır.
     */
    fun gerekirse(url: String, basliklar: Map<String, String> = emptyMap()): String {
        val ulasiliyor = runCatching {
            val istek = Request.Builder().url(url).apply { basliklar.forEach { (k, v) -> header(k, v) } }.head().build()
            yoklamaIstemcisi.newCall(istek).execute().use { true }
        }.getOrDefault(false)
        return if (ulasiliyor) url else adres(url, basliklar)
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

    private fun isle(baglanti: Socket) {
        baglanti.soTimeout = 30_000
        val giris = BufferedInputStream(baglanti.getInputStream())
        val cikis = baglanti.getOutputStream()

        val istekSatiri = satirOku(giris) ?: return
        val istemciBasliklari = mutableMapOf<String, String>()
        while (true) {
            val satir = satirOku(giris) ?: break
            if (satir.isEmpty()) break
            val i = satir.indexOf(':')
            if (i > 0) istemciBasliklari[satir.substring(0, i).trim().lowercase()] = satir.substring(i + 1).trim()
        }

        val parcalar = istekSatiri.split(" ")
        val yontem = parcalar.getOrNull(0) ?: "GET"
        val yol = parcalar.getOrNull(1)?.split("/") ?: return   // ["", "a", url, basliklar, dosya]
        if (yol.size >= 3 && yol[1] == "t") {
            // Tarayıcı yolu: içerik görünmez tarayıcının fetch()'iyle alınır (Cloudflare'li sitelerin afişleri)
            val hedef = runCatching { b64coz(yol[2]) }.getOrNull() ?: return yaz(cikis, 400, "text/plain", "adres".toByteArray())
            val sonuc = runCatching { TarayiciGetir.resim(hedef) }.getOrNull()
                ?: return yaz(cikis, 502, "text/plain", "tarayici".toByteArray())
            return yaz(cikis, 200, sonuc.first, sonuc.second)
        }
        if (yol.size < 4 || yol[1] != "a") return yaz(cikis, 404, "text/plain", "yok".toByteArray())

        val hedef = runCatching { b64coz(yol[2]) }.getOrNull() ?: return yaz(cikis, 400, "text/plain", "adres".toByteArray())
        val basliklar = runCatching { b64coz(yol[3]) }.getOrDefault("")
            .split(AYRAC_SATIR).mapNotNull { s -> s.split(AYRAC_KV).takeIf { it.size == 2 }?.let { it[0] to it[1] } }

        val istek = Request.Builder().url(hedef).apply {
            basliklar.forEach { (k, v) -> header(k, v) }
            istemciBasliklari["range"]?.let { header("Range", it) }
            if (yontem == "HEAD") head()
        }.build()

        EngelAsici.client.newCall(istek).execute().use { yanit ->
            val tur = yanit.header("Content-Type") ?: ""
            val sonUrl = yanit.request.url.toString()
            val listeMi = tur.contains("mpegurl", true) || sonUrl.substringBefore("?").endsWith(".m3u8") ||
                hedef.substringBefore("?").endsWith(".m3u8")

            if (listeMi && yanit.isSuccessful && yontem != "HEAD") {
                val metin = yanit.body.string()
                if (metin.trimStart().startsWith("#EXTM3U")) {
                    val yeni = listeyiYenidenYaz(metin, sonUrl, basliklar.toMap())
                    return yaz(cikis, 200, "application/vnd.apple.mpegurl", yeni.toByteArray(Charsets.UTF_8))
                }
                return yaz(cikis, yanit.code, tur.ifBlank { "application/octet-stream" }, metin.toByteArray(Charsets.UTF_8))
            }

            // Ham aktarım (Range korunur)
            val sb = StringBuilder("HTTP/1.1 ${yanit.code} ${yanit.message.ifBlank { "OK" }}\r\n")
            sb.append("Content-Type: ${tur.ifBlank { "application/octet-stream" }}\r\n")
            listOf("Content-Length", "Content-Range", "Accept-Ranges").forEach { k -> yanit.header(k)?.let { sb.append("$k: $it\r\n") } }
            sb.append("Access-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n")
            cikis.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
            if (yontem != "HEAD") yanit.body.byteStream().use { it.copyTo(cikis, 64 * 1024) }
            cikis.flush()
        }
    }

    /** m3u8 içindeki tüm adresleri (satırlar ve URI="…" öznitelikleri) aktarıcıya yönlendirir. */
    private fun listeyiYenidenYaz(metin: String, taban: String, basliklar: Map<String, String>): String {
        val tabanUrl = taban.toHttpUrlOrNull()
        fun cevir(u: String): String {
            val mutlak = tabanUrl?.resolve(u.trim())?.toString() ?: u.trim()
            return adres(mutlak, basliklar)
        }
        return metin.lineSequence().joinToString("\n") { satir ->
            when {
                satir.isBlank() -> satir
                satir.startsWith("#") -> Regex("""URI="([^"]+)"""").replace(satir) { m -> "URI=\"${cevir(m.groupValues[1])}\"" }
                else -> cevir(satir)
            }
        }
    }

    private fun yaz(cikis: OutputStream, kod: Int, tur: String, govde: ByteArray) {
        val baslik = "HTTP/1.1 $kod ${if (kod == 200) "OK" else "Hata"}\r\nContent-Type: $tur\r\n" +
            "Content-Length: ${govde.size}\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
        cikis.write(baslik.toByteArray(Charsets.ISO_8859_1)); cikis.write(govde); cikis.flush()
    }
}
