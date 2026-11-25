# -*- coding: utf-8 -*-
"""CizgiMax /ara/ parametresi + DiziPal main.min.js arama ucu."""
import re

import requests
from bs4 import BeautifulSoup

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"}

print("=== CizgiMax arama formu input adlari ===")
h = requests.get("https://cizgimax.online/", headers=UA, timeout=25).text
soup = BeautifulSoup(h, "html.parser")
form = soup.select_one("form#search") or soup.select_one("form")
if form:
    print("  form:", form.attrs)
    for inp in form.select("input, button"):
        print("   ", inp.name, inp.attrs)

print("\n=== CizgiMax /ara/ adaylari ===")
for u in ["https://cizgimax.online/ara/?s=mucize",
          "https://cizgimax.online/ara/?q=mucize",
          "https://cizgimax.online/ara/?qr=mucize",
          "https://cizgimax.online/ara/mucize/",
          "https://cizgimax.online/ara/mucize"]:
    try:
        r = requests.get(u, headers=UA, timeout=22)
        s = BeautifulSoup(r.text, "html.parser")
        n = len(s.select("div.film-item"))
        a = len(s.select("a.film-name"))
        print(f"  {r.status_code} film-item={n:<4} a.film-name={a:<4} {u}")
    except Exception as e:
        print(f"  HATA {type(e).__name__} {u}")

print("\n=== DiziPal main.min.js arama uclari ===")
try:
    js = requests.get("https://dizipal1.rest/assets/js/main.min.js?v=1793861211", headers=UA, timeout=25).text
    print(f"  JS {len(js)}B")
    for kw in ["arama", "search", "autocomplete", "kelime", "fetch(", "XMLHttpRequest", "\$.post", "\$.ajax"]:
        idxs = [m.start() for m in re.finditer(re.escape(kw), js, re.I)][:4]
        for i in idxs:
            print(f"  [{kw}] ..." + js[max(0, i - 90):i + 120].replace("\n", " ") + "...")
except Exception as e:
    print("  HATA", type(e).__name__, str(e)[:70])
