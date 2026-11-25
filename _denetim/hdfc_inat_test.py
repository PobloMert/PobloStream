# -*- coding: utf-8 -*-
"""HDFilmCehennemi kaynak zinciri ve InatBox domain/endpoint canlilik testi."""
import re

import requests
from bs4 import BeautifulSoup

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
      "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.8"}

print("########## 1) HDFilmCehennemi domain adaylari ##########")
adaylar = [
    "https://www.hdfilmcehennemi.nl", "https://hdfilmcehennemi.nl",
    "https://www.hdfilmcehennemi.la", "https://hdfilmcehennemi.la",
    "https://www.hdfilmcehennemi.com", "https://www.hdfilmcehennemi.com.tr",
    "https://www.hdfilmcehennemi.biz", "https://hdfilmcehennemi7.com",
]
for u in adaylar:
    try:
        r = requests.get(u, headers=UA, timeout=20)
        s = BeautifulSoup(r.text, "html.parser")
        baslik = s.title.get_text(strip=True)[:55] if s.title else ""
        print(f"  {r.status_code} {len(r.content):>8}B alt-link={len(s.select('.alternative-link')):<3} iframe={len(s.select('iframe')):<3} {u} | {baslik}")
    except Exception as e:
        print(f"  HATA {type(e).__name__:<18} {u}")

print("\n########## 2) InatBox endpoint ##########")
for u in ["https://diziboxen.help/CDN/001/002/dizibox/tv/list1.php",
          "https://diziboxen.help/CDN/001/002/dizibox/film/yerli-filmler.php",
          "https://diziboxen.help/"]:
    try:
        r = requests.get(u, headers=UA, timeout=25)
        print(f"  {r.status_code} {len(r.content):>8}B ct={r.headers.get('content-type')} {u}")
        print("     ", r.text[:180].replace("\n", " "))
    except Exception as e:
        print(f"  HATA {type(e).__name__} {u}")
