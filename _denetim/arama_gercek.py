# -*- coding: utf-8 -*-
"""Kodda yazili GERCEK arama endpoint'lerini dogrula."""
import json

import requests
from bs4 import BeautifulSoup

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"}


def html_testi(ad, url, sel):
    try:
        r = requests.get(url, headers=UA, timeout=25)
        s = BeautifulSoup(r.text, "html.parser")
        n = len(s.select(sel))
        ornek = ""
        if n:
            ornek = " | " + s.select(sel)[0].get_text(" ", strip=True)[:45]
        print(f"{ad:<14} {r.status_code} sonuc={n:<4} {url}{ornek}")
    except Exception as e:
        print(f"{ad:<14} HATA {type(e).__name__} {url}")


def post_testi(ad, url, data, headers=None):
    try:
        h = dict(UA)
        if headers:
            h.update(headers)
        r = requests.post(url, data=data, headers=h, timeout=25)
        try:
            j = r.json()
            print(f"{ad:<14} {r.status_code} JSON OK {url}")
            print("        ornek: " + json.dumps(j, ensure_ascii=False)[:320])
        except Exception:
            print(f"{ad:<14} {r.status_code} JSON DEGIL {url} -> {r.text[:160]}")
    except Exception as e:
        print(f"{ad:<14} HATA {type(e).__name__} {url}")


if __name__ == "__main__":
    # DiziMom: /?s=  -> div.single-item
    for q in ["kral", "superciher", "cueciler"]:
        html_testi("DiziMom", f"https://www.dizidad.com/?s={q}", "div.single-item")
    html_testi("DiziMom-mu", "https://www.dizimom.plus/?s=kral", "div.single-item")

    # DiziPal: POST /api/search-autocomplete  data=query=
    post_testi("DiziPal", "https://dizipal1.rest/api/search-autocomplete",
               {"query": "kin"},
               {"Accept": "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With": "XMLHttpRequest"})

    # CizgiMax: /ajaxservice/index.php?qr=  (JSON)
    for u in ["https://cizgimax.online/ajaxservice/index.php?qr=mucize",
              "https://cizgiduo.online/ajaxservice/index.php?qr=mucize"]:
        try:
            r = requests.get(u, headers=UA, timeout=22)
            print(f"CizgiMax       {r.status_code} {len(r.content)}B {u}")
            if r.status_code == 200 and len(r.content) < 200000:
                print("        " + r.text[:260].replace("\n", " "))
        except Exception as e:
            print(f"CizgiMax       HATA {type(e).__name__} {u}")

    # Dizilla: /arsiv?q=  -> a.pcard
    html_testi("Dizilla", "https://dizilla.la/arsiv?q=blade", "a.pcard")
