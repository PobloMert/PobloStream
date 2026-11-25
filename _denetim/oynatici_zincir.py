# -*- coding: utf-8 -*-
"""Oynatici zincirlerini PC'den taklit et: DiziMom player'lari ve Dizilla bolum sayfasi."""
import re

import requests
from bs4 import BeautifulSoup

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"}

print("########## DiziMom oynaticilari ##########")
for u in [
    "https://peacemakerst.com/tv/video/beeb1024d6d782530220e25ff8b80caa",
    "https://hdplayersystem.com/video/3f525057ae137b068b53a794911bfa80",
]:
    try:
        r = requests.get(u, headers={**UA, "Referer": "https://www.dizidad.com/"}, timeout=25)
        print(f"\n  {r.status_code} {len(r.content)}B {u}")
        print("   ", r.text[:400].replace("\n", " "))
        print("    m3u8:", re.findall(r"https?[^\"'\s]+\.m3u8[^\"'\s]*", r.text)[:2])
        print("    NP/yonlendirme:", re.findall(r"(?:window\.location|href\s*=|fetch\()[^\n]{0,90}", r.text)[:2])
    except Exception as e:
        print("  HATA", type(e).__name__, u)

print("\n########## Dizilla bolum sayfasi ##########")
try:
    r = requests.get("https://dizilla.la/", headers=UA, timeout=25)
    s = BeautifulSoup(r.text, "html.parser")
    bolumler = []
    for a in s.select("ul.v4-epgrid li a.gb, a[href*='-bolum']"):
        h = a.get("href", "")
        if h and "/tum-bolumler" not in h:
            bolumler.append(h)
    print("  ana sayfa bolum linki:", bolumler[:5])
    hedef = bolumler[0] if bolumler else None
    if hedef:
        if hedef.startswith("/"): hedef = "https://dizilla.la" + hedef
        d = requests.get(hedef, headers=UA, timeout=25)
        print(f"  bolum: {d.status_code} {len(d.content)}B {d.url}")
        for pat in ["iframe.php", "pichive", "NP_CONFIG", "m3u8", "<iframe", "video.php", "player"]:
            print(f"    {pat:<15} {d.text.lower().count(pat.lower())}")
        for m in set(re.findall(r"https?://[^\"'<>\s]*(?:iframe|pichive|player|m3u8)[^\"'<>\s]*", d.text))[:8]:
            print("    URL:", m[:140])
except Exception as e:
    print("  HATA", type(e).__name__, str(e)[:100])
