# -*- coding: utf-8 -*-
"""Arama endpoint adaylarini dene - hangisi sonuc donuyor."""
import requests
from bs4 import BeautifulSoup

HDR = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
       "X-Requested-With": "XMLHttpRequest"}

ADAYLAR = [
    ("DiziPal", "https://dizipal1.rest/api/search-autocomplete?q=kin", "json"),
    ("DiziPal", "https://dizipal1.rest/arama/kin", "grid-card-poster"),
    ("DiziPal", "https://dizipal1.rest/diziler?kelime=kin", "grid-card-poster"),
    ("DiziMom", "https://www.dizidad.com/arama/kin", "single-item|episode-box"),
    ("DiziMom", "https://www.dizidad.com/arama/?s=kin", "single-item|episode-box"),
    ("DiziMom", "https://www.dizidad.com/?s=kin", "single-item|episode-box"),
    ("CizgiMax", "https://cizgimax.online/search?q=mucize", "film-item"),
    ("CizgiMax", "https://cizgimax.online/arama/mucize", "film-item"),
    ("CizgiMax", "https://cizgimax.online/?s=mucize", "film-item"),
    ("Dizilla", "https://dizilla.la/dizi-ara/?d=blade", "pcard"),
    ("Dizilla", "https://dizilla.la/?s=blade", "pcard"),
    ("Dizilla", "https://dizilla.la/arama/blade", "pcard"),
    ("FHFIzlesene", "https://www.fullhdfilmizlesene.now/arama/minyonlar", "li.film"),
]

for ad, url, beklenti in ADAYLAR:
    try:
        r = requests.get(url, headers=HDR, timeout=22)
        extra = ""
        if beklenti == "json":
            try:
                j = r.json()
                extra = " JSON anahtarlar: " + str(list(j)[:5]) + " | ornek: " + str(j)[:220]
            except Exception:
                extra = " JSON degil: " + r.text[:120].replace("\n", " ")
        else:
            s = BeautifulSoup(r.text, "html.parser")
            tot = 0
            for sel in beklenti.split("|"):
                tot = max(tot, len(s.select(sel)))
            extra = f"  sonuc={tot}"
        print(f"{ad:<12} {r.status_code} {len(r.content):>7}B  {url}{extra}")
    except Exception as e:
        print(f"{ad:<12} HATA {type(e).__name__}  {url}")
