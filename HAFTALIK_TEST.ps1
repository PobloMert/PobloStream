# Eklenti testini TÜRKİYE'DEN (bu bilgisayardan) çalıştırır ve eklenti durumlarını günceller.
# GitHub'daki haftalık test yurt dışından çalıştığı için yalnızca rapor verir; Türk siteleri yurt
# dışından boş sayfa döndürdüğünden durum (status) değişikliği burada yapılır.
#
#   powershell -ExecutionPolicy Bypass -File HAFTALIK_TEST.ps1
#
# Ne yapar:
#  1. Depoyu ASCII bir geçici klasöre kopyalar (yolda "ü" olunca Gradle testi sınıfı bulamıyor).
#  2. Tüm eklentileri gerçek sitelerde dener (ana sayfa, detay, arama, canlı kanallar).
#  3. EKLENTI_TESTI.py --durum: bozulan eklentiyi status = 0, düzeleni 1 yapar (bu depoda).
#  4. Raporu _rapor\eklenti_testi_YYYYAAGG.md olarak yazar.
# Commit/push YAPMAZ: değişiklikleri (git diff) gözden geçirip kendin gönderirsin.
$ErrorActionPreference = 'Stop'
$depo = $PSScriptRoot
$kopya = Join-Path $env:TEMP 'poblostream_test'
$tarih = Get-Date -Format 'yyyyMMdd'
$raporKlasor = Join-Path $depo '_rapor'
New-Item -ItemType Directory -Force $raporKlasor | Out-Null

# Gradle 8.14 Java 25 ile çalışmaz: JAVA_HOME başka bir JDK'yı gösterse bile her zaman JDK 21 kullan
$jdk = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jdk) { throw 'JDK 21 bulunamadı (C:\Program Files\Eclipse Adoptium\jdk-21*). Gradle 8.14 Java 25 ile çalışmaz.' }
$env:JAVA_HOME = $jdk.FullName
Write-Host "JDK: $($env:JAVA_HOME)"

Write-Host "Kopya: $kopya"
if (Test-Path $kopya) { Remove-Item -Recurse -Force $kopya }
# build, .git, .gradle ve yedekler hariç kopyala
robocopy $depo $kopya /E /XD build .git .gradle _denetim _rapor __pycache__ /XF '*dosya_yedek*' /NFL /NDL /NJH /NJS /NP | Out-Null
Copy-Item (Join-Path $depo 'local.properties') $kopya -ErrorAction SilentlyContinue

Push-Location $kopya
try {
    & .\gradlew.bat testDebugUnitTest --continue --console=plain *> (Join-Path $kopya 'test_log.txt')
} finally { Pop-Location }

$rapor = Join-Path $raporKlasor "eklenti_testi_$tarih.md"
python (Join-Path $depo 'EKLENTI_TESTI.py') --durum --sonuclar $kopya --rapor $rapor
Write-Host "`nRapor: $rapor"
Write-Host 'Durumu değişen eklentiler için: git diff -- */build.gradle.kts'
