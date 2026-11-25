# -*- coding: utf-8 -*-
"""builds dali: repo.json icindeki gercek URL, cs3 dosyalari ve jsdelivr cache durumu."""
import base64
import json
import time

import requests

REPO = "PobloMert/PobloStream"
H = {"User-Agent": "curl/8", "Accept": "application/vnd.github+json"}
BEKLENEN = {"DiziPal": 35, "DiziMom": 37, "Dizilla": 42, "FullHDFilmizlesene": 28, "CizgiMax": 9}


def dosya(ad):
    c = requests.get(f"https://api.github.com/repos/{REPO}/contents/{ad}?ref=builds", headers=H, timeout=25).json()
    return c, base64.b64decode(c["content"]) if "content" in c else b""


c, veri = dosya("plugins.json")
yayin = {e["name"]: int(e["version"]) for e in json.loads(veri)}
print("=== builds/plugins.json ===")
for ad, bek in BEKLENEN.items():
    print(f"  {ad:<20} v{yayin.get(ad):<3} (beklenen {bek}) {'OK' if yayin.get(ad)==bek else 'ESKI'}")

c, veri = dosya("repo.json")
repo = json.loads(veri)
print("\n=== builds/repo.json ===")
print(json.dumps(repo, ensure_ascii=False, indent=2)[:700])

# cs3 dosyalari
a = requests.get(f"https://api.github.com/repos/{REPO}/git/trees/{requests.get(f'https://api.github.com/repos/{REPO}/branches/builds', headers=H, timeout=25).json()['commit']['sha']}?recursive=1", headers=H, timeout=25).json()
cs3 = [x for x in a.get("tree", []) if x["path"].endswith(".cs3")]
print(f"\n=== builds dalinda {len(cs3)} .cs3 dosyasi ===")
for x in cs3:
    if x["path"].split("/")[-1][:-4] in BEKLENEN:
        print(f"  {x['path']:<40} {x['size']}B  sha={x['sha'][:8]}")

# jsdelivr: repo.json'daki URL birebir + purge
if "url" in str(repo).lower() or "pluginsUrl" in repo:
    pass
print("\n=== jsdelivr yaniti ===")
for adres in [
    f"https://cdn.jsdelivr.net/gh/{REPO}@builds/plugins.json?t={int(time.time())}",
    f"https://cdn.jsdelivr.net/gh/{REPO}@builds/repo.json?t={int(time.time())}",
]:
    r = requests.get(adres, headers={"User-Agent": "Mozilla/5.0"}, timeout=25)
    print(f"  {r.status_code} cache={r.headers.get('x-jsd-cache-status')} age={r.headers.get('age')} | {adres.split('@')[1][:60]}")
    try:
        j = r.json()
        if isinstance(j, list):
            print("     surumler:", {e["name"]: e["version"] for e in j if e["name"] in BEKLENEN})
    except Exception:
        pass

p = requests.get(f"https://purge.jsdelivr.net/gh/{REPO}@builds/plugins.json", headers=H, timeout=25)
print(f"\npurge: {p.status_code} {p.text[:200]}")
r = requests.get(f"https://cdn.jsdelivr.net/gh/{REPO}@builds/plugins.json?t={int(time.time())}", headers={"User-Agent": "Mozilla/5.0"}, timeout=25)
j = {e["name"]: int(e["version"]) for e in r.json()}
print("purge sonrasi:", {k: j.get(k) for k in BEKLENEN})
