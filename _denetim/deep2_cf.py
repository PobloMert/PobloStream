# -*- coding: utf-8 -*-
# Faz2b: Cloudflare'li siteler (HDFilmCehennemi, KultFilmler, TurkAnime vb.) cloudscraper ile
import json, os, sys, collections
import cloudscraper
from bs4 import BeautifulSoup

OUT = os.path.dirname(os.path.abspath(__file__))
HD = os.path.join(OUT, "html"); os.makedirs(HD, exist_ok=True)

def save(tag, html):
    open(os.path.join(HD, tag + ".html"), "w", encoding="utf8", errors="ignore").write(html)

def top_classes(html, mn=4):
    soup = BeautifulSoup(html, "html.parser")
    cnt = collections.Counter()
    for a in soup.find_all(["a", "div", "li", "article", "span", "img"]):
        cl = a.get("class")
        if cl: cnt[a.name + "." + ".".join(cl)] += 1
    return {x: y for x, y in cnt.most_common(200) if y >= mn}

def run(name, urls, sels, cf=True):
    s = cloudscraper.create_scraper(browser={"browser": "chrome", "platform": "windows", "desktop": True})
    html = ""
    for u in urls:
        try:
            r = s.get(u, timeout=60)
            print("%s GET %s -> %s len=%s final=%s" % (name, u[:60], r.status_code, len(r.text), r.url[:70]), flush=True)
            if r.status_code == 200 and len(r.text) > len(html):
                html = r.text; final = r.url
        except Exception as e:
            print("%s GET %s -> ERR %s" % (name, u[:60], type(e).__name__ + str(e)[:80]), flush=True)
    if not html:
        print("  !! html yok, selector test edilemedi"); return None
    soup = BeautifulSoup(html, "html.parser")
    print("  TITLE:", (soup.title.string or "").strip()[:80] if soup.title and soup.title.string else "-")
    for label, sel in sels:
        try: n = len(soup.select(sel))
        except Exception as e: n = "ERR" + str(e)[:40]
        print("     %-56s %s" % ((label + " || " + sel)[:56], n))
    return soup, html

print("################ HDFilmCehennemi (www.hdfilmcehennemi.nl) - cloudscraper")
sels = [
    ("main a", "a"),
    ("main h4.title", "h4.title"),
    ("main img[data-src]", "img[data-src]"),
    ("main img.lazyload", "img.lazyload"),
    ("load h1.section-title", "h1.section-title"),
    ("load aside.post-info-poster img.lazyload", "aside.post-info-poster img.lazyload"),
    ("load div.post-info-genres a", "div.post-info-genres a"),
    ("load div.post-info-year-country a", "div.post-info-year-country a"),
    ("load div.seasons", "div.seasons"),
    ("load div.seasons-tab-content a", "div.seasons-tab-content a"),
    ("load div.alternative-links", "div.alternative-links"),
    ("load button.alternative-link", "button.alternative-link"),
    ("load div.section-slider-container div.slider-slide", "div.section-slider-container div.slider-slide"),
    ("load div.post-info-trailer button", "div.post-info-trailer button"),
    ("load article.post-info-content > p", "article.post-info-content > p"),
]
res = run("HDF", ["https://www.hdfilmcehennemi.nl/", "https://www.hdfilmcehennemi.nl/arama/kara"], sels)
if res:
    soup, html = res
    save("hdf_home", html)
    print("  CAND:", json.dumps(top_classes(html, 5), ensure_ascii=False)[:900])
    # detay sayfasi
    hrefs = []
    for a in soup.find_all("a", href=True):
        h = a["href"]
        if h.startswith("https://www.hdfilmcehennemi") and "/film/" in h or "/dizi/" in h:
            hrefs.append(h)
    hrefs = list(dict.fromkeys(hrefs))
    print("  detay link ornegi:", hrefs[:3])
    if hrefs:
        s2 = cloudscraper.create_scraper(browser={"browser": "chrome", "platform": "windows", "desktop": True})
        r = s2.get(hrefs[0], timeout=60)
        print("  DETAY", r.status_code, r.url, len(r.text))
        save("hdf_detail", r.text)
        soup2 = BeautifulSoup(r.text, "html.parser")
        for label, sel in sels:
            try: n = len(soup2.select(sel))
            except Exception as e: n = "ERR"
            print("     %-56s %s" % ((label + " || " + sel)[:56], n))
        print("  DETAY CAND:", json.dumps(top_classes(r.text, 3), ensure_ascii=False)[:900])
        ifr = soup2.find_all("iframe")
        print("  DETAY iframes:", [{k: v[:60] for k, v in i.attrs.items()} for i in ifr][:5])

print()
print("################ KultFilmler (CF?) - cloudscraper")
res = run("KULT", ["https://kultfilmler.net/category/aile-filmleri-izle"], [
    ("main div.col-md-12 div.movie-box", "div.col-md-12 div.movie-box"),
    ("main div.movie-box", "div.movie-box"),
    ("onerilen a.mcard", "a.mcard"),
    ("onerilen div.poster", "div.poster"),
])
if res:
    soup, html = res
    save("kult_home", html)
    print("  CAND:", json.dumps(top_classes(html, 5), ensure_ascii=False)[:700])
    a0 = soup.select_one("a.mcard")
    print("  a.mcard outer:", (a0.prettify()[:600] if a0 else "-"))
