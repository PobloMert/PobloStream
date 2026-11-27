package com.nikyokki

import com.keyiflerolsun.EngelAsici

import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import java.security.MessageDigest

/**
 * 26.11.2026: site Next.js App Router'a geçti. Eski api/bg uçları 404,
 * __NEXT_DATA__ kalktı. Veriler SSR "flight" payload'ında, oynatıcı kaynakları
 * pichive iframe'lerinde.
 */
class AsyaWatch : MainAPI() {
    override var mainUrl = "https://asyawatch.com"
    override var name = "AsyaWatch"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "film" to "Yeni Eklenen Filmler",
        "film:Aksiyon" to "Aksiyon Filmleri",
        "film:Dram" to "Dram Filmleri",
        "film:Komedi" to "Komedi Filmleri",
        "film:Korku" to "Korku Filmleri",
        "film:Bilim Kurgu" to "Bilim Kurgu Filmleri",
        "dizi" to "Yeni Eklenen Diziler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val (kind, genre) = request.data.split(":", limit = 2).let {
            it[0] to it.getOrNull(1).orEmpty()
        }
        val list: List<SearchResponse> = if (kind == "film") {
            parseFilms(EngelAsici.http.get("$mainUrl/film-izle").text)
                .filter { genre.isBlank() || it.genres.contains(genre, ignoreCase = true) }
                .map { it.toSearchResponse() }
        } else {
            parseSeries(EngelAsici.http.get("$mainUrl/kesfet").text)
        }
        val from = (page - 1) * 20
        val items = if (from >= list.size) emptyList() else list.drop(from).take(20)
        return newHomePageResponse(request.name, items, from + 20 < list.size)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        val films = parseFilms(EngelAsici.http.get("$mainUrl/film-izle").text).map { it.toSearchResponse() }
        val series = parseSeries(EngelAsici.http.get("$mainUrl/kesfet").text)
        return (films + series).filter { it.name.lowercase().contains(q) }.take(40)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val html = EngelAsici.http.get(url).text
        val doc = Jsoup.parse(html)
        val ld = doc.select("script[type=application/ld+json]").mapNotNull { script ->
            runCatching { mapper.readTree(script.data()) }.getOrNull()
        }.firstOrNull { node -> node.path("@type").asText() in setOf("Movie", "TVSeries", "TVEpisode") }
            ?: throw ErrorLoadingException("İçerik bulunamadı")

        val title = ld.path("name").asText().ifBlank { doc.selectFirst("h1")?.text().orEmpty() }
        val poster = jsonText(ld, "image")?.let { cleanImg(it) }
        val plot = ld.path("description").asText().ifBlank { null }
        val year = ld.path("datePublished").asText().take(4).toIntOrNull()
        val score = ld.path("aggregateRating").path("ratingValue").asText()
            .takeIf { it.isNotBlank() && it != "0" && it != "0.0" }?.let { Score.from10(it) }
        val actors = ld.path("actor").mapNotNull { actor ->
            actor.path("name").asText().takeIf { it.isNotBlank() }?.let {
                Actor(it, jsonText(actor, "image"))
            }
        }
        val duration = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?").find(ld.path("timeRequired").asText())
            ?.let { (it.groupValues[1].toIntOrNull() ?: 0) * 60 + (it.groupValues[2].toIntOrNull() ?: 0) }
            ?: 0

        val episodes = ld.path("episode").mapNotNull { ep ->
            val epUrl = ep.path("url").asText().ifBlank { return@mapNotNull null }
            newEpisode(fixUrl(epUrl)) {
                this.name = ep.path("name").asText()
                this.season = 1
                this.episode = ep.path("episodeNumber").asInt(0)
            }
        }

        return if (ld.path("@type").asText() == "TVSeries" && episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.score = score
                this.duration = duration
                addActors(actors)
            }
        } else {
            newMovieLoadResponse(title, url, if (ld.path("@type").asText() == "TVEpisode") TvType.TvSeries else TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.score = score
                this.duration = duration
                addActors(actors)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = EngelAsici.http.get(data).text
        val embeds = EMBED_REGEX.findAll(html).map { it.value }.distinct().toList()
        if (embeds.isEmpty()) {
            Log.d("ASYA", "kaynak bulunamadı: $data")
            return false
        }
        var found = false
        for (embed in embeds) {
            found = if (embed.contains("pichive")) {
                resolvePichive(embed, subtitleCallback, callback) || found
            } else {
                loadExtractor(embed, mainUrl, subtitleCallback, callback) || found
            }
        }
        return found
    }

    /** pichive iframe'i: NP_CONFIG'deki master URL'e PoW token'ı eklenir. */
    private suspend fun resolvePichive(
        embed: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = EngelAsici.http.get(embed, referer = mainUrl).text
        val raw = Regex("""window\.NP_CONFIG\s*=\s*(\{.*?\});""").find(html)?.groupValues?.get(1)
            ?: return false
        val config = runCatching { mapper.readTree(raw) }.getOrNull() ?: return false
        val gate = config.path("gate").path("n").asText()
        val token = if (gate.isNotBlank()) proofOfWork(html, gate) else ""

        config.path("sources").forEach { source ->
            val master = source.path("master").asText().ifBlank { return@forEach }
            if (master.isBlank()) return@forEach
            val url = withToken(master, token)
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = source.path("name").asText(name),
                    url = url,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = embed
                    this.headers = mapOf("Referer" to embed)
                    this.quality = getQualityFromName(source.path("quality").asText())
                }
            )
        }
        config.path("subtitles").forEach { sub ->
            val subUrl = withToken(sub.path("url").asText(), token)
            if (subUrl.isNotBlank()) {
                subtitleCallback.invoke(
                    newSubtitleFile(sub.path("label").asText("Altyazı"), subUrl) {
                        this.headers = mapOf("Referer" to embed)
                    }
                )
            }
        }
        return config.path("sources").size() > 0
    }

    private fun withToken(url: String, token: String): String {
        if (url.isBlank() || token.isBlank()) return url
        return url + (if (url.contains("?")) "&" else "?") + "p=$token"
    }

    /**
     * Sayfa başına değişen NP_GATE fonksiyonunu okur: tuz, sayı tabanı,
     * birleştirme sırası, hash kelimesi ve maske. SHA-256 PoW token'ı üretir.
     */
    private fun proofOfWork(iframeHtml: String, gate: String): String {
        val fn = GATE_REGEX.find(iframeHtml)?.groupValues ?: return ""
        val params = fn[1].split(",").map { it.trim() }
        val body = fn[2]
        val saltMatch = SALT_REGEX.find(body) ?: return ""
        val saltVar = saltMatch.groupValues[1]
        val salt = saltMatch.groupValues[2].split(",")
            .mapNotNull { it.trim().toIntOrNull()?.toChar() }.joinToString("")
        val radix = RADIX_REGEX.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 36
        val candidateVar = CAND_REGEX.find(body)?.groupValues?.get(1) ?: return ""
        val wordIndex = WORD_REGEX.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 2
        val mask = MASK_REGEX.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 4095
        val expr = Regex("""${Regex.escape(params[1])}\(([^)]*)\)""")
            .find(body)?.groupValues?.get(1) ?: return ""
        val parts: List<String?> = expr.split("+").map { token ->
            val t = token.trim()
            when (t) {
                params[0] -> gate
                saltVar -> salt
                candidateVar -> null
                else -> t.trim('\'', '"')
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        for (i in 0 until 100_000_000) {
            val candidate = i.toString(radix)
            val input = parts.joinToString("") { it ?: candidate }
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            val at = wordIndex * 4
            val word = ((hash[at].toInt() and 0xff) shl 24) or
                    ((hash[at + 1].toInt() and 0xff) shl 16) or
                    ((hash[at + 2].toInt() and 0xff) shl 8) or
                    (hash[at + 3].toInt() and 0xff)
            if ((word and mask) == 0) return candidate
        }
        return ""
    }

    private fun parseSeries(html: String): List<SearchResponse> {
        val doc = Jsoup.parse(html)
        return doc.select("a.pc[href^=/dizi/]").mapNotNull { a ->
            val href = a.attr("href")
            if (href.count { it == '/' } > 2) return@mapNotNull null
            val title = a.selectFirst("b")?.text()?.ifBlank { null }
                ?: a.attr("aria-label").substringBefore(",").ifBlank { return@mapNotNull null }
            newTvSeriesSearchResponse(title, fixUrl(href), TvType.TvSeries) {
                this.posterUrl = a.selectFirst("img")?.attr("src")?.let { cleanImg(it) }
            }
        }.distinctBy { it.url }
    }

    private fun parseFilms(html: String): List<FilmCard> {
        val doc = Jsoup.parse(html)
        return doc.select("a.pc[href^=/film/]").mapNotNull { a ->
            val href = a.attr("href")
            val title = a.selectFirst("b")?.text()?.ifBlank { null } ?: return@mapNotNull null
            FilmCard(
                title = title,
                url = fixUrl(href),
                poster = a.selectFirst("img")?.attr("src")?.let { cleanImg(it) },
                score = a.selectFirst(".imdb")?.text()?.trim(),
                genres = a.selectFirst("small")?.text().orEmpty()
            )
        }.distinctBy { it.url }
    }

    private fun jsonText(node: com.fasterxml.jackson.databind.JsonNode, key: String): String? {
        val value = node.get(key) ?: return null
        return if (value.isTextual) value.asText() else value.path("url").asText().ifBlank { null }
    }

    private fun cleanImg(url: String?): String? =
        url?.replace("images-macellan-online.cdn.ampproject.org/i/s/", "")

    private data class FilmCard(
        val title: String,
        val url: String,
        val poster: String?,
        val score: String?,
        val genres: String
    )

    private fun FilmCard.toSearchResponse(): SearchResponse =
        newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = poster
            this.score = this@toSearchResponse.score?.let { Score.from10(it) }
        }

    private companion object {
        val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val EMBED_REGEX = Regex("""https?://[a-zA-Z0-9.\-]+/iframe\.php\?v=[a-zA-Z0-9_\-]+""")
        val GATE_REGEX = Regex("""window\.NP_GATE\s*=\s*function\s*\(([^)]*)\)\s*\{(.*?)\};""")
        val SALT_REGEX = Regex("""var\s+(\w+)\s*=\s*String\.fromCharCode\(([^)]+)\)""")
        val RADIX_REGEX = Regex("""toString\((\d+)\)""")
        val CAND_REGEX = Regex("""var\s+(\w+)\s*=\s*\w+\.toString\(\d+\)""")
        val WORD_REGEX = Regex("""\)\[(\d+)\]""")
        val MASK_REGEX = Regex("""&(\d+)""")
    }
}
