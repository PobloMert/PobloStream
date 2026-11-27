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
