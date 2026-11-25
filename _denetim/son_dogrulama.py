# -*- coding: utf-8 -*-
"""Son CI kosusunu ve builds/jsdelivr yayinini dogrular."""
import base64
import json
import time

import requests

REPO = "PobloMert/PobloStream"
H = {"User-Agent": "curl/8", "Accept": "application/vnd.github+json"}
UA = {"User-Agent": "Mozilla/5.0"}
BEKLENEN = {"DiziPal": 35, "DiziMom": 37, "Dizilla": 42, "FullHDFilmizlesene": 28, "CizgiMax": 9}


def api_dosya(ad):
    r = requests.get(f"https://api.github.com/repos/{REPO}/contents/{ad}?ref=builds", headers=H, timeout=25)
    return json.loads(base64.b64decode(r.json()["content"]))


for deneme in range(1, 22):
    runs = requests.get(f"https://api.github.com/repos/{REPO}/actions/runs?per_page=5", headers=H, timeout=25).json()["workflow_runs"]
    derle = [r for r in runs if r["name"] == "CloudStream Derleyici" and r["head_sha"].startswith("b139961")]
    durum = derle[0]["status"] + "/" + str(derle[0]["conclusion"]) if derle else "kuyrukta"
    print(f"[{deneme:02d}] CI: {durum}")
    if derle and derle[0]["status"] == "completed":
        if derle[0]["conclusion"] != "success":
            print("  CI BASARISIZ!")
            jobs = requests.get(derle[0]["jobs_url"], headers=H, timeout=25).json()["jobs"]
            for j in jobs:
                for s in j["steps"]:
                    if s["conclusion"] == "failure":
                        print("  basarisiz adim:", s["name"])
            break
        pj = api_dosya("plugins.json")
        sur = {e["name"]: int(e["version"]) for e in pj}
        print("  builds surumleri:", {k: sur.get(k) for k in BEKLENEN})
        repo = api_dosya("repo.json")
        print("  builds repo.json t:", repo.get("timestamp"))
        j = requests.get(f"https://cdn.jsdelivr.net/gh/{REPO}@builds/plugins.json?t={int(time.time())}", headers=UA, timeout=25).json()
        jsur = {e["name"]: int(e["version"]) for e in j}
        print("  jsdelivr surumleri:", {k: jsur.get(k) for k in BEKLENEN})
        ikon = [e["iconUrl"] for e in pj if e["name"] == "DiziPal"]
        print("  DiziPal ikon:", ikon)
        print("\nSONUC:", "TAMAM" if all(sur.get(k) == v and jsur.get(k) == v for k, v in BEKLENEN.items()) else "KONTROL ET")
        break
    time.sleep(20)
else:
    print("\nCI 7 dk icinde bitmedi")
