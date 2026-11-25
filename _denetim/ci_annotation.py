# -*- coding: utf-8 -*-
"""Public API uzerinden basarisiz Actions job'unun annotation'larini (hat ozetini) cek."""
import requests

REPO = "PobloMert/PobloStream"
H = {"User-Agent": "curl/8", "Accept": "application/vnd.github+json"}

run = requests.get(f"https://api.github.com/repos/{REPO}/actions/runs?per_page=1", headers=H, timeout=25).json()["workflow_runs"][0]
job = requests.get(run["jobs_url"], headers=H, timeout=25).json()["jobs"][0]
print("run:", run["id"], run["head_sha"][:7], "| job:", job["name"], job["conclusion"])

cr = requests.get(job["check_run_url"], headers=H, timeout=25).json()
print("check-run output:", {k: (str(v)[:120] if isinstance(v, str) else v) for k, v in (cr.get("output") or {}).items()})

ann = requests.get(f"{job['check_run_url']}/annotations", headers=H, timeout=25).json()
print(f"\n=== annotation sayisi: {len(ann)} ===")
for a in ann[:25]:
    print(f"[{a.get('annotation_level')}] {a.get('path')}:{a.get('start_line')}")
    print("   ", (a.get("message") or "").replace("\n", " | ")[:900])
if not ann:
    print("(annotation yok)")
