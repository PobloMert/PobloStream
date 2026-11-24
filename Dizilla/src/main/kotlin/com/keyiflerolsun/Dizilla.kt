// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.
// ! Yeniden yazıldı: Ahmet — yeni HTML yapısı (v4-epgrid, pcard) + ContentX zinciri (Pichive PoW)

package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class Dizilla : MainAPI() {
    override var mainUrl = "https://dizilla.la"
    override var name = "Dizilla"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries)

    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 150L
    override var sequentialMainPageScrollDelay = 150L

    // ! CloudFlare v2
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)
            val doc = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.html().contains("verifying") || doc.html().contains("Just a moment")) {
                return cloudflareKiller.intercept(chain)
            }
            return response
        }
    }

    override val supportedSyncNames = setOf(SyncIdName.Simkl)

    override val mainPage = mainPageOf(
        "${mainUrl}/tum-bolumler"       to "Yeni Eklenen Bölümler",
        "${mainUrl}/arsiv"              to "Yeni Eklenen Diziler",
        "${mainUrl}/dizi-turu/aile"     to "Aile",
        "${mainUrl}/dizi-turu/aksiyon"  to "Aksiyon",
        "${mainUrl}/dizi-turu/bilim-kurgu" to "Bilim Kurgu",
        "${mainUrl}/dizi-turu/dram"     to "Dram",
        "${mainUrl}/dizi-turu/fantastik" to "Fantastik",
        "${mainUrl}/dizi-turu/gerilim"  to "Gerilim",
        "${mainUrl}/dizi-turu/komedi"   to "Komedi",
        "${mainUrl}/dizi-turu/korku"    to "Korku",
        "${mainUrl}/dizi-turu/macera"   to "Macera",
        "${mainUrl}/dizi-turu/romantik" to "Romantik",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.contains("/arsiv") && page > 1) "${request.data}?page=$page" else request.data
        val document = app.get(url, interceptor = interceptor).document

        val home = when {
            request.data.contains("/tum-bolumler") -> {
                // ul.v4-epgrid > li > a.gb
                document.select("ul.v4-epgrid li a.gb").mapNotNull { it.bolumSonuc() }
            }
            else -> {
                // /arsiv ve /dizi-turu/* -> a.pcard
                document.select("a.pcard").mapNotNull { it.diziSonuc() }
            }
        }

        val hasNext = when {
            request.data.contains("/tum-bolumler") -> document.select("ul.v4-epgrid li").size >= 20
            else -> document.select("a.pcard").size >= 24
        }

        return newHomePageResponse(request.name, home, hasNext)
    }

    // --- Element extension'ları ---
    private fun Element.bolumSonuc(): SearchResponse? {
        val aria = attr("aria-label") ?: return null
        // "Love in Disguise, 1. Sezon 12. Bölüm, 17:17"
        val parcalar = aria.split(", ")
        if (parcalar.size < 3) return null
        val diziAdi = parcalar[0]
        val sezonBolum = parcalar[1].replace(". Sezon ", "x").replace(". Bölüm", "")
        val title = "$diziAdi $sezonBolum"
        val href = fixUrlNull(attr("href")) ?: return null

        // poster: icindeki img src
        val posterUrl = selectFirst("img")?.let { fixUrlNull(it.attr("src") ?: it.attr("data-src")) }

        // href format: /love-in-disguise-1-sezon-12-bolum -> dizi slug: /love-in-disguise
        val diziSlug = href.substringBeforeLast("-${sezonBolum.replace("x", "-sezon-").replace("x", "-bolum")}")
            .takeIf { it.startsWith("/") } ?: href

        return newTvSeriesSearchResponse(title, diziSlug, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.diziSonuc(): SearchResponse? {
        val title = selectFirst(".mvk-t")?.text()?.trim() ?: return null
        val href = fixUrlNull(attr("href")) ?: return null
        val posterUrl = selectFirst(".pimg img")?.let { fixUrlNull(it.attr("src") ?: it.attr("data-src")) }
        val yil = selectFirst(".mvk-m")?.text()?.trim()?.toIntOrNull()

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
            this.year = yil
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "${mainUrl}/arsiv?q=${query}"
        val document = app.get(url, interceptor = interceptor).document
        return document.select("a.pcard").mapNotNull { it.diziSonuc() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // --- Dizi detay (load) ---
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = document.selectFirst(".pimg img, .v4-epgrid img")?.let { fixUrlNull(it.attr("src") ?: it.attr("data-src")) }
        val description = document.selectFirst("meta[name=description]")?.attr("content")?.trim()
        val year = description?.let { it.takeIf { it.length == 4 && it.all { it.isDigit() } }?.toIntOrNull() }

        val tags = document.select(".v4-rich a, .tags a").map { it.text() }.distinct()

        // Sezon/Bölüm linklerini topla
        val slug = url.substringAfterLast("/")
        val episodeLinks = mutableMapOf<Int, MutableList<Pair<Int, String>>>()

        document.select("a[href^=/$slug-][href$=-bolum]").forEach { link ->
            val href = fixUrl(link.attr("href"))
            val bolumText = link.attr("aria-label") ?: link.text()
            val sezBol = bolumText.split(", ").lastOrNull() ?: bolumText
            val sezBolClean = sezBol.replace(". Sezon ", "x").replace(". Bölüm", "")
            val parca = sezBolClean.split("x")
            if (parca.size == 2) {
                val s = parca[0].toIntOrNull() ?: 1
                val e = parca[1].toIntOrNull() ?: 1
                episodeLinks.getOrPut(s) { mutableListOf() }.add(e to href)
            }
        }

        val episodes = mutableListOf<Episode>()
        val sortedSeasons = episodeLinks.keys.sorted()
        for (s in sortedSeasons) {
            val bolumler = episodeLinks[s]!!.sortedBy { it.first }
            for ((ep, epUrl) in bolumler) {
                episodes.add(newEpisode(epUrl) {
                    this.season = s
                    this.episode = ep
                })
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
        }
    }

    // --- Video linkleri (loadLinks) ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, interceptor = interceptor).document

        // 1) NP_CONFIG JSON'ini script icinden cikar
        val scripts = document.select("script")
        var npConfigJson: String? = null
        for (s in scripts) {
            val txt = s.data()
            if (txt.contains("window.NP_CONFIG")) {
                npConfigJson = extractJsonBlock(txt, "window.NP_CONFIG")
                break
            }
        }

        if (npConfigJson == null) {
            // Eski yapi: ContentX iframe -> loadExtractor
            val iframe = document.selectFirst("iframe")?.attr("src")?.let { fixUrlNull(it) }
            if (iframe != null) {
                return loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
            }
            return false
        }

        // 2) NP_CONFIG'i parse et
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val config = objectMapper.readTree(npConfigJson)
        val gate = config.get("gate")
        val nonce = gate?.get("n")?.asText()
        val bitWork = gate?.get("b")?.asInt() ?: 12

        // PoW coz (sadece bir kez, tum kaynaklar icin ayni)
        val powToken = if (nonce != null && nonce.isNotBlank()) {
            solvePow(nonce, bitWork)
        } else null

        // 3) Her kaynak icin master playlist cek
        val sources = config.get("sources") ?: return false
        var bulundu = false

        for (i in 0 until sources.size()) {
            val src = sources[i]
            val kind = src.get("kind")?.asText() ?: "orig"
            val title = src.get("title")?.asText() ?: "Kaynak ${i + 1}"
            val masterUrl = src.get("master")?.asText() ?: continue

            val finalUrl = if (powToken != null) "$masterUrl&p=$powToken" else masterUrl

            try {
                val response = app.get(finalUrl, headers = mapOf(
                    "Referer" to data,
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )).text

                if (response.trim().startsWith("#EXTM3U")) {
                    callback.invoke(
                        newExtractorLink(
                            source = "Dizilla-$kind",
                            name = title,
                            url = finalUrl,
                            type = ExtractorLinkType.M3U8
                        ) {
                            headers = mapOf("Referer" to data)
                            quality = Qualities.Unknown.value
                        }
                    )
                    bulundu = true
                }
            } catch (e: Exception) {
                Log.w("Dizilla", "Master playlist hatasi ($finalUrl): ${e.message}")
            }
        }

        // 4) Altyazilar
        val subtitles = config.get("subtitles")
        if (subtitles != null) {
            for (i in 0 until subtitles.size()) {
                val sub = subtitles[i]
                val lang = sub.get("lang")?.asText() ?: "tr"
                val url = sub.get("url")?.asText() ?: continue
                val label = sub.get("langLabel")?.asText() ?: lang
                subtitleCallback.invoke(SubtitleFile(lang = label, url = fixUrl(url)))
            }
        }

        return bulundu
    }

    // --- Yardimci fonksiyonlar ---
    private fun extractJsonBlock(text: String, prefix: String): String? {
        val idx = text.indexOf(prefix)
        if (idx == -1) return null
        val start = text.indexOf('{', idx)
        if (start == -1) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return text.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }

    // PoW: SHA-256(nonce + ":Ho_68CkexS:" + counter) 3. kelime (byte 8-11) >>> 20 == 0
    private fun solvePow(nonce: String, bits: Int): Int {
        val tuz = "Ho_68CkexS"
        val shift = 32 - bits
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = StringBuilder()
        val maxIter = 2_000_000

        for (counter in 0..maxIter) {
            buffer.setLength(0)
            buffer.append(nonce).append(':').append(tuz).append(':').append(counter)
            val digest = md.digest(buffer.toString().toByteArray())
            // 3. kelime (byte 8-11) big-endian
            val word3 = ((digest[8].toInt() and 0xFF) shl 24) or
                        ((digest[9].toInt() and 0xFF) shl 16) or
                        ((digest[10].toInt() and 0xFF) shl 8) or
                        (digest[11].toInt() and 0xFF)
            if ((word3 shr shift) == 0) {
                return counter
            }
        }
        return 0 // bulunamadi (cok nadir)
    }
}