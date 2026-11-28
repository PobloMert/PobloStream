"""spor_tv_kanallar.json'u SporTVKanallar.kt içine gömer (uzak liste okunamazsa kullanılan kopya)."""
import json, pathlib
k = pathlib.Path(__file__).resolve().parent
j = (k / "spor_tv_kanallar.json").read_text(encoding="utf-8")
json.loads(j)  # geçerli JSON mu
assert '"""' not in j and "$" not in j, "JSON içinde \"\"\" ya da $ olamaz"
(k / "src/main/kotlin/com/keyiflerolsun/SporTVKanallar.kt").write_text(
    "package com.keyiflerolsun\n\n"
    "// ! Bu dosya spor_tv_kanallar.json'un gömülü kopyasıdır (uzak liste okunamazsa kullanılır).\n"
    "// ! JSON'u değiştirince bunu da güncelleyin:  python SporTV/kanallari_gom.py\n"
    "object SporTVKanallar {\n    const val JSON = \"\"\"\n" + j.rstrip() + "\n\"\"\"\n}\n",
    encoding="utf-8", newline="\n")
print("SporTVKanallar.kt güncellendi")
