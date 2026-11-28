#!/usr/bin/env python3
"""
Eklenti testinin (_test/EklentiTesti.kt) sonuçlarını toplar ve eklenti durumlarını günceller.

Önce testleri çalıştırın:
    ./gradlew testDebugUnitTest --continue          (tek eklenti: ./gradlew :DiziBox:testDebugUnitTest)
Sonra:
    python EKLENTI_TESTI.py                 # tablo
    python EKLENTI_TESTI.py --durum         # build.gradle.kts'deki status'u sonuca göre değiştir
    python EKLENTI_TESTI.py --rapor r.md    # markdown rapor (GitHub Actions PR gövdesi)

--durum: BOZUK → status = 0 (CloudStream "çalışmıyor" gösterir), status 0 iken yeniden CALISIYOR → 1.
DOGRULANAMADI (Cloudflare; bilgisayarda WebView yok) ve sonucu olmayan eklentilere dokunulmaz.

! Klasör yolunda Türkçe karakter (ör. "Masaüstü") varsa Gradle testi sınıfı bulamıyor; testi
  ASCII bir yolda çalıştırın. GitHub Actions'ta sorun yok.
"""
import argparse
import json
import os
import pathlib
import re

KOK = pathlib.Path(__file__).resolve().parent
DURUM_RE = re.compile(r"^(\s*status\s*=\s*)(\d)", re.M)


def sonuclar():
    for f in sorted(KOK.glob("*/build/eklenti_testi.json")):
        try:
            yield json.loads(f.read_text(encoding="utf-8"))
        except ValueError:
            pass


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--durum", action="store_true", help="build.gradle.kts status değerini güncelle")
    ap.add_argument("--rapor", help="markdown rapor dosyası")
    a = ap.parse_args()

    satirlar, degisen, arama_sorun, kanal_sorun = [], [], [], []
    for s in sonuclar():
        ad, durum = s.get("eklenti"), s.get("durum")
        g = KOK / ad / "build.gradle.kts"
        metin = g.read_text(encoding="utf-8") if g.exists() else ""
        m = DURUM_RE.search(metin)
        eski = int(m.group(2)) if m else None
        yeni = eski
        if durum == "BOZUK" and eski not in (0, None):
            yeni = 0
        elif durum == "CALISIYOR" and eski == 0:
            yeni = 1
        if a.durum and m and yeni != eski:
            g.write_text(metin[:m.start(2)] + str(yeni) + metin[m.end(2):], encoding="utf-8", newline="\n")
        if yeni != eski:
            degisen.append((ad, eski, yeni))
        ayrinti = s.get("hata") or f"{s.get('liste', '?')} içerik ({s.get('bolum', '')}), detay: {s.get('detay', '')}"
        arama = s.get("arama", "-")
        satirlar.append((ad, durum, s.get("adres", ""), str(ayrinti).replace("|", "/")[:160], str(arama).replace("|", "/")))
        for kanal, sonuc in (s.get("kanallar") or {}).items():
            if sonuc != "OK":
                kanal_sorun.append((ad, kanal, sonuc))
        if str(arama).startswith(("BOS", "HATA")):
            arama_sorun.append((ad, s.get("arama_sorgu", ""), arama))
        print(f"{ad:20} {durum:14} arama={arama:12} {ayrinti}")

    if a.rapor:
        r = []
        if os.environ.get("GITHUB_ACTIONS"):
            r += ["> ⚠️ Bu test GitHub'ın yurt dışındaki sunucusunda çalıştı. Birçok site (ve TRT yayını) yurt dışından "
                  "gelen bağlantıya boş sayfa/hata veriyor; **BOZUK görünen eklentiler Türkiye'de çalışıyor olabilir.** "
                  "Eklenti durumları burada değiştirilmez; Türkiye'den `python EKLENTI_TESTI.py --durum` ile yapılır.", ""]
        if degisen:
            baslik = "## Durumu değişen eklentiler" if a.durum else "## Durum önerisi (uygulanmadı)"
            r += [baslik, "", "| Eklenti | Önce | Sonra |", "|---|---|---|"]
            r += [f"| {ad} | {e} | {y} {'(çalışmıyor)' if y == 0 else '(çalışıyor)'} |" for ad, e, y in degisen]
            r += ["", "`status = 0` olan eklenti CloudStream'de \"çalışmıyor\" olarak görünür. Düzeltince test bir sonraki hafta 1'e geri alır.", ""]
        if kanal_sorun:
            r += ["## Açılmayan canlı kanallar", "", "| Eklenti | Kanal | Sonuç |", "|---|---|---|"]
            r += [f"| {ad} | {k} | {str(sn).replace('|', '/')} |" for ad, k, sn in kanal_sorun]
            r += ["", "Kanalın yayın adresi değişmiş olabilir; ilgili JSON'u (ör. SporTV/spor_tv_kanallar.json) düzeltin.", ""]
        if arama_sorun:
            r += ["## Araması sonuç vermeyenler", "", "| Eklenti | Sorgu | Sonuç |", "|---|---|---|"]
            r += [f"| {ad} | `{q}` | {a} |" for ad, q, a in arama_sorun]
            r += ["", "Arama bozuk olsa da eklenti \"çalışmıyor\" sayılmaz; seçicileri elle kontrol edin.", ""]
        r += ["## Test sonuçları", "", "| Eklenti | Durum | Arama | Adres | Ayrıntı |", "|---|---|---|---|---|"]
        r += [f"| {ad} | {d} | {ar} | `{adr}` | {ay} |" for ad, d, adr, ay, ar in satirlar]
        r += ["", "Test her eklentide ana sayfadan liste çeker ve ilk içeriğin detay sayfasını açar. "
              "DOGRULANAMADI: site Cloudflare doğrulaması istiyor, tarayıcısız test edilemedi (bozuk sayılmaz)."]
        pathlib.Path(a.rapor).write_text("\n".join(r) + "\n", encoding="utf-8")

    cikti = os.environ.get("GITHUB_OUTPUT")
    if cikti:
        with open(cikti, "a", encoding="utf-8") as f:
            f.write(f"degisti={1 if degisen else 0}\n")
    for ad, k, sn in kanal_sorun:
        print(f"  açılmayan kanal: {ad} / {k}: {sn}")
    print(f"\n{len(satirlar)} eklenti, durumu değişen: {len(degisen)}")


if __name__ == "__main__":
    main()
