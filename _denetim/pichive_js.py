# -*- coding: utf-8 -*-
"""pichive np.js icindeki NP_GATE cagrisini ve hash fonksiyonunu bul."""
import re

import requests

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"}
for u in ["https://four.pichive.online/assets/np/np.js?v=11.to6jix"]:
    r = requests.get(u, headers=UA, timeout=25)
    t = r.text
    print("HTTP", r.status_code, len(t), "B")
    for m in list(re.finditer(r"NP_GATE", t))[:8]:
        i = m.start()
        print("\n--- NP_GATE @", i, "---")
        print(t[max(0, i - 400):i + 400].replace("\n", " ")[:800])
    for kw in ["md5", "sha256", "crypto", "createHash", "subtle.digest"]:
        print(kw, t.lower().count(kw.lower()))
