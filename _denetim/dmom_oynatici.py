# -*- coding: utf-8 -*-
"""dizidad.com: arama -> dizi -> bolum sayfasi oynaticu yapisini incele."""
import re

import requests
from bs4 import BeautifulSoup

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"}
TABAN = "https://www.dizidad.com"


def al(u, **kw):
    r = requests.get(u, headers=UA, timeout=25, **kw)
    return r, BeautifulSoup(r.text, "html.parser")


print("=== arama ===")
r, s = al(f"{TABAN}/?s=kral")
linkler = []
for a in s.select("a[href]"):
    h = a.get("href", "")
    t = a.get_text(" ", strip=True)
    if t and "kral" in (t + h).lower() and h.startswith("http") and TABAN.split("//")[1] in h:
        linkler.append((t[:45], h))
for t, h in linkler[:12]:
    print("  ", t, "->", h[:110])

if not linkler:
    print("  sonuc yok, ana sayfa episode-box'lardan gidiyorum")
    r, s = al(f"{TABAN}/tum-bolumler/")
    for a in s.select("div.episode-box a[href]")[:10]:
        print("  ", a.get_text(" ", strip=True)[:40], "->", a.get("href", "")[:110])

# ilk uygun bolum linkini dene
hedef = None
for t, h in linkler:
    if "/bolum" in h or "episode" in h or h.rstrip("/").count("/") > 3:
        hedef = h
        break
if not hedef and linkler:
    hedef = linkler[0][1]
print("\nhedef:", hedef)

if hedef:
    r, d = al(hedef)
    print(f"sayfa: {r.status_code} {len(r.content)}B {r.url}")
    for sel in ["div.video", "div.video p iframe", "iframe", "div.sources a", "[data-video]", "div.player",
                "video", "source", "a[href*='player']", "script[src]"]:
        els = d.select(sel)
        print(f"  {sel:<25} {len(els):<4}", ("| " + str(els[0])[:180].replace("\n", " ")) if els else "")
    # iframe'larin tum attr'lari
    for f in d.select("iframe")[:6]:
        print("   iframe attrs:", {k: (v[:100] if isinstance(v, str) else v) for k, v in f.attrs.items()})
    # m3u8 / player sozleri
    for m in set(re.findall(r"https?://[^\"'<> ]{10,120}", r.text)):
        if any(x in m.lower() for x in ("player", "m3u8", "embed", "video", "stream")):
            print("   URL:", m[:130])
