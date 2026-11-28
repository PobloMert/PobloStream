// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.StringUtils.decodeUri
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

class DiziBox : MainAPI() {
    init {
        // Sitenin her sayfaya gömdüğü ~4.900 dizilik arama listesi (sayfanın ~1,6 MB'ı); eklenti
        // kullanmıyor: tarayıcı yolunda sayfa eklentiye aktarılmadan önce atılır (TV'de hız)
        TarayiciGetir.kirp("dizibox.live", "li.search-target")
    }

    override var mainUrl              = AlanAdi.al("DiziBox", "https://www.dizibox.live")
    override var name                 = "DiziBox"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)

    // ! CloudFlare bypass
    override var sequentialMainPage = false // kategoriler aynı anda (TarayiciGetir fetch\'leri paralel)
    override var sequentialMainPageDelay       = 50L  // ? 0.05 saniye
    override var sequentialMainPageScrollDelay = 50L  // ? 0.05 saniye

    // ! CloudFlare v2
    private val interceptor      by lazy { TarayiciGetir.Yakalayici() } // Cloudflare: sayfa görünmez tarayıcıyla alınır



    override val mainPage = mainPageOf(
        "${mainUrl}/ulke/turkiye"              to "Yerli",
        "${mainUrl}/dizi-arsivi/page/SAYFA/"   to "Dizi Arşivi",
        "${mainUrl}/tur/aile/page/SAYFA/"      to "Aile",
        "${mainUrl}/tur/aksiyon/page/SAYFA"    to "Aksiyon",
        "${mainUrl}/tur/animasyon/page/SAYFA"  to "Animasyon",
        "${mainUrl}/tur/belgesel/page/SAYFA"   to "Belgesel",
        "${mainUrl}/tur/bilimkurgu/page/SAYFA" to "Bilimkurgu",
        "${mainUrl}/tur/biyografi/page/SAYFA"  to "Biyografi",
        "${mainUrl}/tur/dram/page/SAYFA"       to "Dram",
        "${mainUrl}/tur/drama/page/SAYFA"      to "Drama",
        "${mainUrl}/tur/fantastik/page/SAYFA"  to "Fantastik",
        "${mainUrl}/tur/gerilim/page/SAYFA"    to "Gerilim",
        "${mainUrl}/tur/gizem/page/SAYFA"      to "Gizem",
        "${mainUrl}/tur/komedi/page/SAYFA"     to "Komedi",
        "${mainUrl}/tur/korku/page/SAYFA"      to "Korku",
        "${mainUrl}/tur/macera/page/SAYFA"     to "Macera",
        "${mainUrl}/tur/muzik/page/SAYFA"      to "Müzik",
        "${mainUrl}/tur/muzikal/page/SAYFA"    to "Müzikal",
        "${mainUrl}/tur/reality-tv/page/SAYFA" to "Reality TV",
        "${mainUrl}/tur/romantik/page/SAYFA"   to "Romantik",
        "${mainUrl}/tur/savas/page/SAYFA"      to "Savaş",
        "${mainUrl}/tur/spor/page/SAYFA"       to "Spor",
        "${mainUrl}/tur/suc/page/SAYFA"        to "Suç",
        "${mainUrl}/tur/tarih/page/SAYFA"      to "Tarih",
        "${mainUrl}/tur/western/page/SAYFA"    to "Western",
        "${mainUrl}/tur/yarisma/page/SAYFA"    to "Yarışma"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url      = request.data.replace("SAYFA", "$page")
        val document = EngelAsici.http.get(
            url,
            cookies     = mapOf(
                "LockUser"      to "true",
                "isTrustedUser" to "true",
                "dbxu"          to "1743289650198"
            ),
            interceptor = interceptor, cacheTime = 60
        ).document
        val home = document.select("article.poster-article, article.article-series-poster, article.detailed-article").mapNotNull {
        it.toMainPageResult()
    }
        return newHomePageResponse(request.name, home)
    }

