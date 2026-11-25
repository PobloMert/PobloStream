# -*- coding: utf-8 -*-
# Faz2d: DiziPal alternatif domain + yapi analizi
import json, os, re, collections
import requests, urllib3
from bs4 import BeautifulSoup
urllib3.disable_warnings()
OUT = os.path.dirname(os.path.abspath(__file__)); HD = os.path.join(OUT, "html")
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.8",
      "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
      "X-Requested-With": "XMLHttpRequest",
      "Referer": "https://dizipal0341.com/"}

def save(tag, x): open(os.path.join(HD, tag + ".html"), "w", encoding="utf8", errors="ignore").write(x)

for dom in ["dizipal0341.com", "dizipal.tv", "dizipal.com"]:
    try:
        r = requests.get("https://" + dom + "/", headers=UA, timeout=20, verify=False)
        print("HOME", dom, r.status_code, len(r.text), r.url)
        if dom == "dizipal0341.com": save("dp_home", r.text)
    except Exception as e:
        print("HOME", dom, "ERR", type(e).__name__, str(e)[:60])

h = open(os.path.join(HD, "dp_home.html"), encoding="utf8", errors="ignore").read()
soup = BeautifulSoup(h, "html.parser")
cnt = collections.Counter()
for a in soup.find_all(["a", "div", "li", "article", "img"]):
    cl = a.get("class")
    if cl: cnt[a.name + "." + ".".join(cl)] += 1
print("\nTEKRARLANAN YAPILAR:")
for k, v in cnt.most_common(35):
    if v >= 6: print("  %-58s %s" % (k[:58], v))

print("\nEKLENTININ SELECTORLARI:")
for sel in ["div.episode-item", "article.type2 ul li", "article.type2", "div.name", "div.episode",
            "span.title", "div.cover h5", "div.summary p", ".series-player-container iframe",
            "div#vast_new iframe", "div.content-item", "div.poster"]:
    print("  %-40s %s" % (sel, len(soup.select(sel))))

# ilk kartin gercek HTML'i
cands = [a for a in soup.find_all("a", href=True) if re.search(r"/(dizi|film)/", a["href"])]
print("\niçerik link ornegi:", [c["href"] for c in cands[:5]])
if cands:
    p = cands[0]
    up = p
    for _ in range(3):
        if up.parent and up.parent.name not in ("body", "html"): up = up.parent
        else: break
    print("\nKART ÇEVRESİ (3 seviye yukarı):\n", up.prettify()[:1600])

print("\n--- /diziler sayfasi")
r = requests.get("https://dizipal0341.com/diziler", headers=UA, timeout=30, verify=False)
print("  status", r.status_code, len(r.text)); save("dp_diziler", r.text)
s2 = BeautifulSoup(r.text, "html.parser")
for sel in ["div.episode-item", "article.type2 ul li", "article.type2", "div.poster-item", "div.row-item", "article", "div.content"] :
    print("  %-34s %s" % (sel, len(s2.select(sel))))
cnt2 = collections.Counter()
for a in s2.find_all(["a", "div", "li", "article", "img"]):
    cl = a.get("class")
    if cl: cnt2[a.name + "." + ".".join(cl)] += 1
print("  /diziler CAND:", json.dumps(dict(cnt2.most_common(20)), ensure_ascii=False)[:800])

print("\n--- arama (GET /arama?query= ve /diziler?kelime=)")
for u in ["https://dizipal0341.com/arama?query=kara", "https://dizipal0341.com/diziler?kelime=kara",
          "https://dizipal0341.com/ara/kara"]:
    try:
        rr = requests.get(u, headers=UA, timeout=25, verify=False)
        s3 = BeautifulSoup(rr.text, "html.parser")
        print("  %s -> %s len=%s title=%s | a.pcard=%s div.episode-item=%s article.type2=%s" % (
            u.replace("https://dizipal0341.com", ""), rr.status_code, len(rr.text),
            (s3.title.string or "")[:40] if s3.title and s3.title.string else "-",
            len(s3.select("a.pcard")), len(s3.select("div.episode-item")), len(s3.select("article.type2"))))
    except Exception as e:
        print("  ", u, "ERR", type(e).__name__)
