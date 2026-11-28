# SporTV — Durum (28 Kasım 2026)

Yalnızca kanalların **kendi sitelerinden** ücretsiz ve herkese açık yayınladığı canlı yayınlar.
Ücretli platformlar (beIN, S Sport, Exxen, TOD, Tabii Spor) ve izinsiz yeniden yayın yapan siteler kullanılmaz.

| Kanal | Kaynak | Yayın adresi nereden |
|---|---|---|
| TRT Spor | sabit | `tv-trtspor1.medya.trt.com.tr` (TRT) |
| TRT Spor Yıldız | sabit | `tv-trtspor2.medya.trt.com.tr` (TRT) |
| A Spor | turkuvaz | Turkuvaz oynatıcı API'si `videojs.tmgrup.com.tr/getvideo/...` → VideoUrl |
| HT Spor | sayfa | `htspor.com/canli-yayin` sayfasındaki m3u8 |
| TJK TV | sabit | `tjktv-live.tjk.org/tjktv` (TJK) |
| TAY TV | sayfa | `tjk.org` canlı sayfasındaki m3u8 |

- Kanal eklemek/değiştirmek: `spor_tv_kanallar.json` → `master`'a gönder (uygulama depodaki kopyayı okur).
  Gömülü kopyayı da güncellemek için: `python SporTV/kanallari_gom.py`.
- Sports TV (sportstv.com.tr) sitesi şu an "yapım aşamasında"; yayın bulunursa eklenebilir.
- Bazı yayınlar yalnızca Türkiye'den açılır (yurt dışında 403 dönebilir).
- **Şu an yayında:** `akis_url` + `akis_desen` (saat, program adı) olan kanallarda kart başlığına eklenir. Şu an TRT Spor, TRT Spor Yıldız (trtspor.com.tr/yayin-akisi) ve A Spor (aspor.com.tr/yayin-akisi). HT Spor ve TJK'nın yayımlanmış akışı bulunamadı.
- **Sıralama:** kanallar `sira` alanına göre (küçük önce), gruplar `gruplar` listesine göre dizilir.
- **Haftalık test** her kanalın yayın bağlantısını ayrı ayrı açar.
