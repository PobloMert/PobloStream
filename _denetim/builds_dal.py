# -*- coding: utf-8 -*-
"""builds dalinin gercek (API uzerinden, cache'siz) durumunu ve son kosunun adimlarini goster."""
import base64
import json

import requests

REPO = "PobloMert/PobloStream"
H = {"User-Agent": "curl/8", "Accept": "application/vnd.github+json"}

br = requests.get(f"https://api.github.com/repos/{REPO}/branches/builds", headers=H, timeout=25).json()
print("=== builds dali son commit ===")
print("  sha:", br["commit"]["sha"][:12], "| tarih:", br["commit"]["commit"]["committer"]["date"], "| mesaj:", br["commit"]["commit"]["message"][:70])

c = requests.get(f"https://api.github.com/repos/{REPO}/contents/plugins.json?ref=builds", headers=H, timeout=25).json()
icerik = base64.b64decode(c["content"]).decode("utf-8")
yayin = {e["name"]: int(e["version"]) for e in json.loads(icerik)}
print("\n=== builds/plugins.json (API, cache yok) === toplam:", len(yayin))
for ad in ["DiziPal", "DiziMom", "Dizilla", "FullHDFilmizlesene", "CizgiMax"]:
    print(f"  {ad:<20} v{yayin.get(ad)}")

print("\n=== son CloudStreamDerleyici kosusu adimlari ===")
runs = requests.get(f"https://api.github.com/repos/{REPO}/actions/runs?per_page=6", headers=H, timeout=25).json()["workflow_runs"]
for run in runs:
    if run["name"] != "CloudStream Derleyici":
        continue
    print(f"run {run['id']} {run['head_sha'][:7]} -> {run['conclusion']} ({run['created_at']})")
    jobs = requests.get(run["jobs_url"], headers=H, timeout=25).json()["jobs"]
    for j in jobs:
        for s in j.get("steps", []):
            print(f"   {'X' if s['conclusion']=='failure' else 'o'} {s['name']} ({s['conclusion']})")
    break
