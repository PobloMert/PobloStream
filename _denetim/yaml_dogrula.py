# -*- coding: utf-8 -*-
"""Derleyici.yml YAML ve icindeki Python heredoc'unu yerelce dogrula."""
import re
import pathlib

p = pathlib.Path(__file__).resolve().parent.parent / ".github/workflows/Derleyici.yml"
s = p.read_text(encoding="utf-8")

try:
    import yaml
    d = yaml.safe_load(s)
    adimlar = [x.get("name") for x in d["jobs"]["CloudStreamDerleyici"]["steps"]]
    print("YAML OK, adimlar:")
    for a in adimlar:
        print("  -", a)
except ImportError:
    print("PyYAML yok, YAML atlaniyor")

m = re.search(r"python3 -c '(.+?)' \"\$SHA\"", s, re.S)
if not m:
    print("Python heredoc BULUNAMADI")
else:
    kod = m.group(1)
    derl = compile(kod, "gomaltili.py", "exec")
    print("Python heredoc sozdizimi OK", len(kod), "karakter")
    # kuru kosim: gecici klasorde repo.json uret
    import json, os, tempfile
    with tempfile.TemporaryDirectory() as td:
        with open(os.path.join(td, "repo.json"), "w", encoding="utf-8") as f:
            json.dump({"name": "PobloStream", "pluginLists": ["eski"], "timestamp": 1, "lastUpdated": 1}, f)
        eski = os.getcwd()
        eski_argv = __import__("sys").argv
        __import__("sys").argv = ["test", "abc123456789"]
        os.chdir(td)
        try:
            exec(derl, {"__name__": "__main__"})
        finally:
            os.chdir(eski)
            __import__("sys").argv = eski_argv
        j = json.load(open(os.path.join(td, "repo.json"), encoding="utf-8"))
        print("kuru kosim pluginLists:", j["pluginLists"])
