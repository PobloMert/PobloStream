// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.fasterxml.jackson.annotation.JsonProperty

class SinemaCX : MainAPI() {
    override var mainUrl              = AlanAdi.al("SinemaCX", "https://sinemacc.com")
    override var name                 = "SinemaCX"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    // ! CloudFlare bypass
	/*
    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 250L // ? 0.25 saniye
    override var sequentialMainPageScrollDelay = 250L // ? 0.25 saniye
	*/

    override val mainPage = mainPageOf(
        "${mainUrl}/page/"			                     to		"Son Eklenen Filmler",
        "${mainUrl}/tur/aile-filmleri/page/"			 to		"Aile Filmleri",
        "${mainUrl}/tur/aksiyon-filmleri/page/"		 to		"Aksiyon Filmleri",
        "${mainUrl}/tur/animasyon-filmleri/page/"		 to		"Animasyon Filmleri",
        "${mainUrl}/tur/belgesel/page/"				 to		"Belgesel Filmleri",
        "${mainUrl}/tur/bilim-kurgu-filmleri/page/"	 to		"Bilim Kurgu Filmler",
        "${mainUrl}/tur/biyografi/page/"				 to		"Biyografi Filmleri",
        "${mainUrl}/tur/fantastik-filmler/page/"		 to		"Fantastik Filmler",
        "${mainUrl}/tur/gizem-filmleri/page/"			 to		"Gizem Filmleri",
        "${mainUrl}/tur/komedi-filmleri/page/"			 to		"Komedi Filmleri",
        "${mainUrl}/tur/korku-filmleri/page/"			 to		"Korku Filmleri",
        "${mainUrl}/tur/macera-filmleri/page/"			 to		"Macera Filmleri",
        "${mainUrl}/tur/romantik-filmler/page/"		 to		"Romantik Filmler",
        "${mainUrl}/tur/erotik-filmler/page/"			 to		"Erotik Film izle",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = EngelAsici.http.get("${request.data}${page}").document
        val home     = document.kartlar()

        return newHomePageResponse(request.name, home)
    }

    /** 2026-11 tasarımı: div.film_kutusu > a > span.title span.text (eski: div.frag-k) */
    private fun org.jsoup.nodes.Document.kartlar(): List<SearchResponse> =
        select("div.icerik div.film_kutusu").mapNotNull { it.toSearchResult() }
            .ifEmpty { select("div.son div.frag-k, div.icerik div.frag-k").mapNotNull { it.toEskiSearchResult() } }

