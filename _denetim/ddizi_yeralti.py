# -*- coding: utf-8 -*-
import re
import requests
from bs4 import BeautifulSoup
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0"}
u = "https://www.ddizi.tel/izle/93592/yeralti-24-bolum-izle-hd2.htm"
r = requests.get(u, headers=UA, timeout=25)
s = BeautifulSoup(r.text, "html.parser")
og = s.select_one("meta[property='og:video']")
print(r.status_code, len(r.content), "og:", og.get("content")[:120] if og else "YOK")
print("iframe:", [(f.get("src") or "")[:90] for f in s.select("iframe")][:3])
