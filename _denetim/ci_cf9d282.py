# -*- coding: utf-8 -*-
"""cf9d282 CI yayinini izle (API'siz)."""
import re
import time

import requests

REPO = "PobloMert/PobloStream"
UA = {"User-Agent": "Mozilla/5.0"}
ESKI = "f76a2071650a"

for i in range(1, 26):
    try:
        repo = requests.get(f"https://raw.githubusercontent.com/{REPO}/builds/repo.json?t={int(time.time())}", headers=UA, timeout=25).json()
        url = repo["pluginLists"][0]
        sha = re.search(r"@([0-9a-f]{12})", url).group(1)
        print(f"[{i:02d}] pinned={sha}")
        if sha != ESKI:
            j = requests.get(url, headers=UA, timeout=25).json()
            pj = {e["name"]: e for e in j}
            d = pj.get("DiziMom")
            print("DiziMom:", d["version"], d["fileSize"], d["fileHash"] if d else None)
            print("SONUC:", "TAMAM" if d and d["version"] == 39 else "KONTROL ET")
            break
    except Exception as e:
        print(f"[{i:02d}] hata {type(e).__name__}")
    time.sleep(20)
else:
    print("\nmanifest 8 dk icinde degismedi")
