# -*- coding: utf-8 -*-
# Faz2e: DoH ile DNS dogrulamasi (yerel mi blokli, yoksa domain mi olu) + DiziPal/DiziMom alternatif domain probe
import json, os, time
import requests, urllib3
urllib3.disable_warnings()
OUT = os.path.dirname(os.path.abspath(__file__))
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"}

def doh(name):
    for srv in ["https://cloudflare-dns.com/dns-query", "https://dns.google/resolve"]:
        try:
            r = requests.get(srv, params={"name": name, "type": "A"},
                             headers={"accept": "application/dns-json", "User-Agent": UA["User-Agent"]}, timeout=15)
            j = r.json()
            ans = [a["data"] for a in j.get("Answer", []) if a.get("type") == 1]
            return j.get("Status"), ans, srv.split("/")[2]
        except Exception as e:
            last = type(e).__name__
    return "ERR:" + last, [], "-"

DOMS = {
 "DiziMom": ["www.dizimom.plus", "dizimom.plus", "www.dizimom.com", "dizimom1.com", "dizimom2.com",
             "dizimom.io", "dizimom.tv", "dizimom.net", "dizimom.app", "www.dizimom1.plus"],
 "DiziPal": ["dizipal1224.com", "dizipal.com", "dizipal.tv", "dizipal0341.com", "dizipal.app", "dizipal1.com"],
 "TurkAnime": ["www.turkanime.co", "turkanime.co", "www.turkanime.tv", "www.turkanime.net", "turkanime.tv",
               "www.turkanime.eu", "www.turkanime1.co", "turkanime.com"],
 "UgurFilm": ["ugurfilm7.com", "ugurfilm.com", "ugurfilm6.com", "ugurfilm8.com", "ugurfilm9.com", "www.ugurfilm7.com"],
 "SuperFilmGeldi": ["superfilmgeldi.info", "superfilmgeldi.com", "superfilmgeldi.net", "superfilmgeldi.vip",
                    "superfilmgeldi.xyz", "www.superfilmgeldi.info"],
 "FullHDFilm": ["hdfilm.us", "hdfilm.com", "www.hdfilm.us", "hdfilmizle.us"],
 "HDFilmCehennemi": ["www.hdfilmcehennemi.nl", "www.hdfilmcehennemi.eu", "www.hdfilmcehennemi.me",
                     "www.hdfilmcehennemi.best", "www.hdfilmcehennemi.vip", "www.hdfilmcehennemi2.nl"],
 "Dizilla": ["dizilla.la", "dizilla.com", "dizilla.tv"],
}
out = {}
for plugin, doms in DOMS.items():
    print("==== " + plugin, flush=True)
    out[plugin] = {}
    for d in doms:
        st, ips, srv = doh(d)
        ok = False; stat = None
        if ips:
            for scheme in ("https",):
                try:
                    r = requests.get(scheme + "://" + d + "/", headers=UA, timeout=15, verify=False, allow_redirects=True)
                    stat = str(r.status_code) + " " + r.url[:60] + " len=" + str(len(r.text)); ok = True
                    break
                except Exception as e:
                    stat = type(e).__name__
                time.sleep(0.3)
        else:
            stat = "-"
        print("   %-26s doh_status=%s ips=%s(%s) http=%s" % (d, st, ",".join(ips)[:34], srv, stat[:95]), flush=True)
        out[plugin][d] = {"doh_status": st, "ips": ips, "http": stat, "alive": ok}
json.dump(out, open(os.path.join(OUT, "doh.json"), "w", encoding="utf8"), ensure_ascii=False, indent=1)
