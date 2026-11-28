// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLEncoder

class TurkAnime : MainAPI() {
    override var mainUrl              = AlanAdi.al("TurkAnime", "https://turkanime.to")
    override var name                 = "TurkAnime"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "${mainUrl}/anime-turu/aksiyon/"     to "Aksiyon",
        "${mainUrl}/anime-turu/macera/"      to "Macera",
        "${mainUrl}/anime-turu/fantastik/"   to "Fantastik",
        "${mainUrl}/anime-turu/komedi/"      to "Komedi",
        "${mainUrl}/anime-turu/dram/"        to "Dram",
        "${mainUrl}/anime-turu/romantizm/"   to "Romantizm",
        "${mainUrl}/anime-turu/bilim-kurgu/" to "Bilim Kurgu",
        "${mainUrl}/harf/A/"                 to "A ile Başlayanlar",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url      = if (page > 1) "${request.data}?sayfa=$page" else request.data
        val document = EngelAsici.http.get(url).document
        val items    = document.select("article.panel-card").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val link    = selectFirst("h2.panel-title a") ?: return null
        val href    = fixUrlNull(link.attr("href")) ?: return null
        val title   = link.text().trim().ifBlank { return null }
        val poster  = fixUrlNull(selectFirst("img.kart-afis-gorsel")?.attr("src"))
            ?: fixUrlNull(selectFirst("img")?.attr("src"))

        return newAnimeSearchResponse(title, href, TvType.Anime) { this.posterUrl = poster }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = EngelAsici.http.get("${mainUrl}/ara?q=${URLEncoder.encode(query, "UTF-8")}").document
        return document.select("article.panel-card").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = EngelAsici.http.get(url).document

        val title    = document.selectFirst("h1.panel-title")?.text()?.trim() ?: return null
        val poster   = fixUrlNull(document.selectFirst("img.anime-detay-afis-gorsel")?.attr("src"))
        val plot     = document.selectFirst("meta[name=description]")?.attr("content")
            ?.replace("&#039;", "'")
            ?.replace("&quot;", "\"")
            ?.replace("&amp;", "&")
            ?.trim()
        val tags     = document.select("a.anime-etiket").map { it.text().trim() }.filter { it.isNotBlank() }
        val year     = document.select("table.anime-bilgi tr")
            .firstOrNull { it.selectFirst("td b")?.text()?.contains("Başlama Tarihi") == true }
            ?.select("td")?.lastOrNull()?.text()?.take(4)?.toIntOrNull()

        val animeId  = Regex("""animeId=(\d+)""").find(document.html())?.groupValues?.get(1)
        val episodes = if (animeId != null) {
            val episodeDoc = EngelAsici.http.get(
                "${mainUrl}/ajax/bolumler&animeId=$animeId",
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                referer = url
            ).document

            episodeDoc.select("a.menum-satir").mapNotNull { element ->
                val href = fixUrlNull(element.attr("href")) ?: return@mapNotNull null
                val name = element.attr("title").ifBlank { element.text() }.trim()
                if (name.isBlank()) return@mapNotNull null

                newEpisode(href) {
                    this.name    = name
                    this.episode = Regex("""(\d+)""").find(name)?.groupValues?.get(1)?.toIntOrNull()
                }
            }
        } else emptyList()

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot      = plot
            this.tags      = tags
            this.year      = year
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = EngelAsici.http.get(data).document
        val sources  = document.select("[data-source]")
            .mapNotNull { it.attr("data-source").takeIf { source -> source.isNotBlank() } }
            .distinct()

        for (source in sources) {
            val embed = runCatching { EngelAsici.http.get(source, referer = data).document }.getOrNull() ?: continue
            val iframe = fixUrlNull(embed.selectFirst("iframe")?.attr("src")) ?: continue

            loadExtractor(iframe, "$mainUrl/", subtitleCallback, callback)
        }

        return true
    }
}
