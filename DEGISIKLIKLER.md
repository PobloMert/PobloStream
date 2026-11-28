# 📝 Değişiklikler

Eklentilerde kullanıcının fark edeceği değişiklikler. En yenisi en üstte.
Yeni bir sürüm çıkarınca buraya kısa bir satır ekleyin: `- **Eklenti** vN: ne değişti`.

## 2026-11-28 (2)

**Yeni**
- **SporTV** v3: kanal kartında **şu an yayındaki program** görünüyor (TRT Spor, TRT Spor Yıldız, A Spor; kanalların resmi yayın akışından, 5 dakikada bir yenilenir). Kanal ve grup sırası JSON'daki `sira` ve `gruplar` alanlarıyla ayarlanıyor.
- **SporTV** v2: yalnızca kanalların kendi sitelerinden ücretsiz yayınladığı canlı yayınlar. Kanallar: TRT Spor, TRT Spor Yıldız, A Spor, HT Spor, TJK TV, TAY TV. Yayın adresi her açılışta kanalın kendi sayfasından ya da oynatıcısından alınır. Kanal listesi depodan okunur.
- **Tüm eklentiler**: site adresi artık depodaki [`domains.json`](domains.json) dosyasından okunuyor. Adres değişince yeni sürüm gerekmiyor.
- **Tüm eklentiler**: eski adresle kaydedilmiş favoriler otomatik olarak yeni adrese yönlendiriliyor.
- **Tüm eklentiler**: eklentinin ayarlarına (CloudStream → Eklentiler → ⚙️) **site adresi** alanı eklendi. Site taşındıysa yeni adresi elle yazabilirsiniz.

**Düzeltme**
- **KultFilmler** v18: site yeni tasarıma geçmişti, liste boş geliyordu. Ana sayfa, sayfalama, arama, detay ve oynatıcı (Vidpapi) yeni tasarıma uyarlandı.
- **DiziBox, HDFilmCehennemi, KultFilmler, DiziMom, DiziKorea, Dizilla, JetFilmizle**: Cloudflare doğrulaması çıkınca sayfa artık görünmez tarayıcıyla alınıyor. Önceki yöntem TV'de zaman aşımına düşebiliyordu.
- **DiziBox** v27: site yeni kart tasarımına geçmişti, ana sayfa boş geliyordu; düzeltildi (TV'de ana sayfa, detay ve oynatma denendi). Cloudflare uygulamanın resim yükleyicisine afiş vermediği için afişler artık görünmez tarayıcıyla alınıyor.
- **HDFilmCehennemi**: ana sayfa çalışıyor; olası alan adları artık her kategoride yeniden denenmiyor. Not: site şu an detay sayfalarında Cloudflare doğrulaması istiyor ve uygulama içi tarayıcıyı "Bu uygulama desteklenmiyor" diye engelliyor; site bu korumayı kaldırana kadar detay sayfaları açılmayabilir. Bu süre için eklenti CloudStream'de "çalışmıyor" olarak işaretlendi; koruma kalkınca haftalık test işareti kendiliğinden geri alır.

**Hız**
- **DiziBox, HDFilmCehennemi, KultFilmler, DiziMom, DiziKorea, Dizilla**: ana sayfa kategorileri sırayla değil aynı anda yükleniyor.
- **DiziPal Orijinal** v3: tarayıcıda yalnızca sitenin ana sayfası açılıyor; diğer sayfalar hızlı yoldan ve aynı anda alınıyor. TV'de ana sayfa yaklaşık 3 saniyede doluyor (önceden ~16 sn). Ana sayfa, detay ve oynatma denendi.
- **FullHDFilm**: afiş sunucusu doğrudan açılıyorsa afişler vekil kullanılmadan yükleniyor.

**Bakım**
- Siteler uygulamayı engellediğinde "Error loading" yerine sebebini söyleyen Türkçe bir mesaj gösteriliyor.
- Haftalık test artık aramayı da deniyor; sonuç vermeyen aramalar raporda ayrıca listeleniyor.
- Haftalık test canlı yayın eklentilerinde her kanalı tek tek açıyor; açılmayan kanal raporda ismiyle görünür.
- Birden çok eklentide kopyası bulunan video çözücüler (Odnoklassniki, VideoSeyred, YildizKisaFilm/Vidpapi) tek ortak dosyada toplandı. RareFilmm'deki Odnoklassniki kopyasının hatalı Referer başlığı bu sayede düzeldi.
- Her pazartesi otomatik site adresi denetimi ve eklenti testi yapılıyor. Bozulan eklenti "çalışmıyor" olarak işaretleniyor.

## 2026-11-28

- **Tüm eklentiler**: Android ve TV'de engelli sitelere VPN'siz erişim düzeltildi.
- **DiziPal Orijinal** v2: TV'de açılmama sorunu giderildi (Cloudflare sayfası görünmez tarayıcıyla alınıyor).

## 2026-11-27

- VPN'siz erişim katmanı (DNS-over-HTTPS + bölünmüş TLS) bütün eklentilere eklendi.
- feroxx/Kekik-cloudstream'den 5 eklenti aktarıldı: **DiziKorea**, **DiziPal Orijinal**, **DiziYou**, **SinemaCX**, **TLCtr**.
- **FullHDFilm** v37: yeni site yapısı.

## 2026-11-26

- **BelgeselX** v15: bölüm yönlendirmesinde 403 hatası giderildi.
- **AsyaWatch** v8–v11: yeni site yapısı, sayfa koruması ve oynatıcı düzeltmeleri.
