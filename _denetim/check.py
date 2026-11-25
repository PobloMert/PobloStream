# -*- coding: utf-8 -*-
# PobloStream eklenti denetimi - Faz 1: tum eklentiler (mainPage + search selector sayimi)
import json, os, re, sys, time, collections
import requests, urllib3
from bs4 import BeautifulSoup
urllib3.disable_warnings()

OUT = os.path.dirname(os.path.abspath(__file__))
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
      "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.8"}

# her eklenti: home =denecek sayfa URLleri, search = arama URL kalibi ({q}), sels = {etiket: (nerege, selector)}
# nerege: 'home' -> home html, 'search' -> arama html
CFG = {
 "BelgeselX": dict(
    home=["https://belgeselx.com/konu/turk-tarihi-belgeselleri&page=1"],
    search=None,
    sels=[("main:div.gen-movie-contain>div.gen-info-contain>div.gen-movie-info","home","div.gen-movie-contain > div.gen-info-contain > div.gen-movie-info"),
          ("card:div.gen-movie-contain","home","div.gen-movie-contain"),
          ("title:div.gen-movie-info > h3 a","home","div.gen-movie-info > h3 a"),
          ("poster:div.gen-movie-img > img","home","div.gen-movie-img > img")]),
 "CizgiMax": dict(
    home=["https://cizgimax.online/diziler/page/1?orderby=date&order=DESC"],
    search="https://cizgimax.online/ajaxservice/index.php?qr={q}",
    sels=[("main:ul.filter-results li","home","ul.filter-results li"),
          ("title:h2.truncate","home","h2.truncate"),
          ("href:div.poster-subject a","home","div.poster-subject a"),
          ("poster:div.poster-media img","home","div.poster-media img")]),
 "DDizi": dict(
    home=["https://www.ddizi.tel/yeni-eklenenler1","https://www.ddizi.tel/"],
    search="POST /arama/ (form)",
    sels=[("main:ul.list_","home","ul.list_"),
          ("main:ul.list_ li>a","home","ul.list_ > li > a"),
          ("card:div.dizi-boxpost","home","div.dizi-boxpost"),
          ("card:div.dizi-boxpost-cat","home","div.dizi-boxpost-cat"),
          ("poster:img.img-back","home","img.img-back"),
          ("poster:div.afis img","home","div.afis img")]),
 "DiziBox": dict(
    home=["https://www.dizibox.live/ulke/turkiye","https://www.dizibox.live/dizi-arsivi/page/1/"],
    search="https://www.dizibox.live/?s={q}",
    sels=[("main:article.detailed-article","home","article.detailed-article"),
          ("main:article.article-series-poster","home","article.article-series-poster"),
          ("load:div.tv-overview h1 a","home","div.tv-overview h1 a"),
          ("load:div#seasons-list a","home","div#seasons-list a")]),
 "Dizilla": dict(
    home=["https://dizilla.la/"],
    search="https://dizilla.la/arsiv?q={q}",
    sels=[("main:a.pcard","home","a.pcard"),
          ("main:ul.v4-epgrid li","home","ul.v4-epgrid li"),
          ("main:ul.v4-epgrid li a.gb","home","ul.v4-epgrid li a.gb"),
          ("load:.mvk-t","home",".mvk-t"),
          ("poster:.pimg img","home",".pimg img")]),
 "DiziMom": dict(
    home=["https://www.dizimom.plus/tum-bolumler/page/1/"],
    search="https://www.dizimom.plus/?s={q}",
    sels=[("main:div.episode-box","home","div.episode-box"),
          ("main:div.single-item","home","div.single-item")]),
 "DiziPal": dict(
    home=["https://dizipal1224.com/diziler/son-bolumler"],
    search="POST https://dizipal1224.com/api/search-autocomplete",
    sels=[("main:div.episode-item","home","div.episode-item"),
          ("main:article.type2 ul li","home","article.type2 ul li")]),
 "DMax": dict(
    home=["https://www.dmax.com.tr/kesfet"],
    search="POST https://www.dmax.com.tr/ajax/search",
    sels=[("main:div.poster","home","div.poster"),
          ("main:section.grid.dyn-content div.poster","home","section.grid.dyn-content div.poster"),
          ("search:section.posters div.poster","home","section.posters div.poster")]),
 "FullHDFilm": dict(
    home=["https://hdfilm.us/tur/turkce-altyazili-film-izle"],
    search="https://hdfilm.us/?s={q}",
    sels=[("main:div.movie-poster","home","div.movie-poster")]),
 "FullHDFilmizlesene": dict(
    home=["https://www.fullhdfilmizlesene.tv/en-cok-izlenen-filmler-izle-hd/"],
    search="https://www.fullhdfilmizlesene.tv/arama/{q}",
    sels=[("main:li.film","home","li.film"),
          ("title:span.film-title","home","span.film-title"),
          ("poster:li.film img","home","li.film img")]),
 "HDFilmCehennemi": dict(
    home=["https://www.hdfilmcehennemi.nl/"],
    search="https://www.hdfilmcehennemi.nl/arama/{q}",
    sels=[("main:a(with img)","home","a"),
          ("poster:img[data-src]","home","img[data-src]"),
          ("load:h4.title","home","h4.title"),
          ("load:h1.section-title","home","h1.section-title"),
          ("load:aside.post-info-poster img.lazyload","home","aside.post-info-poster img.lazyload"),
          ("load:div.seasons-tab-content a","home","div.seasons-tab-content a"),
          ("load:div.alternative-links","home","div.alternative-links")]),
 "JetFilmizle": dict(
    home=["https://jetfilmizle.app/","https://jetfilmizle.app/netflix"],
    search="https://jetfilmizle.app/arama?q={q}",
    sels=[("main:div.film-card-wrapper","home","div.film-card-wrapper"),
          ("main:div.film-card-wrapper a[title]","home","div.film-card-wrapper a[title]"),
          ("poster:.film-poster img","home",".film-poster img"),
          ("poster:img.card-img-top","home","img.card-img-top"),
          ("card:div.movie-poster","home","div.movie-poster"),
          ("card:.film-item","home",".film-item")]),
 "KultFilmler": dict(
    home=["https://kultfilmler.net/category/aile-filmleri-izle"],
    search="https://kultfilmler.net/?s={q}",
    sels=[("main:div.col-md-12 div.movie-box","home","div.col-md-12 div.movie-box"),
          ("main:div.movie-box","home","div.movie-box"),
          ("poster:div.img img","home","div.img img")]),
 "RareFilmm": dict(
    home=["https://rarefilmm.com/page/1/"],
    search="https://rarefilmm.com/?s={q}",
    sels=[("main:div.post","home","div.post"),
          ("title:h2 a","home","h2 a"),
          ("poster:div.featured-image","home","div.featured-image")]),
 "SuperFilmGeldi": dict(
    home=["https://superfilmgeldi.info/page/1/"],
    search="https://superfilmgeldi.info/?s={q}",
    sels=[("main:div.movie-preview-content","home","div.movie-preview-content"),
          ("title:span.movie-title a","home","span.movie-title a")]),
 "TurkAnime": dict(
    home=["https://www.turkanime.co/anime-turu/1/Aksiyon"],
    search="https://www.turkanime.co/arama?q={q}",
    sels=[("main:div#orta-icerik div.panel","home","div#orta-icerik div.panel"),
          ("main:div.panel","home","div.panel"),
          ("title:div.panel-title a","home","div.panel-title a"),
          ("poster:div#detayPaylas div.imaj img","home","div#detayPaylas div.imaj img")]),
 "TvDiziler": dict(
    home=["https://tvdiziler.tv/","https://tvdiziler.tv/dizi/tur/aile"],
    search="https://tvdiziler.tv/arama/{q}",
    sels=[("main:div.poster-xs","home","div.poster-xs"),
          ("main:div.poster-long","home","div.poster-long"),
          ("title:div.poster-long-subject h2","home","div.poster-long-subject h2"),
          ("poster:div.poster-long-image img","home","div.poster-long-image img"),
          ("poster:div.poster-xs-image img","home","div.poster-xs-image img")]),
 "UgurFilm": dict(
    home=["https://ugurfilm7.com/turkce-altyazili-filmler/page/1"],
    search="https://ugurfilm7.com/?s={q}",
    sels=[("main:div.icerik div","home","div.icerik div"),
          ("poster:div.resim img","home","div.resim img")]),
}

