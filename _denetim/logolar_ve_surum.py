# -*- coding: utf-8 -*-
"""Logo düzeltmeleri, FullHDFilm'i down isaretle, etkilenen surumleri yukselt."""
from pathlib import Path
import re

kök = Path(__file__).resolve().parent.parent


def degistir(modul, eski, yeni, say=1):
    f = kök / modul / "build.gradle.kts"
    s = f.read_text(encoding="utf-8")
    if eski not in s:
        print(f"  UYARI {modul}: '{eski[:50]}' bulunamadi")
        return
    s = s.replace(eski, yeni, say)
    f.write_text(s, encoding="utf-8", newline="\n")
    print(f"  {modul}: ok")


degistir("AnimeciX", "https://anm.cx/favicon.ico", "https://www.google.com/s2/favicons?domain=animecix.tv&sz=%size%")
degistir("DiziMom", "domain=www.dizimom.plus", "domain=www.dizidad.com")
degistir("FullHDFilm", "domain=fullhdfilm.us", "domain=www.fullhdfilmizlesene.now")
degistir("FullHDFilm", "status  = 1", "status  = 0")  # site kapali: hdfilm.us

for modul in ("Dizilla", "DiziMom", "AnimeciX", "FullHDFilm"):
    f = kök / modul / "build.gradle.kts"
    s = f.read_text(encoding="utf-8")
    m = re.search(r"(version\s*=\s*)(\d+)", s)
    if m:
        yeni = str(int(m.group(2)) + 1)
        s = s[:m.start(2)] + yeni + s[m.end(2):]
        f.write_text(s, encoding="utf-8", newline="\n")
        print(f"  {modul} surum: {m.group(2)} -> {yeni}")
