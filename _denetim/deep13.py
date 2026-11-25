# -*- coding: utf-8 -*-
# Faz 6: son dogrulamalar
import json, os, re, collections
import requests, urllib3
from bs4 import BeautifulSoup
urllib3.disable_warnings()
OUT = os.path.dirname(os.path.abspath(__file__)); HD = os.path.join(OUT, "html")
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
      "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.8"}
def save(t, x): open(os.path.join(HD, t + ".html"), "w", encoding="utf8", errors="ignore").write(x)
def g(u, referer=None, timeout=45, hdr=None):
    h = dict(UA)
    if hdr: h.update(hdr)
    if referer: h["Referer"] = referer
    try:
        r = requests.get(u, headers=h, timeout=timeout, verify=False)
        return r.status_code, r.url, r.text
    except Exception as e:
        return None, u, "ERR " + type(e).__name__
def sel(tag, html, sels):
    s = BeautifulSoup(html, "html.parser")
    print("  --- %s (len %d) TITLE=%s" % (tag, len(html), ((s.title.string or "").strip()[:55] if s.title and s.title.string else "-")))
    for label, q in sels:
        try: n = len(s.select(q))
        except Exception: n = "ERR"
        print("     %-58s %s" % ((label + " || " + q)[:58], n))
    return s

print("### 1) CizgiMax arama: form input adi + /ara/ testi")
h = open(os.path.join(HD, "czm_diziler.html"), encoding="utf8", errors="ignore").read()
for m in re.finditer(r"<form id=\"search\".{0,400}?</form>", h, re.S):
    print("  form html:", re.sub(r"\s+", " ", m.group(0))[:300])
for u in ["https://cizgimax.online/ara/?q=kara", "https://cizgimax.online/ara/?qr=kara", "https://cizgimax.online/ara/?arama=kara"]:
    st, fu, hh = g(u)
    print("  GET %-28s -> %s len=%s" % (u.replace("https://cizgimax.online", ""), st, len(hh)))

print("\n### 2) DiziBox home kart yapisi")
h = open(os.path.join(HD, "dbx_home.html"), encoding="utf8", errors="ignore").read()
s = BeautifulSoup(h, "html.parser")
cnt = collections.Counter()
for a in s.find_all(["article", "div", "a", "li"]):
    cl = a.get("class")
    if cl: cnt[a.name + "." + ".".join(cl)] += 1
print("  CAND:", json.dumps({k: v for k, v in cnt.most_common(28) if v >= 4}, ensure_ascii=False)[:900])
for q in ["article.article-series-poster", "div.detailed-article", "article.detailed-article", "div.post-title",
          "article.grid-box", "div.grid-box", "a.poster-title"]:
    print("  %-34s %s" % (q, len(s.select(q))))
a0 = s.select_one("article.article-series-poster")
if a0: print("  article.article-series-poster[0]:\n", a0.prettify()[:700])

print("\n### 3) JetFilmizle player-sources blok")
h = open(os.path.join(HD, "jtf_detail.html"), encoding="utf8", errors="ignore").read()
i = h.find("player-sources")
blk = h[max(0, i - 300):i + 3000]
print("  ", re.sub(r"\s+", " ", blk)[:2600])
# iframe'in olusturuldugu yer: data-* URL iceren attr var mi
for m in re.finditer(r'data-[a-z\-]+=\"(https?://[^\"]{10,120})\"', h):
    print("  data-url:", m.group(1)[:110])

print("\n### 4) FullHDFilmizlesene zinciri (fhfz_r1 icerigi)")
h = open(os.path.join(HD, "fhfz_r1.html"), encoding="utf8", errors="ignore").read()
print("  ", re.sub(r"\s+", " ", h)[:800])
for m in re.finditer(r"(?:location\.href|location\.replace|window\.location)\s*=?\s*[\"']?([^\"';\s]{3,120})", h):
    print("  js redirect:", m.group(1)[:110])
for m in re.finditer(r"<meta[^>]+http-equiv[^>]+>", h):
    print("  meta:", m.group(0)[:150])

print("\n### 5) DMax arama (POST /ajax/search)")
try:
    r = requests.post("https://www.dmax.com.tr/ajax/search", headers={**UA, "X-Requested-With": "XMLHttpRequest",
                      "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8", "Referer": "https://www.dmax.com.tr/"},
                      data={"q": "kara"}, timeout=40, verify=False)
    print("  POST /ajax/search ->", r.status_code, len(r.text), r.headers.get("content-type", "")[:40])
    print("  body:", r.text[:200].replace("\n", " "))
    if r.status_code == 200:
        s = BeautifulSoup(r.text, "html.parser")
        print("  section.posters div.poster:", len(s.select("section.posters div.poster")), " div.poster:", len(s.select("div.poster")))
except Exception as e:
    print("  ERR", type(e).__name__, str(e)[:80])

