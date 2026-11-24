# 📺 PobloStream — CloudStream Eklenti Deposu

CloudStream (Android) için Türkçe film/dizi/anime sitesi eklentileri.
Bu depo, [@keyiflerolsun](https://github.com/keyiflerolsun)'un [Kekik-cloudstream](https://github.com/keyiflerolsun/Kekik-cloudstream) projesinden (GPL-3.0) türetilmiştir; **güncel adreslerle çalışan** eklentileri içerir.

## 📁 Depo yapısı

```
PobloStream/
├── DiziBox/                  ← her klasör = bir eklenti
│   ├── build.gradle.kts      ← eklenti kimliği (sürüm, dil, ikon, açıklama)
│   └── src/main/kotlin/com/keyiflerolsun/
│       ├── DiziBox.kt        ← ASIL KOD: siteyi kazıyan dosya
│       └── DiziBoxPlugin.kt  ← 12 satırlık kayıt dosyası (dokunma)
├── __Temel/                  ← yeni eklenti için şablon (kopyala + düzenle)
├── build.gradle.kts          ← kök derleme ayarları (nadiren dokunulur)
├── settings.gradle.kts       ← klasörleri otomatik bulur (yeni eklenti = hiçbir şey yapma)
├── repo.json                 ← CloudStream'e eklenecek depo adresi
├── KONTROL.py                ← domain değişikliği yakalayan otomatik denetleyici
└── .github/workflows/
    ├── Derleyici.yml         ← push atınca .cs3'leri otomatik derler (builds dalına)
    └── Kontrol.yml           ← 9 saatte bir domain kontrolü yapar, değişince PR açar
``

## 📲 Telefona kurulum (derlemeden sonra)

1. `EklentiAdı/build/EklentiAdı.cs3` dosyasını telefona at (ör. WhatsApp/USB/Drive).
2. CloudStream → **Ayarlar → Eklentiler → 📦 Dosyadan kur** → .cs3'ü seç.

Ya da yayınladıysan depoyu ekle: **Ayarlar → Eklentiler → Depo ekle** → repo.json adresi.

## 🔧 Site domain değiştirdiğinde (30 saniyelik onarım)

Bu siteler adres değiştirmeyi sever. Düzeltme tek yerde:

1. `EklentiAdi/src/main/kotlin/com/keyiflerolsun/EklentiAdi.kt` dosyasını aç
2. `override var mainUrl = "https://eski-adres.com"` → yeni adresi yaz
3. `EklentiAdi/build.gradle.kts` içindeki `version = X` → `X+1` yap
4. Derle, kur, bitti.

Eski adres hâlâ yönlenme (redirect) yapıyorsa hiçbir şey yapmana bile gerek yok — CloudStream yönlendirmeyi takip eder ve **KONTROL.py robotu** bunu zaten 9 saatte bir kontrol edip otomatik PR açar (bkz. `Kontrol.yml`). Robot yalnızca eski adres tamamen ölünce çaresizdir; o durumda yeni adresi yukarıdaki gibi elle gir.

## ➕ Yeni site ekleme

1. `__Temel/` klasörünü kopyala, klasör adını site adı yap (ör. `YeniSite`)
2. İçindeki `Temel.kt` → `YeniSite.kt` olarak yeniden adlandır, sınıf adını da değiştir
3. `YeniSitePlugin.kt` içindeki `registerMainAPI(Temel())` → `registerMainAPI(YeniSite())`
4. `build.gradle.kts`'te açıklama/dil/tür/ikon ayarla, `version = 1`
5. `YeniSite.kt` içinde 5 fonksiyonu sitenin HTML'ine göre doldur:

| Fonksiyon | Ne yapar |
|---|---|
| `mainPage` | Ana ekrandaki sekmeler ve adresleri |
| `getMainPage()` | Sekme listesini doldurur (site + Jsoup selector) |
| `search()` | Arama sonuçları |
| `load()` | Dizi/film detayı + bölüm listesi |
| `loadLinks()` | Video linklerini çıkarır (oynatıcı iframe'leri) |

Selector'ları bulmak için: tarayıcıda siteyi aç → sağ tık → İncele → HTML'de
listelerin/kartların ortak class'larını bul → `document.select("article.card")` gibi kullan.

`settings.gradle.kts` klasörleri otomatik bulur; kayıt yapmana gerek yok.

## 🌐 GitHub'a yayınlama

Depo adresi: **https://github.com/PobloMert/PobloStream**

1. GitHub'da `PobloStream` adında **public** depo aç
2. Bu klasördeki git deposunu bağla ve push et:
   ```
   git remote add origin https://github.com/PobloMert/PobloStream.git
   git push -u origin master
   ```
   (`build.gradle.kts` ve `repo.json` içindeki adresler zaten PobloMert/PobloStream olarak ayarlı)
3. GitHub'da **Actions** sekmesinden "CloudStream Derleyici" işini kontrol et — bitince `builds` dalı oluşur
4. CloudStream'de Depo ekle → `https://raw.githubusercontent.com/PobloMert/PobloStream/master/repo.json`

Bundan sonra her `git push` tüm eklentileri yeniden derler; domain robotu da kendi PR'larını açar.

## 🔑 Kısa kod: `!poblostream`

CloudStream kısa kodları uygulamaya gömülü **değildir** — uygulama yazılan kodu bir URL kısaltma servisine gönderip yönlendirmeyi takip eder (kaynak: `RepositoryManager.parseRepoUrl`):
- `!poblostream` → `https://py.md/poblostream` → 302 → repo.json  ✅ **aktif**
- işaretsiz kodlar → `https://cutt.ly/{kod}` → repo.json

### Kurulum (kullanıcılar için)
CloudStream → Eklentiler → Depo ekle → şunu yaz:
```
!poblostream
```

### Bakım (senin için)
Kısa kod [py.md](https://py.md/) üzerinde oluşturulmuş bir custom alias'tır:
- **Custom URL:** `py.md/poblostream`
- **Hedef:** `https://raw.githubusercontent.com/PobloMert/PobloStream/master/repo.json`
- ⚠️ **Şifre kesinlikle boş olmalı** — şifre konursa CloudStream yönlendirmeyi göremez ve depoyu ekleyemez
- Depo adresi değişirse py.md'de linki düzenleyip hedefi güncelle

Doğrulama komutu:
```powershell
curl.exe -sI https://py.md/poblostream
# HTTP/1.1 302 Found + location: https://raw.githubusercontent.com/PobloMert/PobloStream/master/repo.json
```

## 📜 Lisans ve teşekkür

GPL-3.0. Eklentilerin asıl yazarı [@keyiflerolsun](https://github.com/keyiflerolsun) ve katkıda bulunanlar;
bakım ve güncel domainler: Ahmet (PobloMert).
