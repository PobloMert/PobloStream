# -*- coding: utf-8 -*-
import re, time, requests
REPO = "PobloMert/PobloStream"; UA={"User-Agent":"Mozilla/5.0"}; ESKI="abc2d5641a66"
for i in range(1,26):
    try:
        repo=requests.get(f"https://raw.githubusercontent.com/{REPO}/builds/repo.json?t={int(time.time())}",headers=UA,timeout=25).json()
        u=repo["pluginLists"][0]; sha=re.search(r"@([0-9a-f]{12})",u).group(1)
        print(f"[{i:02d}] {sha}")
        if sha!=ESKI:
            j=requests.get(u,headers=UA,timeout=25).json()
            d=next(e for e in j if e["name"]=="DiziMom")
            print("DiziMom",d["version"],d["fileHash"],d["fileSize"])
            print("SONUC:", "TAMAM" if d["version"]==40 else "KONTROL ET")
            break
    except Exception as e: print(f"[{i:02d}] {type(e).__name__}")
    time.sleep(20)
else: print("8 dk icinde degismedi")
