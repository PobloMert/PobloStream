package com.keyiflerolsun

import com.lagradost.cloudstream3.app
import com.lagradost.nicehttp.Requests
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketImpl
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

/**
 * Türkiye'deki erişim engelini VPN'siz aşan HTTP istemcisi.
 *
 * Engel iki katmanlı: (1) DNS cevap vermiyor, (2) TLS ClientHello içindeki site adı (SNI)
 * görülünce bağlantı kesiliyor. Bu istemci:
 *  1. Alan adını DNS-over-HTTPS ile (1.1.1.1, yedek 8.8.8.8 — IP adresine gidildiği için DNS gerekmez) çözer.
 *  2. ClientHello'yu SNI'nin ortasından ikiye bölüp iki ayrı TLS kaydı olarak gönderir.
 *     TLS standardına uygundur (sunucular parçalı kaydı birleştirir), engelin aradığı adı ise tek parçada göremez.
 *
 * Engelli olmayan sitelerde de zararsızdır (2026-11 testi: depodaki 16 sitenin hepsi aynı yanıtı verdi).
 *
 * ! Bu dosya _ortak/ klasöründedir ve kök build.gradle.kts tarafından her eklentiye otomatik eklenir.
 * ! Eklentilerde `app.get/post(...)` yerine `EngelAsici.http.get/post(...)` kullanın.
 */
