# -*- coding: utf-8 -*-
"""Dizilla iframe.php icegi + DiziMom peacemakerst oynatici icerigi."""
import re

import requests

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"}

print("=== Dizilla iframe.php URL'leri ===")
r = requests.get("https://dizilla.la/carrie-1-sezon-1-bolum", headers=UA, timeout=25)
url_list = []
for m in re.finditer(r"https?://[^\"'<>\s\\]+iframe\.php\?v=[^\"'<>\s\\]+", r.text):
    u = m.group(0).replace("\\u0026", "&").replace("\\/", "/")
    if u not in url_list: url_list.append(u)
for m in re.finditer(r"iframe\.php\?v=[a-zA-Z0-9_-]+", r.text):
    print("  ham:", m.group(0)[:100])
print("  tam URL'ler:")
for u in url_list[:8]: print("   ", u[:160])

if url_list:
    iu = url_list[0]
    try:
        ir = requests.get(iu, headers={**UA, "Referer": "https://dizilla.la/"}, timeout=25)
        print(f"\n  iframe: {ir.status_code} {len(ir.content)}B {ir.url}")
        for kw in ["NP_CONFIG", "gate", "sources", "master", "m3u8", "pichive", "pow"]:
            print(f"    {kw:<12} {ir.text.lower().count(kw.lower())}")
        print("    ilk 700:", ir.text[:700].replace("\n", " "))
    except Exception as e:
        print("  iframe HATA", type(e).__name__, str(e)[:100])

print("\n=== DiziMom peacemakerst oynatici ===")
try:
    pr = requests.get("https://peacemakerst.com/tv/video/beeb1024d6d782530220e25ff8b80caa",
                      headers={**UA, "Referer": "https://www.dizidad.com/"}, timeout=25)
    t = pr.text
    print("  HTTP", pr.status_code, len(t))
    for kw in ["m3u8", ".mp4", "video_url", "file:", "sources", "window.", "location", "decrypt", "atob"]:
        print(f"    {kw:<12} {t.lower().count(kw.lower())}")
    for m in set(re.findall(r"https?://[^\"'<>\s]{6,120}", t)):
        if any(x in m for x in ("m3u8", "player", "video", "stream")):
            print("    URL:", m[:130])
    print("  son 900:", t[-900:].replace("\n", " "))
except Exception as e:
    print("  HATA", type(e).__name__, str(e)[:100])
