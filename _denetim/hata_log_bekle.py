# -*- coding: utf-8 -*-
"""1ea8859 kosunu bekle; hata.log varsa hata satirlarini goster."""
import base64
import json
import re
import time

import requests

REPO = "PobloMert/PobloStream"
H = {"User-Agent": "curl/8", "Accept": "application/vnd.github+json"}
UA = {"User-Agent": "Mozilla/5.0"}

for i in range(1, 26):
    runs = requests.get(f"https://api.github.com/repos/{REPO}/actions/runs?per_page=5", headers=H, timeout=25).json()["workflow_runs"]
    r = next((x for x in runs if x["name"] == "CloudStream Derleyici" and x["head_sha"].startswith("4aec547")), None)
    durum = (r["status"] + "/" + str(r["conclusion"])) if r else "kuyrukta"
    print(f"[{i:02d}] CI: {durum}")
    if r and r["status"] == "completed":
        break
    time.sleep(20)
else:
    print("CI 8 dk icinde bitmedi")
    raise SystemExit

print("\nCI sonucu:", r["conclusion"])
if r["conclusion"] != "success":
    jobs = requests.get(r["jobs_url"], headers=H, timeout=25).json()["jobs"]
    for j in jobs:
        for s in j["steps"]:
            if s["conclusion"] == "failure":
                print("basarisiz adim:", s["name"])
    resp = requests.get(f"https://raw.githubusercontent.com/{REPO}/builds/hata.log?t={int(time.time())}", headers=UA, timeout=25)
    print("hata.log HTTP", resp.status_code, len(resp.content), "B")
    if resp.status_code == 200 and resp.text.strip():
        satirlar = resp.text.splitlines()
        hatalar = [s for s in satirlar if re.search(r"^e: |error:|FAILURE:|BUILD FAILED|What went wrong|Caused by|OutOfMemory|Killed|cikis kodu", s)]
        print(f"\n=== hata satirlari ({len(hatalar)}) ===")
        for s in hatalar[:50]:
            print(" >", s[:250])
        print("\n=== son 30 satir ===")
        for s in satirlar[-30:]:
            print(" |", s[:250])
else:
    c = requests.get(f"https://api.github.com/repos/{REPO}/contents/repo.json?ref=builds", headers=H, timeout=25).json()
    repo = json.loads(base64.b64decode(c["content"]))
    print("sabit manifest:", repo["pluginLists"])
