# -*- coding: utf-8 -*-
"""Yeni domainlerde eklenti selector'larini dogrula."""
import requests
from bs4 import BeautifulSoup

HDR = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"}

TESTLER = [
    # (eklenti, url, [selector'lar])
    ("DiziMom", "https://www.dizidad.com", ["div.episode-box", "div.single-item", "div.categorytitle a", "div.cat-img img"]),
    ("DiziMom", "https://www.dizidad.com/tum-bolumler/page/1", ["div.episode-name a"]),
    ("DiziPal", "https://dizipal1.rest/diziler/son-bolumler", ["div.episode-item", "div.name", "div.episode"]),
    ("DiziPal", "https://dizipal1.rest/diziler", ["article.type2 ul li", "span.title"]),
    ("CizgiMax", "https://cizgimax.online", ["ul.filter-results li", "h2.truncate", "div.poster-subject a", "div.poster-media img"]),
    ("Dizilla", "https://dizilla.la", ["a.pcard", ".pimg img"]),
    ("Dizilla", "https://dizilla.la/tum-bolumler", ["ul.v4-epgrid li a.gb", "ul.v4-epgrid li"]),
    ("FullHDFilmizlesene", "https://www.fullhdfilmizlesene.now", ["li.film", "span.film-title", "div[class=izle-titles]"]),
]


def main():
    for ad, url, sels in TESTLER:
        print(f"\n=== {ad}  {url}")
        try:
            r = requests.get(url, headers=HDR, timeout=25)
            print(f"   HTTP {r.status_code}  {len(r.content)}B  son={r.url}")
            if r.status_code != 200:
                continue
            soup = BeautifulSoup(r.text, "html.parser")
            for s in sels:
                try:
                    n = len(soup.select(s))
                    isaret = "OK " if n > 0 else "BOZUK"
                    orn = ""
                    if n:
                        el = soup.select(s)[0]
                        txt = el.get_text(" ", strip=True)[:40]
                        orn = f"  ornek: {txt!r}"
                    print(f"   [{isaret}] {s:<32} -> {n}{orn}")
                except Exception as e:
                    print(f"   [HATA] {s:<32} {e}")
        except Exception as e:
            print(f"   BAGLANTI HATASI: {type(e).__name__}: {str(e)[:60]}")


if __name__ == "__main__":
    main()
