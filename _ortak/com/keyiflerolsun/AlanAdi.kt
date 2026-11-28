package com.keyiflerolsun

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Sitelerin güncel adresi depodaki domains.json dosyasından okunur. Bir site adres değiştirince
 * yalnızca o dosyayı düzenlemek yeter; yeni sürüm derlemeye ya da kullanıcının güncelleme
 * indirmesine gerek kalmaz.
 *
 * Kullanım:
 *   Plugin.load():  AlanAdi.kur(context)            (API nesnesi oluşturulmadan ÖNCE)
 *   Ana sınıf:      override var mainUrl = AlanAdi.al("KlasorAdi", "https://varsayilan.com")
 *
 * domains.json değeri ya düz adres ya da eski adresleriyle birlikte nesnedir:
 *   "FullHDFilm": "https://hdfilm.us"
 *   "DiziPalOrijinal": { "adres": "https://dizipal1600.com", "eski": ["https://dizipal1599.com"] }
 *
 * Eski adresler: kullanıcının kayıtlı film/dizileri eski adresle saklanır. [Yonlendirici]
 * (EngelAsici istemcisine takılı) eski adrese giden isteği güncel adrese çevirir. Eski adres
 * listesi = domains.json'daki "eski" + koddaki varsayılan + bu cihazda daha önce görülen adresler.
 *
 * Sıra: kullanıcının ayarlardan yazdığı adres → indirilen liste → listenin cihazdaki kopyası → koddaki varsayılan.
 * Ayar ekranı: Plugin.load() içinde  openSettings = { AlanAdi.ayarEkrani(it, "KlasorAdi") }
 * Liste alınamazsa eklenti her zamanki gibi koddaki adresle çalışır.
 *
 * ! _ortak/ klasöründedir; kök build.gradle.kts her eklentiye ekler.
 */
object AlanAdi {
    private const val ETIKET = "AlanAdi"
    private val KAYNAKLAR = listOf(
        "https://raw.githubusercontent.com/PobloMert/PobloStream/master/domains.json",
        "https://cdn.jsdelivr.net/gh/PobloMert/PobloStream@master/domains.json",
    )

    @Volatile private var liste: JSONObject? = null
    @Volatile private var tercihler: SharedPreferences? = null
    @Volatile private var basladi = false
    private val bitti = CountDownLatch(1)

    /** Eklenti adı → koddaki varsayılan adres ([al] çağrılınca kaydedilir). */
    private val varsayilanlar = ConcurrentHashMap<String, String>()
    /** Eski host → güncel kök adres (ör. "dizipal1599.com" → "https://dizipal1600.com"). */
    @Volatile private var eskiHarita: Map<String, String> = emptyMap()

