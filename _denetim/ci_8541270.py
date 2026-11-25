# -*- coding: utf-8 -*-
"""CI sonrasini API'siz izle: pinned manifest degisince surumleri dogrula."""
import re
import time

import requests

REPO = "PobloMert/PobloStream"
UA = {"User-Agent": "Mozilla/5.0"}
ESKI = "f2c5070a052c"
BEKLENEN = {"Dizilla": 43, "DiziMom": 38, "AnimeciX": 22, "FullHDFilm": 36}


def json_al(u):
    return requests.get(u, headers=UA, timeout=25).json()


for i in range(1, 26):
    try:
        repo = json_al(f"https://raw.githubusercontent.com/{REPO}/builds/repo.json?t={int(time.time())}")
        url = repo["pluginLists"][0]
        sha = re.search(r"@([0-9a-f]{12})", url).group(1)
        print(f"[{i:02d}] pinned={sha}")
        if sha != ESKI:
            j = json_al(url)
            sur = {e["name"]: int(e["version"]) for e in j}
            print("\nsurumler:", {k: sur.get(k) for k in BEKLENEN})
            print("SONUC:", "TAMAM" if all(sur.get(k) == v for k, v in BEKLENEN.items()) else "KONTROL ET")
            break
    except Exception as e:
        print(f"[{i:02d}] hata {type(e).__name__}")
    time.sleep(20)
else:
    print("\nmanifest 8 dk icinde degismedi")
