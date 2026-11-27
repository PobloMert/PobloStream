// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty

open class PeaceMakerst : ExtractorApi() {
    override val name            = "PeaceMakerst"
    override val mainUrl         = "https://peacemakerst.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val m3uLink:String?
        val extRef  = referer ?: ""
        val postUrl = "${url}?do=getVideo"
        Log.d("Kekik_${this.name}", "postUrl » $postUrl")

        val response = EngelAsici.http.post(
            postUrl,
            data = mapOf(
                "hash" to url.substringAfter("video/"),
                "r"    to extRef,
                "s"    to ""
            ),
            referer = extRef,
            headers = mapOf(
                "Content-Type"     to "application/x-www-form-urlencoded; charset=UTF-8",
                "X-Requested-With" to "XMLHttpRequest"
            )
        )
        if (response.text.contains("teve2.com.tr\\/embed\\/")) {
            val teve2Id       = response.text.substringAfter("teve2.com.tr\\/embed\\/").substringBefore("\"")
            val teve2Response = EngelAsici.http.get(
                "https://www.teve2.com.tr/action/media/${teve2Id}",
                referer = "https://www.teve2.com.tr/embed/${teve2Id}"
            ).parsedSafe<Teve2ApiResponse>() ?: throw ErrorLoadingException("teve2 response is null")

            m3uLink           = teve2Response.media.link.serviceUrl + "//" + teve2Response.media.link.securePath
        } else {
            val videoResponse = response.parsedSafe<PeaceResponse>() ?: throw ErrorLoadingException("peace response is null")
            val videoSources  = videoResponse.videoSources
            m3uLink = if (videoSources.isNotEmpty()) {
                videoSources.lastOrNull()?.file
            } else {
                null
            }
        }

        val sonAdres = m3uLink ?: throw ErrorLoadingException("m3u link not found")
        // ! 26.11.2026: kaynak video.twimg.com oldu; site Refereri ile 403,
        // ! twitter.com Refereri ile 200 (Ddizi'de de ayni sorun vardi).
        val linkReferer = if (sonAdres.contains("video.twimg.com", ignoreCase = true)) "https://twitter.com/" else extRef

        // ! HlsProxy master+variant playlist'leri aliyor ama segment isteklerinde
        // ! ozellestirilmis Referer kayboluyor.twimg icin varyantlari acikca uret.
        if (sonAdres.contains(".m3u8", ignoreCase = true)) {
            val basliklar = mapOf(
                "Referer" to linkReferer,
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
            )
            val hlsBaglantilari = M3u8Helper.generateM3u8(this.name, sonAdres, linkReferer, headers = basliklar)
            if (hlsBaglantilari.isNotEmpty()) {
                hlsBaglantilari.forEach(callback)
                return
            }
        }

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = sonAdres,
                type    = INFER_TYPE
            ) {
                quality = Qualities.Unknown.value
                headers = mapOf("Referer" to linkReferer)
            }
        )
    }

    data class PeaceResponse(
        @JsonProperty("videoImage")   val videoImage: String?,
        @JsonProperty("videoSources") val videoSources: List<VideoSource>,
        @JsonProperty("sIndex")       val sIndex: String,
        @JsonProperty("sourceList")   val sourceList: Map<String, String>
    )

    data class VideoSource(
        @JsonProperty("file")  val file: String,
        @JsonProperty("label") val label: String,
        @JsonProperty("type")  val type: String
    )

    data class Teve2ApiResponse(
        @JsonProperty("Media") val media: Teve2Media
    )

    data class Teve2Media(
        @JsonProperty("Link") val link: Teve2Link
    )

    data class Teve2Link(
        @JsonProperty("ServiceUrl") val serviceUrl: String,
        @JsonProperty("SecurePath") val securePath: String
    )
}