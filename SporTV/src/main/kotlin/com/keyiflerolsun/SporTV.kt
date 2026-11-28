package com.keyiflerolsun

import android.util.Log
import kotlinx.coroutines.async
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

/**
 * SporTV - spor kanallarının KENDİ sitelerinden ücretsiz yayınladığı canlı yayınlar
 * (TRT Spor, TRT Spor Yıldız, A Spor, HT Spor, TJK TV, TAY TV).
 *
 * Kanal listesi JSON'dadır (spor_tv_kanallar.json; depodaki kopya her açılışta okunur, okunamazsa
 * gömülü kopya). Kanal eklemek/değiştirmek için yalnızca JSON'u düzenlemek yeter.
 *
 * Yayın adresi kaynak türüne göre bulunur:
 *   sabit    : JSON'daki m3u8 adresi (kanalın resmi yayın sunucusu)
 *   sayfa    : kanalın canlı yayın sayfası açılır, m3u8 adresi "desen" ile sayfadan alınır
 *   turkuvaz : Turkuvaz Medya oynatıcısının resmi API'si (getvideo) → VideoUrl
 * Bulunan adres açılıp #EXTM3U ile doğrulanır; olmazsa "yedek" adres denenir.
 */
class SporTV : MainAPI() {
    override var mainUrl              = "https://www.trt.net.tr"
    override var name                 = "SporTV"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = false
    override val supportedTypes       = setOf(TvType.Live)

    companion object {
        private const val ETIKET = "SporTV"
        /** JSON'da "gruplar" yoksa ana sayfadaki grup sırası. */
        private val GRUP_SIRASI = listOf("Spor Kanalları", "At Yarışı")
        /** Yayın akışı 5 dakika önbellekte tutulur. */
        private const val AKIS_MS = 5 * 60_000L
        private val TR = java.util.TimeZone.getTimeZone("Europe/Istanbul")
    }

    // ---------- JSON modeli ----------
    data class Kanal(
        val id: String,
        val ad: String,
        val grup: String? = null,
        val site: String? = null,
        val kaynak: String = "sabit",
        val url: String,
        val desen: String? = null,
        val yedek: String? = null,
        val referer: String? = null,
        val logo: String? = null,
        val aciklama: String? = null,
        val aktif: Boolean = true,
        /** Ana sayfada sıralama (küçük önce). */
        val sira: Int = 1000,
        /** "Şu an yayında" için kanalın resmi yayın akışı sayfası ve (saat, program adı) deseni. */
        val akis_url: String? = null,
        val akis_desen: String? = null
    ) {
        /** Logo yazılmamışsa kanalın kendi sitesinin simgesi. (Fonksiyon: JSON'a yazılmasın.) */
        fun gorsel(): String? = logo?.ifBlank { null }
            ?: site?.let { "https://www.google.com/s2/favicons?domain=${it.substringAfter("://").substringBefore("/")}&sz=256" }
    }

    data class Liste(
        val surum: Int? = null,
        val uzak_kanallar: String? = null,
        val kanallar: List<Kanal>? = null,
        /** Ana sayfadaki grup sırası. */
        val gruplar: List<String>? = null
    )

    data class TurkuvazVideo(val VideoUrl: String? = null, val VideoSmilUrl: String? = null)
    data class TurkuvazYanit(val success: Boolean? = null, val video: TurkuvazVideo? = null)

    // ---------- JSON yükleme ----------
    @Volatile private var listeCache: Liste? = null

    private suspend fun liste(): Liste {
        listeCache?.let { return it }
        val gomulu = tryParseJson<Liste>(SporTVKanallar.JSON)
            ?: throw ErrorLoadingException("Kanal listesi okunamadı")
        val uzak = gomulu.uzak_kanallar?.takeIf { it.isNotBlank() }?.let { adres ->
            runCatching {
                val y = EngelAsici.http.get(adres, timeout = 8)
                if (y.code == 200) tryParseJson<Liste>(y.text) else null
            }.onFailure { Log.d(ETIKET, "Uzak liste okunamadı, gömülü kullanılacak: ${it.message}") }.getOrNull()
        }
        // Uzak liste daha eskiyse (ör. önbellekte kalmış) gömülüyü kullan
        val sec = if (uzak?.kanallar.isNullOrEmpty() || (uzak?.surum ?: 0) < (gomulu.surum ?: 0)) gomulu else uzak!!
        listeCache = sec
        return sec
    }

    private suspend fun kanallar(): List<Kanal> = liste().kanallar.orEmpty().filter { it.aktif }.sortedBy { it.sira }

    // ---------- "Şu an yayında" ----------
    private val akisOnbellek = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String?>>()

    /** Kanalın resmi yayın akışından şu an yayındaki program (yoksa null). Hata olursa sessizce null. */
    private suspend fun simdi(k: Kanal): String? {
        val url = k.akis_url ?: return null
        val desen = k.akis_desen ?: return null
        akisOnbellek[k.id]?.let { (zaman, deger) -> if (System.currentTimeMillis() - zaman < AKIS_MS) return deger }
        val sonuc = runCatching {
            val html = EngelAsici.http.get(url, timeout = 8).text
            val simdiDk = java.util.Calendar.getInstance(TR).let { it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE) }
            // (saat, ad) çiftleri sayfa sırasıyla; saat "HH:mm" ya da "2026-11-28T23:00:00"
            Regex(desen, RegexOption.DOT_MATCHES_ALL).findAll(html).mapNotNull { m ->
                val saat = Regex("""(\d{2}):(\d{2})""").find(m.groupValues[1].substringAfter("T"))
                    ?: return@mapNotNull null
                val dk = saat.groupValues[1].toInt() * 60 + saat.groupValues[2].toInt()
                dk to org.jsoup.parser.Parser.unescapeEntities(m.groupValues[2].trim(), false)
            }.toList()
                .takeWhile { (dk, _) -> dk <= simdiDk } // gece yarısını geçen sonraki günün programlarına geçme
                .lastOrNull()?.second
        }.onFailure { Log.d(ETIKET, "${k.ad} yayın akışı okunamadı: ${it.message}") }.getOrNull()
        akisOnbellek[k.id] = System.currentTimeMillis() to sonuc
        return sonuc
    }

