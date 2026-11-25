# -*- coding: utf-8 -*-
# Faz 2f: olu domainler icin alternatif domain adaylarini DNS + HTTP + selector markup ile dogrula
import json, os, socket, sys, time
import requests, urllib3
from bs4 import BeautifulSoup
urllib3.disable_warnings()
OUT = os.path.dirname(os.path.abspath(__file__))
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.8"}

CAND = {
 "DiziMom":   ["dizimom.plus", "dizimom1.com", "dizimom2.com", "dizimom1.plus", "dizimom.net", "dizimom.org",
               "dizimomizle.com", "dizimom.tv", "dizimom.eu", "www.dizimom1.com"],
 "DiziPal":   ["dizipal1224.com", "dizipal2080.com", "dizipal2076.com", "dizipal2081.com", "dizipal1951.com",
               "dizipal1586.com", "dizipal1600.com", "dizipalizle.org", "dizipalcix.com", "dizipal.com", "dizipal.tv"],
 "TurkAnime": ["turkanime.co", "turkanime.tv", "turkanime.net", "turkanime.eu", "turkanime1.tv", "turkanime.com",
               "www.turkanime.co", "turkanime.org", "turkanimate.net"],
 "UgurFilm":  ["ugurfilm7.com", "ugurfilm.com", "ugurfilm6.com", "ugurfilm8.com", "ugurfilm9.com", "ugurfilm1.com",
               "ugurfilmizle.com", "ugur-film.com"],
 "SuperFilmGeldi": ["superfilmgeldi.info", "superfilmgeldi.com", "superfilmgeldi.net", "superfilmgeldi.org",
                    "superfilmgeldi1.com", "superfilmgeldiizle.com", "superfilmgeldi.vip", "superfilmgeldi.xyz"],
 "FullHDFilm": ["hdfilm.us", "hdfilm.tv", "hdfilm.net", "hdfilmizle.us", "hdfilmizle.net", "fullhdfilm.us",
                "hdfilm.co", "hdfilmizle.org"],
 "HDFilmCehennemi": ["hdfilmcehennemi.nl", "hdfilmcehennemi.eu", "hdfilmcehennemi.me", "hdfilmcehennemi.best",
                     "hdfilmcehennemi.vip", "hdfilmcehennemi2.nl", "hdfilmcehennemi.biz", "hdfilmcehennemi.cyou",
                     "www.hdfilmcehennemi.nl"],
}
MARKERS = ["search-autocomplete", "episode-item", 'class="pcard', "film-card-wrapper", "dizi-", "bolum", "poster"]

def probe(dom, path="/"):
    try:
        ip = socket.getaddrinfo(dom, 443)[0][4][0]
    except Exception as e:
        return {"dns": "NXDOMAIN/FAIL"}
    out = {"dns": ip}
    try:
        r = requests.get("https://" + dom + path, headers=UA, timeout=20, verify=False)
        out["status"] = r.status_code
        out["final"] = r.url[:90]
        out["len"] = len(r.text)
        t = r.text
        out["markers"] = [m for m in MARKERS if m in t]
        soup = BeautifulSoup(t, "html.parser")
        out["title"] = ((soup.title.string or "").strip()[:60] if soup.title and soup.title.string else "-")
        out["n_a"] = len(t.split("<a "))
    except Exception as e:
        out["err"] = type(e).__name__ + ":" + str(e)[:70]
    return out

res = {}
for plugin, doms in CAND.items():
    print("==== " + plugin, flush=True)
    res[plugin] = {}
    for d in doms:
        p = probe(d)
        res[plugin][d] = p
        print("   %-24s %s" % (d, json.dumps(p, ensure_ascii=False)[:230]), flush=True)
        time.sleep(0.3)
json.dump(res, open(os.path.join(OUT, "alt_domains.json"), "w", encoding="utf8"), ensure_ascii=False, indent=1)
