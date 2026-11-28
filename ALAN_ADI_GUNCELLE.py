#!/usr/bin/env python3
"""
domains.json'daki her sitenin güncel adresini bulur.

Siteler adres değiştirince eski adres çoğu zaman yenisine yönlendirir (301/302, meta refresh
ya da kısa bir JavaScript yönlendirmesi). Bu betik her adresi açar, yönlendirmeyi izler ve
farklı bir alan adına varırsa onu yeni adres olarak bildirir.

Kullanım:
    python ALAN_ADI_GUNCELLE.py                 # yalnızca rapor
    python ALAN_ADI_GUNCELLE.py --yaz           # domains.json'u güncelle (eski adres "eski" listesine eklenir)
    python ALAN_ADI_GUNCELLE.py --rapor r.md    # markdown rapor yaz (GitHub Actions PR/issue gövdesi)
    python ALAN_ADI_GUNCELLE.py DiziPalOrijinal # tek site

Not: Türkiye'den çalıştırılırsa erişim engeli yüzünden siteler "ulaşılamıyor" görünebilir;
asıl kullanım yeri haftalık GitHub Actions işidir (.github/workflows/AlanAdi.yml).
"""
import argparse
import json
import os
import pathlib
import re
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request

KOK = pathlib.Path(__file__).resolve().parent
DOSYA = KOK / "domains.json"
ZAMAN = 20
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"

# Yönlendirme bunlara giderse yeni adres sayılmaz (duyuru kanalı, park sayfası vb.)
YOK_SAY = re.compile(
    r"(^|\.)(t\.me|telegram\.(me|org)|twitter\.com|x\.com|instagram\.com|facebook\.com|google\.[a-z.]+|"
    r"bit\.ly|linktr\.ee|cloudflare\.com|sedo\.com|dan\.com|afternic\.com|godaddy\.com|hugedomains\.com|"
    r"parkingcrew\.net|bodis\.com|namecheap\.com|btk\.gov\.tr|ihbarweb\.org\.tr)$",
    re.I,
)
META = re.compile(r"""<meta[^>]+http-equiv=["']?refresh["']?[^>]+content=["'][^"']*url=([^"'>\s]+)""", re.I)
JS = re.compile(r"""(?:window\.|document\.)?location(?:\.href)?\s*(?:=|\.replace\(|\.assign\()\s*["'](https?://[^"']+)["']""", re.I)


def kok_adres(url):
    p = urllib.parse.urlsplit(url)
    return f"{p.scheme}://{p.netloc}".lower()


def host(url):
    return (urllib.parse.urlsplit(url).hostname or "").lower()


def ayni_site(a, b):
    return host(a).removeprefix("www.") == host(b).removeprefix("www.")


class _Tut(urllib.request.HTTPRedirectHandler):
    """Yönlendirme zincirini kaydeder."""
    def __init__(self):
        self.zincir = []

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        self.zincir.append((code, newurl))
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def ac(url):
    """(son_url, kod, govde_basi, basliklar) ya da hata metni."""
    tut = _Tut()
    acici = urllib.request.build_opener(tut, urllib.request.HTTPSHandler(context=ssl.create_default_context()))
    istek = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "text/html,*/*", "Accept-Language": "tr-TR,tr;q=0.9"})
    try:
        with acici.open(istek, timeout=ZAMAN) as r:
            return r.geturl(), r.status, r.read(64 * 1024).decode("utf-8", "replace"), dict(r.headers)
    except urllib.error.HTTPError as e:
        govde = e.read(64 * 1024).decode("utf-8", "replace") if e.fp else ""
        return e.geturl() or url, e.code, govde, dict(e.headers or {})
    except Exception as e:  # DNS, bağlantı, TLS
        return str(getattr(e, "reason", e))


def cloudflare_mi(kod, govde, basliklar):
    b = {k.lower(): v for k, v in basliklar.items()}
    return kod in (403, 503, 429) and ("cloudflare" in b.get("server", "").lower() or "challenge-platform" in govde or "Just a moment" in govde)


