# -*- coding: utf-8 -*-
"""DiziPal(.rest) ve CizgiMax(.online) icin arama endpoint'i ara."""
import re

import requests

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"}


def bul(ad, url):
    print(f"\n########## {ad} {url}")
    try:
        r = requests.get(url, headers=UA, timeout=25)
        html = r.text
        print(f"HTTP {r.status_code} {len(html)}B")
        # form action
        for m in re.findall(r"<form[^>]*>", html, re.I)[:6]:
            print("  FORM:", m[:200])
        # api / ajax yollari
        yollar = set(re.findall(r"['\"](/(?:api|ajax|arama|search|dizi|action)[^'\"]{0,60})['\"]", html))
        for y in sorted(yollar)[:25]:
            print("  YOL:", y)
        # fetch/axios cagrilari
        for m in list(set(re.findall(r"(?:fetch|axios\.(?:get|post)|\\\$\.post|\\\$\.get)\(\s*[\"'`]([^\"'`]{3,80})", html)))[:15]:
            print("  FETCH:", m)
        js = re.findall(r'<script[^>]*src="([^"]+)"', html)
        print("  JS:", [j for j in js if "google" not in j and "gtag" not in j][:8])
        return html, js
    except Exception as e:
        print("  HATA", type(e).__name__, str(e)[:70])
        return "", []


if __name__ == "__main__":
    h1, js1 = bul("DiziPal", "https://dizipal1.rest/")
    # ana JS dosyasinda arama endpoint'i ara
    for src in js1[:6]:
        u = src if src.startswith("http") else "https://dizipal1.rest" + (src if src.startswith("/") else "/" + src)
        try:
            j = requests.get(u, headers=UA, timeout=20).text
            for m in set(re.findall(r"[\"'`](/[a-z0-9\-_/]{2,50}(?:search|arama|autocomplete)[a-z0-9\-_/]{0,30})[\"'`]", j, re.I)):
                print(f"    JS-ENDPOINT {u.split('/')[-1]}: {m}")
        except Exception:
            pass

    h2, js2 = bul("CizgiMax", "https://cizgimax.online/")
    for src in js2[:6]:
        u = src if src.startswith("http") else "https://cizgimax.online" + (src if src.startswith("/") else "/" + src)
        try:
            j = requests.get(u, headers=UA, timeout=20).text
            for m in set(re.findall(r"[\"'`](/[a-z0-9\-_/]{2,50}(?:search|arama|ajax)[a-z0-9\-_./]{0,30})[\"'`]", j, re.I)):
                print(f"    JS-ENDPOINT {u.split('/')[-1]}: {m}")
        except Exception:
            pass
