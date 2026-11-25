# -*- coding: utf-8 -*-
import re, os
p = os.path.join(os.path.dirname(os.path.abspath(__file__)), "html")
h = open(os.path.join(p, "jtf_detail.html"), encoding="utf8", errors="ignore").read()
for pat in ['id="movie"', 'jetv', 'd2rs', 'data-litespeed', 'player', 'iframe', '/izle']:
    print(pat, len(re.findall(pat, h, re.I)))
i = h.find('id="movie"')
print("--- id=movie ctx ---")
print(h[max(0, i - 800):i + 1500] if i > 0 else "YOK")
print("--- iframe/player bloklari ---")
for m in re.finditer(r'<[^>]*(iframe|player|video)[^>]*>', h, re.I):
    s = m.group(0)
    if len(s) < 400:
        print(s[:300])
