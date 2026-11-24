# 📺 cs-ahmet — CloudStream Eklenti Deposu

CloudStream (Android) için Türkçe film/dizi/anime sitesi eklentileri.
Bu depo, [@keyiflerolsun](https://github.com/keyiflerolsun)'un [Kekik-cloudstream](https://github.com/keyiflerolsun/Kekik-cloudstream) projesinden (GPL-3.0) türetilmiştir; **güncel adreslerle çalışan** eklentileri içerir.

## 📁 Depo yapısı

```
cs-ahmet/
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
```

## 🛠 Bilgisayarında derleme (local build)

Gereksinimler (bu makinede hazır): JDK 21, Android SDK (platform-35).

```powershell
cd "C:\Users\ahmet\OneDrive\Masaüstü\Youtube Direktörü\cloudstream\cs-ahmet"
$env:JAVA_HOME    = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat make makePluginsJson
```

Çıktılar: her eklentinin `build/*.cs3` dosyası + kökte `build/plugins.json`.

> Not: `JAVA_HOME` sistemde JDK 25'i gösteriyor; Gradle 8.12 onu desteklemediği
> için yukarıdaki gibi JDK 21'i açıkça göstermek gerekiyor.

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

1. GitHub'da `cs-ahmet` adında depo aç (private bile olur ama CloudStream'in çekebilmesi için **public** olmalı)
2. Bu klasördeki `KULLANICI_ADIN` geçen 2 yeri kendi kullanıcı adınla değiştir:
   - `build.gradle.kts` → `setRepo(...)`
   - `repo.json` → `pluginLists` adresi
3. `git init && git add . && git commit -m "ilk sürüm" && git remote add origin https://github.com/KULLANICI_ADIN/cs-ahmet.git && git push -u origin master`
4. GitHub'da **Actions** sekmesinden "CloudStream Derleyici" işini kontrol et — bitince `builds` dalı oluşur
5. CloudStream'de Depo ekle → `https://raw.githubusercontent.com/KULLANICI_ADIN/cs-ahmet/master/repo.json`

Bundan sonra her `git push` tüm eklentileri yeniden derler; domain robotu da kendi PR'larını açar.

## 📜 Lisans ve teşekkür

GPL-3.0. Eklentilerin asıl yazarı [@keyiflerolsun](https://github.com/keyiflerolsun) ve katkıda bulunanlar;
bakım ve güncel domainler: Ahmet.