object EngelAsici {
    private val dohClient: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    }
    private val dnsCache = ConcurrentHashMap<String, Pair<List<InetAddress>, Long>>()
    private val dohUrls = listOf(
        "https://1.1.1.1/dns-query?type=A&name=",
        "https://8.8.8.8/resolve?type=A&name="
    )
    private val aRecord = Regex(""""type"\s*:\s*1\s*,[^}]*?"data"\s*:\s*"([0-9.]+)"""")

    object DohDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            if (hostname.all { it.isDigit() || it == '.' } || hostname.contains(':')) return Dns.SYSTEM.lookup(hostname)
            dnsCache[hostname]?.let { (ips, until) -> if (System.currentTimeMillis() < until) return ips }
            for (base in dohUrls) {
                val ips = runCatching {
                    val body = dohClient.newCall(
                        Request.Builder().url(base + hostname).header("accept", "application/dns-json").build()
                    ).execute().use { it.body.string() }
                    aRecord.findAll(body).map { InetAddress.getByAddress(hostname, InetAddress.getByName(it.groupValues[1]).address) }.toList()
                }.getOrNull().orEmpty()
                if (ips.isNotEmpty()) {
                    dnsCache[hostname] = ips to System.currentTimeMillis() + 10 * 60_000
                    return ips
                }
            }
            return Dns.SYSTEM.lookup(hostname)
        }
    }

    /** İlk TLS kaydını (ClientHello) SNI'nin ortasından iki kayda böler; gerisini aynen geçirir. */
    internal class BolenCikis(out: OutputStream) : FilterOutputStream(out) {
        private var ilkYazim = true

        // Android'in TLS katmanı (Conscrypt) ClientHello'yu birden çok write() ile yazabilir
        // (ör. önce 5 baytlık başlık, sonra gövde). İlk TLS kaydı tamamlanana kadar biriktirilir.
        private val tampon = java.io.ByteArrayOutputStream()

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (!ilkYazim) { out.write(b, off, len); return }
            tampon.write(b, off, len)
            val t = tampon.toByteArray()
            if (t[0] != 0x16.toByte()) { gecir(t, "tls-degil"); return }   // TLS handshake değil: dokunma
            if (t.size < 5) return                                           // başlık henüz tamamlanmadı
            val kayitUz = ((t[3].toInt() and 0xff) shl 8) or (t[4].toInt() and 0xff)
            if (t.size < 5 + kayitUz) return                                 // kayıt henüz tamamlanmadı
            ilkYazim = false; tampon.reset()
            val bolunmus = bol(t, 0, t.size)
            if (bolunmus != null) { out.write(bolunmus); out.flush() } else out.write(t)
        }

        // Tamamlanmamış ilk kayıt tamponda bekler; flush yalnızca alttaki akışı boşaltır
        override fun flush() = out.flush()

        override fun close() {
            if (ilkYazim && tampon.size() > 0) gecir(tampon.toByteArray(), "kapanis")
            super.close()
        }

        private fun gecir(t: ByteArray, neden: String) {
            ilkYazim = false; tampon.reset()
            android.util.Log.d("EngelAsici", "bölme yok ($neden), ${t.size} bayt aynen gönderildi")
            out.write(t); out.flush()
        }

        private fun bol(b: ByteArray, off: Int, len: Int): ByteArray? {
            if (len < 6 || b[off] != 0x16.toByte()) return null          // TLS handshake kaydı değil
            val kayitUz = ((b[off + 3].toInt() and 0xff) shl 8) or (b[off + 4].toInt() and 0xff)
            if (len < 5 + kayitUz || kayitUz < 2) return null            // kayıt tek yazımda değil
            val sni = sniOrtasi(b, off + 5, kayitUz)
            val kes = sni ?: 1                                           // payload içi bölme noktası
            android.util.Log.d("EngelAsici", "ClientHello ${kayitUz} bayt, ${if (sni != null) "SNI ortasından ($kes)" else "SNI bulunamadı, 1. bayttan"} bölündü")
            val sonuc = ByteArray(len + 5)
            var p = 0
            fun baslik(uz: Int) {
                System.arraycopy(b, off, sonuc, p, 3); p += 3
                sonuc[p++] = (uz shr 8).toByte(); sonuc[p++] = uz.toByte()
            }
            baslik(kes); System.arraycopy(b, off + 5, sonuc, p, kes); p += kes
            baslik(kayitUz - kes); System.arraycopy(b, off + 5 + kes, sonuc, p, kayitUz - kes); p += kayitUz - kes
            System.arraycopy(b, off + 5 + kayitUz, sonuc, p, len - 5 - kayitUz)
            return sonuc
        }

        /** ClientHello'yu ayrıştırıp server_name değerinin ortasının payload ofsetini döndürür. */
        private fun sniOrtasi(b: ByteArray, bas: Int, uz: Int): Int? = runCatching {
            fun u8(i: Int) = b[bas + i].toInt() and 0xff
            fun u16(i: Int) = (u8(i) shl 8) or u8(i + 1)
            if (u8(0) != 0x01) return null                               // ClientHello değil
            var i = 4 + 2 + 32                                           // başlık + sürüm + random
            i += 1 + u8(i)                                               // session id
            i += 2 + u16(i)                                              // cipher suites
            i += 1 + u8(i)                                               // compression
            val uzantiSonu = i + 2 + u16(i); i += 2
            while (i + 4 <= uzantiSonu && i + 4 <= uz) {
                val tip = u16(i); val eUz = u16(i + 2)
                if (tip == 0) {                                          // server_name
                    val adUz = u16(i + 4 + 3)
                    val adBas = i + 4 + 5
                    return if (adUz > 1 && adBas + adUz <= uz) adBas + adUz / 2 else null
                }
                i += 4 + eUz
            }
            null
        }.getOrNull()
    }

    /** Kendi gerçek soketini saran, SocketImpl'i olmayan soket: SSL katmanı yazmaları mutlaka buradan geçer. */
    private class BolenSoket : Socket(null as SocketImpl?) {
        private val ic = Socket()
        private val cikis: OutputStream by lazy { BolenCikis(ic.getOutputStream()) }

        override fun connect(endpoint: SocketAddress?) = ic.connect(endpoint)
        override fun connect(endpoint: SocketAddress?, timeout: Int) = ic.connect(endpoint, timeout)
        override fun bind(bindpoint: SocketAddress?) = ic.bind(bindpoint)
        override fun getInputStream(): InputStream = ic.getInputStream()
        override fun getOutputStream(): OutputStream = cikis
        override fun close() = ic.close()
        override fun shutdownInput() = ic.shutdownInput()
        override fun shutdownOutput() = ic.shutdownOutput()
        override fun isConnected() = ic.isConnected
        override fun isBound() = ic.isBound
        override fun isClosed() = ic.isClosed
        override fun isInputShutdown() = ic.isInputShutdown
        override fun isOutputShutdown() = ic.isOutputShutdown
        override fun getInetAddress(): InetAddress? = ic.inetAddress
        override fun getLocalAddress(): InetAddress? = ic.localAddress
        override fun getPort() = ic.port
        override fun getLocalPort() = ic.localPort
        override fun getRemoteSocketAddress(): SocketAddress? = ic.remoteSocketAddress
        override fun getLocalSocketAddress(): SocketAddress? = ic.localSocketAddress
        override fun setSoTimeout(timeout: Int) { ic.soTimeout = timeout }
        override fun getSoTimeout() = ic.soTimeout
        override fun setTcpNoDelay(on: Boolean) { ic.tcpNoDelay = on }
        override fun getTcpNoDelay() = ic.tcpNoDelay
        override fun setKeepAlive(on: Boolean) { ic.keepAlive = on }
        override fun getKeepAlive() = ic.keepAlive
        override fun setSoLinger(on: Boolean, linger: Int) = ic.setSoLinger(on, linger)
        override fun getSoLinger() = ic.soLinger
        override fun setReceiveBufferSize(size: Int) { ic.receiveBufferSize = size }
        override fun getReceiveBufferSize() = ic.receiveBufferSize
        override fun setSendBufferSize(size: Int) { ic.sendBufferSize = size }
        override fun getSendBufferSize() = ic.sendBufferSize
        override fun setReuseAddress(on: Boolean) { ic.reuseAddress = on }
        override fun getReuseAddress() = ic.reuseAddress
        override fun setTrafficClass(tc: Int) { ic.trafficClass = tc }
        override fun getTrafficClass() = ic.trafficClass
        override fun setOOBInline(on: Boolean) { ic.oobInline = on }
        override fun getOOBInline() = ic.oobInline
        override fun sendUrgentData(data: Int) = ic.sendUrgentData(data)
        override fun toString() = "BolenSoket[$ic]"
    }

    private object BolenSoketFabrikasi : SocketFactory() {
        override fun createSocket(): Socket = BolenSoket()
        override fun createSocket(host: String, port: Int): Socket = BolenSoket().apply { connect(InetSocketAddress(host, port)) }
        override fun createSocket(host: String, port: Int, localHost: InetAddress?, localPort: Int): Socket =
            BolenSoket().apply { bind(InetSocketAddress(localHost, localPort)); connect(InetSocketAddress(host, port)) }
        override fun createSocket(host: InetAddress, port: Int): Socket = BolenSoket().apply { connect(InetSocketAddress(host, port)) }
        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
            BolenSoket().apply { bind(InetSocketAddress(localAddress, localPort)); connect(InetSocketAddress(address, port)) }
    }

    /**
     * Güvenlik ağı: bölmeli bağlantı bir G/Ç hatasıyla düşerse (ör. bir cihazın TLS katmanı
     * özel soketi kabul etmezse) istek uygulamanın normal istemcisiyle bir kez daha denenir.
     * Böylece hiçbir eklenti bu katman yüzünden eskisinden kötü çalışmaz.
     */
    private val yedekInterceptor = Interceptor { chain ->
        try {
            chain.proceed(chain.request())
        } catch (e: IOException) {
            if (chain.call().isCanceled()) throw e
            app.baseClient.newCall(chain.request()).execute()
        }
    }

    val client: OkHttpClient by lazy {
        app.baseClient.newBuilder()
            .dns(DohDns)
            .socketFactory(BolenSoketFabrikasi)
            .addInterceptor(AlanAdi.Yonlendirici) // eski site adresini güncele çevirir (kayıtlı favoriler)
            .addInterceptor(yedekInterceptor)
            .build()
    }

    /** `app` yerine kullanılacak istek nesnesi: aynı get/post API'si, uygulamanın varsayılan başlıkları ve ayarları. */
    val http: Requests by lazy {
        Requests(
            baseClient          = client,
            defaultHeaders      = app.defaultHeaders,
            defaultReferer      = app.defaultReferer,
            defaultData         = app.defaultData,
            defaultCookies      = app.defaultCookies,
            defaultCacheTime    = app.defaultCacheTime,
            defaultCacheTimeUnit = app.defaultCacheTimeUnit,
            defaultTimeOut      = app.defaultTimeOut,
            responseParser      = app.responseParser
        )
    }

    /**
     * Uygulamanın resim yükleyicisi engelli siteye ulaşamaz. Afişin sunucusu cihazdan doğrudan
     * açılıyorsa afiş olduğu gibi kullanılır (daha hızlı, dış servise bağımlılık yok); açılmıyorsa
     * engelsiz bir resim vekilinden (wsrv.nl) geçirilir. (Not: Cloudflare'li siteler wsrv.nl'e ve
     * uygulamanın resim yükleyicisine resim vermeyebilir; bunu yalnızca gerçek tarayıcı aşar.) Sonuç sunucu başına bir kez denenip hatırlanır.
     */
    fun resim(url: String?): String? {
        if (url.isNullOrBlank() || !url.startsWith("http")) return url
        val h = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return url
        val acik = resimSunuculari.getOrPut(h) { dogrudanAcilir(h) }
        return if (acik) url else "https://wsrv.nl/?url=" + java.net.URLEncoder.encode(url, "UTF-8")
    }

    private val resimSunuculari = ConcurrentHashMap<String, Boolean>()

    /** Engel sayfasının IP'leri (BTK/ISS yönlendirmesi): bunlara çözülen ad engelli sayılır. */
    private val engelIpleri = setOf("195.175.254.2") // TTNet engel sayfası; diğer ISS'lerde el sıkışma zaten düşer

    /** Sistem DNS'i + normal TLS el sıkışması (uygulamanın resim yükleyicisinin yaptığı gibi) 3 sn içinde başarılı mı? */
    private fun dogrudanAcilir(host: String): Boolean = runCatching {
        val ip = InetAddress.getAllByName(host).firstOrNull { it.hostAddress !in engelIpleri } ?: return false
        Socket().use { s ->
            s.connect(InetSocketAddress(ip, 443), 3_000)
            s.soTimeout = 3_000
            (javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory)
                .createSocket(s, host, 443, false).use { (it as javax.net.ssl.SSLSocket).startHandshake() }
        }
        true
    }.getOrDefault(false)
}
