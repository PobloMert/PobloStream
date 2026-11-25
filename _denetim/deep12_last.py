# -*- coding: utf-8 -*-
# Faz 5 (son ag turu): kalan eklentilerin home/search selector sayimlari + ipuclari
import json, os, re, collections
import requests, urllib3
from bs4 import BeautifulSoup
urllib3.disable_warnings()
OUT = os.path.dirname(os.path.abspath(__file__)); HD = os.path.join(OUT, "html")
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
      "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.8"}
def save(t, x): open(os.path.join(HD, t + ".html"), "w", encoding="utf8", errors="ignore").write(x)
def g(u, referer=None, timeout=45):
    h = dict(UA)
    if referer: h["Referer"] = referer
    try:
        r = requests.get(u, headers=h, timeout=timeout, verify=False)
        return r.status_code, r.url, r.text
    except Exception as e:
        return None, u, "ERR " + type(e).__name__
def sel(tag, html, sels):
    s = BeautifulSoup(html, "html.parser")
    print("  --- %s (len %d) TITLE=%s" % (tag, len(html), ((s.title.string or "").strip()[:60] if s.title and s.title.string else "-")))
    for label, q in sels:
        try: n = len(s.select(q))
        except Exception: n = "ERR"
        print("     %-56s %s" % ((label + " || " + q)[:56], n))
    return s

print("### DMax")
st, fu, h = g("https://www.dmax.com.tr/")
save("dmax_home", h)
sel("dmax home", h, [("main div.poster", "div.poster"), ("section.grid div.poster", "section.grid.dyn-content div.poster"), ("a[href*='/programlar/']", "a[href*='/programlar/']")])

print("\n### RareFilmler")
st, fu, h = g("https://rarefilmm.com/")
save("rare_home", h)
s = sel("rare home", h, [("main div.post", "div.post"), ("h2 a", "h2 a"), ("div.featured-image", "div.featured-image"), ("article", "article")])

print("\n### DDizi")
st, fu, h = g("https://www.ddizi.tel/")
save("ddz_home", h)
sel("ddizi home", h, [("ul.list_", "ul.list_"), ("div.dizi-boxpost", "div.dizi-boxpost"),
                      ("div.dizi-boxpost-cat", "div.dizi-boxpost-cat"), ("img.img-back", "img.img-back"), ("div.afis img", "div.afis img")])

print("\n### DiziBox home")
st, fu, h = g("https://www.dizibox.live/", timeout=90)
save("dbx_home", h)
sel("dbx home", h, [("article.detailed-article", "article.detailed-article"),
                    ("article.article-series-poster", "article.article-series-poster"),
                    ("div.post-title a", "div.post-title a")])

print("\n### FullHDFilmizlesene: redirect hedefini takip et")
st, fu, h = g("https://www.fullhdfilmizlesene.tv/.IGV0hOcNusVJOgqoD1HuWTk0PssdueKB")
print("  adim1:", st, fu[:80], len(h))
save("fhfz_r1", h)
m = re.findall(r"href=\"([^\"]+)\"", h)
print("  href:", m[:4])
if st == 200 and len(h) < 8000:
    for hh in m[:2]:
        st2, fu2, h2 = g("https://www.fullhdfilmizlesene.tv" + hh if hh.startswith("/") else hh)
        print("  adim2", hh[:40], "->", st2, len(h2))
        if st2 == 200:
            save("fhfz_r2", h2)
            sel("fhfz r2", h2, [("li.film", "li.film"), ("span.film-title", "span.film-title"), ("div[class=izle-titles]", "div[class=izle-titles]")])
            break

print("\n### AsyaWatch: home kaydet + detay sayfasinda __NEXT_DATA__/secureData ara")
st, fu, h = g("https://asyawatch.com/")
save("asya_home", h)
s = BeautifulSoup(h, "html.parser")
print("  home", st, len(h), "__NEXT_DATA__:", "__NEXT_DATA__" in h, "self.__next_f:", "self.__next_f" in h)
cnt = collections.Counter()
for a in s.find_all(["div", "a", "article"]):
    cl = a.get("class")
    if cl: cnt[a.name + "." + ".".join(cl)] += 1
