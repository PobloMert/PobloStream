# -*- coding: utf-8 -*-
"""cizgimax.online sayfalama + tur URL'leri dogrulama."""
import requests
from bs4 import BeautifulSoup

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0"}

adaylar = [
    "https://cizgimax.online/diziler/?page=2",
    "https://cizgimax.online/diziler/?page=3",
    "https://cizgimax.online/?page=2",
    "https://cizgimax.online/tur/animasyon",
    "https://cizgimax.online/tur/animasyon?page=2",
    "https://cizgimax.online/tur/aile",
    "https://cizgimax.online/tur/komedi",
    "https://cizgimax.online/tur/cocuklar",
    "https://cizgimax.online/tur/aksiyon-macera",
]
for u in adaylar:
    try:
        r = requests.get(u, headers=UA, timeout=22)
        s = BeautifulSoup(r.text, "html.parser")
        n = len(s.select("div.film-item"))
        ad = s.select_one("h1")
        print(f"{r.status_code} film-item={n:<4} {u}  {(ad.get_text(strip=True)[:34] if ad else '')}")
    except Exception as e:
        print(f"HATA {type(e).__name__} {u}")

print("\n=== a.tur link ornekleri (ana sayfa) ===")
r = requests.get("https://cizgimax.online/", headers=UA, timeout=22)
s = BeautifulSoup(r.text, "html.parser")
import re
for m in sorted(set(re.findall(r'href="(/tur/[^"]+)"', r.text)))[:14]:
    print("  ", m)
