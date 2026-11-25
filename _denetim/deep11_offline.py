# -*- coding: utf-8 -*-
# Faz 4: kayitli HTML/JS uzerinde kalan analizi (ag bagimli kisim ayri)
import json, os, re, collections
from bs4 import BeautifulSoup
OUT = os.path.dirname(os.path.abspath(__file__)); HD = os.path.join(OUT, "html")

def load(tag):
    p = os.path.join(HD, tag + ".html")
    return open(p, encoding="utf8", errors="ignore").read() if os.path.exists(p) else ""

def sel(tag, html, sels):
    s = BeautifulSoup(html, "html.parser")
    print("  --- " + tag + " (len %d) TITLE=%s" % (len(html), ((s.title.string or "").strip()[:60] if s.title and s.title.string else "-")))
    for label, q in sels:
        try: n = len(s.select(q))
        except Exception: n = "ERR"
        print("     %-56s %s" % ((label + " || " + q)[:56], n))
    return s

print("### A) DiziBox arama sayfasi gercek mi? (dbx_search3.html)")
h = load("dbx_search3")
s = sel("dbx search", h, [("article.detailed-article", "article.detailed-article"),
                          ("article.article-series-poster", "article.article-series-poster"),
                          ("div.post-title a", "div.post-title a"), ("a", "a"),
                          ("title", "title")])
print("     metin ornek:", " | ".join(s.get_text(" ", strip=True).split(" ")[:40]))

print("\n### B) TvDiziler kayitli arama (tvd_search.html)")
h = load("tvd_search")
if h:
    sel("tvd search", h, [("div.poster-xs", "div.poster-xs"), ("div.poster-long", "div.poster-long"),
                          ("div.poster-long-subject h2 a", "div.poster-long-subject h2 a"),
                          ("div.poster-long-image img", "div.poster-long-image img"),
                          ("span.rating", "span.rating"), ("a[href*='/dizi/']", "a[href*='/dizi/']")])
else:
    print("  kayit yok")

print("\n### C) FullHDFilmizlesene redirect hedefi (fhfz_home.html)")
h = load("fhfz_home")
for m in re.finditer(r"(?:location\.href|window\.location|url)\s*[=:]\s*[\"']([^\"']+)[\"']", h):
    print("   redirect:", m.group(1)[:120])
mm = re.findall(r"href=\"([^\"]+)\"", h)
print("   href'ler:", mm[:6])

print("\n### D) JetFilmizle film-page.min.js: iframe URL nasil olusuyor?")
js = load("jtf_filmpage").replace(".js", "")  # yanlis uzanti yok; js dosyasi ayri
p = os.path.join(HD, "jtf_filmpage.js")
js = open(p, encoding="utf8", errors="ignore").read() if os.path.exists(p) else ""
for kw in ["data-source-index", "data-player-type", "sourceButtons", "iframe", "src", "playerUrl", "embed", "/film/", "api/"]:
    print("   kw %-18s %s" % (kw, len(re.findall(re.escape(kw), js))))
# fetch cagrilarinin tam URL'leri
for m in re.finditer(r"fetch\(([^)]{0,160})\)", js):
    print("   fetch:", m.group(1)[:150])
# url benzeri stringler
urls = set(re.findall(r"[\"'`]((?:https?://|/)[a-zA-Z0-9\-_./{}$]{4,90})[\"'`]", js))
for u in sorted(urls)[:40]:
    print("   url:", u)
# data-source-index etrafi
i = js.find("data-source-index")
print("   --- data-source-index baglami:")
print("   ", js[max(0, i - 700):i + 1500].replace("\n", " ")[:1800] if i > 0 else "YOK")

print("\n### E) AsyaWatch home: __NEXT_DATA__ / secureData / api endpoint ipuclari")
h = load("dmom_fun")  # bos olabilir; asya icin kayit yok, sadece bilgi
print("   (asya home kaydedilmedi; webfetch gerekmez - kodda POST /api/bg/searchcontent 404 dondu)")
