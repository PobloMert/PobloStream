package com.keyiflerolsun

import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Eklentinin gerçek sitede çalıştığını dener: ana sayfadan liste çeker ve ilk içeriğin
 * detay sayfasını açar. Site tasarımı değişip seçiciler bozulursa (adres aynı kalsa bile) yakalar.
 *
 * Her eklenti için kök build.gradle.kts çalıştırır:  ./gradlew <Eklenti>:testDebugUnitTest
 * Sonuç build/eklenti_testi.json'a yazılır (EKLENTI_TESTI.py rapora çevirir).
 * ! _test/ klasöründedir; kök build.gradle.kts her eklentinin test kaynaklarına ekler.
 */
class EklentiTesti {
    @Test
    fun anaSayfaVeDetay() {
        val sinif = System.getProperty("pobl.api") ?: return
        val cikti = System.getProperty("pobl.cikti")?.let { File(it) }
        // Uygulamanın okuyacağı adresler (depodaki domains.json) denensin
        System.getProperty("pobl.alanlar")?.let { File(it) }?.takeIf { it.exists() }?.let { AlanAdi.yukle(it.readText()) }
        val api = Class.forName(sinif).getDeclaredConstructor().newInstance() as MainAPI
        val kayit = JSONObject().put("eklenti", System.getProperty("pobl.ad")).put("adres", api.mainUrl)

        var hata: String? = null
        try {
            runBlocking {
                withTimeout(150_000) {
                    var ilk: SearchResponse? = null
                    var bolum = ""
                    var tumListe: List<SearchResponse> = emptyList()
                    // İlk 3 ana sayfa bölümünden biri dolu gelmeli
                    for (s in api.mainPage.take(3)) {
                        val liste = runCatching {
                            api.getMainPage(1, MainPageRequest(s.name, s.data, s.horizontalImages))
                        }.onFailure { kayit.put("bolum_hatasi", "${s.name}: ${it.javaClass.simpleName}: ${it.message}") }
                            .getOrNull()?.items?.flatMap { it.list }.orEmpty()
                        if (liste.isNotEmpty()) { ilk = liste.first(); tumListe = liste; bolum = s.name; kayit.put("liste", liste.size); break }
                    }
                    val icerik = ilk ?: error("ana sayfa boş (${api.mainPage.take(3).joinToString { it.name }})")
                    kayit.put("bolum", bolum).put("ilk", icerik.name).put("ilk_url", icerik.url)
                    val detay = api.load(icerik.url) ?: error("detay sayfası açılmadı: ${icerik.url}")
                    kayit.put("detay", detay.name)
                    // Arama: ilk içeriğin adının ilk kelimeleriyle ara (ayrı alan; eklentiyi "bozuk" saydırmaz,
                    // raporda "arama" sütununda görünür)
                    // Bölüm işaretlerini (S1B7, 3x4, "2. Sezon", "Bölüm") at; ilk anlamlı kelime (en az 4 harf)
                    val sorgu = icerik.name
                        .replace(Regex("""(?i)\bS\d+\s*B\d+\b|\b\d+x\d+\b|\b\d+\.?\s*(sezon|bölüm)\b|\b(sezon|bölüm|izle)\b"""), " ")
                        .replace(Regex("""[^\p{L}\p{N} ]"""), " ").trim().split(Regex("\\s+"))
                        .firstOrNull { it.length >= 4 && it.any { c -> c.isLetter() } } ?: ""
                    if (sorgu.isNotBlank()) {
                        val arama = runCatching { withTimeout(40_000) { api.search(sorgu) } }
                        kayit.put("arama_sorgu", sorgu)
                        kayit.put("arama", arama.fold(
                            { s -> if (s.isNullOrEmpty()) "BOS" else "OK (${s.size})" },
                            { e -> "HATA: ${e.javaClass.simpleName}: ${e.message}".take(150) }
                        ))
                    }
                    // Canlı yayında bağlantı da denenir (film/dizide video sunucuları ayrı eklentilerde, atlanır)
                    if (detay is LiveStreamLoadResponse) {
                        var sayi = 0
                        api.loadLinks(detay.dataUrl, false, {}) { sayi++ }
                        kayit.put("baglanti", sayi)
                        if (sayi == 0) error("canlı yayın bağlantısı yok: ${detay.name}")

                        // Her kanal ayrı ayrı: bir kanal kapanırsa raporda ismiyle görünür
                        // (eklentiyi "çalışmıyor" saydırmaz; diğer kanallar çalışıyor olabilir)
                        val kanallar = org.json.JSONObject()
                        for (k in tumListe.drop(1).take(20)) {
                            val sonuc = runCatching {
                                withTimeout(30_000) {
                                    val d = api.load(k.url) as? LiveStreamLoadResponse ?: error("detay yok")
                                    var n = 0
                                    api.loadLinks(d.dataUrl, false, {}) { n++ }
                                    if (n == 0) error("bağlantı yok")
                                }
                            }
                            kanallar.put(k.name, sonuc.fold({ "OK" }, { e -> "HATA: ${e.message ?: e.javaClass.simpleName}".take(120) }))
                        }
                        kanallar.put(detay.name, "OK")
                        kayit.put("kanallar", kanallar)
                    }
                }
            }
        } catch (t: Throwable) {
            hata = "${t.javaClass.simpleName}: ${t.message}".take(300)
        }
        // Cloudflare doğrulaması isteyen site bilgisayarda (WebView yok) denenemez: bozuk sayılmaz
        val durum = when {
            hata == null -> "CALISIYOR"
            TarayiciGetir.dogrulamali.isNotEmpty() || cloudflareMi(api.mainUrl) -> "DOGRULANAMADI"
            else -> "BOZUK"
        }
        kayit.put("durum", durum).put("hata", hata ?: JSONObject.NULL)
        cikti?.apply { parentFile?.mkdirs(); writeText(kayit.toString(2)) }
        println(kayit.toString(2))
        assertTrue(hata ?: "", durum != "BOZUK")
    }

    /** Site tarayıcı olmayan istemciye Cloudflare doğrulaması mı gösteriyor? (CloudflareKiller bilgisayarda çalışmaz) */
    private fun cloudflareMi(adres: String): Boolean = runCatching {
        runBlocking {
            withTimeout(20_000) {
                val y = EngelAsici.http.get("$adres/", timeout = 15)
                y.code in setOf(403, 503, 429) && (y.text.contains("Just a moment") || y.text.contains("challenge-platform"))
            }
        }
    }.getOrDefault(false)
}
