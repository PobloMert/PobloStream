# -*- coding: utf-8 -*-
import re
from pathlib import Path
f = Path(__file__).resolve().parent.parent / "HDFilmCehennemi" / "build.gradle.kts"
s = f.read_text(encoding="utf-8")
m = re.search(r"(version\s*=\s*)(\d+)", s)
y = str(int(m.group(2)) + 1)
f.write_text(s[:m.start(2)] + y + s[m.end(2):], encoding="utf-8", newline="\n")
print("HDFilmCehennemi", m.group(2), "->", y)
