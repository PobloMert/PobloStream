# -*- coding: utf-8 -*-
# Faz 2c: JetFilmizle player mekanizmasi + Dizilla detay/tum-bolumler + DiziBox cookie'li arama
import json, os, re, collections
import requests, urllib3
from bs4 import BeautifulSoup
urllib3.disable_warnings()

OUT = os.path.dirname(os.path.abspath(__file__))
HD = os.path.join(OUT, "html")
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.8"}

def save(tag, html):
    open(os.path.join(HD, tag + ".html"), "w", encoding="utf8", errors="ignore").write(html)

print("### JetFilmizle #active-player ve kaynak yukleme mekanizmasi")
h = open(os.path.join(HD, "jtf_detail.html"), encoding="utf8", errors="ignore").read()
soup = BeautifulSoup(h, "html.parser")
ap = soup.select_one("#active-player")
print("  #active-player attrs:", json.dumps({k: (v[:100] if isinstance(v, str) else v) for k, v in ap.attrs.items()}, ensure_ascii=False, indent=1) if ap else "YOK")
# player-source-btn butonlari
btns = soup.select("button.player-source-btn")
print("  player-source-btn sayisi:", len(btns))
if btns:
    print("  btn0 attrs:", json.dumps({k: (v[:120] if isinstance(v, str) else v) for k, v in btns[0].attrs.items()}, ensure_ascii=False))
# player kaynaklarini getiren script
for sc in soup.find_all("script"):
    t = sc.string or ""
    if "player" in t.lower() and ("ajax" in t.lower() or "fetch(" in t.lower() or "source" in t.lower()):
        tt = t.strip()
        if len(tt) < 6000 and ("source" in tt.lower() and "player" in tt.lower()):
            print("  SCRIPT (ilk 3500):")
            print(tt[:3500])
            print("  ...")

print()
print("### Dizilla detay sayfasi (mutlak URL) + tum-bolumler analizi")
r = requests.get("https://dizilla.la/dizi/mafia", headers=UA, timeout=40, verify=False)
print("  detay status:", r.status_code, len(r.text))
save("dzl_detail", r.text)
ds = BeautifulSoup(r.text, "html.parser")
for label, sel in [
    ("h1", "h1"), (".mvk-t", ".mvk-t"), (".mvk-m", ".mvk-m"),
    (".pimg img", ".pimg img"), ("ul.v4-epgrid li", "ul.v4-epgrid li"),
    ("ul.v4-epgrid li a.gb", "ul.v4-epgrid li a.gb"), ("a.pcard", "a.pcard"),
    (".v4-rich a", ".v4-rich a"), (".tags a", ".tags a"),
    ("img", "img")]:
    try: print("     %-24s %s" % (label, len(ds.select(sel))))
    except Exception: pass
cnt = collections.Counter()
for a in ds.find_all(["a", "div", "li", "ul"]):
    cl = a.get("class")
    if cl: cnt[a.name + "." + ".".join(cl)] += 1
print("  DZL detail CAND:", json.dumps(dict(cnt.most_common(20)), ensure_ascii=False)[:500])

# tum-bolumler sayfasindaki epgrid yapisinin gercek hali
h2 = open(os.path.join(HD, "dzl__tum-bolumler.html"), encoding="utf8", errors="ignore").read()
s2 = BeautifulSoup(h2, "html.parser")
print("  --- tum-bolumler sayfasi")
for label, sel in [("ul.v4-epgrid li", "ul.v4-epgrid li"), ("ul.v4-epgrid li a.gb", "ul.v4-epgrid li a.gb"),
                   ("li.vz4-c", "li.vz4-c"), ("a.vz4-lnk", "a.vz4-lnk"), ("a.gb", "a.gb"),
                   ("epgrid-section", ".v4-epgrid-section"), ("section", "section")]:
    try: print("     %-28s %s" % (label, len(s2.select(sel))))
    except Exception as e: print("     ", label, "ERR")
gb = s2.select("a.gb")
if gb:
    print("  a.gb ornek attrs:", json.dumps({k: (v[:80] if isinstance(v, str) else v) for k, v in gb[0].attrs.items()}, ensure_ascii=False))
    print("  a.gb parent yapisi:", str(gb[0].parent)[:300])
vz = s2.select("a.vz4-lnk")
if vz:
    print("  a.vz4-lnk ornek:", json.dumps({k: (v[:80] if isinstance(v, str) else v) for k, v in vz[0].attrs.items()}, ensure_ascii=False))

print()
print("### DiziBox arama - LockUser/isTrustedUser cookie ile")
s = requests.Session(); s.headers.update(UA)
s.cookies.set("LockUser", "true", domain="www.dizibox.live")
s.cookies.set("isTrustedUser", "true", domain="www.dizibox.live")
r = s.get("https://www.dizibox.live/?s=kara", timeout=90, verify=False)
print("  status:", r.status_code, len(r.text), r.url)
save("dbx_search2", r.text)
ds = BeautifulSoup(r.text, "html.parser")
for label, sel in [("article.detailed-article", "article.detailed-article"),
                   ("article.article-series-poster", "article.article-series-poster"),
                   ("a.poster-title", "a.poster-title")]:
    print("     %-34s %s" % (label, len(ds.select(sel))))
