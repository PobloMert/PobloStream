# -*- coding: utf-8 -*-
"""GitHub Actions'in builds dalginin yayina aldigi plugins.json'u jsdelivr'den izler."""
import json
import time

import requests

BEKLENEN = {
    "DiziPal": 35,
    "DiziMom": 37,
    "Dizilla": 42,
    "FullHDFilmizlesene": 28,
    "CizgiMax": 9,
}
URL = "https://cdn.jsdelivr.net/gh/PobloMert/PobloStream@builds/plugins.json"


def sor():
    r = requests.get(URL, headers={"User-Agent": "Mozilla/5.0"}, timeout=25)
    r.raise_for_status()
    return {e["name"]: int(e["version"]) for e in r.json()}


for deneme in range(1, 26):
    try:
        yayin = sor()
    except Exception as e:
        print(f"[{deneme:02d}] hata: {type(e).__name__}")
        time.sleep(20)
        continue

    eksik = {k: (yayin.get(k), v) for k, v in BEKLENEN.items() if yayin.get(k) != v}
    print(f"[{deneme:02d}] toplam={len(yayin)} eksik={eksik if eksik else 'YOK'}")
    if not eksik:
        print("\n=== YAYINDA - tum surumler tutuyor ===")
        for ad, surum in sorted(yayin.items()):
            print(f"  {ad:<22} v{surum}")
        break
    time.sleep(20)
else:
    print("\n=== 8 dk icinde guncellenmedi (Actions ko\$masini kontrol et) ===")
