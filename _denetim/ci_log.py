# -*- coding: utf-8 -*-
"""Basarisiz Actions job logunu indirmeyi dene (basarili olursa hat satirlarini goster)."""
import io
import re
import zipfile

import requests

REPO = "PobloMert/PobloStream"
H = {"User-Agent": "curl/8", "Accept": "application/vnd.github+json"}

runs = requests.get(f"https://api.github.com/repos/{REPO}/actions/runs?per_page=1", headers=H, timeout=25).json()
run = runs["workflow_runs"][0]
job = requests.get(run["jobs_url"], headers=H, timeout=25).json()["jobs"][0]
print("job:", job["name"], job["conclusion"], "| url:", job["html_url"])

for aday in (f"https://api.github.com/repos/{REPO}/actions/jobs/{job['id']}/logs",
             f"https://api.github.com/repos/{REPO}/actions/runs/{run['id']}/logs"):
    r = requests.get(aday, headers=H, timeout=60, allow_redirects=True)
    print(f"\n--- {r.status_code} {aday[:90]}  ({len(r.content)}B)")
    if r.status_code != 200:
        print("   ", r.text[:200].replace("\n", " "))
        continue
    if r.content[:2] == b"PK":
        z = zipfile.ZipFile(io.BytesIO(r.content))
        print("   icerik:", z.namelist()[:5])
        metin = b"".join(z.read(n) for n in z.namelist()).decode("utf-8", "replace")
    else:
        metin = r.text
    satirlar = metin.splitlines()
    vurus = [s for s in satirlar if re.search(r"^e: |error:|FAILURE:|BUILD FAILED|What went wrong|> Task .*FAILED", s)]
    print(f"   toplam satir {len(satirlar)}, hata satiri {len(vurus)}")
    for s in vurus[:40]:
        print("   >", s[:220])
    break
