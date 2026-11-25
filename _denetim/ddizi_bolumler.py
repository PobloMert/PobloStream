# -*- coding: utf-8 -*-
"""Ddizi birkac bolum: og:video / iframe yapisi."""
import re

import requests
from bs4 import BeautifulSoup

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"}
urls = [
 "https://www.ddizi.tel/izle/87546/kurulus-osman-194-bolum-izle-sezon-finali-hd1.htm",
 "https://www.ddizi.tel/izle/87496/kurulus-osman-193-bolum-izle-hd1.htm",
 "https://www.ddizi.tel/izle/87448/kurulus-osman-192-bolum-izle-hd1.htm",
]
for u in urls:
    r = requests.get(u, headers=UA, timeout=25)
    s = BeautifulSoup(r.text, "html.parser")
    og = s.select_one("meta[property='og:video']")
    ifr = [(f.get("src") or "")[:110] for f in s.select("iframe")]
    jw = len(re.findall(r"jwplayer|sources\s*:", r.text))
    print(f"\n{r.status_code} {len(r.content)}B {u.split('/')[-1]}")
    print("  og:video:", (og.get("content")[:110] if og else "YOK"))
    print("  iframe:", ifr[:3])
    print("  jwplayer/sources:", jw)
