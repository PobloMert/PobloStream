# -*- coding: utf-8 -*-
# Faz 2g: alternative domainlerin ic yapisi - HDFilmCehennemi .vip/.biz selector dogrulamasi,
# DiziPal/DiziMom adaylari, UgurFilm redirect takibi
import json, os, re, collections
import requests, urllib3
from bs4 import BeautifulSoup
urllib3.disable_warnings()
OUT = os.path.dirname(os.path.abspath(__file__)); HD = os.path.join(OUT, "html")
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.8"}

def save(t, x): open(os.path.join(HD, t + ".html"), "w", encoding="utf8", errors="ignore").write(x)

def show(tag, html, sels):
    s = BeautifulSoup(html, "html.parser")
    print("  --- " + tag + " (len %d)" % len(html))
    print("     TITLE:", (s.title.string or "").strip()[:70] if s.title and s.title.string else "-")
    for label, sel in sels:
        try: n = len(s.select(sel))
        except Exception as e: n = "ERR"
        print("     %-52s %s" % ((label + " || " + sel)[:52], n))
    return s

print("################ HDFilmCehennemi .vip / .biz (kod .nl icin yazilmis selectorlar)")
HDF_SELS = [
    ("main h4.title", "h4.title"), ("main img[data-src]", "img[data-src]"),
    ("load h1.section-title", "h1.section-title"),
    ("load aside.post-info-poster img.lazyload", "aside.post-info-poster img.lazyload"),
    ("load div.post-info-genres a", "div.post-info-genres a"),
    ("load div.post-info-year-country a", "div.post-info-year-country a"),
    ("load article.post-info-content > p", "article.post-info-content > p"),
    ("load div.seasons", "div.seasons"), ("load div.seasons-tab-content a", "div.seasons-tab-content a"),
    ("load div.alternative-links", "div.alternative-links"),
    ("load button.alternative-link", "button.alternative-link"),
    ("load div.post-info-trailer button", "div.post-info-trailer button"),
    ("load div.section-slider-container div.slider-slide", "div.section-slider-container div.slider-slide"),
    ("load div.post-info-cast a", "div.post-info-cast a"),
]
for dom in ["hdfilmcehennemi.vip", "hdfilmcehennemi.biz"]:
    try:
        r = requests.get("https://" + dom + "/", headers=UA, timeout=40, verify=False)
        print("HOME", dom, r.status_code, len(r.text))
        save("hdf_" + dom.split(".")[-1], r.text)
        s = show(dom + " home", r.text, HDF_SELS)
        links = [a["href"] for a in s.find_all("a", href=True) if re.search(r"/(film|dizi)/", a["href"])]
        print("     içerik link:", list(dict.fromkeys(links))[:2])
        if links:
            u = links[0] if links[0].startswith("http") else "https://" + dom + links[0]
            r2 = requests.get(u, headers=UA, timeout=40, verify=False)
            print("  DETAY", r2.status_code, r2.url[:80], len(r2.text))
            save("hdf_" + dom.split(".")[-1] + "_detail", r2.text)
            s2 = show(dom + " detail", r2.text, HDF_SELS)
            print("     iframe:", [dict(i.attrs) for i in s2.find_all("iframe")][:3])
            # arama
            r3 = requests.get("https://" + dom + "/arama/kara", headers=UA, timeout=40, verify=False)
            print("  ARAMA /arama/kara ->", r3.status_code, len(r3.text))
            show(dom + " search", r3.text, [("h4.title", "h4.title"), ("img[data-src]", "img[data-src]")])
    except Exception as e:
        print(" ", dom, "ERR", type(e).__name__, str(e)[:90])

print("\n################ DiziPal adayi: dizipalizle.org")
try:
    r = requests.get("https://dizipalizle.org/", headers=UA, timeout=30, verify=False)
    save("dp_izle_home", r.text)
    s = show("dizipalizle home", r.text, [
        ("div.episode-item", "div.episode-item"), ("article.type2 ul li", "article.type2 ul li"),
        ("article.type2", "article.type2"), ("div.name", "div.name"), ("div.episode", "div.episode"),
        ("span.title", "span.title"), ("a.pcard", "a.pcard"), ("div.poster", "div.poster"),
        ("div.content-item", "div.content-item"), ("a[href*='/dizi/']", "a[href*='/dizi/']")])
    cnt = collections.Counter()
    for a in s.find_all(["a", "div", "li", "article"]):
        cl = a.get("class")
        if cl: cnt[a.name + "." + ".".join(cl)] += 1
    print("     CAND:", json.dumps({k: v for k, v in cnt.most_common(28) if v >= 5}, ensure_ascii=False)[:800])
    # api var mi
    for u in ["/api/search-autocomplete", "/arama?query=kara", "/arama?q=kara"]:
        rr = requests.get("https://dizipalizle.org" + u, headers=UA, timeout=20, verify=False)
        print("     %-30s -> %s len=%s" % (u, rr.status_code, len(rr.text)), rr.text[:90].replace("\n", " "))
except Exception as e:
    print("  ERR", type(e).__name__, str(e)[:90])

print("\n################ DiziMom adayi: www.dizimom1.com")
try:
    r = requests.get("https://www.dizimom1.com/", headers=UA, timeout=25, verify=False)
    print("  status", r.status_code, len(r.text))
    print("  HTML ilk 500:", r.text[:500].replace("\n", " "))
    save("dmom1", r.text)
except Exception as e:
    print("  ERR", type(e).__name__, str(e)[:80])

print("\n################ UgurFilm adayi: ugurfilm8.com redirect takibi")
try:
    r = requests.get("https://ugurfilm8.com/", headers=UA, timeout=25, verify=False)
    print("  status", r.status_code, len(r.text))
    print("  HTML ilk 700:", r.text[:700].replace("\n", " "))
except Exception as e:
    print("  ERR", type(e).__name__, str(e)[:80])