def denetle(adres):
    """{'durum': AYNI|DEGISTI|OLU, 'yeni': ..., 'not': ...}"""
    sonuc = ac(adres)
    if isinstance(sonuc, str):
        return {"durum": "OLU", "not": sonuc[:120]}
    son, kod, govde, basliklar = sonuc

    # HTTP yönlendirmesi başka bir siteye vardıysa
    aday = son if not ayni_site(son, adres) else None
    # Sayfa içi yönlendirme (meta refresh / kısa JS) — yalnızca küçük sayfalarda güvenilir
    if not aday and len(govde) < 8000:
        m = META.search(govde) or JS.search(govde)
        if m:
            hedef = urllib.parse.urljoin(son, m.group(1))
            if not ayni_site(hedef, adres):
                aday = hedef

    if aday:
        if YOK_SAY.search(host(aday)):
            return {"durum": "OLU" if kod >= 400 else "AYNI", "not": f"yönlendirme yok sayıldı → {host(aday)}"}
        # Yeni adres gerçekten açılıyor mu?
        dogrula = ac(kok_adres(aday) + "/")
        if isinstance(dogrula, str):
            return {"durum": "OLU", "not": f"{host(aday)} adresine yönlendiriyor ama o da açılmıyor ({dogrula[:60]})"}
        _, k2, g2, b2 = dogrula
        if k2 < 400 or cloudflare_mi(k2, g2, b2):
            return {"durum": "DEGISTI", "yeni": kok_adres(aday), "not": f"HTTP {k2}"}
        return {"durum": "OLU", "not": f"{host(aday)} → HTTP {k2}"}

    if kod < 400 or cloudflare_mi(kod, govde, basliklar):
        return {"durum": "AYNI", "not": f"HTTP {kod}" + (" (Cloudflare)" if kod >= 400 else "")}
    return {"durum": "OLU", "not": f"HTTP {kod}"}


def adres_of(deger):
    return deger if isinstance(deger, str) else deger.get("adres")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("site", nargs="?")
    ap.add_argument("--yaz", action="store_true", help="domains.json'u güncelle")
    ap.add_argument("--rapor", help="markdown rapor dosyası")
    a = ap.parse_args()

    veri = json.loads(DOSYA.read_text(encoding="utf-8"))
    degisen, olu = [], []
    for ad, deger in veri.items():
        if a.site and ad.lower() != a.site.lower():
            continue
        adres = adres_of(deger).rstrip("/")
        s = denetle(adres)
        print(f"{ad:20} {s['durum']:8} {adres}" + (f" → {s['yeni']}" if s.get("yeni") else "") + f"   {s.get('not', '')}", flush=True)
        if s["durum"] == "DEGISTI":
            degisen.append((ad, adres, s["yeni"]))
            if a.yaz:
                eski = [] if isinstance(deger, str) else list(deger.get("eski", []))
                if adres not in eski:
                    eski.append(adres)
                veri[ad] = {"adres": s["yeni"], "eski": eski}
        elif s["durum"] == "OLU":
            olu.append((ad, adres, s.get("not", "")))

    if a.yaz and degisen:
        DOSYA.write_text(json.dumps(veri, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")

    if a.rapor:
        satirlar = []
        if degisen:
            satirlar += ["## Adresi değişen siteler", "", "| Eklenti | Eski | Yeni |", "|---|---|---|"]
            satirlar += [f"| {ad} | `{e}` | `{y}` |" for ad, e, y in degisen]
            satirlar += ["", "Eski adresler `eski` listesine eklendi; kayıtlı favoriler uygulamada otomatik olarak yeni adrese çevrilir.", ""]
        if olu:
            satirlar += ["## Ulaşılamayan siteler", "", "| Eklenti | Adres | Ayrıntı |", "|---|---|---|"]
            satirlar += [f"| {ad} | `{e}` | {n} |" for ad, e, n in olu]
            satirlar += ["", "Bu siteler yönlendirme yapmadan kapanmış ya da yeni adrese taşınmış olabilir; yeni adresi elle `domains.json`'a yazın.", ""]
        pathlib.Path(a.rapor).write_text("\n".join(satirlar) or "Tüm siteler aynı adreste.\n", encoding="utf-8")

    cikti = os.environ.get("GITHUB_OUTPUT")
    if cikti:
        with open(cikti, "a", encoding="utf-8") as f:
            f.write(f"degisti={1 if degisen else 0}\nolu={1 if olu else 0}\n")
    print(f"\n{len(degisen)} değişen, {len(olu)} ulaşılamayan")


if __name__ == "__main__":
    sys.exit(main())
