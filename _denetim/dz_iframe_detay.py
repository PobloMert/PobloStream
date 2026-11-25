# -*- coding: utf-8 -*-
"""Dizilla pichive iframe HTML'sini kaydet ve NP_GATE/NP_CONFIG bolumlerini goster."""
import re
from pathlib import Path

import requests

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"}
u = "https://four.pichive.online/iframe.php?v=7584503cf0e81f0e5df3f9a72e6e2bc9"
r = requests.get(u, headers={**UA, "Referer": "https://dizilla.la/"}, timeout=25)
p = Path(__file__).parent / "html" / "dz_iframe.html"
p.write_text(r.text, encoding="utf-8")
print("kaydedildi:", p, len(r.content), "B")

t = r.text
for kw in ["NP_GATE", "NP_CONFIG", "gate", "sources", "master"]:
    for m in list(re.finditer(kw, t))[:3]:
        i = m.start()
        print(f"\n--- {kw} @{i} ---")
        print(t[max(0, i - 180):i + 420].replace("\n", " ")[:600])