print("  CAND:", json.dumps({k: v for k, v in cnt.most_common(15) if v >= 4}, ensure_ascii=False)[:400])
links = [a["href"] for a in s.find_all("a", href=True) if "/dizi/" in a["href"]]
links = list(dict.fromkeys(links))[:2]
print("  dizi linkleri:", links)
for u in links:
    uu = u if u.startswith("http") else "https://asyawatch.com" + u
    st, fu, h = g(uu, referer="https://asyawatch.com/")
    print("  DETAY", st, len(h), "__NEXT_DATA__:", "__NEXT_DATA__" in h, "secureData:", "secureData" in h)
    save("asya_detail", h)
    if st == 200:
        break

print("\n### CizgiMax: gecerli arama endpoint'i bul (kayitli html'de form)")
h = open(os.path.join(HD, "czm_diziler.html"), encoding="utf8", errors="ignore").read()
for m in re.finditer(r"<form[^>]{0,300}>", h):
    print("  form:", m.group(0)[:200])
for m in re.finditer(r"(?:action|fetch|url)[\"']?\s*[:=]\s*[\"']([^\"']*(?:arama|search|qr)[^\"']*)[\"']", h, re.I):
    print("  arama ipucu:", m.group(1)[:120])
# script icinde arama
for m in re.finditer(r"[\"'](/[^\"']{2,60})[\"']", h):
    u = m.group(1)
    if "arama" in u or "search" in u or "ajax" in u:
        print("  script/url:", u)
for u in ["https://cizgimax.online/arama?qr=kara", "https://cizgimax.online/?qr=kara", "https://cizgimax.online/arama/kara"]:
    st, fu, hh = g(u)
    print("  GET", u.replace("https://cizgimax.online", ""), "->", st, len(hh))
    if st == 200 and len(hh) > 30000:
        save("czm_search", hh)
        sel("czm search", hh, [("div.film-item", "div.film-item"), ("a.film-name", "a.film-name")])

print("\n### Dizilla: /tum-bolumler epgrid gercek yapi + sezon/ajax ipuclari")
h = open(os.path.join(HD, "dzl_tum_bolumler.html"), encoding="utf8", errors="ignore").read()
i = h.find("v4-epgrid")
print("  v4-epgrid baglami:\n", h[max(0, i - 500):i + 1600][:2100])
h2 = open(os.path.join(HD, "dzl_detail.html"), encoding="utf8", errors="ignore").read()
for kw in ["data-sezon", "sezon=", "/bolum", "ajax", "data-src", "data-ep"]:
    print("  detail kw %-12s %s" % (kw, len(re.findall(re.escape(kw), h2, re.I))))

print("\n### JetFilmizle: download-btn + site-actions.min.js kaynak endpoint")
h = open(os.path.join(HD, "jtf_detail.html"), encoding="utf8", errors="ignore").read()
sel("jtf detail (offline)", h, [("download a.download-btn[href]", "a.download-btn[href]"),
                                ("player div#active-player", "div#active-player"),
                                ("player button.player-source-btn", "button.player-source-btn"),
                                ("episode a[href*='/izle/']", "a[href*='/izle/']"),
                                ("episode div.episode-list a", "div.episode-list a")])
st, fu, js = g("https://jetfilmizle.app/assets/js/site-actions.min.js")
print("  site-actions.min.js:", st, len(js))
if st == 200:
    open(os.path.join(HD, "jtf_siteactions.js"), "w", encoding="utf8", errors="ignore").write(js)
    for kw in ["data-source-index", "iframe", "embed", "api/", "src"]:
        print("    kw %-18s %s" % (kw, len(re.findall(re.escape(kw), js))))
    for m in re.finditer(r"fetch\(([^)]{0,150})\)", js):
        print("    fetch:", m.group(1)[:140])
    i = js.find("data-source-index")
    if i > 0:
        print("    --- data-source-index baglami:")
        print("    ", js[max(0, i - 400):i + 2200].replace("\n", " ")[:2400])

print("\n### TvDiziler: POST /search?qr= + GET /arama/kara hangisi calisiyor")
try:
    r = requests.post("https://tvdiziler.tv/search?qr=kara", headers={**UA, "X-Requested-With": "XMLHttpRequest", "Referer": "https://tvdiziler.tv/"}, timeout=40, verify=False)
    print("  POST /search?qr= ->", r.status_code, len(r.text), r.headers.get("content-type", "")[:30])
    print("  body:", r.text[:150].replace("\n", " "))
except Exception as e:
    print("  POST ERR", type(e).__name__)
for u in ["https://tvdiziler.tv/arama/kara"]:
    st, fu, h = g(u)
    print("  GET", u.replace("https://tvdiziler.tv", ""), "->", st, len(h))
