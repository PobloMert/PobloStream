# -*- coding: utf-8 -*-
"""builds dalindaki hata.log / plugins.json yayinlanmasini bekler ve hatayi gosterir."""
import re
import time

import requests

H = {"User-Agent": "Mozilla/5.0"}
TABAN = "https://cdn.jsdelivr.net/gh/PobloMert/PobloStream@builds"
BEKLENEN = {"DiziPal": 35, "DiziMom": 37, "Dizilla": 42, "FullHDFilmizlesene": 28, "CizgiMax": 9}


for deneme in range(1, 31):
    try:
        pj = requests.get(f"{TABAN}/plugins.json?t={int(time.time())}", headers=H, timeout=25)
        yayin = {e["name"]: int(e["version"]) for e in pj.json()}
    except Exception as e:
        print(f"[{deneme:02d}] plugins.json okunamadi {type(e).__name__}")
        time.sleep(20)
        continue

    tutan = {k: v for k, v in BEKLENEN.items() if yayin.get(k) == v}
    print(f"[{deneme:02d}] toplam={len(yayin)} dogru_surum={len(tutan)}/5")

    if len(tutan) == 5:
        print("\n=== YAYIN BASARILI: bes eklentinin surumu yayinda ===")
        for ad, surum in sorted(yayin.items()):
            print(f"  {ad:<22} v{surum}")
        break

    # derleme patladiysa hata.log yayinlanmis olmali
    hata = requests.get(f"{TABAN}/hata.log?t={int(time.time())}", headers=H, timeout=25)
    if hata.status_code == 200 and hata.text.strip():
        metin = hata.text
        print(f"\n=== hata.log yayinlandi ({len(metin)}B) ===")
        satirlar = metin.splitlines()
        vurus = [s for s in satirlar if re.search(r"^e: |error:|FAILURE:|BUILD FAILED|What went wrong|Caused by|OutOfMemory|Killed|exit code", s)]
        print(f"hata satiri: {len(vurus)}")
        for s in vurus[:45]:
            print("  >", s[:230])
        print("\n--- son 25 satir ---")
        for s in satirlar[-25:]:
            print("  |", s[:230])
        break

    time.sleep(20)
else:
    print("\n=== 10 dk icinde sonuc alinamadi ===")
