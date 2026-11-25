# -*- coding: utf-8 -*-
"""Yeni pichive PoW algoritmasini dogrula: SHA256('X7zRkiNmvG'+nonce+'.'+i)[5] & 4095 == 0."""
import hashlib
import json
import re

import requests

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"}
t = open(r"C:\Users\ahmet\OneDrive\Masaüstü\Youtube Direktörü\cloudstream\PobloStream\_denetim\html\dz_iframe.html", encoding="utf-8").read()
cfg = re.search(r"window\.NP_CONFIG\s*=\s*(\{.*?\});", t, re.S).group(1)
j = json.loads(cfg)
nonce = j["gate"]["n"]
master = j["sources"][0]["master"]
print("nonce:", nonce, "| bits:", j["gate"]["b"], "| master uzunlugu:", len(master))

for i in range(200000):
    d = hashlib.sha256(("X7zRkiNmvG" + nonce + "." + str(i)).encode()).digest()
    w6 = int.from_bytes(d[20:24], "big")
    if w6 & 4095 == 0:
        print("POW cozuldu:", i)
        break
else:
    print("POW bulunamadi")
    i = 0

for adres in [master, master + "&p=" + str(i)]:
    try:
        r = requests.get(adres, headers={**UA, "Referer": "https://four.pichive.online/"}, timeout=25)
        print(f"  {r.status_code} {len(r.content)}B EXT={r.text[:12]!r} | {adres[-40:]}")
        if r.text.startswith("#EXTM3U"):
            print("   BOZULMADI: ilk satirlar:", r.text[:150].replace("\n", " "))
    except Exception as e:
        print("  HATA", type(e).__name__, str(e)[:80])
