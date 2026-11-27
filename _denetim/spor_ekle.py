# -*- coding: utf-8 -*-
"""WioSpor (canli spor kanallari) eklentilerini PobloStream deposuna aktarir.

Yapilanlar:
  1. WioSpor plugins.json indirilir.
  2. Her .cs3 indirilir, sha256 dogrulanir, builds/spor/ altina kopyalanir.
  3. Ikonlar builds/spor/icons/ altina aynalanir (404 ise depo logosu kullanilir).
  4. builds/spor_plugins.json uretilir (URL'ler kendi depomuza bakar, status=1).
  5. builds/repo.json ve kok repo.json icindeki pluginLists iki liste olacak sekilde yazilir.

Kullanim:  python -X utf8 _denetim/spor_ekle.py <builds_klon_klasoru>
"""
import hashlib
import json
import re
import sys
from pathlib import Path

import requests

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
KAYNAK = "https://raw.githubusercontent.com/Wiojelt/WioSpor/main/plugins.json"
RAW = "https://raw.githubusercontent.com/PobloMert/PobloStream/builds"
SPOR_LIST = f"{RAW}/spor_plugins.json"
YEDEK_IKON = "https://raw.githubusercontent.com/PobloMert/PobloStream/master/assets/logo.png"
PROJE = Path(__file__).resolve().parent.parent


def guvenli(ad: str) -> str:
    ad = re.sub(r"[^A-Za-z0-9_.-]+", "_", ad.strip())
    return ad.strip("_") or "eklenti"


def indir(url: str, timeout: int = 60):
    return requests.get(url, headers=UA, timeout=timeout)


def main() -> int:
    if len(sys.argv) < 2:
        print("kullanim: spor_ekle.py <builds_klon_klasoru>")
        return 2
    builds = Path(sys.argv[1]).resolve()
    if not (builds / ".git").exists():
        print(f"HATA: {builds} bir git klonu degil")
        return 2

    spor_dir = builds / "spor"
    ikon_dir = spor_dir / "icons"
    ikon_dir.mkdir(parents=True, exist_ok=True)

    kaynak = indir(KAYNAK).json()
    print(f"kaynak eklenti: {len(kaynak)}")

    yeni = []
    for e in kaynak:
        ad = re.sub(r"^\d+\s+", "", e["name"]).strip()   # "0 WioSpor" -> "WioSpor"
        ic = guvenli(e.get("internalName") or ad)
        dosya = f"{ic}.cs3"
        try:
            r = indir(e["url"])
        except Exception as ex:
            print(f"  HATA indirme {ad}: {type(ex).__name__}")
            return 1
        h = "sha256-" + hashlib.sha256(r.content).hexdigest()
        if r.status_code != 200 or h != e["fileHash"]:
            print(f"  HATA dogrulama {ad}: HTTP {r.status_code} hash_esles={h == e['fileHash']}")
            return 1
        (spor_dir / dosya).write_bytes(r.content)

        # ikon aynala
        ikon_adi = f"{ic}.png"
        ikon_url = YEDEK_IKON
        try:
            ir = indir(e.get("iconUrl", ""), timeout=40)
            if ir.status_code == 200 and len(ir.content) > 500 and len(ir.content) < 3_000_000:
                (ikon_dir / ikon_adi).write_bytes(ir.content)
                ikon_url = f"{RAW}/spor/icons/{ikon_adi}"
            else:
                print(f"  ikon yok ({ad}): HTTP {ir.status_code} -> depo logosu")
        except Exception:
            print(f"  ikon yok ({ad}): indirilemedi -> depo logosu")

        yeni.append({
            "name": ad,
            "internalName": e.get("internalName") or ad,
            "version": e["version"],
            "description": e.get("description", ""),
            "authors": e.get("authors", ["Wiojelt"]),
            "repositoryUrl": "https://github.com/Wiojelt/WioSpor",
            "url": f"{RAW}/spor/{dosya}",
            "iconUrl": ikon_url,
            "tvTypes": e.get("tvTypes", ["Live"]),
            "language": e.get("language", "tr"),
            # ! kaynakta 28 eklenti status=3 (beta) idi; normal listede gorunmeleri icin 1
            "status": 1,
            "fileSize": len(r.content),
            "fileHash": h,
            "apiVersion": e.get("apiVersion", 1),
        })
        print(f"  OK {ad:<20} v{e['version']:<4} {len(r.content):>7} bayt")

    (builds / "spor_plugins.json").write_text(
        json.dumps(yeni, ensure_ascii=False, indent=2), encoding="utf-8", newline="\n"
    )

    # repo.json: iki plugin listesi (kendi pinli manifestimiz + spor listesi)
    for yol in [builds / "repo.json", PROJE / "repo.json"]:
        j = json.loads(yol.read_text(encoding="utf-8-sig"))
        mevcut = [u for u in j.get("pluginLists", []) if "spor_plugins.json" not in u]
        if not mevcut:
            mevcut = [f"https://cdn.jsdelivr.net/gh/PobloMert/PobloStream@PIN/plugins.json?t=PIN"]
        j["pluginLists"] = mevcut[:1] + [SPOR_LIST]
        yol.write_text(json.dumps(j, ensure_ascii=False, indent=2), encoding="utf-8", newline="\n")

    print(f"\nspor eklenti: {len(yeni)} | builds: {builds}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
