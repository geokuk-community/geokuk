# Zkouška hotového zipu pro Windows: rozbalí ho, spustí přes GeoKuk.cmd a start.jar a ověří složku data,
# výměnu staženého jaru, paměť a upozornění na nevhodné umístění.
param([string]$Zip = "GeoKuk-windows.zip")

$ErrorActionPreference = "Stop"
$koren = Join-Path $env:RUNNER_TEMP "zkouska-zipu"
$problemy = [System.Collections.Generic.List[string]]::new()
$souhrn = [System.Collections.Generic.List[string]]::new()

Add-Type @"
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;
public static class Okna {
    delegate bool EnumProc(IntPtr h, IntPtr p);
    [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc f, IntPtr p);
    [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
    public static List<string> Titulky(uint proces) {
        var vysledek = new List<string>();
        EnumWindows((h, p) => {
            uint pid;
            GetWindowThreadProcessId(h, out pid);
            if (pid == proces && IsWindowVisible(h)) {
                var sb = new StringBuilder(512);
                GetWindowText(h, sb, sb.Capacity);
                if (sb.Length > 0) vysledek.Add(sb.ToString());
            }
            return true;
        }, IntPtr.Zero);
        return vysledek;
    }
}
"@

function Rozbal([string]$cil) {
    New-Item -ItemType Directory -Force $cil | Out-Null
    Expand-Archive $Zip $cil -Force
    $slozka = Join-Path $cil "GeoKuk"
    # Bez kontroly aktualizací, ať program nečeká na síť a neotevírá dialog.
    New-Item -ItemType Directory -Force (Join-Path $slozka "data") | Out-Null
    $nastaveni = '<?xml version="1.0" encoding="UTF-8" standalone="no"?>' +
        '<!DOCTYPE preferences SYSTEM "http://java.sun.com/dtd/preferences.dtd">' +
        '<preferences EXTERNAL_XML_VERSION="1.0"><root type="user"><map/><node name="geokuk"><map/><node name="current"><map/>' +
        '<node name="vseobecne"><map><entry key="nextUpdateCheckTimestamp" value="9223372036854775807"/></map></node></node></node></root></preferences>'
    Set-Content -Encoding utf8 (Join-Path $slozka "data\nastaveni.xml") $nastaveni
    $slozka
}

# Spustí program jako uživatel dvojklikem na GeoKuk.cmd a vrátí proces GeoKuku (ne spouštěče).
function Spust([string]$slozka, [string[]]$parametry) {
    $zacatek = Get-Date
    $spust = @{ FilePath = (Join-Path $slozka "GeoKuk.cmd"); WorkingDirectory = $slozka; WindowStyle = "Hidden" }
    if ($parametry) { $spust.ArgumentList = $parametry }
    Start-Process @spust
    $jar = Join-Path $slozka "geokuk.jar"
    for ($i = 0; $i -lt 60; $i++) {
        $p = Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe'" | Where-Object { $_.CommandLine -like "*-jar*$jar*" }
        if ($p) { return [pscustomobject]@{ Proces = $p; Zacatek = $zacatek } }
        Start-Sleep -Milliseconds 500
    }
    throw "GeoKuk ve složce $slozka se nespustil"
}

function Ukonci([string]$slozka) {
    Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe'" | Where-Object { $_.CommandLine -like "*$slozka*" } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}

function Cekej([int]$sekund, [scriptblock]$podminka) {
    $konec = (Get-Date).AddSeconds($sekund)
    while ((Get-Date) -lt $konec) {
        try { $v = & $podminka; if ($v) { return $v } } catch { }
        Start-Sleep -Milliseconds 300
    }
    return $null
}

function Ovladani([string]$slozka) {
    $soubor = Join-Path $slozka "data\ovladani.properties"
    if (-not (Cekej 60 { Test-Path $soubor })) { throw "Nevznikl $soubor" }
    $p = Get-Content -Raw $soubor | ConvertFrom-StringData
    [pscustomobject]@{ Url = "http://127.0.0.1:$($p.port)"; Hlavicky = @{ Authorization = "Bearer $($p.token)" } }
}

function Volej($o, [string]$metoda, [string]$cesta) {
    Invoke-RestMethod -Method $metoda -Uri ($o.Url + $cesta) -Headers $o.Hlavicky -NoProxy -TimeoutSec 60
}

function Ocekavej([bool]$plati, [string]$popis) {
    if (-not $plati) { $problemy.Add($popis); Write-Host "CHYBA: $popis" } else { Write-Host "OK: $popis" }
}

function Registr { @(Get-ChildItem "HKCU:\Software\JavaSoft" -Recurse -ErrorAction SilentlyContinue | ForEach-Object Name) }

# Soubory a klíče registru, které od času $od vznikly nebo se změnily mimo složku programu (bez složek Windows a PowerShellu).
function ZapsanoMimo([datetime]$od, [string]$slozka) {
    $mista = @($env:APPDATA, $env:LOCALAPPDATA, $env:TEMP, (Join-Path $env:USERPROFILE ".java")) | Where-Object { $_ -and (Test-Path $_) }
    $zmeny = @(Get-ChildItem $mista -Recurse -Force -ErrorAction SilentlyContinue | Where-Object { $_.LastWriteTime -gt $od -and -not $_.FullName.StartsWith($slozka) -and $_.FullName -notlike "*\Microsoft\*" -and
            -not ($_.PSIsContainer -and $_.FullName -in $mista) } |
        ForEach-Object FullName)
    $zmeny += @(Get-ChildItem $env:USERPROFILE -Force -ErrorAction SilentlyContinue | Where-Object { $_.CreationTime -gt $od } | ForEach-Object FullName)
    $zmeny += @(Registr | Where-Object { $_ -notin $registrPred })
    $zmeny
}

# 1. Obvyklé spuštění z rozbaleného zipu, včetně výměny jaru staženého aktualizací.
$slozka = Rozbal (Join-Path $koren "obvykle")
$predSpustenim = Get-Date
$registrPred = Registr
Copy-Item (Join-Path $slozka "geokuk.jar") (Join-Path $slozka "geokuk.jar.new")
$beh = Spust $slozka @("--ovladani=0", "--ovladani-devel")
try {
    $o = Ovladani $slozka
    $stav = Cekej 60 { Volej $o GET "/stav" }
    $startMs = [int]((Get-Date) - $beh.Zacatek).TotalMilliseconds
    Ocekavej ($null -ne $stav) "dálkové ovládání odpovídá"
    Write-Host "Stav: $($stav | ConvertTo-Json -Compress -Depth 2)"
    $souhrn.Add("| Start do odpovědi ovládání | $startMs ms |")
    $souhrn.Add("| Verze | $($stav.verze) |")
    # Upozornění se ukazují až po zobrazení hlavního okna.
    Start-Sleep -Seconds 5
    $okna = @(Volej $o GET "/okna")
    Ocekavej (($okna.Count -eq 1) -and ($okna[0].titulek -eq "GeoKuk")) "otevřené je jen hlavní okno: $(($okna | ForEach-Object { $_.titulek + ': ' + $_.text }) -join ' | ')"

    Ocekavej (Test-Path (Join-Path $slozka "geokuk.jar.bak")) "stažený jar vyměněn, starý zůstal jako .bak"
    Ocekavej (-not (Test-Path (Join-Path $slozka "geokuk.jar.new"))) "geokuk.jar.new po výměně nezůstal"

    $xmx = [regex]::Match($beh.Proces.CommandLine, "-Xmx(\d+)m")
    $ram = [long]((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1MB)
    $cekana = [math]::Min(3072, [math]::Max(1024, [math]::Floor($ram / 2)))
    Ocekavej ($xmx.Success -and [math]::Abs([int]$xmx.Groups[1].Value - $cekana) -le 64) "paměť $($xmx.Value) odpovídá polovině RAM $ram MB v mezích 1–3 GB (čekáno $cekana)"
    $souhrn.Add("| Paměť | $($xmx.Value), RAM $ram MB |")
    Ocekavej ($beh.Proces.ExecutablePath -eq (Join-Path $slozka "runtime\bin\javaw.exe")) "běží přibalená Java: $($beh.Proces.ExecutablePath)"

    foreach ($d in "data\tmp", "data\log\geokuk.log", "data\gpx", "data\ikony\moje", "data\ikony\ostatni") {
        Ocekavej (Test-Path (Join-Path $slozka $d)) "vzniklo $d"
    }
    Volej $o POST "/menu?cesta=$([uri]::EscapeDataString('Soubor > Konec'))" | Out-Null
    $proces = Get-Process -Id $beh.Proces.ProcessId -ErrorAction SilentlyContinue
    Ocekavej ((-not $proces) -or $proces.WaitForExit(30000)) "program po Soubor > Konec skončil"
    $xml = [xml](Get-Content -Raw -Encoding utf8 (Join-Path $slozka "data\nastaveni.xml"))
    Ocekavej ($null -ne $xml.preferences.root) "data\nastaveni.xml je po ukončení platné"
    $chyby = @(Get-ChildItem (Join-Path $slozka "data\log\chyby") -ErrorAction SilentlyContinue)
    Ocekavej ($chyby.Count -eq 0) "bez výpisů chyb v data\log\chyby: $($chyby.Name -join ', ')"
    $mimo = @(ZapsanoMimo $predSpustenim $slozka)
    Write-Host "Zapsáno mimo složku programu:"; $mimo | ForEach-Object { Write-Host "  $_" }
    $souhrn.Add("| Zapsáno mimo složku | $($mimo.Count) |")
    Ocekavej ($mimo.Count -eq 0) "mimo složku programu se nic nezapsalo: $($mimo -join ', ')"
} finally {
    Ukonci $slozka
}

# 2. Složka synchronizovaná OneDrivem.
$slozka = Rozbal (Join-Path $koren "OneDrive")
$beh = Spust $slozka @("--ovladani=0", "--ovladani-devel")
try {
    $o = Ovladani $slozka
    $upozorneni = Cekej 60 { @(Volej $o GET "/okna") | Where-Object { $_.titulek -like "Geokuk: Upozorn*" } }
    Ocekavej ($upozorneni -and $upozorneni.text -like "*synchronizuje OneDrive*") "upozornění na synchronizovanou složku: $($upozorneni.text)"
} finally {
    Ukonci $slozka
}

# 3. Složka, kam uživatel nesmí zapisovat (jako Program Files bez práv správce).
$slozka = Rozbal (Join-Path $koren "jen-cteni")
try {
    icacls $slozka /deny "$($env:USERNAME):(OI)(CI)(AD,WD)" | Out-Null
    Start-Process -FilePath (Join-Path $slozka "runtime\bin\javaw.exe") -ArgumentList "-jar", "`"$(Join-Path $slozka 'start.jar')`"" -WorkingDirectory $slozka | Out-Null
    $beh = Cekej 60 {
        Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe'" | Where-Object { $_.CommandLine -like "*-jar*$(Join-Path $slozka 'geokuk.jar')*" }
    }
    Ocekavej ($null -ne $beh) "program ze složky bez práva zápisu se spustil"
    if ($beh) {
        $titulky = Cekej 60 { $t = [Okna]::Titulky([uint32]$beh.ProcessId); if ($t -like "Geokuk: Chyba") { $t } }
        Ocekavej ($null -ne $titulky) "chyba o složce bez práva zápisu: okna $([Okna]::Titulky([uint32]$beh.ProcessId) -join ', ')"
    }
} finally {
    Ukonci $slozka
    icacls $slozka /remove:d $env:USERNAME /T /C | Out-Null
}

if ($env:GITHUB_STEP_SUMMARY) {
    @("### Zkouška zipu na Windows", "", "| | |", "|---|---|") + $souhrn + @("") + ($problemy | ForEach-Object { "- CHYBA: $_" }) |
        Add-Content -Encoding utf8 $env:GITHUB_STEP_SUMMARY
}
if ($problemy.Count -gt 0) {
    throw "Zkouška zipu selhala:`n$($problemy -join "`n")"
}
