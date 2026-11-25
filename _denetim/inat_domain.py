# -*- coding: utf-8 -*-
"""InatBox domain durumu: DNS (DoH), eski surumler, BigBullApp'te yeni kaynak."""
import re

import requests

UA = {"User-Agent": "Mozilla/5.0", "accept": "application/dns-json"}

print("=== DNS (Cloudflare DoH) ===")
for ad in ["diziboxen.help", "dizibox.live", "inatbox.net"]:
    try:
        r = requests.get("https://cloudflare-dns.com/dns-query", params={"name": ad, "type": "A"}, headers=UA, timeout=20).json()
        print(f"  {ad:<20} status={r.get('Status')} cevap={[a.get('data') for a in r.get('Answer', [])]}")
    except Exception as e:
        print("  HATA", ad, type(e).__name__)

print("\n=== eski surumlerde contentUrl ===")
kök = r"C:\Users\ahmet\OneDrive\Masaüstü\Youtube Direktörü\cloudstream"
for r in ["referans\\cs-Kekik", "referans\\nik-cloudstream", "_kekik_ref"]:
    f = f"{kök}\\{r}\\InatBox\\src\\main\\kotlin\\com\\keyiflerolsun\\InatBox.kt"
    try:
        t = open(f, encoding="utf-8").read()
        m = re.search(r'contentUrl\s*=\s*"([^"]+)"', t)
        print(f"  {r:<25} {m.group(1) if m else 'YOK'}")
    except Exception as e:
        print("  HATA", r, type(e).__name__)

print("\n=== BigBullApp deposunda Inat dosyalari ===")
try:
    a = requests.get("https://api.github.com/repos/F0Rextasy/bigbullapp-cs/git/trees/master?recursive=1",
                     headers={"User-Agent": "curl/8"}, timeout=25).json()
    yollar = [x["path"] for x in a.get("tree", []) if re.search(r"inat", x["path"], re.I)]
    print("  bulunan:", yollar[:20])
except Exception as e:
    print("  HATA", type(e).__name__, str(e)[:100])
