# -*- coding: utf-8 -*-
# Faz 3: kalan bosluklar - dizimom.fun, HDF .vip/.biz kayitli HTML uzerinde selector,
# FullHDFilmizlesene icerik, AnimeciX/AsyaWatch API, CizgiMax, DiziPal aday yapisi
import json, os, re, collections
import requests, urllib3
from bs4 import BeautifulSoup
urllib3.disable_warnings()
OUT = os.path.dirname(os.path.abspath(__file__)); HD = os.path.join(OUT, "html")
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.8"}

def load(tag):
    p = os.path.join(HD, tag + ".html")
    return open(p, encoding="utf8", errors="ignore").read() if os.path.exists(p) else ""

def save(t, x): open(os.path.join(HD, t + ".html"), "w", encoding="utf8", errors="ignore").write(x)

def sel(tag, html, sels):
    s = BeautifulSoup(html, "html.parser")
    print("  --- " + tag + " (len %d) TITLE=%s" % (len(html), ((s.title.string or "").strip()[:60] if s.title and s.title.string else "-")))
    for label, q in sels:
        try: n = len(s.select(q))
        except Exception as e: n = "ERR"
        print("     %-56s %s" % ((label + " || " + q)[:56], n))
    return s

print("############ 1) HDFilmCehennemi .vip / .biz KAYITLI html uzerinde kodun selectorlari")
HDF = [("main h4.title", "h4.title"), ("main img[data-src]", "img[data-src]"),
       ("load h1.section-title", "h1.section-title"),
       ("load aside.post-info-poster img.lazyload", "aside.post-info-poster img.lazyload"),
       ("load div.post-info-genres a", "div.post-info-genres a"),
       ("load div.post-info-year-country a", "div.post-info-year-country a"),
       ("load article.post-info-content > p", "article.post-info-content > p"),
       ("load div.seasons", "div.seasons"),
       ("load div.seasons-tab-content a", "div.seasons-tab-content a"),
       ("load div.alternative-links", "div.alternative-links"),
       ("load button.alternative-link", "button.alternative-link")]
for dom in ["vip", "biz"]:
    h = load("hdf_" + dom); d = load("hdf_" + dom + "_detail")
    if not h: continue
    sel("hdf " + dom + " home", h, HDF)
    cnt = collections.Counter()
    for a in BeautifulSoup(h, "html.parser").find_all(["a", "div", "li", "article", "img"]):
        cl = a.get("class")
        if cl: cnt[a.name + "." + ".".join(cl)] += 1
    print("     CAND:", json.dumps({k: v for k, v in cnt.most_common(30) if v >= 6}, ensure_ascii=False)[:800])
    if d:
        s2 = sel("hdf " + dom + " detail", d, HDF + [("h1", "h1"), ("img", "img"), ("iframe", "iframe")])
        print("     iframes:", [{k: str(v)[:80] for k, v in i.attrs.items()} for i in s2.find_all("iframe")][:4])
        cnt2 = collections.Counter()
        for a in s2.find_all(["div", "article", "aside", "h1", "h2", "span"]):
            cl = a.get("class")
            if cl: cnt2[a.name + "." + ".".join(cl)] += 1
        print("     DETAIL CAND:", json.dumps({k: v for k, v in cnt2.most_common(30) if v >= 2}, ensure_ascii=False)[:800])

print("\n############ 2) DiziMom adayi: www.dizimom.fun")
try:
    r = requests.get("https://www.dizimom.fun/", headers=UA, timeout=40, verify=False)
    print("  HOME", r.status_code, r.url, len(r.text))
    save("dmom_fun", r.text)
    s = sel("dizimom.fun home", r.text, [
        ("main div.episode-box", "div.episode-box"), ("main div.single-item", "div.single-item"),
        ("main div.cat-img a", "div.cat-img a"), ("div.categorytitle a", "div.categorytitle a"),
        ("div.cat-img img", "div.cat-img img"), ("a[href*='/dizi/']", "a[href*='/dizi/']")])
    cnt = collections.Counter()
    for a in s.find_all(["div", "a", "li", "article"]):
        cl = a.get("class")
        if cl: cnt[a.name + "." + ".".join(cl)] += 1
    print("     CAND:", json.dumps({k: v for k, v in cnt.most_common(30) if v >= 4}, ensure_ascii=False)[:800])
    links = [a.get("href") for a in s.find_all("a", href=True) if "/dizi/" in a.get("href")]
    links = list(dict.fromkeys(links))
    print("     dizi link:", links[:3])
    if links:
        u = links[0] if links[0].startswith("http") else "https://www.dizimom.fun" + links[0]
        r2 = requests.get(u, headers=UA, timeout=40, verify=False)
        print("  DETAY", r2.status_code, len(r2.text)); save("dmom_fun_detail", r2.text)
        sel("dizimom.fun detail", r2.text, [
            ("div.title h1", "div.title h1"), ("div.category_image img", "div.category_image img"),
            ("div.bolumust", "div.bolumust"), ("div.video p iframe", "div.video p iframe"),
            ("div.sources a", "div.sources a"), ("h1", "h1"), ("iframe", "iframe"),
            ("div.episode-box", "div.episode-box")])
    rs = requests.get("https://www.dizimom.fun/?s=kara", headers=UA, timeout=40, verify=False)
    print("  ARAMA", rs.status_code, len(rs.text))
    sel("dizimom.fun search", rs.text, [("div.episode-box", "div.episode-box"), ("div.single-item", "div.single-item")])
