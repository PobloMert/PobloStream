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
        val yil = selectFirst(".mvk-m")?.text()?.trim()?.let { Regex("""\b(19|20)\d{2}\b""").find(it)?.groupValues?.get(0)?.toIntOrNull() }

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
        val html = app.get(url, interceptor = interceptor).text
        val document = Jsoup.parse(html)

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = document.selectFirst(".pimg img, .v4-epgrid img")?.let { fixUrlNull(it.attr("src") ?: it.attr("data-src")) }
        val description = document.selectFirst("meta[name=description]")?.attr("content")?.trim()
        val year = description?.let { Regex("""\b(19|20)\d{2}\b""").find(it)?.groupValues?.get(0)?.toIntOrNull() }

        val tags = document.select(".v4-rich a, .tags a").map { it.text() }.distinct()

        // Bölüm linkleri DOM'da değil, flight JSON / JSON-LD içinde:
        // "url":"https://dizilla.la/slug-1-sezon-2-bolum" veya \"url\":\"...\"
        val slug = url.substringAfterLast("/")
        val episodeLinks = mutableMapOf<Int, MutableList<Pair<Int, String>>>()
        val epRegex = Regex("""${Regex.escape(slug)}-(\d+)-sezon-(\d+)-bolum""")

        epRegex.findAll(html).forEach { m ->
            val s = m.groupValues[1].toIntOrNull() ?: return@forEach
            val e = m.groupValues[2].toIntOrNull() ?: return@forEach
            val epUrl = fixUrl("/${slug}-${s}-sezon-${e}-bolum")
            val list = episodeLinks.getOrPut(s) { mutableListOf() }
            if (list.none { it.first == e }) list.add(e to epUrl)
        }

        val episodes = mutableListOf<Episode>()
        for (s in episodeLinks.keys.sorted()) {
            for ((ep, epUrl) in episodeLinks[s]!!.sortedBy { it.first }) {
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
        val body = app.get(data, interceptor = interceptor).text

        // 1) Bölüm sayfasındaki kaçışlı JSON'dan gerçek Pichive iframe URL'sini bul
        //    (Next.js flight verisi: \"src\":\"https://four.pichive.online/iframe.php?v=...\")
        val iframeUrl = Regex("""(https://[a-z0-9.]*pichive[a-z0-9.]*/iframe\.php\?v=[a-zA-Z0-9_-]+)""")
            .find(body)?.groupValues?.get(1)
            ?: Regex("""https://[^"\s]+/iframe\.php\?v=[a-zA-Z0-9_-]+""").find(body)?.value
            ?: run {
                // Eski yapi: DOM'da gerçek iframe varsa
                val doc = Jsoup.parse(body)
                doc.selectFirst("iframe")?.attr("src")?.let { fixUrlNull(it) }
            }
            ?: return false

        // 2) NP_CONFIG'i iframe sayfasından çıkar
        val iframeBody = app.get(iframeUrl, headers = mapOf("Referer" to data)).text
        val npConfigJson = extractJsonBlock(iframeBody, "window.NP_CONFIG") ?: return false

        // 3) NP_CONFIG'i parse et
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val config = objectMapper.readTree(npConfigJson)
        val gate = config.get("gate")
        val nonce = gate?.get("n")?.asText()
        val bitWork = gate?.get("b")?.asInt() ?: 12

        // PoW coz (sadece bir kez, tum kaynaklar icin ayni)
        val powToken = if (nonce != null && nonce.isNotBlank()) solvePow(nonce, bitWork) else null

        // Referer: Pichive kendi origin'i (dizilla.la Referer'i 404 veriyor)
        val referer = "https://four.pichive.online/"

        // 4) Her kaynak icin master playlist cek
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
                    "Referer" to referer,
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
                            headers = mapOf("Referer" to referer)
                            quality = Qualities.Unknown.value
                        }
                    )
                    bulundu = true
                }
            } catch (e: Exception) {
                Log.w("Dizilla", "Master playlist hatasi ($finalUrl): ${e.message}")
            }
        }

        // 5) Altyazilar (PoW token'i onlara da eklenmeli)
        val subtitles = config.get("subtitles")
        if (subtitles != null) {
            for (i in 0 until subtitles.size()) {
                val sub = subtitles[i]
                val lang = sub.get("lang")?.asText() ?: "tr"
                val url = sub.get("url")?.asText() ?: continue
                val label = sub.get("langLabel")?.asText() ?: lang
                val subUrl = if (powToken != null) "$url&p=$powToken" else url
                subtitleCallback.invoke(SubtitleFile(lang = label, url = fixUrl(subUrl)))
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

    // ! 26.11.2026: pichive gate degisti.
    // ! Yeni: SHA256("X7zRkiNmvG" + nonce + "." + counter)[5] & ((1<<bits)-1) == 0
    // ! (iframe sayfasindaki window.NP_GATE ve /assets/np/np.js kaynak kodundan dogrulandi;
    // !  dogru token ile /nm.php?...&p=... #EXTM3U donuyor.)
    private fun solvePow(nonce: String, bits: Int): Int {
        val prefix = "X7zRkiNmvG"
        val maske = (1L shl bits.coerceIn(1, 31)) - 1L
        val md = MessageDigest.getInstance("SHA-256")
        for (counter in 0 until 2_000_000) {
            val digest = md.digest((prefix + nonce + "." + counter).toByteArray())
            val kelime6 = ((digest[20].toInt() and 0xFF) shl 24) or
                          ((digest[21].toInt() and 0xFF) shl 16) or
                          ((digest[22].toInt() and 0xFF) shl 8) or
                          (digest[23].toInt() and 0xFF)
            if ((kelime6.toLong() and maske) == 0L) return counter
        }
        return 0 // bulunamadi (cok nadir)
    }
}