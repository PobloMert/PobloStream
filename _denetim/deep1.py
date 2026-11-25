# -*- coding: utf-8 -*-
# Faz 2: kritik eklentiler - detay sayfa / player selector'lari ve DOGRU selector onerileri
import json, os, re, sys, collections
import requests, urllib3
from bs4 import BeautifulSoup
urllib3.disable_warnings()

OUT = os.path.dirname(os.path.abspath(__file__))
HD = os.path.join(OUT, "html"); os.makedirs(HD, exist_ok=True)
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
      "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.8"}

def get(url, referer=None, timeout=40):
    h = dict(UA)
    if referer: h["Referer"] = referer
    try:
        r = requests.get(url, headers=h, timeout=timeout, verify=False)
        return r.status_code, r.url, r.text
    except Exception as e:
        return None, url, ""

def save(tag, html):
    p = os.path.join(HD, tag + ".html")
    open(p, "w", encoding="utf8", errors="ignore").write(html)
    return p

def counts(tag, html, sels):
    soup = BeautifulSoup(html, "html.parser")
    print("  --- " + tag + " (len %d)" % len(html))
    for label, sel in sels:
        try:
            n = len(soup.select(sel))
        except Exception as e:
            n = "ERR:" + str(e)[:40]
        print("     %-58s %s" % ((label + " || " + sel)[:58], n))
    return soup

def top_classes(html, k=30, mn=4):
    soup = BeautifulSoup(html, "html.parser")
    cnt = collections.Counter()
    for a in soup.find_all(["a", "div", "li", "article", "span", "img"]):
        cl = a.get("class")
        if cl: cnt[a.name + "." + ".".join(cl)] += 1
    return {x: y for x, y in cnt.most_common(200) if y >= mn}

print("################ JetFilmizle (jetfilmizle.app)")
st, fu, home = get("https://jetfilmizle.app/")
save("jtf_home", home)
soup = counts("JET home", home, [
    ("main", "div.film-card-wrapper"),
    ("link", "div.film-card-wrapper a[title]"),
    ("poster", ".film-poster img"),
])
# içerik linki topla
links = [a.get("href") for a in soup.select("div.film-card-wrapper a[title]") if a.get("href")]
print("  örnek içerik linkleri:", links[:3])
if links:
    st, fu, det = get(links[0], referer="https://jetfilmizle.app/")
    print("  DETAY", st, fu)
    save("jtf_detail", det)
    counts("JET detail", det, [
        ("title", "h1.film-title"),
        ("poster", "img.film-poster"),
        ("poster2", "img.card-img-top"),
        ("desc", "div.film-description"),
        ("info", "section.film-detay.film-bilgileri-section"),
        ("infoLinks", "section.film-detay.film-bilgileri-section a"),
        ("actors", "section.film-detay.film-bilgileri-section a[href*='oyuncu']"),
        ("recos", "section.film-detay.benzer-filmler-section div.film-card-wrapper"),
        ("PLAYER div#movie iframe", "div#movie iframe"),
        ("PLAYER any iframe", "iframe"),
        ("download a.download-btn[href]", "a.download-btn[href]"),
    ])
    d = BeautifulSoup(det, "html.parser")
    print("  iframes:", [(i.name, {k: v[:70] for k, v in i.attrs.items()}) for i in d.find_all("iframe")][:6])
    print("  CAND:", json.dumps(top_classes(det, mn=3), ensure_ascii=False)[:700])
# arama
st, fu, sr = get("https://jetfilmizle.app/arama?q=kara", referer="https://jetfilmizle.app/")
save("jtf_search", sr)
counts("JET search /arama?q=", sr, [("main", "div.film-card-wrapper")])

print()
print("################ Dizilla (dizilla.la)")
st, fu, home = get("https://dizilla.la/")
save("dzl_home", home)
soup = counts("DZL home", home, [
    ("main a.pcard", "a.pcard"),
    ("main ul.v4-epgrid li", "ul.v4-epgrid li"),
    ("main ul.v4-epgrid li a.gb", "ul.v4-epgrid li a.gb"),
    ("a.gb", "a.gb"),
    ("a.vz4-lnk", "a.vz4-lnk"),
])
dlinks = [a.get("href") for a in soup.select("a.pcard") if a.get("href")]
print("  örnek seri linkleri:", dlinks[:3])
for u in ["/tum-bolumler", "/diziler"]:
    s2, f2, h2 = get("https://dizilla.la" + u)
    print("  probe", u, s2, len(h2))
    if h2:
        save("dzl" + u.replace("/", "_"), h2)
        counts("DZL " + u, h2, [("a.pcard", "a.pcard"), ("ul.v4-epgrid li", "ul.v4-epgrid li"),
                                ("ul.v4-epgrid li a.gb", "ul.v4-epgrid li a.gb")])
if dlinks:
    st, fu, det = get(dlinks[0], referer="https://dizilla.la/")
    print("  DETAY", st, fu)
    save("dzl_detail", det)
    counts("DZL detail", det, [
        ("title h1", "h1"),
        ("poster .pimg img", ".pimg img"),
        ("poster .v4-epgrid img", ".v4-epgrid img"),
        ("mvk-t", ".mvk-t"), ("mvk-m", ".mvk-m"),
        ("tags .v4-rich a", ".v4-rich a"), ("tags .tags a", ".tags a"),
        ("episodes ul.v4-epgrid li a.gb", "ul.v4-epgrid li a.gb"),
        ("episodes ul.v4-epgrid li", "ul.v4-epgrid li"),
        ("pcard", "a.pcard"),
    ])
st, fu, sr = get("https://dizilla.la/arsiv?q=kara", referer="https://dizilla.la/")
save("dzl_search", sr)
counts("DZL search /arsiv?q=", sr, [("a.pcard", "a.pcard")])

print()
print("################ DiziBox load sayfası (article.detailed-article kontrolü)")
st, fu, sr = get("https://www.dizibox.live/?s=kara", timeout=90)
save("dbx_search", sr)
print("  search status", st, len(sr))
counts("DBX search", sr, [("article.detailed-article", "article.detailed-article"),
                          ("article.article-series-poster", "article.article-series-poster"),
                          ("grid-box", "article.grid-box")])
soup = BeautifulSoup(sr or "", "html.parser")
l = [a.get("href") for a in soup.select("article a[href]") if a.get("href")][:3]
print("  linkler:", l)
if l:
    s2, f2, det = get(l[0], referer="https://www.dizibox.live/")
    print("  DETAY", s2, f2, len(det)); save("dbx_detail", det)
    counts("DBX detail", det, [
        ("div.tv-overview h1 a", "div.tv-overview h1 a"),
        ("div.tv-overview figure img", "div.tv-overview figure img"),
        ("div.tv-story p", "div.tv-story p"),
        ("div#seasons-list a", "div#seasons-list a"),
        ("article.grid-box", "article.grid-box"),
        ("div#video-area iframe", "div#video-area iframe"),
        ("div#Player iframe", "div#Player iframe"),
        ("div.video-toolbar option", "div.video-toolbar option[value]"),
    ])
    print("  CAND:", json.dumps(top_classes(det, mn=3), ensure_ascii=False)[:700])
