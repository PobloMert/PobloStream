// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class CizgiMax : MainAPI() {
    override var mainUrl              = "https://cizgimax.online"
    override var name                 = "CizgiMax"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Cartoon)

    override val mainPage = mainPageOf(
        "${mainUrl}/diziler/"                                       to "Son Eklenenler",
        "${mainUrl}/tur/aile/"                                      to "Aile",
        "${mainUrl}/tur/aksiyon/"                                   to "Aksiyon",
        "${mainUrl}/tur/komedi/"                                    to "Komedi",
        "${mainUrl}/tur/bilim-kurgu/"                               to "Bilim Kurgu",
        "${mainUrl}/tur/cocuk/"                                     to "Çocuk",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url      = if (page > 1) "${request.data}?page=$page" else request.data
        val document = EngelAsici.http.get(url).document
        val kartlar  = document.select("div.film-item")
        val home     = kartlar.mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home, kartlar.size >= 30)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val linkEl    = this.selectFirst("a.film-name") ?: return null
        val title     = linkEl.text().trim().ifBlank { attr("data-anime-name") }.trim()
        if (title.isBlank()) return null
        val href      = fixUrlNull(linkEl.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a.poster img")?.let { it.attr("src").ifBlank { it.attr("data-src") } })

        return newTvSeriesSearchResponse(title, href, TvType.Cartoon) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = EngelAsici.http.get("${mainUrl}/ara/?q=${query}").document

        return document.select("div.film-item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = EngelAsici.http.get(url).document

        val title       = document.selectFirst("h1")?.text()?.trim()?.removeSuffix(" izle")?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content")) ?: return null
        val description = document.selectFirst("meta[name=description]")?.attr("content")?.trim()
        val tags        = document.selectFirst("meta[name=keywords]")?.attr("content")?.split(",")?.mapNotNull { it.trim().takeIf { t -> t.isNotEmpty() } } ?: listOf()

        // ! Bolum linkleri: /dizi-adi-2-sezon-7-bolum-izle/
        val bolumRegex = Regex("""-(\d+)-sezon-(\d+)-bolum""")
        val episodes = document.select("a.ep-num-btn").mapNotNull {
            val epHref = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
            val epName = it.attr("title").trim().ifBlank { it.selectFirst("span.ep-num-label")?.text()?.trim() ?: "" }
            if (epName.isBlank()) return@mapNotNull null
            val grup = bolumRegex.find(epHref)

            newEpisode(epHref) {
                this.name    = epName
                this.season  = grup?.groupValues?.get(1)?.toIntOrNull() ?: 1
                this.episode = grup?.groupValues?.get(2)?.toIntOrNull()
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.Cartoon, episodes) {
            this.posterUrl = poster
            this.plot      = description
            this.tags      = tags
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        // ! 26.11.2026: eski `ul.linkler li [data-frame]` yapisi kaldirildi. Yeni site
        // ! sunuculari script icinde base64 JSON olarak veriyor (/api/stream/... proxy).
        // ! Cozucu Kekik'in guncel CizgiMax kaynak katmaninda (CizgiMaxDonor).
        return CizgiMaxDonor().loadLinks(data, isCasting, subtitleCallback, callback)
    }
}