print("\n### 6) Dizilla: episode regex + /dizi-turu/aile pcard")
h = open(os.path.join(HD, "dzl_detail.html"), encoding="utf8", errors="ignore").read()
print("  mafia ep regex:", len(re.findall(r"mafia-(\d+)-sezon-(\d+)-bolum", h)))
print("  genel ep regex:", len(re.findall(r"-[0-9]+-sezon-[0-9]+-bolum", h)))
st, fu, hh = g("https://dizilla.la/dizi-turu/aile")
save("dzl_aile", hh)
sel("dzl /dizi-turu/aile", hh, [("a.pcard", "a.pcard"), (".mvk-t", ".mvk-t"), (".pimg img", ".pimg img"), (".mvk-m", ".mvk-m")])

print("\n### 7) TvDiziler dizi sayfasi load selectorlari")
h = open(os.path.join(HD, "tvd_search.html"), encoding="utf8", errors="ignore").read()
s = BeautifulSoup(h, "html.parser")
lnk = [a.get("href") for a in s.find_all("a", href=True) if "/dizi/" in a.get("href")]
print("  dizi link:", list(dict.fromkeys(lnk))[:2])
if lnk:
    u = lnk[0] if lnk[0].startswith("http") else "https://tvdiziler.tv" + lnk[0]
    st, fu, hh = g(u, referer="https://tvdiziler.tv/")
    save("tvd_detail", hh)
    sel("tvd detail", hh, [("div.page-title h1", "div.page-title h1"), ("div.series-profile-image img", "div.series-profile-image img"),
                           ("div.series-profile-summary p", "div.series-profile-summary p"), ("div.series-profile-type a", "div.series-profile-type a"),
                           ("div.series-profile-cast li", "div.series-profile-cast li"), ("div.series-profile-episode-list li", "div.series-profile-episode-list li"),
                           ("h6.truncate a", "h6.truncate a"), ("li.series-alter-active button", "li.series-alter-active button")])

print("\n### 8) DDizi dizi sayfasi")
st, fu, hh = g("https://www.ddizi.tel/")
s = BeautifulSoup(hh, "html.parser")
lnk = [a.get("href") for a in s.find_all("a", href=True) if "/dizi" in (a.get("href") or "")]
print("  link:", list(dict.fromkeys(lnk))[:3])
if lnk:
    u = lnk[0] if lnk[0].startswith("http") else "https://www.ddizi.tel" + lnk[0]
    st, fu, hh = g(u, referer="https://www.ddizi.tel/")
    save("ddz_detail", hh)
    sel("ddizi detail", hh, [("div.afis img", "div.afis img"), ("img.afis", "img.afis"), ("img.img-back", "img.img-back"),
                             ("h1", "h1"), ("div.dizi-aciklama", "div.dizi-aciklama"),
                             ("div.bolumler a", "div.bolumler a"), ("div.sezonlar a", "div.sezonlar a"),
                             ("div.dizi-arsiv a", "div.dizi-arsiv a"), ("div.dizi-boxpost-cat a", "div.dizi-boxpost-cat a")])

print("\n### 9) BelgeselX detay (yeni tasarim)")
h = open(os.path.join(HD, "bgx_home.html"), encoding="utf8", errors="ignore").read()
s = BeautifulSoup(h, "html.parser")
lnk = [a.get("href") for a in s.select("a.px-card") if a.get("href")]
print("  link:", lnk[:2])
if lnk:
    u = lnk[0] if lnk[0].startswith("http") else "https://belgeselx.com" + lnk[0]
    st, fu, hh = g(u, referer="https://belgeselx.com/")
    save("bgx_detail", hh)
    sel("bgx detail", hh, [("h2.gen-title", "h2.gen-title"), ("div.gen-tv-show-top img", "div.gen-tv-show-top img"),
                           ("div.gen-single-tv-show-info p", "div.gen-single-tv-show-info p"), ("div.gen-movie-contain", "div.gen-movie-contain"),
                           ("h1", "h1"), ("img", "img"), ("iframe", "iframe")])

print("\n### 10) KultFilmler detay")
h = open(os.path.join(HD, "kult_home.html"), encoding="utf8", errors="ignore").read()
s = BeautifulSoup(h, "html.parser")
lnk = [a.get("href") for a in s.select("a.mcard") if a.get("href")]
print("  link:", lnk[:2])
if lnk:
    st, fu, hh = g(lnk[0], referer="https://kultfilmler.net/")
    save("kult_detail", hh)
    sel("kult detail", hh, [("div.film-bilgileri img", "div.film-bilgileri img"), ("[property='og:image']", "[property='og:image']"),
                            ("div.description", "div.description"), ("ul.post-categories a", "ul.post-categories a"),
                            ("li.release", "li.release"), ("li.time", "li.time"), ("div.actors a", "div.actors a"),
                            ("div.episode-box", "div.episode-box"), ("div.container#player", "div.container#player"),
                            ("iframe", "iframe")])

print("\n### 11) AsyaWatch api ipuclari")
h = open(os.path.join(HD, "asya_home.html"), encoding="utf8", errors="ignore").read()
for m in set(re.findall(r"[\"'](/api/[a-zA-Z0-9\-_/]{2,50})[\"']", h)):
    print("  api:", m)
print("  'searchcontent' var mi:", "searchcontent" in h, " 'findMovies':", "findMovies" in h)
