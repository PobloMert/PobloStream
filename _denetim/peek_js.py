import re, os
p = os.path.join(os.path.dirname(os.path.abspath(__file__)), "html")
js = open(os.path.join(p, "jtf_filmpage.js"), encoding="utf8", errors="ignore").read()
print("### fetch(上下文 (JetFilmizle film-page.min.js)")
for m in re.finditer(r"fetch\(", js):
    s = max(0, m.start() - 120); e = min(len(js), m.end() + 260)
    frag = js[s:e].replace("\n", " ")
    if "http" in frag or "/" in frag[:200]:
        print(" ...", frag[:360], "\n")
print("### 'url' ile geçen stringler")
for m in set(re.findall(r"[\"'`](/[a-zA-Z0-9\-_/{}.]{2,60})[\"'`]", js)):
    print("  ", m)
