# -*- coding: utf-8 -*-
"""Bozuk iki sitenin gercek kart yapisini bul."""
import collections
import re

import requests

HDR = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"}


def class_say(html, etiket="div"):
    """En sik tekrarlayan class kombinasyonlarini say."""
    pat = re.compile(r"<%s[^>]*class=\"([^\"]+)\"" % etiket)
    c = collections.Counter(pat.findall(html))
    return c.most_common(12)


def main():
    for ad, url, etiket in [
        ("DiziMom-kategori", "https://www.dizidad.com/yerli-dizi-izle/page/1", "div"),
        ("CizgiMax", "https://cizgimax.online", "div"),
        ("CizgiMax-li", "https://cizgimax.online", "li"),
        ("DiziPal", "https://dizipal1.rest/diziler/son-bolumler", "div"),
        ("DiziPal-li", "https://dizipal1.rest/diziler/son-bolumler", "li"),
    ]:
        print(f"\n=== {ad}  {url}")
        try:
            r = requests.get(url, headers=HDR, timeout=25)
            print(f"   HTTP {r.status_code} {len(r.content)}B son={r.url}")
            for cls, n in class_say(r.text, "li" if etiket == "li" else "div"):
                if n >= 3:
                    print(f"   {n:>4}x  <{etiket} class=\"{cls[:70]}\">")
        except Exception as e:
            print(f"   HATA {type(e).__name__}: {str(e)[:60]}")


if __name__ == "__main__":
    main()
