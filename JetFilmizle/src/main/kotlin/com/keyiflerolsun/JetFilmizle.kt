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
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

class JetFilmizle : MainAPI() {
    override var mainUrl              = AlanAdi.al("JetFilmizle", "https://jetfilmizle.app")
    override var name                 = "JetFilmizle"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    // ! CloudFlare bypass
    private val interceptor      by lazy { TarayiciGetir.Yakalayici() } // Cloudflare: sayfa görünmez tarayıcıyla alınır



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
        val document = EngelAsici.http.get(urlpage, interceptor = interceptor).document

        // Yeni yapı: div.film-card-wrapper > a[title] + .film-poster img
        val home = document.select("div.film-card-wrapper").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val link = selectFirst("a[title]") ?: selectFirst("a") ?: return null
        var title = link.attr("title").takeIf { it.isNotBlank() } ?: link.text().takeIf { it.isNotBlank() } ?: return null
        title = title.replace(Regex("""\s+[İi]zle.*$"""), "").trim()

        val href = fixUrlNull(link.attr("href")) ?: return null
        val posterUrl = selectFirst(".film-poster img, img.card-img-top")?.let { fixUrlNull(it.attr("data-src") ?: it.attr("src")) }
            ?: selectFirst("img")?.let { fixUrlNull(it.attr("data-src") ?: it.attr("src")) }

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Yeni arama: GET /arama?q= (POST /filmara.php artık 404)
        val document = EngelAsici.http.get("${mainUrl}/arama?q=$query", referer = "${mainUrl}/", interceptor = interceptor).document
        return document.select("div.film-card-wrapper").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = EngelAsici.http.get(url, interceptor = interceptor).document

        val title       = document.selectFirst("h1.film-title")?.text()?.replace(Regex("""\s+[İi]zle.*$"""), "")?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("img.film-poster, img.card-img-top")?.attr("data-src")
            ?: document.selectFirst("img.film-poster, img.card-img-top")?.attr("src"))
        val description = document.selectFirst("div.film-description")?.text()?.trim()
        val year        = Regex("""\b(19|20)\d{2}\b""").find(document.selectFirst("section.film-detay.film-bilgileri-section")?.text() ?: "")?.groupValues?.get(0)?.toIntOrNull()
        val tags        = document.select("section.film-detay.film-bilgileri-section a").map { it.text() }.distinct()
        val actors      = document.select("section.film-detay.film-bilgileri-section a[href*='oyuncu']").mapNotNull {
            val name = it.text().trim(); if (name.isNotBlank()) Actor(name) else null
        }

        val recommendations = document.select("section.film-detay.benzer-filmler-section div.film-card-wrapper").mapNotNull {
            it.toSearchResult()
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
        val document = EngelAsici.http.get(data, interceptor = interceptor).document

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
                val jetvDoc    = EngelAsici.http.get(iframe, interceptor = interceptor).document
                val jetvIframe = fixUrlNull(jetvDoc.selectFirst("iframe")?.attr("src")) ?: continue
                Log.d("JTF", "jetvIframe » $jetvIframe")
                loadExtractor(jetvIframe, "${mainUrl}/", subtitleCallback, callback)
            } else if (iframe.contains("jetv.xyz")) {
                Log.d("JTF", "jetv » $iframe")
                val jetvDoc    = EngelAsici.http.get(iframe, interceptor = interceptor).document
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