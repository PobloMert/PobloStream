# -*- coding: utf-8 -*-
"""Son CI adimlarini incele + jsdelivr'e manuel purge atip dogrula."""
import requests

REPO = "PobloMert/PobloStream"
H = {"User-Agent": "curl/8", "Accept": "application/vnd.github+json"}

runs = requests.get(f"https://api.github.com/repos/{REPO}/actions/runs?per_page=3", headers=H, timeout=25).json()["workflow_runs"]
r = next(x for x in runs if x["name"] == "CloudStream Derleyici")
print("run:", r["head_sha"][:7], r["conclusion"])
for j in requests.get(r["jobs_url"], headers=H, timeout=25).json()["jobs"]:
    for s in j["steps"]:
        print(f"  [{s['conclusion']}] {s['name']}  {s['started_at']} -> {s['completed_at']}")

print("\n=== manuel purge ===")
for f in ("plugins.json", "repo.json"):
    p = requests.get(f"https://purge.jsdelivr.net/gh/{REPO}@builds/{f}",
                     headers={"Accept": "application/json", "User-Agent": "Mozilla/5.0"}, timeout=30)
    print(f"  {f}: {p.status_code} {p.text[:150]}")

j = requests.get(f"https://cdn.jsdelivr.net/gh/{REPO}@builds/plugins.json?t=999", headers={"User-Agent": "Mozilla/5.0"}, timeout=25).json()
print("purge sonrasi jsdelivr:", {e["name"]: e["version"] for e in j if e["name"] in ("DiziPal", "CizgiMax", "DiziMom", "Dizilla", "FullHDFilmizlesene")})
