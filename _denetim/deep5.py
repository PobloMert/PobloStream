# -*- coding: utf-8 -*-
import json, os, re, collections
import requests, urllib3
from bs4 import BeautifulSoup
urllib3.disable_warnings()
OUT = os.path.dirname(os.path.abspath(__file__)); HD = os.path.join(OUT, "html")
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.8"}

print("### JetFilmizle player JS: kaynak URL nasil uretiliyor?")
h = open(os.path.join(HD, "jtf_detail.html"), encoding="utf8", errors="ignore").read()
for kw in ["data-film-id", "filmId", "player-source", "/ajax/", "loadPlayer", "sourceUrl", "data-source-url", "sources"]:
    print("  kw %-18s %s" % (kw, len(re.findall(re.escape(kw), h, re.I))))
# scriptleri tara
soup = BeautifulSoup(h, "html.parser")
inline = [s.string or "" for s in soup.find_all("script") if not s.get("src")]
for i, t in enumerate(inline):
    if re.search(r"player|source|iframe", t, re.I) and len(t) > 500:
        print("\n  --- INLINE SCRIPT #%d (len %d) ilk 2500:" % (i, len(t)))
        print(t[:2500])
print("\n  SCRIPT src'leri:", [s.get("src") for s in soup.find_all("script") if s.get("src")][:15])

print("\n\n### Dizilla /arsiv kart yapisi")
r = requests.get("https://dizilla.la/arsiv", headers=UA, timeout=40, verify=False)
print("  status", r.status_code, len(r.text))
open(os.path.join(HD, "dzl_arsiv.html"), "w", encoding="utf8", errors="ignore").write(r.text)
s = BeautifulSoup(r.text, "html.parser")
for sel in ["a.pcard", "a.pcard .mvk-t", ".mvk-t", ".mvk-m", ".pimg img", "a.pcard img", "h3", "h2"]:
    print("     %-22s %s" % (sel, len(s.select(sel))))
pc = s.select("a.pcard")
if pc:
    print("\n  a.pcard[0] HTML:\n", pc[0].prettify()[:1200])

print("\n### Dizilla /tum-bolumler kart yapisi")
r = requests.get("https://dizilla.la/tum-bolumler", headers=UA, timeout=40, verify=False)
open(os.path.join(HD, "dzl_tum_bolumler.html"), "w", encoding="utf8", errors="ignore").write(r.text)
print("  status", r.status_code, len(r.text))
s = BeautifulSoup(r.text, "html.parser")
for sel in ["ul.v4-epgrid", "ul.v4-epgrid li", "ul.v4-epgrid li a.gb", "a.gb", "li.vz4-c", "a.vz4-lnk",
            "a.epcard", ".epgrid", "li.ccard", "a.pcard"]:
    print("     %-28s %s" % (sel, len(s.select(sel))))
gb = s.select("a.gb")
if gb:
    print("\n  a.gb[0] HTML:", gb[0].prettify()[:700])
    print("  a.gb[0] parent:", gb[0].parent.name, gb[0].parent.get("class"))
# tekrar eden yapilar
cnt = collections.Counter()
for a in s.find_all(["a", "div", "li", "article"]):
    cl = a.get("class")
    if cl: cnt[a.name + "." + ".".join(cl)] += 1
print("  CAND:", json.dumps({k: v for k, v in cnt.most_common(40) if v >= 4}, ensure_ascii=False)[:900])
