#!/usr/bin/env python3
"""
ENGEL_KONTROL.py — eklenti sitelerini Türkiye'deki erişim engelinden etkilenmeden denetler.

Yerel denetim betikleri engelli bir siteyi "ölü" sanıyordu (FullHDFilm bu yüzden yanlışlıkla
kapatıldı). Bu betik her siteyi üç yoldan dener ve sonucu ayırır:

  CALISIYOR   : normal bağlantı (sistem DNS'i, düz TLS) açılıyor
  ENGELLI     : normal yol kapalı ama EngelAsici yöntemiyle (DoH + bölünmüş ClientHello) açılıyor
                -> site yayında; eklenti EngelAsici.http kullandığı için kullanıcıda çalışır
  OLU         : alan adı hiçbir yerde yok (DoH: NXDOMAIN)
  ERISILEMIYOR: alan adı var ama iki yolla da yanıt yok (sunucu kapalı / başka bir engel)

Kullanım:  python ENGEL_KONTROL.py            (tüm eklentiler)
           python ENGEL_KONTROL.py FullHDFilm (tek eklenti)
Yalnızca Python standart kütüphanesi kullanır.
"""
import json
import pathlib
import re
import socket
import ssl
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

KOK = pathlib.Path(__file__).resolve().parent
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0 Safari/537.36"
DOH = ["https://1.1.1.1/dns-query?type=A&name=", "https://8.8.8.8/resolve?type=A&name="]
ZAMAN = 10


def eklenti_adresleri(filtre=None):
    """Her eklentinin ana sınıfındaki mainUrl'i bulur (eklenti klasörü = build.gradle.kts olan klasör)."""
    sonuc = {}
    for d in sorted(KOK.iterdir()):
        if not (d / "build.gradle.kts").exists() or d.name.startswith("_"):
            continue
        if filtre and d.name.lower() != filtre.lower():
            continue
        dosyalar = sorted((d / "src").rglob("*.kt"), key=lambda p: (p.stem.lower() != d.name.lower(), str(p)))
        for kt in dosyalar:
            m = re.search(r'override\s+va[rl]\s+mainUrl\s*=\s*"(https?://[^"/]+)', kt.read_text(encoding="utf-8", errors="replace"))
            if m:
                sonuc[d.name] = m.group(1)
                break
    return sonuc


def doh(host):
    """(durum, ip_listesi): durum 'ok' | 'nxdomain' | 'hata'."""
    for base in DOH:
        try:
            req = urllib.request.Request(base + host, headers={"accept": "application/dns-json"})
            with urllib.request.urlopen(req, timeout=ZAMAN) as r:
                j = json.load(r)
            if j.get("Status") == 3:
                return "nxdomain", []
            ips = [a["data"] for a in j.get("Answer", []) if a.get("type") == 1]
            if ips:
                return "ok", ips
        except Exception:
            continue
    return "hata", []


def normal_istek(url):
    """Sistem DNS'i ve düz TLS ile istek: (http_kodu | None, hata)."""
    try:
        req = urllib.request.Request(url + "/", headers={"User-Agent": UA})
        with urllib.request.urlopen(req, timeout=ZAMAN) as r:
            return r.status, None
    except urllib.error.HTTPError as e:
        return e.code, None  # 403/503 de "sunucu yanıt verdi" demektir
    except Exception as e:
        neden = getattr(e, "reason", e)
        return None, f"{type(e).__name__}({type(neden).__name__})"


def _sni_ortasi(kayit):
    """ClientHello kaydı içinde server_name değerinin ortasının payload ofseti."""
    p = kayit[5:]
    try:
        if p[0] != 1:
            return None
        i = 4 + 2 + 32
        i += 1 + p[i]
        i += 2 + int.from_bytes(p[i:i + 2], "big")
        i += 1 + p[i]
        son = i + 2 + int.from_bytes(p[i:i + 2], "big")
        i += 2
        while i + 4 <= son:
            tip = int.from_bytes(p[i:i + 2], "big")
            uz = int.from_bytes(p[i + 2:i + 4], "big")
            if tip == 0:
                ad_uz = int.from_bytes(p[i + 7:i + 9], "big")
                return i + 9 + ad_uz // 2
            i += 4 + uz
    except IndexError:
        return None
    return None


