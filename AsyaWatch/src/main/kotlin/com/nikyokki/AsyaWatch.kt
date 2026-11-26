package com.nikyokki

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
        val list = if (kind == "film") {
            val flight = flightText(app.get("$mainUrl/film-izle").text)
            val films = jsonArray(flight, "films")?.let { mapper.readTree(it) } ?: return newHomePageResponse(request.name, emptyList())
            films.filter { genre.isBlank() || it.path("g").toString().contains(genre) }
                .map { film ->
                    val href = fixUrl(film.path("u").asText())
                    val title = film.path("t").asText()
                    val poster = cleanImg(film.path("p").asText())
                    val score = Score.from10(film.path("im").asDouble(0.0))
                    if (href.contains("/dizi/")) {
                        newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                            this.posterUrl = poster
                            this.score = score
                        }
                    } else {
                        newMovieSearchResponse(title, href, TvType.Movie) {
                            this.posterUrl = poster
                            this.score = score
                        }
                    }
                }
        } else {
            parseSeries(app.get("$mainUrl/kesfet").text)
        }
        val from = (page - 1) * 20
        val items = if (from >= list.size) emptyList() else list.drop(from).take(20)
        return newHomePageResponse(request.name, items, from + 20 < list.size)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        val flight = flightText(app.get("$mainUrl/film-izle").text)
        val films = jsonArray(flight, "films")?.let { mapper.readTree(it) }?.map { film ->
            val href = fixUrl(film.path("u").asText())
            val title = film.path("t").asText()
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = cleanImg(film.path("p").asText())
                this.score = Score.from10(film.path("im").asDouble(0.0))
            }
        }.orEmpty()
        val series = parseSeries(app.get("$mainUrl/kesfet").text)
        return (films + series).filter { it.name.lowercase().contains(q) }.take(40)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val html = app.get(url).text
        val doc = Jsoup.parse(html)
        val ld = doc.select("script[type=application/ld+json]").mapNotNull { script ->
            runCatching { mapper.readTree(script.data()) }.getOrNull()
        }.firstOrNull { node -> node.path("@type").asText() in setOf("Movie", "TVSeries", "TVEpisode") }
            ?: throw ErrorLoadingException("İçerik bulunamadı")

        val title = ld.path("name").asText().ifBlank { doc.selectFirst("h1")?.text().orEmpty() }
        val poster = jsonText(ld, "image")?.let { cleanImg(it) }
        val plot = ld.path("description").asText().ifBlank { null }
        val year = ld.path("datePublished").asText().take(4).toIntOrNull()
        val score = ld.path("aggregateRating").path("ratingValue").asDouble(0.0)
            .takeIf { it > 0.0 }?.let { Score.from10(it) }
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
        val html = app.get(data).text
        val sources = jsonArray(flightText(html), "sources")?.let { mapper.readTree(it) }
            ?: run {
                Log.d("ASYA", "kaynak bulunamadı: $data")
                return false
            }
        var found = false
        for (src in sources) {
            val embed = fixUrl(src.path("src").asText().ifBlank { continue })
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
        val html = app.get(embed, referer = mainUrl).text
        val raw = Regex("""window\.NP_CONFIG\s*=\s*(\{.*?\});""").find(html)?.groupValues?.get(1)
            ?: return false
        val config = runCatching { mapper.readTree(raw) }.getOrNull() ?: return false
        val gate = config.path("gate").path("n").asText()
        val token = if (gate.isNotBlank()) proofOfWork(gate) else ""

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
                    this.quality = getQualityFromName(source.path("quality").asText())
                }
            )
        }
        config.path("subtitles").forEach { sub ->
            val subUrl = withToken(sub.path("url").asText(), token)
            if (subUrl.isNotBlank()) {
                subtitleCallback.invoke(
                    newSubtitleFile(sub.path("label").asText("Altyazı"), subUrl)
                )
            }
        }
        return config.path("sources").size() > 0
    }

    private fun withToken(url: String, token: String): String {
        if (url.isBlank() || token.isBlank()) return url
        return url + (if (url.contains("?")) "&" else "?") + "p=$token"
    }

    /** np.js: sha256(gate + ":llOCCpAv5V:" + base36(i))[2] & 4095 == 0 */
    private fun proofOfWork(gate: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (i in 0 until 100_000_000) {
            val candidate = i.toString(36)
            val hash = digest.digest("$gate:llOCCpAv5V:$candidate".toByteArray(Charsets.UTF_8))
            val word = ((hash[8].toInt() and 0xff) shl 24) or
                    ((hash[9].toInt() and 0xff) shl 16) or
                    ((hash[10].toInt() and 0xff) shl 8) or
                    (hash[11].toInt() and 0xff)
            if ((word and 0xFFF) == 0) return candidate
        }
        return ""
    }

    private fun parseSeries(html: String): List<SearchResponse> {
        val doc = Jsoup.parse(html)
        return doc.select("a.pc[href^=/dizi/]").mapNotNull { a ->
            val href = a.attr("href")
            if (href.count('/') > 2) return@mapNotNull null
            val title = a.selectFirst("b")?.text()?.ifBlank { null }
                ?: a.attr("aria-label").substringBefore(",").ifBlank { return@mapNotNull null }
            newTvSeriesSearchResponse(title, fixUrl(href), TvType.TvSeries) {
                this.posterUrl = a.selectFirst("img")?.attr("src")?.let { cleanImg(it) }
            }
        }.distinctBy { it.url }
    }

    private fun flightText(html: String): String {
        val sb = StringBuilder()
        for (match in FLIGHT_REGEX.findAll(html)) {
            runCatching { mapper.readValue(match.groupValues[1], String::class.java) }
                .getOrNull()?.let { sb.append(it) }
        }
        return sb.toString()
    }

    private fun jsonArray(text: String, key: String): String? {
        val keyPos = text.indexOf("\"$key\":[")
        if (keyPos < 0) return null
        val start = text.indexOf('[', keyPos)
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun jsonText(node: com.fasterxml.jackson.databind.JsonNode, key: String): String? {
        val value = node.get(key) ?: return null
        return if (value.isTextual) value.asText() else value.path("url").asText().ifBlank { null }
    }

    private fun cleanImg(url: String?): String? =
        url?.replace("images-macellan-online.cdn.ampproject.org/i/s/", "")

    private companion object {
        val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val FLIGHT_REGEX = Regex("""self\.__next_f\.push\(\[1,("(?:[^"\\]|\\.)*")\]\)""")
    }
}
