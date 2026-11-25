# CloudStream masaüstü için fiziksel UI test yardımcıları.
# Kullanım: .\_denetim\ui_test.ps1 komutları  (komutlar: shot, click, key, aktif, list)
param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Komutlar)

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class PobloUI {
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x,int y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint f,uint x,uint y,uint d,IntPtr e);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h,int n);
  public static void Click(int x,int y){ SetCursorPos(x,y); mouse_event(0x0002,0,0,0,IntPtr.Zero); System.Threading.Thread.Sleep(60); mouse_event(0x0004,0,0,0,IntPtr.Zero); }
}
"@

$wsh = New-Object -ComObject WScript.Shell

function Aktif {
  $p = Get-Process CloudStream -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
  if (-not $p) { Write-Host "CloudStream penceresi yok"; return }
  [PobloUI]::ShowWindow($p.MainWindowHandle, 9) | Out-Null
  [PobloUI]::SetForegroundWindow($p.MainWindowHandle) | Out-Null
  Start-Sleep -Milliseconds 350
  Write-Host "etkin: $($p.ProcessId) $($p.MainWindowTitle)"
}
function Shot([string]$yol) {
  $b = [System.Windows.Forms.SystemInformation]::VirtualScreen
  $bmp = New-Object System.Drawing.Bitmap($b.Width, $b.Height)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.CopyFromScreen($b.Location, [System.Drawing.Point]::Empty, $b.Size)
  $bmp.Save($yol, [System.Drawing.Imaging.ImageFormat]::Png)
  $g.Dispose(); $bmp.Dispose()
  Write-Host "ekran goruntusu: $yol ($($b.Width)x$($b.Height))"
}
Add-Type -AssemblyName System.Windows.Forms

$cmd = if ($Komutlar.Count -gt 0) { $Komutlar[0] } else { "list" }
switch ($cmd) {
  "aktif" { Aktif }
  "shot"  { Shot $Komutlar[1] }
  "click" { [PobloUI]::Click([int]$Komutlar[1], [int]$Komutlar[2]); Write-Host "tiklandi: $($Komutlar[1]),$($Komutlar[2])" }
  "key"   { Aktif | Out-Null; $wsh.SendKeys($Komutlar[1]); Write-Host "tus: $($Komutlar[1])" }
  "list"  { Get-Process CloudStream -ErrorAction SilentlyContinue | ForEach-Object { "pid=$($_.ProcessId) handle=$($_.MainWindowHandle) title=$($_.MainWindowTitle)" } }
}
