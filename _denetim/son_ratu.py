# -*- coding: utf-8 -*-
"""DiziPal ikonunu guncel domaine cevir, repo.json onbellek damgasini yenile."""
import re
import time
from pathlib import Path

kök = Path(__file__).resolve().parent.parent
t = int(time.time())

f = kök / "DiziPal" / "build.gradle.kts"
s = f.read_text(encoding="utf-8")
s2 = s.replace("dizipal1224.com", "dizipal1.rest")
f.write_text(s2, encoding="utf-8", newline="\n")
print("DiziPal iconUrl:", "dizipal1.rest" in s2)

r = kök / "repo.json"
s = r.read_text(encoding="utf-8")
s2 = re.sub(r"\b1795629456\b", str(t), s)
r.write_text(s2, encoding="utf-8", newline="\n")
print("repo.json t:", t, "| eski damga kalmadi:", "1795629456" not in s2)
print(s2[:500])