except Exception as e:
    print("  ERR", type(e).__name__, str(e)[:100])

print("\n############ 3) FullHDFilmizlesene 5667 byte ne?")
h = load("fhfz_home")
print("  ", re.sub(r"\s+", " ", h)[:600])
print("  selectorlar:", [ (q, len(BeautifulSoup(h, "html.parser").select(q))) for q in ["li.film", "span.film-title", "div[class=izle-titles]", "li", "div.film"] ])

print("\n############ 4) AnimeciX JSON API (x-e-h header)")
AX = {"User-Agent": UA["User-Agent"], "Accept": "application/json",
      "x-e-h": "7Y2ozlO+QysR5w9Q6Tupmtvl9jJp7ThFH8SB+Lo7NvZjgjqRSqOgcT2v4ISM9sP10LmnlYI8WQ==.xrlyOBFS5BHjQ2Lk",
      "Referer": "https://animecix.tv/"}
for u in ["https://animecix.tv/secure/search/kara?limit=20", "https://animecix.tv/secure/search/kara"]:
    try:
        r = requests.get(u, headers=AX, timeout=30, verify=False)
        print("  %-46s -> %s len=%s ct=%s" % (u.replace("https://animecix.tv", ""), r.status_code, len(r.text), r.headers.get("content-type", "")[:30]))
        print("     body:", r.text[:180].replace("\n", " "))
    except Exception as e:
        print("  ERR", type(e).__name__, str(e)[:80])

print("\n############ 5) AsyaWatch POST /api/bg/searchcontent")
try:
    r = requests.post("https://asyawatch.com/api/bg/searchcontent?searchterm=kara",
                      headers={**UA, "Content-Type": "application/json", "Referer": "https://asyawatch.com/"},
                      json={"searchterm": "kara"}, timeout=40, verify=False)
    print("  status", r.status_code, len(r.text), "ct", r.headers.get("content-type", "")[:30])
    print("  body:", r.text[:250].replace("\n", " "))
except Exception as e:
    print("  ERR", type(e).__name__, str(e)[:100])
try:
    r = requests.get("https://asyawatch.com/", headers=UA, timeout=30, verify=False)
    print("  home", r.status_code, len(r.text), "__NEXT_DATA__:", "__NEXT_DATA__" in r.text)
except Exception as e:
    print("  home ERR", type(e).__name__)

print("\n############ 6) CizgiMax: diziler sayfasi kayitli html selectors")
h = load("czm_diziler")
if h:
    s = sel("cizgimax /diziler", h, [("ul.filter-results li", "ul.filter-results li"),
                                     ("div.poster-subject a", "div.poster-subject a"),
                                     ("div.poster-media img", "div.poster-media img"),
                                     ("h2.truncate", "h2.truncate"), ("div.poster", "div.poster"),
                                     ("a[href*='/dizi/']", "a[href*='/dizi/']")])
    cnt = collections.Counter()
    for a in s.find_all(["div", "a", "li", "article"]):
        cl = a.get("class")
        if cl: cnt[a.name + "." + ".".join(cl)] += 1
    print("     CAND:", json.dumps({k: v for k, v in cnt.most_common(25) if v >= 4}, ensure_ascii=False)[:700])

print("\n############ 7) DiziPal aday dizipalizle.org yapisi (kayitli)")
h = load("dp_izle_home")
if h:
    s = sel("dizipalizle.org home", h, [("div.episode-item", "div.episode-item"),
        ("article.type2 ul li", "article.type2 ul li"), ("article.type2", "article.type2"),
        ("span.title", "span.title"), ("div.cover h5", "div.cover h5"),
        ("div.summary p", "div.summary p"), ("a[href*='/dizi/']", "a[href*='/dizi/']"),
        ("a[href*='/bolum/']", "a[href*='/bolum/']")])
    cnt = collections.Counter()
    for a in s.find_all(["div", "a", "li", "article", "span"]):
        cl = a.get("class")
        if cl: cnt[a.name + "." + ".".join(cl)] += 1
    print("     CAND:", json.dumps({k: v for k, v in cnt.most_common(28) if v >= 5}, ensure_ascii=False)[:800])

print("\n############ 8) Dizilla: kodun gercek kullandigi selectorlar detay uzerinde")
h = load("dzl_detail")
if h:
    sel("dzl detail", h, [("a.pcard", "a.pcard"), (".pimg img", ".pimg img"), ("li.ccard", "li.ccard"),
                          ("div.tnum", "div.tnum"), ("a.top", "a.top"), ("li.ftag", "li.ftag"),
                          ("div.dstat", "div.dstat"), ("h1", "h1"), ("ul.eps li", "ul.eps li")])
h = load("dzl_tum_bolumler")
if h:
    s = sel("dzl /tum-bolumler", h, [("a.pcard", "a.pcard"), ("ul.v4-epgrid li", "ul.v4-epgrid li"),
                                     ("a.gb", "a.gb"), ("li.ccard", "li.ccard"), ("a.vz4-lnk", "a.vz4-lnk")])
    p = s.select_one("a.pcard")
    if p: print("     a.pcard[0]:", p.prettify()[:500])
