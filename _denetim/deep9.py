# -*- coding: utf-8 -*-
# Faz 2h: JetFilmizle player JS, CizgiMax, FullHDFilmizlesene, TvDiziler arama, UgurFilm redirect, BelgeselX
import json, os, re, collections
import requests, urllib3
from bs4 import BeautifulSoup
urllib3.disable_warnings()
OUT = os.path.dirname(os.path.abspath(__file__)); HD = os.path.join(OUT, "html")
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
      "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.8"}
def save(t, x): open(os.path.join(HD, t + ".html"), "w", encoding="utf8", errors="ignore").write(x)
def g(u, **kw):
    try:
        r = requests.get(u, headers=dict(UA, **kw), timeout=45, verify=False)
        return r.status_code, r.url, r.text
    except Exception as e:
        return None, u, "ERR " + type(e).__name__ + " " + str(e)[:70]

print("################ 1) JetFilmizle player JS (film-page.min.js)")
st, fu, js = g("https://jetfilmizle.app/assets/js/film-page.min.js")
print("  status", st, "len", len(js))
if js and not js.startswith("ERR"):
    open(os.path.join(HD, "jtf_filmpage.js"), "w", encoding="utf8", errors="ignore").write(js)
    for kw in ["/player", "player/", "data-source", "source-index", "fetch(", "/ajax", "iframe", "src="]:
        n = len(re.findall(re.escape(kw), js))
        print("     kw %-16s %s" % (kw, n))
    for m in re.finditer(r"[\"']([^\"']*(?:player|source|ajax|iframe)[^\"']*)[\"']", js):
        s = m.group(1)
        if len(s) < 90: print("     str:", s)

print("\n################ 2) CizgiMax")
for u in ["https://cizgimax.online/", "https://cizgimax.online/diziler", "https://cizgimax.online/diziler/page/1"]:
    st, fu, h = g(u)
    print("  %-46s -> %s len=%s final=%s" % (u, st, len(h), fu[:60]))
    if st == 200 and "diziler" in u:
        save("czm_diziler", h)
s = BeautifulSoup(open(os.path.join(HD, "czm_diziler.html"), encoding="utf8", errors="ignore").read(), "html.parser") if os.path.exists(os.path.join(HD, "czm_diziler.html")) else None
if s:
    for sel in ["ul.filter-results li", "ul.filter-results", "div.poster-subject a", "div.poster-media img", "a.az-btn", "div.item", "li.item"]:
        print("     %-32s %s" % (sel, len(s.select(sel))))
    cnt = collections.Counter()
    for a in s.find_all(["a", "div", "li", "article"]):
        cl = a.get("class")
        if cl: cnt[a.name + "." + ".".join(cl)] += 1
    print("     CAND:", json.dumps({k: v for k, v in cnt.most_common(25) if v >= 4}, ensure_ascii=False)[:700])

print("\n################ 3) FullHDFilmizlesene (5667 byte sayfa ne?)")
st, fu, h = g("https://www.fullhdfilmizlesene.tv/en-cok-izlenen-filmler-izle-hd/")
save("fhfz_home", h)
print("  status", st, len(h)); print("  içerik:", re.sub(r"\s+", " ", h)[:700])

print("\n################ 4) TvDiziler arama alternatifleri")
for u in ["https://tvdiziler.tv/arama/kara", "https://tvdiziler.tv/ara?q=kara", "https://tvdiziler.tv/search?q=kara",
          "https://tvdiziler.tv/arama?q=kara", "https://tvdiziler.tv/?s=kara"]:
    st, fu, h = g(u)
    print("  %-40s -> %s len=%s final=%s" % (u.replace("https://tvdiziler.tv", ""), st, len(h), fu[:50]))
    if st == 200 and len(h) > 20000:
        s2 = BeautifulSoup(h, "html.parser")
        print("       div.poster-xs=%s div.poster-md=%s a[href*='/dizi/']=%s" % (
            len(s2.select("div.poster-xs")), len(s2.select("div.poster-md")), len(s2.select("a[href*='/dizi/']"))))
        save("tvd_search", h)

print("\n################ 5) UgurFilm redirect hedefi (ugurfilm8.com)")
st, fu, h = g("https://ugurfilm8.com/")
m = re.findall(r"(?:location\.(?:replace|href)\s*=\s*|window\.location\s*=\s*)[\"']([^\"']+)[\"']", h)
print("  redirect adaylari:", m[:3])
mm = re.findall(r"atob\(([^)]{0,120})\)", h)
print("  atob:", mm[:3])
base = re.findall(r"[\"'](/[a-z0-9\-/]{3,60})[\"']", h)
print("  path parcalari:", list(dict.fromkeys(base))[:8])

print("\n################ 6) BelgeselX gerçek kart yapısı (a.px-card)")
st, fu, h = g("https://belgeselx.com/konu/turk-tarihi-belgeselleri&page=1")
save("bgx_home", h)
s3 = BeautifulSoup(h, "html.parser")
for sel in ["a.px-card", "div.gen-movie-contain", "div.px-card-title", "div.px-card-poster img", "img"]:
    print("     %-30s %s" % (sel, len(s3.select(sel))))
c0 = s3.select_one("a.px-card")
if c0: print("  a.px-card[0]:\n", c0.prettify()[:900])

print("\n################ 7) DiziBox arama (uzun timeout, cookies)")
s4 = requests.Session(); s4.headers.update(UA)
for k, v in [("LockUser", "true"), ("isTrustedUser", "true")]: s4.cookies.set(k, v, domain="www.dizibox.live")
try:
    r = s4.get("https://www.dizibox.live/?s=kara", timeout=120, verify=False)
    print("  status", r.status_code, len(r.text), r.url)
    save("dbx_search3", r.text)
    bs = BeautifulSoup(r.text, "html.parser")
    for sel in ["article.detailed-article", "article.article-series-poster", "a.poster-title"]:
        print("     %-32s %s" % (sel, len(bs.select(sel))))
except Exception as e:
    print("  ERR", type(e).__name__, str(e)[:100])
