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

Son değişiklikler: [DEGISIKLIKLER.md](DEGISIKLIKLER.md)

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
| SporTV 🆕 | Spor kanallarının resmî canlı yayınları: TRT Spor, TRT Spor Yıldız, A Spor, HT Spor, TJK TV, TAY TV | kanalların kendi siteleri |
| TLCtr 🆕 | TLC programları | tlctv.com.tr |
| TurkAnime | Anime | turkanime.to |
| TvDiziler | Yerli dizi | tvdiziler.tv |

🆕 = feroxx/Kekik-cloudstream deposundan aktarılıp güncellenen eklentiler.

### ⚽ Canlı spor kanalları

Depo, film/dizilerin yanında **29 canlı spor eklentisini** de listeler (`Live`). Bunlar [Wiojelt/WioSpor](https://github.com/Wiojelt/WioSpor) deposundan bu depoya aynalanmıştır; kurulum ve indirme yine PobloStream üzerinden yapılır ve eklentiler CloudStream'de **Canlı TV** sekmesinde görünür.

SelcukSports · Taraftarium24 · PatronHD · KralSporHD · ArdaSpor · BetmatikTV · TRGoals · DomatesTV · MahsunSports · AtomSpor · LeoTV · Streamed · JestYayin · PapazSports · NetVGold · v10 Spor · Mien TV · Kool TV · StbTV · AslanTV · KoalaTV · BeyazElma · NewTVLive · çiftd2m · TimStreams · RecTV · BasketballVideo · BasketballReplays · WioSpor


---

## 🛡️ Erişim engeli (VPN gerekmez)

Türkiye'deki erişim engeli iki katmanlıdır: DNS sorgusu cevapsız kalır ve bağlantı sırasında site adı (TLS ClientHello içindeki SNI) görülünce bağlantı kesilir. Bütün eklentiler ortak bir katmanla bunu kendi içinde aşar:

| Dosya | Ne yapar |
|---|---|
| `_ortak/…/EngelAsici.kt` | Alan adını DNS-over-HTTPS (1.1.1.1, yedek 8.8.8.8) ile çözer. ClientHello'yu site adının ortasından iki TLS kaydına böler; bu TLS standardına uygundur, sunucu parçaları birleştirir, engel ise adı tek parçada göremez. Herhangi bir sorunda istek uygulamanın normal istemcisiyle yeniden denenir. |
| `_ortak/…/YerelAktarici.kt` | Videoyu uygulamanın oynatıcısı indirir, eklenti değil. Video sunucusu engelliyse oynatıcı `127.0.0.1` üzerindeki bu küçük aktarıcıya bağlanır, istek `EngelAsici` üzerinden iletilir. HLS çalma listeleri buna göre yeniden yazılır. |

`_ortak/` klasörü kök `build.gradle.kts` tarafından her eklentiye otomatik eklenir. Kullanıcının ayrıca bir şey yapması gerekmez.

---

## 🌐 Site adresi değişince

Eklentiler açılışta sitelerin güncel adresini [`domains.json`](domains.json) dosyasından okur. Bir site adres değiştirince (ör. `dizipal1599.com` → `dizipal1600.com`) **yalnızca bu dosyadaki satırı düzenleyip `master`'a göndermek yeter**; yeni sürüm derlemeye ya da kullanıcıların güncelleme indirmesine gerek yoktur. Yeni adres, uygulama bir sonraki açılışta devreye girer.

```json
"DiziPalOrijinal": { "adres": "https://dizipal1600.com", "eski": ["https://dizipal1599.com"] }
```

- Anahtar, eklentinin klasör adıdır. Eski adres yoksa düz yazmak da olur: `"FullHDFilm": "https://hdfilm.us"`.
- **Kayıtlı favoriler bozulmaz:** eski adresle kaydedilmiş film/dizi açılınca istek otomatik olarak yeni adrese çevrilir (`eski` listesi + koddaki adres + cihazın daha önce gördüğü adresler).
- Dosya okunamazsa cihazdaki son liste, o da yoksa koddaki adres kullanılır.
- `python ENGEL_KONTROL.py` de adresleri bu dosyadan alır.
- **Kullanıcı da değiştirebilir:** CloudStream → Eklentiler → eklentinin ⚙️ simgesi → yeni adres. Yazılan adres listedekinin önüne geçer ve hemen geçerli olur; boş bırakılırsa otomatik adrese dönülür.

**Otomatik denetim:** [Site Adresi Denetimi](.github/workflows/AlanAdi.yml) her pazartesi çalışır (Actions sekmesinden elle de başlatılabilir). Eski adres yenisine yönlendiriyorsa `domains.json`'u düzelten bir PR açar; birleştirmen yeter. Hiç açılmayan siteler için bir issue açar. Aynı denetim yerelde: `python ALAN_ADI_GUNCELLE.py` (Türkiye'den çalıştırınca engelli siteler "OLU" görünebilir).

## 🧪 Haftalık eklenti testi

[Eklenti Testi](.github/workflows/EklentiTesti.yml) her pazartesi her eklentiyi gerçek sitede dener: ana sayfadan liste çeker ve ilk içeriğin detay sayfasını açar. Site tasarımı değişip eklenti boş gelmeye başlarsa (adres aynı kalsa bile) yakalanır. İlk içeriğin adıyla arama da denenir; sonuç vermeyen aramalar raporda ayrı listelenir (eklentiyi "çalışmıyor" saydırmaz).

- Bozulan eklenti için `status = 0` yapan bir PR açılır; birleştirince CloudStream onu **"çalışmıyor"** gösterir. Eklenti düzelince test bir sonraki hafta `1`'e geri alır.
- Sonuç tablosu her çalışmanın **Summary** sayfasında görünür.
- Cloudflare doğrulaması isteyen siteler tarayıcısız denenemez; `DOGRULANAMADI` yazılır, bozuk sayılmaz.
- Bilgisayarda: `./gradlew :DiziBox:testDebugUnitTest` ardından `python EKLENTI_TESTI.py` (klasör yolunda Türkçe karakter olmamalı).

## 🔑 Kısa kod bakımı

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
