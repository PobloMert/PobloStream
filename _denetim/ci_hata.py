# -*- coding: utf-8 -*-
"""Basarisiz Actions kosusunun hangi adimda hatoldigini API'den bul."""
import json

import requests

REPO = "PobloMert/PobloStream"
H = {"User-Agent": "curl/8", "Accept": "application/vnd.github+json"}

runs = requests.get(f"https://api.github.com/repos/{REPO}/actions/runs?per_page=3", headers=H, timeout=25).json()
for run in runs.get("workflow_runs", []):
    print(f"\n########## run {run['id']} {run['head_sha'][:7]} {run['conclusion']}")
    jobs = requests.get(run["jobs_url"], headers=H, timeout=25).json()
    for job in jobs.get("jobs", []):
        print(f"  job: {job['name']} -> {job['conclusion']}")
        for step in job.get("steps", []):
            isaret = "X" if step["conclusion"] == "failure" else ("-" if step["conclusion"] == "skipped" else "o")
            print(f"    [{isaret}] {step['name']} ({step['conclusion']})")
