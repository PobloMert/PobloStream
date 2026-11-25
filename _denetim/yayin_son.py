# -*- coding: utf-8 -*-
"""Yayin son durumu: sabit manifest + surumler + cs3 hash (CI polling yok)."""
import base64
import hashlib
import json

import requests

REPO = "PobloMert/PobloStream"
H = {"User-Agent": "curl/8", "Accept": "application/vnd.github+json"}
UA = {"User-Agent": "Mozilla/5.0"}
BEKLENEN = {"DiziPal": 35, "DiziMom": 37, "Dizilla": 42, "FullHDFilmizlesene": 28, "CizgiMax": 9}


def api(ad):
    r = requests.get(f"https://api.github.com/repos/{REPO}/contents/{ad}?ref=builds", headers=H, timeout=25).json()
    return json.loads(base64.b64decode(r["content"]))


repo = api("repo.json")
pj = api("plugins.json")
url = repo["pluginLists"][0]
print("=== builds/repo.json ===")
print("  pluginLists:", url)

j = requests.get(url, headers=UA, timeout=25)
print("\n=== sabit URL ===")
print("  HTTP", j.status_code, "| cache:", j.headers.get("cache-control"))
jsur = {e["name"]: int(e["version"]) for e in j.json()}
print("  surumler:", {k: jsur.get(k) for k in BEKLENEN})

cs3 = requests.get(f"https://raw.githubusercontent.com/{REPO}/builds/DiziPal.cs3?t=999", headers=UA, timeout=25).content
dh = "sha256-" + hashlib.sha256(cs3).hexdigest()
kayit = next(e for e in pj if e["name"] == "DiziPal")
print("\n=== DiziPal.cs3 ===")
print("  boyut:", len(cs3), "| hash:", dh, "| manifest:", kayit["fileHash"])
print("  eslesiyor:", dh == kayit["fileHash"])

ok = all(jsur.get(k) == v for k, v in BEKLENEN.items()) and dh == kayit["fileHash"] and "@" in url
print("\nSONUC:", "TAMAM ✅" if ok else "KONTROL ET ❌")
