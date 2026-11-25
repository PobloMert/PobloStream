# -*- coding: utf-8 -*-
"""4 eklentinin surumunu yukselt (CloudStream yeni kodu ceksin)."""
from pathlib import Path
import re

kök = Path(__file__).resolve().parent.parent
for modul in ("CizgiMax", "BelgeselX", "Ddizi", "HDFilmCehennemi"):
    f = kök / modul / "build.gradle.kts"
    s = f.read_text(encoding="utf-8")
    m = re.search(r"(version\s*=\s*)(\d+)", s)
    yeni = str(int(m.group(2)) + 1)
    f.write_text(s[:m.start(2)] + yeni + s[m.end(2):], encoding="utf-8", newline="\n")
    print(f"  {modul}: {m.group(2)} -> {yeni}")
