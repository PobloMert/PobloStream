# 📺 PobloStream

[CloudStream](https://github.com/recloudstream/cloudstream) için Türkçe film, dizi, anime ve belgesel eklentileri.

- **Güncel adresler:** Siteler sık sık adres değiştirir; bu depo onları takip eder.
- **VPN'siz erişim:** Türkiye'de erişimi engellenen sitelerde de eklentiler VPN gerektirmeden çalışır (bkz. [Erişim engeli](#️-erişim-engeli-vpn-gerekmez)).
- **Açık kaynak:** [@keyiflerolsun](https://github.com/keyiflerolsun)'un Kekik-cloudstream projesinden türetilmiştir (GPL-3.0).

---

## 📲 Kurulum

CloudStream → **Ayarlar → Eklentiler → Depo ekle** → şunlardan birini yaz:

```
!poblostream
```

```
https://raw.githubusercontent.com/PobloMert/PobloStream/builds/repo.json
```

Ardından depodan istediğin eklentileri indir. Güncellemeler uygulama içinden otomatik gelir.

---

## 🧩 Eklentiler

| Eklenti | İçerik | Site |
|---|---|---|
| AnimeciX | Anime | animecix.tv |
| AsyaWatch | Asya dizi ve filmleri | asyawatch.com |
| BelgeselX | Belgesel | belgeselx.com |
| CizgiMax | Çizgi film | cizgimax.online |
| Ddizi | Dizi | ddizi.tel |
| DiziBox | Dizi | dizibox.live |
| DiziKorea 🆕 | Kore, Çin, Japon, Tayland dizileri | dizikorea3.com |
| Dizilla | Dizi | dizilla.la |
| DiziMom | Dizi | dizidad.com |
| DiziPal | Dizi ve film | dizipal1.rest |
| DiziPal Orijinal 🆕 | Dizi ve film | dizipal1599.com |
| DiziYou 🆕 | Yabancı dizi (dublaj ve altyazı) | diziyou.love |
| DMax | DMAX programları | dmax.com.tr |
| FullHDFilm | Film | hdfilm.us |
| FullHDFilmizlesene | Film | fullhdfilmizlesene.now |
| HDFilmCehennemi | Film ve dizi | hdfilmcehennemi.nl |
| JetFilmizle | Film | jetfilmizle.app |
| KultFilmler | Film ve dizi | kultfilmler.net |
| RareFilmm | Nadir filmler | rarefilmm.com |
| SinemaCX 🆕 | Film | sinemacc.com |
| TLCtr 🆕 | TLC programları | tlctv.com.tr |
| TurkAnime | Anime | turkanime.to |
| TvDiziler | Yerli dizi | tvdiziler.tv |

🆕 = feroxx/Kekik-cloudstream deposundan aktarılıp güncellenen eklentiler.

---

## 🛡️ Erişim engeli (VPN gerekmez)

Türkiye'deki erişim engeli iki katmanlıdır: DNS sorgusu cevapsız kalır ve bağlantı sırasında site adı (TLS ClientHello içindeki SNI) görülünce bağlantı kesilir. Bütün eklentiler ortak bir katmanla bunu kendi içinde aşar:

| Dosya | Ne yapar |
|---|---|
| `_ortak/…/EngelAsici.kt` | Alan adını DNS-over-HTTPS (1.1.1.1, yedek 8.8.8.8) ile çözer. ClientHello'yu site adının ortasından iki TLS kaydına böler; bu TLS standardına uygundur, sunucu parçaları birleştirir, engel ise adı tek parçada göremez. Herhangi bir sorunda istek uygulamanın normal istemcisiyle yeniden denenir. |
| `_ortak/…/YerelAktarici.kt` | Videoyu uygulamanın oynatıcısı indirir, eklenti değil. Video sunucusu engelliyse oynatıcı `127.0.0.1` üzerindeki bu küçük aktarıcıya bağlanır, istek `EngelAsici` üzerinden iletilir. HLS çalma listeleri buna göre yeniden yazılır. |

`_ortak/` klasörü kök `build.gradle.kts` tarafından her eklentiye otomatik eklenir. Kullanıcının ayrıca bir şey yapması gerekmez.

---

## 🛠️ Geliştirici rehberi

### Depo yapısı

```
PobloStream/
├── <Eklenti>/                     ← her klasör bir eklenti
│   ├── build.gradle.kts           ← sürüm, durum, açıklama, ikon, yazarlar
│   └── src/main/kotlin/…/
│       ├── <Eklenti>.kt           ← siteyi okuyan asıl kod
│       └── <Eklenti>Plugin.kt     ← kayıt dosyası
├── _ortak/                        ← tüm eklentilere eklenen ortak kod (EngelAsici, YerelAktarici)
├── __Temel/                       ← yeni eklenti şablonu (derlenmez)
├── build.gradle.kts               ← kök derleme ayarları
├── settings.gradle.kts            ← eklenti klasörlerini otomatik bulur
├── repo.json                      ← CloudStream depo tanımı
├── KONTROL.py                     ← adres değişikliği denetleyicisi (GitHub Actions'ta çalışır)
├── ENGEL_KONTROL.py               ← erişim engeline dayanıklı site denetleyicisi (yerelde)
└── .github/workflows/
    ├── Derleyici.yml              ← her push'ta .cs3'leri derleyip builds dalına koyar
    └── Kontrol.yml                ← 9 saatte bir adres kontrolü, değişince PR açar
```

### Derleme

Yerel derleme **JDK 21** ister; Gradle 8.12 Java 25 ile çalışmaz.

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
.\gradlew.bat make                 # tüm eklentiler
.\gradlew.bat FullHDFilm:make      # tek eklenti
```

Çıktı: `<Eklenti>/build/<Eklenti>.cs3`

### Kod yazarken kurallar

- İstekler için `app.get/post(...)` yerine **`EngelAsici.http.get/post(...)`** kullan. Aynı API'dir, engelsiz sitelerde de zararsızdır.
- Paket adı `com.keyiflerolsun` değilse dosyaya `import com.keyiflerolsun.EngelAsici` ekle.
- Site engelliyse posterler için `EngelAsici.resim(url)` kullan (wsrv.nl resim vekili), çünkü uygulamanın resim yükleyicisi engelli siteye ulaşamaz.
- Video adresi engelli bir sunucudaysa `YerelAktarici.gerekirse(url, mapOf("Referer" to …))` ile ver. Sunucu normal yoldan açılıyorsa adres aynen kalır.
- Her değişiklikte o eklentinin `build.gradle.kts` dosyasındaki `version` değerini bir artır; yoksa kullanıcılara güncelleme gitmez.

### Masaüstü CloudStream'de test

1. Eklentiyi derle.
2. `.cs3` dosyasını `%APPDATA%\CloudStream\storage\Cloudstream3\plugins\` klasörüne kopyala.
3. CloudStream'i yeniden başlat. Eklenti sandbox'ının açılması ~40 saniye sürebilir.
4. Günlük: `%APPDATA%\CloudStream\logs\app.log`

> Depodan kurulu bir eklentiyle aynı adı taşıyan yerel eklenti, depodaki kopya kaldırılırken onunla birlikte kaldırılabilir. Test derlemesinde `name` değerini geçici olarak değiştir (ör. `"FullHDFilm Test"`).

### Site adres değiştirdiğinde

1. `<Eklenti>/src/main/kotlin/…/<Eklenti>.kt` içinde `override var mainUrl = "…"` satırını yeni adresle değiştir.
2. `build.gradle.kts` içindeki `version` değerini bir artır.
3. Push et; GitHub Actions derleyip yayınlar.

Eski adres yeni adrese yönleniyorsa genelde bir şey yapmaya gerek kalmaz: CloudStream yönlendirmeyi takip eder ve `KONTROL.py` robotu otomatik PR açar.

### Bir site "ölmüş" görünüyorsa

Önce engel yüzünden yanlış alarm olup olmadığını kontrol et:

```powershell
python ENGEL_KONTROL.py              # tüm eklentiler
python ENGEL_KONTROL.py FullHDFilm   # tek eklenti
```

| Sonuç | Anlamı |
|---|---|
| `CALISIYOR` | Site normal yoldan açılıyor. |
| `ENGELLI` | Türkiye'de engelli ama yayında. Eklenti çalışır, **kapatma**. |
| `OLU` | Alan adı hiçbir yerde yok; yeni adres bulunmalı. |
| `ERISILEMIYOR` | Alan adı var ama sunucu iki yoldan da cevap vermiyor. |

### Yeni site ekleme

1. `__Temel/` klasörünü kopyala ve site adıyla yeniden adlandır (ör. `YeniSite`).
2. `Temel.kt` → `YeniSite.kt` yap ve sınıf adını değiştir; `YeniSitePlugin.kt` içinde `registerMainAPI(YeniSite())` yaz.
3. `build.gradle.kts` içinde açıklama, dil, tür ve ikonu ayarla; `version = 1` ile başla.
4. `YeniSite.kt` içindeki fonksiyonları sitenin HTML'ine göre doldur:

| Fonksiyon | Görevi |
|---|---|
| `mainPage` | Ana ekrandaki sekmeler ve adresleri |
| `getMainPage()` | Sekmeleri doldurur |
| `search()` | Arama sonuçları |
| `load()` | Film/dizi detayı ve bölüm listesi |
| `loadLinks()` | Video bağlantıları ve altyazılar |

`settings.gradle.kts` yeni klasörü kendisi bulur, ayrıca kayıt gerekmez.

### Yayınlama

Her push'ta `Derleyici.yml` bütün eklentileri derleyip `builds` dalına `.cs3` dosyalarını ve `plugins.json`'u koyar. Kullanıcılar güncellemeyi CloudStream üzerinden alır.

### Kısa kod bakımı

`!poblostream`, [py.md](https://py.md/) üzerinde tanımlı bir kısa bağlantıdır (`py.md/poblostream`) ve depo tanımına yönlenir. CloudStream `!` ile başlayan kodları bu servise gönderip yönlendirmeyi takip eder.

- Hedef: `https://raw.githubusercontent.com/PobloMert/PobloStream/builds/repo.json`
- ⚠️ Bağlantıya **şifre konmamalı**; şifre olursa CloudStream yönlendirmeyi göremez.

Doğrulama:

```powershell
curl.exe -sI https://py.md/poblostream
# HTTP/1.1 302 Found
# location: https://raw.githubusercontent.com/PobloMert/PobloStream/builds/repo.json
```

---

## 🙏 Emeği geçenler

- **[@keyiflerolsun](https://github.com/keyiflerolsun)**: Eklentilerin büyük çoğunluğunun asıl yazarı. [Kekik-cloudstream](https://github.com/keyiflerolsun/Kekik-cloudstream) projesi bu deponun temelidir.
- **[@feroxx](https://github.com/feroxx)**: [Kekik-cloudstream çatalı](https://github.com/feroxx/Kekik-cloudstream). DiziKorea, DiziPal Orijinal, DiziYou, SinemaCX ve TLCtr eklentileri bu depodan aktarılıp güncellendi.
- **Eklenti katkıcıları:** hexated (HDFilmCehennemi), inatchii ve JustRelaxable (AnimeciX, BelgeselX), muratcesmecioglu (DiziPal), nikyokki (AsyaWatch, DMax, TvDiziler) ve diğer Kekik katkıcıları.
- **[CloudStream](https://github.com/recloudstream/cloudstream)** ekibi: Uygulama ve eklenti altyapısı.
- **Bakım:** Ahmet ([@PobloMert](https://github.com/PobloMert)): güncel adresler, erişim engeli katmanı, onarımlar.

Her eklentinin yazarları kendi `build.gradle.kts` dosyasındaki `authors` alanında listelenir.

## 📜 Lisans

[GPL-3.0](LICENSE). Bu depo GPL-3.0 lisanslı Kekik-cloudstream projesinden türetilmiştir; türetilmiş çalışmalar da aynı lisansla paylaşılmalıdır.

Bu depo yalnızca eklenti kodu içerir; hiçbir video veya içerik barındırmaz. İçerikler üçüncü taraf sitelerden gelir ve onların sorumluluğundadır.