private fun Element.toMainPageResult(): SearchResponse? {
    val title = this.selectFirst("a")?.text() ?: return null
    val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
    val posterUrl = fixUrlNull(
        this.selectFirst("img")?.let { img ->
            img.attr("data-src").takeIf { it.isNotBlank() } ?: img.attr("src")
        }
    )

    return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
        // Cloudflare uygulamanın resim yükleyicisine 403 veriyor: afiş görünmez tarayıcıyla alınır
        this.posterUrl = posterUrl?.let { YerelAktarici.tarayiciIle(it) }
    }
}

    override suspend fun search(query: String): List<SearchResponse> {
        val document = EngelAsici.http.get(
            "${mainUrl}/?s=${query}",
            cookies     = mapOf(
                "LockUser"      to "true",
                "isTrustedUser" to "true",
                "dbxu"          to "1743289650198"
            ),
            interceptor = interceptor
        ).document

        return document.select("article.poster-article, article.article-series-poster, article.detailed-article").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = EngelAsici.http.get(
            url,
            cookies     = mapOf(
                "LockUser"      to "true",
                "isTrustedUser" to "true",
                "dbxu"          to "1743289650198"
            ),
            interceptor = interceptor
        ).document

        val title       = document.selectFirst("div.tv-overview h1 a")?.text()?.trim() ?: return null
        val poster      = fixUrlNull((document.selectFirst("div.tv-overview figure img") ?: document.selectFirst("#tv-story figure img"))?.attr("src"))
        // Masaüstü şablonu: div.tv-story p — mobil şablon (Android/TV'nin aldığı): div#tv-story > p
        val description = (document.selectFirst("div.tv-story p") ?: document.selectFirst("div#tv-story > p"))
            ?.text()?.trim()?.takeIf { it.isNotEmpty() }
        val year        = document.selectFirst("a[href*='/yil/']")?.text()?.trim()?.toIntOrNull()
        val tags        = document.select("a[href*='/tur/']").map { it.text() }
        val actors      = document.select("a[href*='/oyuncu/']").map { Actor(it.text()) }
        val trailer     = document.selectFirst("div.tv-overview iframe")?.attr("src")

        val episodeList = mutableListOf<Episode>()
        document.select("div#seasons-list a").forEach {
            val epUrl = fixUrlNull(it.attr("href")) ?: return@forEach
            val epDoc = EngelAsici.http.get(
                epUrl,
                cookies     = mapOf(
                    "LockUser"      to "true",
                    "isTrustedUser" to "true",
                    "dbxu"          to "1743289650198"
                ),
                interceptor = interceptor
            ).document

            epDoc.select("article.grid-box").forEach ep@ { epElem ->
                val epTitle   = epElem.selectFirst("div.post-title a")?.text()?.trim() ?: return@ep
                val epHref    = fixUrlNull(epElem.selectFirst("div.post-title a")?.attr("href")) ?: return@ep
                val epSeason  = Regex("""(\d+)\. ?Sezon""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val epEpisode = Regex("""(\d+)\. ?Bölüm""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()

                episodeList.add(newEpisode(epHref) {
                    this.name = epTitle
                    this.season = epSeason
                    this.episode = epEpisode
                })
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList) {
            this.posterUrl = poster?.let { YerelAktarici.tarayiciIle(it) }
            this.plot      = description
            this.year      = year
            this.tags      = tags
            addActors(actors)
            addTrailer(trailer)
        }
    }

    private suspend fun iframeDecode(data:String, iframe:String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        @Suppress("NAME_SHADOWING") var iframe = iframe

        if (iframe.contains("/player/king/king.php")) {
            iframe = iframe.replace("king.php?v=", "king.php?wmode=opaque&v=")
            val subDoc = EngelAsici.http.get(
                iframe,
                referer     = data,
                cookies     = mapOf(
                    "LockUser"      to "true",
                    "isTrustedUser" to "true",
                    "dbxu"          to "1743289650198"
                ),
                interceptor = interceptor
            ).document
            val subFrame = subDoc.selectFirst("div#Player iframe")?.attr("src") ?: return false

            val iDoc          = EngelAsici.http.get(subFrame, referer="${mainUrl}/").text
            val cryptData     = Regex("""CryptoJS\.AES\.decrypt\("(.*)","""").find(iDoc)?.groupValues?.get(1) ?: return false
            val cryptPass     = Regex("""","(.*)"\);""").find(iDoc)?.groupValues?.get(1) ?: return false
            val decryptedData = CryptoJS.decrypt(cryptPass, cryptData)
            val decryptedDoc  = Jsoup.parse(decryptedData)
            val vidUrl        = Regex("""file: '(.*)',""").find(decryptedDoc.html())?.groupValues?.get(1) ?: return false

            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = vidUrl,
                    type = ExtractorLinkType.M3U8 // Tür olarak M3U8 ayarlandı
                ) {
                    headers = mapOf("Referer" to vidUrl) // Referer başlığı ayarlandı
                    quality = getQualityFromName("4k") // Kalite ayarlandı
                }
            )

        } else if (iframe.contains("/player/moly/moly.php")) {
            iframe = iframe.replace("moly.php?h=", "moly.php?wmode=opaque&h=")
            var subDoc = EngelAsici.http.get(
                iframe,
                referer     = data,
                cookies     = mapOf(
                    "LockUser"      to "true",
                    "isTrustedUser" to "true",
                    "dbxu"          to "1743289650198"
                ),
                interceptor = interceptor
            ).document

            val atobData = Regex("""unescape\("(.*)"\)""").find(subDoc.html())?.groupValues?.get(1)
            if (atobData != null) {
                val decodedAtob = atobData.decodeUri()
                val strAtob     = String(Base64.decode(decodedAtob, Base64.DEFAULT), Charsets.UTF_8)
                subDoc          = Jsoup.parse(strAtob)
            }

            val subFrame = subDoc.selectFirst("div#Player iframe")?.attr("src") ?: return false

            loadExtractor(subFrame, "${mainUrl}/", subtitleCallback, callback)

        } else if (iframe.contains("/player/haydi.php")) {
            iframe = iframe.replace("haydi.php?v=", "haydi.php?wmode=opaque&v=")
            var subDoc = EngelAsici.http.get(
                iframe,
                referer     = data,
                cookies     = mapOf(
                    "LockUser"      to "true",
                    "isTrustedUser" to "true",
                    "dbxu"          to "1743289650198"
                ),
                interceptor = interceptor
            ).document

            val atobData = Regex("""unescape\("(.*)"\)""").find(subDoc.html())?.groupValues?.get(1)
            if (atobData != null) {
                val decodedAtob = atobData.decodeUri()
                val strAtob     = String(Base64.decode(decodedAtob, Base64.DEFAULT), Charsets.UTF_8)
                subDoc          = Jsoup.parse(strAtob)
            }

            val subFrame = subDoc.selectFirst("div#Player iframe")?.attr("src") ?: return false

            loadExtractor(subFrame, "${mainUrl}/", subtitleCallback, callback)
        }

        return true
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("DZBX", "data » $data")
        val document = EngelAsici.http.get(
            data,
            cookies     = mapOf(
                "LockUser"      to "true",
                "isTrustedUser" to "true",
                "dbxu"          to "1743289650198"
            ),
            interceptor = interceptor
        ).document
        var iframe = document.selectFirst("div#video-area iframe")?.attr("src")?: return false
        Log.d("DZBX", "iframe » $iframe")

        iframeDecode(data, iframe, subtitleCallback, callback)

        document.select("div.video-toolbar option[value]").forEach {
            val altLink = it.attr("value")
            val subDoc  = EngelAsici.http.get(
                altLink,
                cookies     = mapOf(
                    "LockUser"      to "true",
                    "isTrustedUser" to "true",
                    "dbxu"          to "1743289650198"
                ),
                interceptor = interceptor
            ).document
            iframe = subDoc.selectFirst("div#video-area iframe")?.attr("src")?: return false
            Log.d("DZBX", "iframe » $iframe")

            iframeDecode(data, iframe, subtitleCallback, callback)
        }

        return true
    }
}
