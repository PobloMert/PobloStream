# -*- coding: utf-8 -*-
"""Basarisiz son CI'in adimlarini ve hata annotation'unu gosterir."""
import requests

REPO = "PobloMert/PobloStream"
H = {"User-Agent": "curl/8", "Accept": "application/vnd.github+json"}

runs = requests.get(f"https://api.github.com/repos/{REPO}/actions/runs?per_page=4", headers=H, timeout=25).json()["workflow_runs"]
r = next(x for x in runs if x["name"] == "CloudStream Derleyici")
print("run:", r["id"], r["head_sha"][:7], r["conclusion"])
for j in requests.get(r["jobs_url"], headers=H, timeout=25).json()["jobs"]:
    for s in j["steps"]:
        print(f"  [{s['conclusion']}] {s['name']}")
cr = requests.get(j["check_run_url"], headers=H, timeout=25).json()
for a in requests.get(f"{j['check_run_url']}/annotations", headers=H, timeout=25).json():
    print(f"\n[{a['annotation_level']}] {a.get('path')}:{a.get('start_line')} -> {a.get('message','')[:400]}")
