# -*- coding: utf-8 -*-
"""Kart HTML'lerinin ic yapisini goster - kesin selector icin (BeautifulSoup)."""
import requests
from bs4 import BeautifulSoup

HDR = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"}


def goster(ad, url, selector, adet=2):
    print(f"\n########## {ad}  {url}  selector={selector}")
    try:
        r = requests.get(url, headers=HDR, timeout=25)
        print(f"HTTP {r.status_code}")
        soup = BeautifulSoup(r.text, "html.parser")
        els = soup.select(selector)
        print(f"eslesme: {len(els)}")
        for el in els[:adet]:
            print(el.prettify()[:1100])
            print("-----")
    except Exception as e:
        print("HATA", type(e).__name__, str(e)[:80])


if __name__ == "__main__":
    print("\n### DiziMom /page/1 testi")
    try:
        r = requests.get("https://www.dizidad.com/page/1", headers=HDR, timeout=25)
        s = BeautifulSoup(r.text, "html.parser")
        print(f"HTTP {r.status_code} son={r.url}")
        for sel in ["div.single-item", "div.episode-box", "div.categorytitle a", "div.cat-img img"]:
            print(f"   {sel:<26} -> {len(s.select(sel))}")
    except Exception as e:
        print("HATA", type(e).__name__, str(e)[:70])

    goster("DiziMom kategori", "https://www.dizidad.com/yerli-dizi-izle/page/1", "div.single-item", 1)
    goster("CizgiMax", "https://cizgimax.online", "div.film-item", 1)
    goster("DiziPal", "https://dizipal1.rest/diziler/son-bolumler", "div.grid-card-poster", 1)
