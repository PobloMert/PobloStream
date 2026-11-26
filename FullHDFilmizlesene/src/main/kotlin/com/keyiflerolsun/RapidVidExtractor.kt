// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*


open class RapidVid : ExtractorApi() {
    override val name            = "RapidVid"
    override val mainUrl         = "https://rapidvid.net"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef   = referer ?: ""
        val response = app.get(url, referer = extRef)
        val html     = response.text

        // ! 26.11.2026: rapidvid.org yeni "atom" oynatıcısına geçti. Yayın adresi
        // ! sayfada değil, `window._p8` içindeki şifreli JSON'da ("cm"/"tm").
        val p8 = Regex("""window\._p8\s*=\s*'([^']+)'""").find(html)?.groupValues?.get(1)
        if (!p8.isNullOrBlank()) {
            val json = runCatching { decodeSecret(p8) }.getOrNull().orEmpty()
            val file = Regex("\"(?:cm|tm)\"\\s*:\\s*\"([^\"]+)\"")
                .find(json)?.groupValues?.get(1)?.let { unescapeJson(it) }
            if (!file.isNullOrBlank()) {
                addSubtitles(json, subtitleCallback)
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name   = this.name,
                        url    = file,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = url
                        this.quality = Qualities.Unknown.value
                    }
                )
                return
            }
            Log.d("Rapid", "_p8 çözüldü ama yayın adresi yok.")
        }

        // ! Eski oynatıcı (rapidvid.net): jwSetup.sources = [{file: av('...')}]
        val videoReq = response.document
        val script = videoReq.select("script").find { it.data().contains("jwSetup.sources") }?.data() ?: ""

        if (script.isNotEmpty()) {
            val jwTrack = script.substringAfter("jwSetup.tracks =").substringBefore(";")
            val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val tracks: List<CaptionData> = objectMapper.readValue(jwTrack)
            tracks.forEach { track ->
                val lang = track.label?.replace("\\u0131", "ı")?.replace("\\u0130", "İ")?.replace("\\u00fc", "ü")?.replace("\\u00e7", "ç")
                val url  = (track.file.replace("\\", ""))
                if (lang != null){
                    subtitleCallback.invoke(
                        newSubtitleFile(lang, url)
                    )
                }
            }
            val jwSetup = script.substringAfter("jwSetup.sources =").substringBefore(";")
            val regex = Regex("""av\('([^']+)'\)""")
            val match = regex.find(jwSetup)
            if (match != null) {
                val encodedValue = match.groupValues[1]
                val m3Url = av(encodedValue)
                callback.invoke(
                    newExtractorLink(
                        source  = this.name,
                        name    = this.name,
                        url     = m3Url,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
            } else {
                Log.d("Rapid", "No match found.")
            }
        }

    }

    /** `_p8` JSON'undaki `ct` altyazı listesini ekler. */
    private suspend fun addSubtitles(json: String, subtitleCallback: (SubtitleFile) -> Unit) {
        val ct = Regex("\"ct\"\\s*:\\s*(\\[[^\\]]*\\])").find(json)?.groupValues?.get(1) ?: return
        for (item in Regex("\\{[^{}]*\\}").findAll(ct)) {
            val body = item.value
            val file = Regex("\"file\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: continue
            val label = Regex("\"label\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
            val name = label?.let { unescapeJson(it) }?.trim().orEmpty().ifBlank { "Altyazı" }
            subtitleCallback.invoke(newSubtitleFile(name, unescapeJson(file)))
        }
    }

    private fun unescapeJson(value: String): String {
        var out = value.replace("\\/", "/")
        out = Regex("""\\u([0-9a-fA-F]{4})""").replace(out) { m ->
            m.groupValues[1].toInt(16).toChar().toString()
        }
        return out
    }

    fun av(o: String): String {
        return decodeSecret(o)
    }


    fun decodeSecret(encodedString: String): String {
        val reversedBase64Input = encodedString.reversed()
        val tString = base64Decode(reversedBase64Input)
        val oBuilder = StringBuilder()
        val key = "K9L"
        for (index in tString.indices) {
            val keyChar = key[index % key.length]
            val offset = keyChar.code % 5 + 1

            val originalCharCode = tString[index].code
            val transformedCharCode = originalCharCode - offset
            oBuilder.append(transformedCharCode.toChar())
        }

        val finalResultBytes = base64Decode(oBuilder.toString())
        return finalResultBytes
    }
}

/** rapidvid.org — aynı oynatıcı, farklı alan adı. */
class RapidVidOrg : RapidVid() {
    override val mainUrl = "https://rapidvid.org"
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CaptionData(
    @JsonProperty("kind") var kind: String,
    @JsonProperty("file") var file: String,
    @JsonProperty("label") var label: String? = null,
)