def fetch(url, cf=False):
    s = requests.Session()
    s.headers.update(UA)
    try:
        r = s.get(url, timeout=30, verify=False, allow_redirects=True)
        return r.status_code, r.url, r.text, None
    except Exception as e:
        return None, url, "", type(e).__name__ + ":" + str(e)[:90]

results = {}
only = sys.argv[1:] or list(CFG)
for name in only:
    c = CFG[name]
    rec = {"home": [], "search": None, "sel": {}, "cand": {}}
    home_html = ""
    for u in c["home"]:
        st, fu, html, err = fetch(u)
        rec["home"].append({"req": u, "status": st, "final": fu, "len": len(html), "err": err})
        if html and len(html) > len(home_html):
            home_html = html
    # search
    if c["search"] and "{" in str(c["search"]):
        su = c["search"].format(q="kara")
        st, fu, sh, err = fetch(su)
        rec["search"] = {"req": su, "status": st, "final": fu, "len": len(sh), "err": err}
    else:
        rec["search"] = {"req": c["search"], "note": "POST/GET disi - faz1 atlandi"}
        sh = ""
    for label, where, sel in c["sels"]:
        html = home_html if where == "home" else sh
        if not html:
            rec["sel"][label + " || " + sel] = None
            continue
        try:
            soup = BeautifulSoup(html, "html.parser")
            rec["sel"][label + " || " + sel] = len(soup.select(sel))
        except Exception as e:
            rec["sel"][label + " || " + sel] = "ERR " + str(e)[:60]
    # candidate class names on home page
    if home_html:
        soup = BeautifulSoup(home_html, "html.parser")
        cnt = collections.Counter()
        for a in soup.find_all(["a", "div", "li", "article"]):
            cl = a.get("class")
            if cl:
                key = a.name + "." + ".".join(cl)
                cnt[key] += 1
        rec["cand"] = {k: v for k, v in cnt.most_common(25) if v >= 5}
    results[name] = rec
    print("==== " + name, flush=True)
    for h in rec["home"]:
        print("   HOME %s -> %s %s %s" % (h["req"][:70], h["status"], h["len"], h["err"] or ""), flush=True)
    print("   SEARCH %s" % json.dumps(rec["search"], ensure_ascii=False)[:200], flush=True)
    for k, v in rec["sel"].items():
        print("     %-62s %s" % (k[:62], v), flush=True)
    print("   CAND: " + json.dumps(rec["cand"], ensure_ascii=False)[:900], flush=True)

json.dump(results, open(os.path.join(OUT, "faz1.json"), "w", encoding="utf8"), ensure_ascii=False, indent=1)
