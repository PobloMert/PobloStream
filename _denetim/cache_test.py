# -*- coding: utf-8 -*-
"""raw vs jsdelivr tazelik testi + jsdelivr purge (dogru Accept ile)."""
import time

import requests

REPO = "PobloMert/PobloStream"
UA = {"User-Agent": "Mozilla/5.0"}
t = int(time.time())

for adres in [
    f"https://raw.githubusercontent.com/{REPO}/builds/plugins.json?t={t}",
    f"https://github.com/{REPO}/raw/builds/plugins.json?t={t}",
    f"https://cdn.jsdelivr.net/gh/{REPO}@builds/plugins.json?t={t}",
]:
    try:
        r = requests.get(adres, headers=UA, timeout=25)
        j = r.json()
        sur = {e["name"]: e["version"] for e in j if e["name"] in ("DiziPal", "CizgiMax", "DiziMom")}
        print(f"{r.status_code} | {sur} | cache={r.headers.get('cache-control')} | {adres.split('/')[2]}")
    except Exception as e:
        print("HATA", type(e).__name__, adres[:80])

print("\n=== jsdelivr purge ===")
p = requests.get(
    f"https://purge.jsdelivr.net/gh/{REPO}@builds/plugins.json",
    headers={"Accept": "application/json", "User-Agent": "Mozilla/5.0"}, timeout=30,
)
print(p.status_code, p.text[:300])

r = requests.get(f"https://cdn.jsdelivr.net/gh/{REPO}@builds/plugins.json?t={t+1}", headers=UA, timeout=25)
print("purge sonrasi:", {e["name"]: e["version"] for e in r.json() if e["name"] in ("DiziPal", "CizgiMax", "DiziMom")})