    private fun Kanal.arama(simdi: String? = null): SearchResponse = newLiveSearchResponse(
        // Kart başlığı: "TRT Spor · HAFTA SONU MANŞET" (uzunsa kısaltılır)
        if (simdi.isNullOrBlank()) ad else "$ad · " + simdi.let { if (it.length > 32) it.take(31) + "…" else it },
        toJson(), TvType.Live
    ) {
        this.posterUrl = gorsel()
        this.lang = "tr"
    }

    // ---------- MainAPI ----------
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val sira = liste().gruplar ?: GRUP_SIRASI
        val tumu = kanallar()
        // Yayın akışları aynı anda okunur (her biri en çok 8 sn; 5 dk önbellek)
        val simdiler = kotlinx.coroutines.coroutineScope {
            tumu.map { k -> k.id to async { simdi(k) } }.associate { (id, d) -> id to d.await() }
        }
        val gruplar = tumu.groupBy { it.grup ?: "Diğer" }
            .toList()
            .sortedBy { (g, _) -> sira.indexOf(g).let { if (it < 0) Int.MAX_VALUE else it } }
        return newHomePageResponse(
            gruplar.map { (baslik, liste) -> HomePageList(baslik, liste.map { it.arama(simdiler[it.id]) }, isHorizontalImages = true) },
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim().lowercase()
        return kanallar().filter { k -> k.ad.lowercase().contains(q) || k.id.contains(q) }.map { it.arama() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val k = tryParseJson<Kanal>(url) ?: throw ErrorLoadingException("Kanal bilgisi okunamadı")
        // Kayıtlı kanal JSON'u eskimiş olabilir: güncel listedeki halini kullan
        val guncel = kanallar().firstOrNull { it.id == k.id } ?: k
        return newLiveStreamLoadResponse(guncel.ad, url, guncel.toJson()) {
            this.posterUrl = guncel.gorsel()
            // CloudStream açıklamadaki satır sonlarını göstermiyor: cümleyi noktayla ayır
            this.plot = listOfNotNull(simdi(guncel)?.let { "Şu an yayında: ${it.trimEnd('.')}." }, guncel.aciklama).joinToString(" ")
            this.tags = listOfNotNull(guncel.grup, "Canlı", "Resmî yayın")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val k = tryParseJson<Kanal>(data) ?: throw ErrorLoadingException("Kanal bilgisi okunamadı")
        val referer = k.referer ?: k.site?.let { "$it/" } ?: ""

        val adaylar = buildList {
            runCatching { coz(k) }
                .onFailure { Log.w(ETIKET, "${k.ad}: kaynak çözülemedi (${it.message}), yedek denenecek") }
                .getOrNull()?.let { add(it) }
            k.yedek?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.distinct()

        val akis = adaylar.firstOrNull { calisiyor(it, referer) }
            ?: throw ErrorLoadingException("${k.ad} yayını şu an açılmıyor")
        Log.d(ETIKET, "${k.ad} → $akis")

        callback.invoke(
            newExtractorLink(source = name, name = k.ad, url = akis, type = ExtractorLinkType.M3U8) {
                this.referer = referer
                this.headers = if (referer.isNotBlank()) mapOf("Referer" to referer, "Origin" to referer.trimEnd('/')) else emptyMap()
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }

    // ---------- yayın adresini bulma ----------
    private suspend fun coz(k: Kanal): String = when (k.kaynak) {
        "sayfa" -> {
            val desen = Regex(k.desen ?: throw ErrorLoadingException("desen yok"))
            val html = EngelAsici.http.get(k.url, referer = k.referer).text
            desen.find(html)?.value?.replace("\\/", "/")?.replace("&amp;", "&")
                ?: throw ErrorLoadingException("sayfada yayın adresi yok")
        }
        "turkuvaz" -> {
            val y = EngelAsici.http.get(
                k.url,
                headers = mapOf("X-isApp" to "false", "Content-Type" to "application/json", "Origin" to (k.site ?: "")),
                referer = k.referer
            )
            val v = tryParseJson<TurkuvazYanit>(y.text)?.video
            (v?.VideoUrl ?: v?.VideoSmilUrl)?.takeIf { it.contains(".m3u8") }
                ?: throw ErrorLoadingException("oynatıcı API'si adres vermedi")
        }
        else -> k.url
    }

    /** Adres açılıyor ve gerçekten bir HLS listesi mi? */
    private suspend fun calisiyor(url: String, referer: String): Boolean = runCatching {
        val y = EngelAsici.http.get(url, referer = referer.ifBlank { null }, timeout = 10)
        y.code == 200 && y.text.trimStart().startsWith("#EXTM3U")
    }.getOrDefault(false)
}