    private fun Element.toSearchResult(): SearchResponse? {
        val a         = this.selectFirst("a[href]") ?: return null
        val href      = fixUrlNull(a.attr("href")) ?: return null
        val title     = this.selectFirst("span.title span.text")?.text()?.trim()
            ?: a.attr("title").removeSuffix(" İzle").trim().ifBlank { return null }
        val img       = this.selectFirst("img")
        val posterUrl = fixUrlNull(img?.attr("data-src")?.ifBlank { null } ?: img?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    private fun Element.toEskiSearchResult(): SearchResponse? {
        val title     = this.selectFirst("div.yanac span")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("div.yanac a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a.resim img")?.attr("data-src")) ?: fixUrlNull(this.selectFirst("a.resim img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = EngelAsici.http.get("${mainUrl}/arama/?s=${java.net.URLEncoder.encode(query, "UTF-8")}").document

        return document.kartlar()
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = EngelAsici.http.get(url).document

        // 2026-11 tasarımı: başlık h1.baslik, ayrıntılar JSON-LD (Movie) içinde
        val html        = document.html()
        val ld          = document.select("script[type=application/ld+json]").map { it.data() }.firstOrNull { it.contains("\"Movie\"") } ?: ""
        fun ldStr(k: String) = Regex(""""$k"\s*:\s*"([^"]*)"""").find(ld)?.groupValues?.get(1)

        val title       = document.selectFirst("div.f-bilgi h1, h1.baslik")?.text()?.removeSuffix(" İzle")?.removeSuffix(" izle")?.trim()
            ?: ldStr("name") ?: return null
        val poster      = fixUrlNull(document.selectFirst("link[rel='image_src']")?.attr("href"))
            ?: fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content")) ?: ldStr("image")
        val year        = document.selectFirst("div.f-bilgi ul.detay a[href*='yapim']")?.text()?.toIntOrNull()
            ?: Regex("""-(\d{4})/?$""").find(url)?.groupValues?.get(1)?.toIntOrNull()
            ?: ldStr("dateCreated")?.take(4)?.toIntOrNull()
        val description = (document.selectFirst("div.f-bilgi div.ackl, div.kategori_aciklama p")?.text()?.trim()
            ?: ldStr("description"))?.let { org.jsoup.parser.Parser.unescapeEntities(it, false) }
        val tags        = document.select("div.f-bilgi div.tur a, div.video_info div.tags a").map { it.text().trim() }.distinct()
        val duration    = Regex("""Süre: </span>(\d+) Dakika</li>""").find(html)?.groupValues?.get(1)?.toIntOrNull()
            ?: ldStr("duration")?.let { Regex("""PT(\d+)M""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        val actors      = document.select("li.oync li.oyuncu-k").mapNotNull {
            val isim = it.selectFirst("span.isim")?.text() ?: return@mapNotNull null
            Actor(isim, it.selectFirst("img")?.attr("data-src"))
        }.ifEmpty {
            val aktorBlok = Regex(""""actor"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL).find(ld)?.groupValues?.get(1) ?: ""
            Regex(""""name"\s*:\s*"([^"]+)"""").findAll(aktorBlok).map { Actor(it.groupValues[1]) }.toList()
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year      = year
            this.plot      = description
            this.tags      = tags
            this.duration  = duration
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("SCX", "data » $data")
        val document = EngelAsici.http.get(data).document

        // 2026-11 tasarımı: dil seçenekleri <button class="part_button" rel="base64(url)">Türkçe Dublaj</button>
        // Eski tasarım: <iframe data-vsrc="url">. İkisi de base64 ya da düz adres olabilir.
        fun coz(deger: String): String? {
            val d = deger.trim()
            if (d.isEmpty()) return null
            if (d.startsWith("http") || d.startsWith("//")) return d
            return runCatching { String(android.util.Base64.decode(d, android.util.Base64.DEFAULT), Charsets.UTF_8) }
                .getOrNull()?.takeIf { it.startsWith("http") }
        }

        val kaynaklar = document.select("button.part_button[rel]").mapNotNull { b ->
            val adres = coz(b.attr("rel")) ?: return@mapNotNull null
            b.text().trim() to adres
        }.ifEmpty {
            document.select("iframe[data-vsrc]").mapNotNull { f -> coz(f.attr("data-vsrc"))?.let { name to it } }
        }.filterNot { (ad, adres) ->
            ad.contains("fragman", true) || adres.contains("youtube", true) || adres.contains("trailer", true)
        }.map { (ad, adres) -> ad to adres.substringBefore("?img=") }

        Log.d("SCX", "kaynaklar » $kaynaklar")
        if (kaynaklar.isEmpty()) return false

        var bulundu = false
        val altyazilar = mutableSetOf<String>()
        val akislar    = mutableSetOf<String>()
        for ((ad, iframe) in kaynaklar) {
            runCatching {
                // Altyazılar: playerjsSubtitle = "[Dil]url,[Dil]url"
                val iframeSource = EngelAsici.http.get(iframe, referer = "${mainUrl}/").text
                Regex("""playerjsSubtitle\s*=\s*"(.+?)"""").find(iframeSource)?.groupValues?.get(1)?.let { blok ->
                    Regex("""\[(.*?)](https?://[^\s",]+)""").findAll(blok).forEach { m ->
                        if (altyazilar.add(m.groupValues[2])) subtitleCallback.invoke(SubtitleFile(m.groupValues[1], fixUrl(m.groupValues[2])))
                    }
                }

                if (iframe.contains("player.filmizle.in", true)) {
                    // FirePlayer: video kimliği yoldaki son parça (?lng=… dil seçimi sorgu kısmında kalır)
                    val host    = Regex("""https?://([^/]+)""").find(iframe)?.groupValues?.get(1) ?: return@runCatching
                    val vidId   = iframe.substringBefore("?").trimEnd('/').substringAfterLast("/")
                    val lng     = iframe.substringAfter("lng=", "").substringBefore("&")
                    val sorgu   = if (lng.isNotEmpty()) "&lng=$lng" else ""
                    val vidUrl  = EngelAsici.http.post(
                        "https://$host/player/index.php?data=$vidId&do=getVideo$sorgu",
                        headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                        data    = mapOf("hash" to vidId, "r" to "${mainUrl}/"),
                        referer = iframe
                    ).parsedSafe<Panel>()?.securedLink ?: return@runCatching
                    // Dil seçenekleri çoğunlukla aynı (çok sesli) HLS akışını döndürür: bir kez listele
                    if (!akislar.add(vidUrl.substringBefore("?"))) { bulundu = true; return@runCatching }

                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name   = if (ad.isNotBlank()) "${this.name} - $ad" else this.name,
                            url    = vidUrl,
                            type   = ExtractorLinkType.M3U8
                        ) {
                            quality = Qualities.Unknown.value
                            headers = mapOf("Referer" to iframe)
                        }
                    )
                    bulundu = true
                } else {
                    if (loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)) bulundu = true
                }
            }.onFailure { Log.d("SCX", "kaynak hatası ($ad): ${it.message}") }
        }

        return bulundu
    }

    data class Panel(
        @JsonProperty("hls")         val hls: Boolean?        = null,
        @JsonProperty("securedLink") val securedLink: String? = null
    )
}
