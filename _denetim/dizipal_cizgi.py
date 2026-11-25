# -*- coding: utf-8 -*-
"""DiziPal /search?q= dogrulama + kart link yapisı."""
import json

import requests
from bs4 import BeautifulSoup

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"}

print("=== DiziPal /search?q=kin (XHR) ===")
try:
    r = requests.get("https://dizipal1.rest/search?q=kin", headers={**UA, "X-Requested-With": "XMLHttpRequest", "Accept": "application/json"}, timeout=25)
    print(f"HTTP {r.status_code} {len(r.content)}B  ct={r.headers.get('content-type')}")
    try:
        j = r.json()
        print("JSON:", json.dumps(j, ensure_ascii=False)[:700])
    except Exception:
        s = BeautifulSoup(r.text, "html.parser")
        print("HTML parca. grid-card-poster =", len(s.select("div.grid-card-poster")), "| a =", len(s.select("a")))
        print(r.text[:600].replace("\n", " "))
except Exception as e:
    print("HATA", type(e).__name__, str(e)[:80])

print("\n=== DiziPal kart ust yapisi (/diziler) ===")
try:
    html = requests.get("https://dizipal1.rest/diziler", headers=UA, timeout=25).text
    soup = BeautifulSoup(html, "html.parser")
    cards = soup.select("div.grid-card-poster")
    print("grid-card-poster:", len(cards))
    if cards:
        a = cards[0].find_parent("a")
        par = cards[0].parent
        print("parent a:", a.attrs if a else None)
        print("direkt parent:", par.name, par.attrs if par else None)
        kok = par
        for _ in range(4):
            if kok and kok.name == "a":
                break
            kok = kok.parent if kok else None
        print("yukselen a:", kok.attrs if kok is not None and kok.name == "a" else "YOK")
        img = cards[0].select_one("img")
        print("img attrs:", {k: (v[:60] if isinstance(v, str) else v) for k, v in (img.attrs.items() if img else [])})
except Exception as e:
    print("HATA", type(e).__name__, str(e)[:80])

print("\n=== CizgiMax /ara/?q= kart detayi ===")
try:
    html = requests.get("https://cizgimax.online/ara/?q=ladybug", headers=UA, timeout=25).text
    soup = BeautifulSoup(html, "html.parser")
    cards = soup.select("div.film-item")
    print("film-item:", len(cards))
    if cards:
        print(cards[0].prettify()[:700])
except Exception as e:
    print("HATA", type(e).__name__, str(e)[:80])