def bolunmus_istek(host, ip):
    """EngelAsici ile aynı yöntem: IP'ye bağlan, ClientHello'yu SNI ortasından iki TLS kaydına böl."""
    ctx = ssl.create_default_context()
    gelen, giden = ssl.MemoryBIO(), ssl.MemoryBIO()
    tls = ctx.wrap_bio(gelen, giden, server_hostname=host)
    s = socket.create_connection((ip, 443), timeout=ZAMAN)
    try:
        ilk = True
        while True:
            try:
                tls.do_handshake()
                break
            except ssl.SSLWantReadError:
                veri = giden.read()
                if ilk and veri and veri[0] == 0x16:
                    ilk = False
                    uz = int.from_bytes(veri[3:5], "big")
                    kayit, kalan = veri[:5 + uz], veri[5 + uz:]
                    kes = _sni_ortasi(kayit) or 1
                    pl = kayit[5:]
                    veri = (kayit[:3] + kes.to_bytes(2, "big") + pl[:kes]
                            + kayit[:3] + (uz - kes).to_bytes(2, "big") + pl[kes:] + kalan)
                if veri:
                    s.sendall(veri)
                al = s.recv(65536)
                if not al:
                    raise ConnectionError("sunucu bağlantıyı kapattı")
                gelen.write(al)
        tls.write(f"GET / HTTP/1.1\r\nHost: {host}\r\nUser-Agent: {UA}\r\nConnection: close\r\n\r\n".encode())
        s.sendall(giden.read())
        yanit = b""
        while b"\r\n" not in yanit:
            try:
                yanit += tls.read(4096)
            except ssl.SSLWantReadError:
                al = s.recv(65536)
                if not al:
                    break
                gelen.write(al)
        m = re.match(rb"HTTP/\d\.?\d? (\d{3})", yanit)
        return (int(m.group(1)) if m else None), None
    except Exception as e:
        return None, type(e).__name__
    finally:
        s.close()


def denetle(ad, url):
    host = url.split("://", 1)[1]
    kod, hata = normal_istek(url)
    if kod is None:  # anlık ağ hatalarını engel sanmamak için bir kez daha dene
        kod, hata = normal_istek(url)
    if kod is not None:
        return ad, host, "CALISIYOR", f"HTTP {kod}"
    durum, ips = doh(host)
    if durum == "nxdomain":
        return ad, host, "OLU", "alan adı yok (NXDOMAIN)"
    if not ips:
        return ad, host, "ERISILEMIYOR", f"normal: {hata}; DoH yanıt vermedi"
    kod2, hata2 = bolunmus_istek(host, ips[0])
    if kod2 is not None:
        return ad, host, "ENGELLI", f"normal: {hata}; EngelAsici: HTTP {kod2}"
    return ad, host, "ERISILEMIYOR", f"normal: {hata}; EngelAsici: {hata2}"


def main():
    hedefler = eklenti_adresleri(sys.argv[1] if len(sys.argv) > 1 else None)
    if not hedefler:
        sys.exit("Eklenti bulunamadı.")
    with ThreadPoolExecutor(max_workers=8) as ex:
        sonuclar = list(ex.map(lambda kv: denetle(*kv), hedefler.items()))
    for ad, host, durum, detay in sonuclar:
        print(f"{ad:<20} {host:<30} {durum:<13} {detay}")
    sorunlu = [r for r in sonuclar if r[2] in ("OLU", "ERISILEMIYOR")]
    print(f"\n{len(sonuclar)} eklenti: " + ", ".join(
        f"{d}={sum(1 for r in sonuclar if r[2] == d)}" for d in ("CALISIYOR", "ENGELLI", "OLU", "ERISILEMIYOR")))
    sys.exit(1 if sorunlu else 0)


if __name__ == "__main__":
    main()
