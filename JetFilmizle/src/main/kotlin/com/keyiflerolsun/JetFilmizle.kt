// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.
// ! Yeniden yazıldı: Ahmet — yeni HTML yapısı (film-card-wrapper) + jetfilmizle.app

package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class JetFilmizle : MainAPI() {
    override var mainUrl              = "https://jetfilmizle.app"
    override var name                 = "JetFilmizle"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}"                                         to "Son Filmler",
        "${mainUrl}/netflix"                                 to "Netflix",
        "${mainUrl}/editorun-secimi"                         to "Editörün Seçimi",
        "${mainUrl}/turk-film-full-hd-izle"                  to "Türk Filmleri",
        "${mainUrl}/cizgi-filmler-full-izle"                 to "Çizgi Filmler",
        "${mainUrl}/kategoriler/yesilcam-filmleri-full-izle" to "Yeşilçam Filmleri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val baseUrl = request.data
        val urlpage = if (page == 1) baseUrl else "$baseUrl/page/$page"
        val document = app.get(urlpage).document

        // Yeni yapı: div.film-card-wrapper > a[title] + .film-poster img
        val home = document.select("div.film-card-wrapper").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val link = selectFirst("a[title]") ?: selectFirst("a") ?: return null
        var title = link.attr("title").takeIf { it.isNotBlank() } ?: link.text().takeIf { it.isNotBlank() } ?: return null
        title = title.substringBefore(" izle")

        val href = fixUrlNull(link.attr("href")) ?: return null

        val posterUrl = selectFirst(".film-poster img")?.let { fixUrlNull(it.attr("data-src") ?: it.attr("src")) }
            ?: selectFirst("img")?.let { fixUrlNull(it.attr("data-src") ?: it.attr("src")) }

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.post(
            "${mainUrl}/filmara.php",
            referer = "${mainUrl}/",
            data    = mapOf("s" to query)
        ).document

        // Arama sonucu da aynı kart yapısını kullanıyorsa:
        return document.select("div.film-card-wrapper, article.movie").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title       = document.selectFirst("section.movie-exp div.movie-exp-title")?.text()?.substringBefore(" izle")?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("section.movie-exp img")?.attr("data-src")) ?: fixUrlNull(document.selectFirst("section.movie-exp img")?.attr("src"))
        val yearDiv     = document.selectXpath("//div[@class='yap' and contains(strong, 'Vizyon') or contains(strong, 'Yapım')]").text().trim()
        val year        = Regex("""(\d{4})""").find(yearDiv)?.groupValues?.get(1)?.toIntOrNull()
        val description = document.selectFirst("section.movie-exp p.aciklama")?.text()?.trim()
        val tags        = document.select("section.movie-exp div.catss a").map { it.text() }
        val actors      = document.select("section.movie-exp div.oyuncu").mapNotNull {
            val name = it.selectFirst("div.name")?.text()
            val img  = fixUrlNull(it.selectFirst("img")?.attr("data-src"))
            if (name != null) Actor(name, img) else null
        }

        val recommendations = document.select("div#benzers article").mapNotNull {
            var recName      = it.selectFirst("h2 a")?.text() ?: it.selectFirst("h3 a")?.text() ?: it.selectFirst("h4 a")?.text() ?: it.selectFirst("h5 a")?.text() ?: it.selectFirst("h6 a")?.text() ?: return@mapNotNull null
            recName          = recName.substringBefore(" izle")

            val recHref      = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            val recPosterUrl = fixUrlNull(it.selectFirst("img")?.attr("data-src"))

            newMovieSearchResponse(recName, recHref, TvType.Movie) { this.posterUrl = recPosterUrl }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl       = poster
            this.year            = year
            this.plot            = description
            this.tags            = tags
            this.recommendations = recommendations
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("JTF", "data » $data")
        val document = app.get(data).document

        val iframes    = mutableListOf<String>()
        val iframe = fixUrlNull(document.selectFirst("div#movie iframe")?.attr("data-litespeed-src")) ?: fixUrlNull(document.selectFirst("div#movie iframe")?.attr("src"))
        Log.d("JTF", "iframe » $iframe")
        if (iframe != null) iframes.add(iframe)

        document.select("a.download-btn[href]").forEach downloadLinkForEach@{ link ->
            val href = link.attr("href")
            if (!href.contains("pixeldrain.com")) return@downloadLinkForEach
            val downloadLink = fixUrlNull(href) ?: return@downloadLinkForEach
            iframes.add(downloadLink)
        }

        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        for (iframe in iframes) {
            if (iframe.contains("d2rs")) {
                Log.d("JTF", "jetv » $iframe")
                val jetvDoc    = app.get(iframe).document
                val jetvIframe = fixUrlNull(jetvDoc.selectFirst("iframe")?.attr("src")) ?: continue
                Log.d("JTF", "jetvIframe » $jetvIframe")
                loadExtractor(jetvIframe, "${mainUrl}/", subtitleCallback, callback)
            } else if (iframe.contains("jetv.xyz")) {
                Log.d("JTF", "jetv » $iframe")
                val jetvDoc    = app.get(iframe).document
                val script = jetvDoc.select("script").find { it.data().contains("\"sources\": [") }?.data() ?: ""
                val source = script.substringAfter("\"sources\": [").substringBefore("]")
                    .replace("\'", "\"")
                val son: Source = objectMapper.readValue(source)
                callback.invoke(
                    newExtractorLink(
                        source = "Jetv - ${son.label}",
                        name = "Jetv - ${son.label}",
                        url = son.file,
                        type = ExtractorLinkType.M3U8
                    ) {
                        quality = Qualities.Unknown.value
                    }
                )
            } else {
                loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
            }
        }

        return true
    }

    // Jetv.xyz kaynak JSON yapisi
    private data class Source(
        val file: String,
        val label: String,
        val type: String = "hls"
    )

    private fun String.addMarks(str: String): String {
        return this.replace(Regex("\"?$str\"?"), "\"$str\"")
    }
}