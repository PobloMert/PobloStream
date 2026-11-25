# -*- coding: utf-8 -*-
"""Ddizi Kurulus Osman: loadLinks zincirini PC'den birebir taklit et."""
import re

import requests
from bs4 import BeautifulSoup

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"}
TABAN = "https://www.ddizi.tel"

r = requests.get(f"{TABAN}/diziler/1780/kurulus-osman-67", headers=UA, timeout=25)
s = BeautifulSoup(r.text, "html.parser")
print("series:", r.status_code, len(r.content))
bolumler = []
for a in s.select("a[href]"):
    h = a.get("href", "")
    if "/izle/" in h:
        u = h if h.startswith("http") else TABAN + (h if h.startswith("/") else "/" + h)
        if u not in bolumler: bolumler.append(u)
print("bolum linki sayisi:", len(bolumler), "ornek:", bolumler[:3])

if bolumler:
    ep = bolumler[0]
    er = requests.get(ep, headers=UA, timeout=25)
    ed = BeautifulSoup(er.text, "html.parser")
    og = ed.select_one("meta[property='og:video']")
    print("\nepisode:", er.status_code, len(er.content), ep)
    print("og:video:", og.get("content") if og else "YOK")
    print("iframe'lar:", [(f.get("src") or "")[:100] for f in ed.select("iframe")][:5])
    if og:
        pv = og["content"]
        if not pv.startswith("http"): pv = TABAN + (pv if pv.startswith("/") else "/" + pv)
        pr = requests.get(pv, headers={**UA, "Referer": ep}, timeout=25)
        print("\nplayer:", pr.status_code, len(pr.content), pv)
        m = re.search(r"sources:\s*\[\s*\{(.*?)\}\s*,?\s*\]", pr.text, re.S)
        print("sources regex:", "ESLESTI" if m else "YOK")
        if m:
            fm = re.search(r"file:\s*['\"](.*?)['\"]", m.group(1))
            print("file:", fm.group(1)[:150] if fm else "YOK")
            if fm:
                fu = fm.group(1)
                for ref in [pv, "https://twitter.com/", None]:
                    h = dict(UA)
                    if ref: h["Referer"] = ref
                    try:
                        x = requests.get(fu, headers=h, timeout=20)
                        print(f"  ref={str(ref)[:40]:<42} -> {x.status_code} {len(x.content)}B {x.text[:8]!r}")
                    except Exception as e:
                        print("  HATA", type(e).__name__)
