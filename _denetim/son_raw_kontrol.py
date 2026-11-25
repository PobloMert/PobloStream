# -*- coding: utf-8 -*-
"""API kotasiz nihai dogrulama (raw + sabit jsdelivr URL)."""
import hashlib
import time

import requests

REPO = "PobloMert/PobloStream"
UA = {"User-Agent": "Mozilla/5.0"}
BEKLENEN = {"DiziPal": 35, "DiziMom": 37, "Dizilla": 42, "FullHDFilmizlesene": 28, "CizgiMax": 9}
t = int(time.time())

repo = requests.get(f"https://raw.githubusercontent.com/{REPO}/builds/repo.json?t={t}", headers=UA, timeout=25).json()
url = repo["pluginLists"][0]
j = requests.get(url, headers=UA, timeout=25)
sur = {e["name"]: int(e["version"]) for e in j.json()}
print("manifest:", url)
print("HTTP", j.status_code, "| cache:", j.headers.get("cache-control"))
print("surumler:", {k: sur.get(k) for k in BEKLENEN})

cs3 = requests.get(f"https://raw.githubusercontent.com/{REPO}/builds/DiziPal.cs3?t={t}", headers=UA, timeout=25).content
dh = "sha256-" + hashlib.sha256(cs3).hexdigest()
kayit = next(e for e in j.json() if e["name"] == "DiziPal")
print("DiziPal.cs3:", len(cs3), "B | hash eslesiyor:", dh == kayit["fileHash"])
print("SONUC:", "TAMAM ✅" if all(sur.get(k) == v for k, v in BEKLENEN.items()) and dh == kayit["fileHash"] else "KONTROL ET ❌")
