# -*- coding: utf-8 -*-
"""PobloStream + Kekik domain canlilik testi."""
import concurrent.futures as cf
import json
import re
import sys

import requests

HDR = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"}

# (eklenti, kaynak, url)
HEDEFLER = [
    # --- PobloStream mevcut ---
    ("DiziMom", "poblo", "https://www.dizimom.plus"),
    ("DiziMom", "kekik", "https://www.dizidad.com"),
    ("DiziPal", "poblo", "https://dizipal1224.com"),
    ("DiziPal", "kekik", "https://dizipal1597.com"),
    ("DiziPalOriginal", "kekik", "https://dizipal1.rest"),
    ("FullHDFilmizlesene", "poblo", "https://www.fullhdfilmizlesene.tv"),
    ("FullHDFilmizlesene", "kekik", "https://www.fullhdfilmizlesene.now"),
    ("TurkAnime", "poblo", "https://turkanime.to"),
    ("TurkAnime", "kekik", "https://www.turkanime.tv"),
    ("AsyaWatch", "poblo", "https://asyawatch.com"),
    ("AsyaAnimeleri", "kekik", "https://asyaanimeleri.top"),
    ("TvDiziler", "poblo", "https://tvdiziler.tv"),
    ("DMax", "poblo", "https://www.dmax.com.tr"),
    ("TLCtr", "kekik", "https://www.tlctv.com.tr"),
    ("AnimeciX", "ikisi", "https://animecix.tv"),
    ("BelgeselX", "ikisi", "https://belgeselx.com"),
    ("CizgiMax", "ikisi", "https://cizgiduo.online"),
    ("Ddizi", "ikisi", "https://www.ddizi.tel"),
    ("DiziBox", "ikisi", "https://www.dizibox.live"),
    ("Dizilla", "ikisi", "https://contentx.me"),
    ("FullHDFilm", "ikisi", "https://hdfilm.us"),
    ("JetFilmizle", "ikisi", "https://jetfilmizle.app"),
    ("KultFilmler", "ikisi", "https://kultfilmler.net"),
    # --- Kekik'te olup Poblo'da olmayan adaylar ---
    ("DiziKorea", "kekik", "https://dizikorea3.com"),
    ("DiziPod", "kekik", "https://dizipod.com"),
    ("DiziYou", "kekik", "https://www.diziyou.one"),
    ("FilmMakinesi", "kekik", "https://closeload.filmmakinesi.to"),
    ("FilmModu", "kekik", "https://www.filmmodu.one"),
    ("SelcukFlix", "kekik", "https://selcukflix.app"),
    ("SetFilmIzle", "kekik", "https://fastplay.mom"),
    ("SezonlukDizi", "kekik", "https://sezonlukdizi.cc"),
    ("SinemaCX", "kekik", "https://sinemacc.com"),
    ("TRanimaci", "kekik", "https://tranimaci.com"),
    ("RecTV", "kekik", "https://a.psrectv80.xyz"),
]


def test(item):
    ad, kaynak, url = item
    try:
        r = requests.get(url, headers=HDR, timeout=20, allow_redirects=True)
        html = r.text[:200000]
        title = ""
        m = re.search(r"<title[^>]*>(.*?)</title>", html, re.S | re.I)
        if m:
            title = re.sub(r"\s+", " ", m.group(1)).strip()[:60]
        # Cloudflare / engel tespiti
        cf = ""
        low = html.lower()
        if r.status_code in (403, 503) and ("cloudflare" in low or "just a moment" in low or "cf-chl" in low):
            cf = "CLOUDFLARE"
        elif "bu siteye erişim" in low or "erişim engellendi" in low or "blocked" in low[:5000]:
            cf = "ENGELLI?"
        return {
            "eklenti": ad, "kaynak": kaynak, "url": url,
            "kod": r.status_code, "boyut": len(r.content),
            "son_url": r.url if r.url != url else "",
            "baslik": title, "not": cf, "hata": "",
        }
    except Exception as e:
        return {
            "eklenti": ad, "kaynak": kaynak, "url": url,
            "kod": 0, "boyut": 0, "son_url": "", "baslik": "",
            "not": "", "hata": type(e).__name__ + ": " + str(e)[:70],
        }


def main():
    with cf.ThreadPoolExecutor(max_workers=12) as ex:
        sonuc = list(ex.map(test, HEDEFLER))
    sonuc.sort(key=lambda x: (x["eklenti"], x["kaynak"]))
    for s in sonuc:
        durum = "OK" if s["kod"] == 200 else ("REDIR" if 300 <= s["kod"] < 400 else str(s["kod"]))
        extra = s["not"] or s["hata"]
        yon = (" -> " + s["son_url"]) if s["son_url"] else ""
        print(f"{s['eklenti']:<20} {s['kaynak']:<6} {durum:<6} {s['boyut']:>8}B  {s['url']}{yon}  {s['baslik'][:40]}  {extra}")
    with open("_denetim/domain_test.json", "w", encoding="utf-8") as f:
        json.dump(sonuc, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
