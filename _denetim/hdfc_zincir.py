# -*- coding: utf-8 -*-
"""HDFilmCehennemi: film sayfasi, kaynak dugmeleri, /video/ API ve oynatici zinciri."""
import json

import requests
from bs4 import BeautifulSoup

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
      "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.8"}
TABAN = "https://www.hdfilmcehennemi.biz"

for u in [f"{TABAN}/?s=cam+gece", f"{TABAN}/arama/?q=cam+gece", f"{TABAN}/cam-gece-izle/"]:
    try:
        r = requests.get(u, headers=UA, timeout=25)
        s = BeautifulSoup(r.text, "html.parser")
        print(f"\n--- {r.status_code} {len(r.content)}B {u} | title={s.title.get_text(strip=True)[:60] if s.title else ''}")
        for a in s.select("a[href]")[:200]:
            h = a.get("href", "")
            t = a.get_text(" ", strip=True)
            if "cam" in (h + " " + t).lower() and ("gece" in (h + " " + t).lower()):
                print("   LINK:", t[:50], "->", h[:110])
    except Exception as e:
        print("HATA", type(e).__name__, u)

print("\n=== .biz ana sayfa yapisi ===")
r = requests.get(TABAN, headers=UA, timeout=25)
s = BeautifulSoup(r.text, "html.parser")
for sel in [".alternative-links", "[data-video]", ".language-link", "iframe", "a[href*='-izle']", "a[href*='film/']"]:
    print(f"  {sel:<28} {len(s.select(sel))}")
