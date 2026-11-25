# -*- coding: utf-8 -*-
"""cizgimax.online: liste / detay / bolum sayfasi yapisini incele."""
import re
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"}
TABAN = "https://cizgimax.online"


def al(u):
    r = requests.get(u, headers=UA, timeout=25)
    return r, BeautifulSoup(r.text, "html.parser")


print("=== 1) Liste sayfasi adaylari ===")
for u in ["/diziler/", "/diziler/page/2", "/?orderby=date&order=DESC", "/diziler/page/2?orderby=date&order=DESC", "/diziler?page=2"]:
    try:
        r, s = al(urljoin(TABAN, u))
        print(f"  {r.status_code} film-item={len(s.select('div.film-item')):<4} {u}")
    except Exception as e:
        print(f"  HATA {type(e).__name__} {u}")

print("\n=== 2) Detay sayfasi ===")
r, home = al(TABAN + "/")
link = None
for a in home.select("a.film-name"):
    h = a.get("href", "")
    if "/diziler/" in h:
        link = urljoin(TABAN, h)
        break
print("  detay link:", link)
if link:
    r, d = al(link)
    print(f"  HTTP {r.status_code} {len(r.content)}B")
    for sel in ["h1", "meta[property='og:image']", "img", "p#tv-series-desc", "div.genre-item a",
                "a[href*='-bolum']", "div.episodes a", "ul.episodes li", "div.season a",
                "a.season-episode", "div.episode-list a", "span.season-name", "span.episode-names"]:
        els = d.select(sel)
        print(f"    {sel:<28} -> {len(els)}", ("| " + (els[0].get_text(' ', strip=True)[:45] or els[0].get('href', '') or els[0].get('content', '')[:45])) if els else "")
    # tekrarlayan class'lar (bolum listesi icin)
    import collections
    c = collections.Counter()
    for el in d.select("[class]"):
        c[el.name + "." + " ".join(el.get("class", []))] += 1
    print("    en sik class'lar:")
    for k, v in c.most_common(14):
        if v >= 3:
            print(f"      {v:>4}x {k[:60]}")
