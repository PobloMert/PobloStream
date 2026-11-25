# -*- coding: utf-8 -*-
"""3c88b9b CI yayinini API'siz izle."""
import re
import time

import requests

REPO = "PobloMert/PobloStream"
UA = {"User-Agent": "Mozilla/5.0"}
ESKI = "553b84818637"
BEKLENEN = {"CizgiMax": 10, "BelgeselX": 13, "Ddizi": 12, "HDFilmCehennemi": 61}

for i in range(1, 26):
    try:
        repo = requests.get(f"https://raw.githubusercontent.com/{REPO}/builds/repo.json?t={int(time.time())}", headers=UA, timeout=25).json()
        url = repo["pluginLists"][0]
        sha = re.search(r"@([0-9a-f]{12})", url).group(1)
        print(f"[{i:02d}] pinned={sha}")
        if sha != ESKI:
            j = requests.get(url, headers=UA, timeout=25).json()
            sur = {e["name"]: int(e["version"]) for e in j}
            print("\nsurumler:", {k: sur.get(k) for k in BEKLENEN})
            print("SONUC:", "TAMAM" if all(sur.get(k) == v for k, v in BEKLENEN.items()) else "KONTROL ET")
            break
    except Exception as e:
        print(f"[{i:02d}] hata {type(e).__name__}")
    time.sleep(20)
else:
    print("\nmanifest 8 dk icinde degismedi")
