# -*- coding: utf-8 -*-
"""dizidad.com bolum sayfasindaki gercek oynatici iframe'lerini cikar."""
import re

import requests
from bs4 import BeautifulSoup

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"}

r = requests.get("https://www.dizidad.com/kizil-kralice-2-sezon-6-bolum-izle/", headers=UA, timeout=25)
s = BeautifulSoup(r.text, "html.parser")
print(f"HTTP {r.status_code} {len(r.content)}B | {r.url}")
for sel in ["div.video", "div.video p iframe", "iframe", "div.sources a", "[data-video]", "div.player", "video", "source"]:
    els = s.select(sel)
    print(f"  {sel:<25} {len(els):<4}", ("| " + str(els[0])[:220].replace("\n", " ")) if els else "")
print("\n=== iframe'lar ===")
for f in s.select("iframe")[:12]:
    print("  ", {k: (v[:120] if isinstance(v, str) else v) for k, v in f.attrs.items()})
print("\n=== ilgili URL'ler ===")
for m in sorted(set(re.findall(r"https?://[^\"'<> ]{8,150}", r.text))):
    if any(x in m.lower() for x in ("hdmom", "player", "m3u8", "embed", "video", "stream", "kisa", "xnr")):
        print("  ", m[:150])
print("\n=== 'kaynak/geçiş' iceren kisimlar ===")
for line in r.text.splitlines():
    if re.search(r"video|kaynak|source|iframe", line, re.I) and len(line) < 400:
        print("  ", line.strip()[:250])
