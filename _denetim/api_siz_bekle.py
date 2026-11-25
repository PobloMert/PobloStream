# -*- coding: utf-8 -*-
"""API kotasiz: raw builds/repo.json uzerinden son yayinin tamamlanmasini izle."""
import re
import time

import requests

REPO = "PobloMert/PobloStream"
UA = {"User-Agent": "Mozilla/5.0"}
ESKI = "39b533a06684"
BEKLENEN = {"DiziPal": 35, "DiziMom": 37, "Dizilla": 42, "FullHDFilmizlesene": 28, "CizgiMax": 9}


def al(u):
    return requests.get(u, headers=UA, timeout=25).json()


for i in range(1, 31):
    try:
        repo = al(f"https://raw.githubusercontent.com/{REPO}/builds/repo.json?t={int(time.time())}")
        url = repo["pluginLists"][0]
        sha = re.search(r"@([0-9a-f]{12})", url).group(1)
        print(f"[{i:02d}] pinned={sha} | {url[:95]}")
        if sha != ESKI:
            j = al(url)
            sur = {e["name"]: int(e["version"]) for e in j}
            print("\nYENI YAYIN:", {k: sur.get(k) for k in BEKLENEN})
            print("SONUC:", "TAMAM ✅" if all(sur.get(k) == v for k, v in BEKLENEN.items()) else "KONTROL ET ❌")
            break
    except Exception as e:
        print(f"[{i:02d}] hata {type(e).__name__}")
    time.sleep(20)
else:
    print("\n10 dk icinde yeni manifest yayinlanmadi (CI patlamis olabilir)")