    /**
     * Eklentinin ayar ekranı: kullanıcı site adresini kendisi yazabilir (site taşındı ama liste
     * henüz güncellenmediyse). Kaydedilince hemen geçerli olur: eski adrese giden istekler
     * [Yonlendirici] ile yeni adrese çevrilir.
     */
    fun ayarEkrani(context: Context, ad: String) {
        if (tercihler == null) runCatching {
            tercihler = context.applicationContext.getSharedPreferences("poblostream_alan_adlari", Context.MODE_PRIVATE)
        }
        val simdiki = guncel(ad).ifEmpty { varsayilanlar[ad] ?: "" }
        val kullanici = runCatching { tercihler?.getString("kullanici:$ad", null) }.getOrNull()
        val yogunluk = context.resources.displayMetrics.density
        val bosluk = (20 * yogunluk).toInt()
        val kutu = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(bosluk, bosluk / 2, bosluk, 0)
        }
        kutu.addView(android.widget.TextView(context).apply {
            text = "Şu anki adres: $simdiki" + (if (kullanici != null) "\n(senin yazdığın adres)" else "") +
                "\n\nSite taşındıysa yeni adresi yaz. Boş bırakıp kaydedersen otomatik adrese dönülür."
        })
        val giris = android.widget.EditText(context).apply {
            setSingleLine(true)
            hint = "https://..."
            setText(kullanici ?: simdiki)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        kutu.addView(giris)
        android.app.AlertDialog.Builder(context)
            .setTitle("$ad · site adresi")
            .setView(kutu)
            .setPositiveButton("Kaydet") { _, _ ->
                val yazilan = giris.text.toString().trim().let { if (it.isNotEmpty() && !it.contains("://")) "https://$it" else it }
                val mesaj = when {
                    yazilan.isEmpty() -> { kullaniciAdresi(ad, null); "Otomatik adrese dönüldü" }
                    yazilan.gecerli() == null || host(yazilan) == null -> "Geçersiz adres: $yazilan"
                    else -> { kullaniciAdresi(ad, yazilan.gecerli()); "Kaydedildi: ${yazilan.gecerli()}" }
                }
                android.widget.Toast.makeText(context, "$mesaj. Ana sayfayı yenileyin.", android.widget.Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("Otomatik") { _, _ ->
                kullaniciAdresi(ad, null)
                android.widget.Toast.makeText(context, "Otomatik adrese dönüldü. Ana sayfayı yenileyin.", android.widget.Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun kullaniciAdresi(ad: String, adres: String?) {
        runCatching {
            tercihler?.edit()?.apply { if (adres == null) remove("kullanici:$ad") else putString("kullanici:$ad", adres) }?.apply()
        }
        haritayiKur()
        Log.d(ETIKET, "$ad: kullanıcı adresi = ${adres ?: "(otomatik)"}")
    }

    /** Listeyi (süreçte bir kez) indirmeye başlar; ana iş parçacığında değilse kısa süre bekler. */
    fun kur(context: Context, beklemeMs: Long = 4_000) {
        if (tercihler == null) {
            tercihler = runCatching {
                context.applicationContext.getSharedPreferences("poblostream_alan_adlari", Context.MODE_PRIVATE)
            }.getOrNull()
        }
        synchronized(this) {
            if (!basladi) {
                basladi = true
                Thread({ indir() }, ETIKET).apply { isDaemon = true }.start()
            }
        }
        // Ana iş parçacığını asla bekletme (donma olmasın); orada cihazdaki son liste kullanılır.
        if (Looper.myLooper() != Looper.getMainLooper()) bitti.await(beklemeMs, TimeUnit.MILLISECONDS)
    }

    /** [ad] için güncel adres (sonunda / olmadan). */
    fun al(ad: String, varsayilan: String): String {
        varsayilanlar[ad] = varsayilan.trimEnd('/')
        val adres = guncel(ad)
        if (adres != varsayilan.trimEnd('/')) Log.d(ETIKET, "$ad: $varsayilan → $adres")
        haritayiKur()
        return adres
    }

    /** Listeyi doğrudan verir (bilgisayardaki eklenti testi, yerel domains.json ile). */
    fun yukle(json: String) {
        liste = JSONObject(json)
        haritayiKur()
    }

    /** Eski bir adresle kaydedilmiş bağlantıyı güncel adrese çevirir; eski değilse aynen döndürür. */
    fun duzelt(url: String): String {
        val u = url.toHttpUrlOrNull() ?: return url
        val yeni = eskiHarita[u.host.lowercase()]?.toHttpUrlOrNull() ?: return url
        return u.newBuilder().scheme(yeni.scheme).host(yeni.host).port(yeni.port).build().toString()
    }

    /** Eski adrese giden isteği (ve Referer/Origin başlığını) güncel adrese çevirir. */
    object Yonlendirici : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val istek = chain.request()
            if (eskiHarita.isEmpty()) return chain.proceed(istek)
            val yeniUrl = duzelt(istek.url.toString())
            val yeniRef = istek.header("Referer")?.let { duzelt(it) }
            val yeniOrigin = istek.header("Origin")?.let { duzelt(it).trimEnd('/') }
            if (yeniUrl == istek.url.toString() && yeniRef == istek.header("Referer") && yeniOrigin == istek.header("Origin")) {
                return chain.proceed(istek)
            }
            Log.d(ETIKET, "eski adres düzeltildi: ${istek.url} → $yeniUrl")
            val b = istek.newBuilder().url(yeniUrl)
            if (yeniRef != null) b.header("Referer", yeniRef)
            if (yeniOrigin != null) b.header("Origin", yeniOrigin)
            return chain.proceed(b.build())
        }
    }

    // ---------------------------------------------------------------- iç işler

    private fun guncel(ad: String): String =
        runCatching { tercihler?.getString("kullanici:$ad", null) }.getOrNull().gecerli()
            ?: listedeki(ad)
            ?: varsayilanlar[ad] ?: ""

    /** Listenin (indirilen ya da cihazdaki) verdiği adres; kullanıcı adresi hariç. */
    private fun listedeki(ad: String): String? =
        adresOf(liste?.opt(ad))
            ?: runCatching { tercihler?.getString(ad, null) }.getOrNull().gecerli()

    /** domains.json değerinden adresi okur (düz metin ya da {"adres": ...}). */
    private fun adresOf(deger: Any?): String? = when (deger) {
        is String -> deger.gecerli()
        is JSONObject -> deger.optString("adres").gecerli()
        else -> null
    }

    private fun eskilerOf(deger: Any?): List<String> {
        val dizi = (deger as? JSONObject)?.optJSONArray("eski") ?: return emptyList()
        return (0 until dizi.length()).mapNotNull { dizi.optString(it).gecerli() }
    }

    private fun String?.gecerli(): String? =
        this?.trim()?.trimEnd('/')?.takeIf { it.startsWith("https://") || it.startsWith("http://") }

    private fun host(adres: String): String? = adres.toHttpUrlOrNull()?.host?.lowercase()

    /** Tüm eklentiler için eski host → güncel adres eşlemesini yeniden kurar. */
    @Synchronized
    private fun haritayiKur() {
        val harita = HashMap<String, String>()
        for ((ad, varsayilan) in varsayilanlar) {
            val simdiki = guncel(ad)
            if (simdiki.isEmpty()) continue
            val simdikiHost = host(simdiki) ?: continue
            val eskiler = buildSet {
                add(varsayilan)
                listedeki(ad)?.let { add(it) } // kullanıcı adres yazdıysa listedeki de eski sayılır
                addAll(eskilerOf(liste?.opt(ad)))
                runCatching { tercihler?.getStringSet("eski:$ad", null) }.getOrNull()?.let { addAll(it) }
            }
            for (e in eskiler) {
                val h = host(e) ?: continue
                // www'lu ve www'suz biçimi birlikte eşle; güncel host'un kendisi asla eşlenmez
                for (v in setOf(h, h.removePrefix("www."), "www." + h.removePrefix("www."))) {
                    if (v != simdikiHost && v.removePrefix("www.") != simdikiHost.removePrefix("www.")) harita[v] = simdiki
                }
            }
        }
        eskiHarita = harita
    }

    private fun indir() {
        try {
            for (kaynak in KAYNAKLAR) {
                val j = runCatching { JSONObject(oku(kaynak)) }
                    .onFailure { Log.w(ETIKET, "$kaynak okunamadı: ${it.message}") }
                    .getOrNull() ?: continue
                kaydet(j)
                liste = j
                haritayiKur()
                Log.d(ETIKET, "${j.length()} adres alındı: $kaynak")
                return
            }
        } finally {
            bitti.countDown()
        }
    }

    /** Yeni listeyi cihaza yazar; adresi değişen sitenin önceki adresini "eski" olarak saklar. */
    private fun kaydet(j: JSONObject) {
        val t = tercihler ?: return
        runCatching {
            val e = t.edit()
            j.keys().forEach { ad ->
                val yeni = adresOf(j.opt(ad)) ?: return@forEach
                val onceki = t.getString(ad, null).gecerli()
                if (onceki != null && onceki != yeni) {
                    val eskiler = (t.getStringSet("eski:$ad", null) ?: emptySet()) + onceki
                    e.putStringSet("eski:$ad", eskiler.toSet())
                }
                e.putString(ad, yeni)
            }
            e.apply()
        }
    }

    private fun oku(adres: String): String {
        val b = URL(adres).openConnection() as HttpURLConnection
        b.connectTimeout = 3_000
        b.readTimeout = 3_000
        b.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (b.responseCode != 200) error("HTTP ${b.responseCode}")
            return b.inputStream.bufferedReader().use { it.readText() }
        } finally {
            b.disconnect()
        }
    }
}
