# -*- coding: utf-8 -*-
"""DiziMom: peacemakerst twimg Referer duzeltmesi surumu."""
from pathlib import Path
import re

f = Path(__file__).resolve().parent.parent / "DiziMom" / "build.gradle.kts"
s = f.read_text(encoding="utf-8")
m = re.search(r"(version\s*=\s*)(\d+)", s)
yeni = str(int(m.group(2)) + 1)
f.write_text(s[:m.start(2)] + yeni + s[m.end(2):], encoding="utf-8", newline="\n")
print(f"DiziMom {m.group(2)} -> {yeni}")
