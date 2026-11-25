# -*- coding: utf-8 -*-
"""Nihai dogrulama: CI, sabit manifest, jsdelivr surumleri, cs3 hash."""
import base64
import hashlib
import json
import time

import requests

REPO = "PobloMert/PobloStream"
H = {"User-Agent": "curl/8", "Accept": "application/vnd.github+json"}
UA = {"User-Agent": "Mozilla/5.0"}
BEKLENEN = {"DiziPal": 35, "DiziMom": 37, "Dizilla": 42, "FullHDFilmizlesene": 28, "CizgiMax": 9}


def api(ad):
    r = requests.get(f"https://api.github.com/repos/{REPO}/contents/{ad}?ref=builds", headers=H, timeout=25).json()
    return json.loads(base64.b64decode(r["content"]))


for i in range(1, 26):
    runs = requests.get(f"https://api.github.com/repos/{REPO}/actions/runs?per_page=5", headers=H, timeout=25).json()["workflow_runs"]
    r = next((x for x in runs if x["name"] == "CloudStream Derleyici" and x["head_sha"].startswith("3c39ef1")), None)
    print(f"[{i:02d}] CI: {(r['status']+'/'+str(r['conclusion'])) if r else 'kuyrukta'}")
    if r and r["status"] == "completed":
        if r["conclusion"] != "success":
            print("CI BASARISIZ - builds dali onceki kosudan kaldigi icin sonuc gecerli degil")
            raise SystemExit(1)
        break
    time.sleep(20)
else:
    print("CI 8 dk icinde bitmedi")
    raise SystemExit

repo = api("repo.json")
pj = api("plugins.json")
print("\n=== builds/repo.json (sabit manifest) ===")
print("  pluginLists:", repo["pluginLists"])
print("  timestamp:", repo["timestamp"])

sur = {e["name"]: int(e["version"]) for e in pj}
print("  builds surumleri:", {k: sur.get(k) for k in BEKLENEN})

u = repo["pluginLists"][0].split("?")[0]
j = requests.get(u, headers=UA, timeout=25).json()
jsur = {e["name"]: int(e["version"]) for e in j}
print("\n=== sabit jsdelivr URL ===")
print(" ", u)
print("  surumler:", {k: jsur.get(k) for k in BEKLENEN})

cs3 = requests.get("https://raw.githubusercontent.com/" + REPO + "/builds/DiziPal.cs3?t=" + str(int(time.time())), headers=UA, timeout=25).content
hashlib_digest = "sha256-" + hashlib.sha256(cs3).hexdigest()
kayit = next(e for e in pj if e["name"] == "DiziPal")
print("\n=== DiziPal.cs3 hash ===")
print("  dosya:", len(cs3), "B |", hashlib_digest)
print("  manifest:", kayit["fileHash"], "|", "ESLESIYOR" if hashlib_digest == kayit["fileHash"] else "UYUSMUYOR")
print("  ikon:", kayit["iconUrl"])

sabit = "cdn.jsdelivr.net/gh/" + REPO + "@" in repo["pluginLists"][0]
print("  sabit commit URL:", "EVET" if sabit else "HAYIR")
tam = sabit and all(sur.get(k) == v and jsur.get(k) == v for k, v in BEKLENEN.items()) and hashlib_digest == kayit["fileHash"]
print("\nSONUC:", "TAMAM ✅" if tam else "KONTROL ET ❌")
