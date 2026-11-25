# -*- coding: utf-8 -*-
"""Yayindaki plugins.json'u cache-bust ile oku + Actions kosu durumunu raporla."""
import time

import requests

BEKLENEN = {"DiziPal": 35, "DiziMom": 37, "Dizilla": 42, "FullHDFilmizlesene": 28, "CizgiMax": 9}

r = requests.get(
    f"https://cdn.jsdelivr.net/gh/PobloMert/PobloStream@builds/plugins.json?t={int(time.time())}",
    headers={"User-Agent": "Mozilla/5.0"}, timeout=25,
)
yayin = {e["name"]: int(e["version"]) for e in r.json()}
print("=== jsdelivr (t= ile cache kirildi) === toplam:", len(yayin))
for ad, bek in BEKLENEN.items():
    durum = "TAMAM" if yayin.get(ad) == bek else "ESKI"
    print(f"  {ad:<20} v{yayin.get(ad)}  (beklenen {bek})  {durum}")

print("\n=== Actions kosmalari ===")
a = requests.get(
    "https://api.github.com/repos/PobloMert/PobloStream/actions/runs?per_page=4",
    headers={"User-Agent": "curl/8"}, timeout=25,
).json()
for w in a.get("workflow_runs", []):
    print(f"  {w['name']} | {w['head_sha'][:7]} | {w['status']}/{w['conclusion']} | {w['created_at']}")
if not a.get("workflow_runs"):
    print("  (kosu kaydi yok)", str(a)[:300])
