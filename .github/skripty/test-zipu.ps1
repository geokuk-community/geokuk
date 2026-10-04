# Zkouška hotového zipu pro Windows: zkontroluje jeho obsah, rozbalí ho, spustí přes GeoKuk-prvni-spusteni.cmd a start.jar a ověří složku data,
# výměnu staženého jaru, paměť, zástupce ve složce a jeho opravu po přesunu, restart po aktualizaci a upozornění na nevhodné umístění.
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

# Spustí program jako uživatel dvojklikem (na GeoKuk-prvni-spusteni.cmd, nebo na $soubor) a vrátí proces GeoKuku (ne spouštěče).
function Spust([string]$slozka, [string[]]$parametry, [string]$soubor = (Join-Path $slozka "GeoKuk-prvni-spusteni.cmd")) {
    $zacatek = Get-Date
    $spust = @{ FilePath = $soubor; WorkingDirectory = $slozka; WindowStyle = "Hidden" }
    if ($parametry) { $spust.ArgumentList = $parametry }
    Start-Process @spust
    $jar = Join-Path $slozka "program\geokuk.jar"
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

function Konec($o, $beh) {
    Volej $o POST "/menu?cesta=$([uri]::EscapeDataString('Soubor > Konec'))" | Out-Null
    $proces = Get-Process -Id $beh.Proces.ProcessId -ErrorAction SilentlyContinue
    (-not $proces) -or $proces.WaitForExit(30000)
}

function Zastupce([string]$soubor) {
    if (Test-Path -LiteralPath $soubor) { (New-Object -ComObject WScript.Shell).CreateShortcut($soubor) }
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

# Soubory a klíče registru, které od času $od vznikly nebo se změnily mimo složku programu (bez složek Windows a PowerShellu, Firefoxu runneru a ikon oznamovací oblasti jiných programů).
function ZapsanoMimo([datetime]$od, [string]$slozka) {
    $mista = @($env:APPDATA, $env:LOCALAPPDATA, $env:TEMP, (Join-Path $env:USERPROFILE ".java")) | Where-Object { $_ -and (Test-Path $_) }
    $zmeny = @(Get-ChildItem $mista -Recurse -Force -ErrorAction SilentlyContinue | Where-Object { $_.LastWriteTime -gt $od -and -not $_.FullName.StartsWith($slozka) -and $_.FullName -notlike "*\Microsoft\*" -and $_.FullName -notmatch '\\Mozilla(\\|$)' -and
            $_.Name -notlike "NotifyIconGeneratedAumid_*" -and
            -not ($_.PSIsContainer -and $_.CreationTime -le $od) } |
        ForEach-Object FullName)
    $zmeny += @(Get-ChildItem $env:USERPROFILE -Force -ErrorAction SilentlyContinue | Where-Object { $_.CreationTime -gt $od } | ForEach-Object FullName)
    $zmeny += @(Registr | Where-Object { $_ -notin $registrPred })
    $zmeny
}

# 1. Obvyklé spuštění z rozbaleného zipu, včetně výměny jaru staženého aktualizací.
$slozka = Rozbal (Join-Path $koren "obvykle")
foreach ($f in "LICENSE", "THIRD-PARTY.txt", "CTIMNE.txt", "GeoKuk-prvni-spusteni.cmd") {
    Ocekavej (Test-Path (Join-Path $slozka $f)) "zip obsahuje $f"
}
$priklady = @(Get-ChildItem "priklady\mapy\*.mapa" | ForEach-Object Name | Sort-Object)
$vZipu = @(Get-ChildItem (Join-Path $slozka "data\mapy-priklady") -Filter "*.mapa" -ErrorAction SilentlyContinue | ForEach-Object Name | Sort-Object)
Ocekavej ($priklady.Count -gt 0 -and ($priklady -join ",") -eq ($vZipu -join ",")) "zip obsahuje ukázky map v data\mapy-priklady: $($vZipu -join ', ')"
Ocekavej (-not (Test-Path (Join-Path $slozka "data\mapy"))) "zip neobsahuje data\mapy (ukázky by se načetly jako mapy)"
$predSpustenim = Get-Date
$registrPred = Registr
Copy-Item (Join-Path $slozka "program\geokuk.jar") (Join-Path $slozka "program\geokuk.jar.new")
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

    Ocekavej (Test-Path (Join-Path $slozka "program\geokuk.jar.bak")) "stažený jar vyměněn, starý zůstal jako .bak"
    Ocekavej (-not (Test-Path (Join-Path $slozka "program\geokuk.jar.new"))) "geokuk.jar.new po výměně nezůstal"

    $xmx = [regex]::Match($beh.Proces.CommandLine, "-Xmx(\d+)m")
    $ram = [long]((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1MB)
    $cekana = [math]::Min(3072, [math]::Max(1024, [math]::Floor($ram / 2)))
    Ocekavej ($xmx.Success -and [math]::Abs([int]$xmx.Groups[1].Value - $cekana) -le 64) "paměť $($xmx.Value) odpovídá polovině RAM $ram MB v mezích 1–3 GB (čekáno $cekana)"
    $souhrn.Add("| Paměť | $($xmx.Value), RAM $ram MB |")
    $javaw = Join-Path $slozka "program\runtime\bin\javaw.exe"
    Ocekavej ($beh.Proces.ExecutablePath -eq $javaw) "běží přibalená Java: $($beh.Proces.ExecutablePath)"
    $lnk = Cekej 60 { Zastupce (Join-Path $slozka "GeoKuk.lnk") }
    Ocekavej ($lnk -and $lnk.TargetPath -eq $javaw -and $lnk.Arguments -like "*$(Join-Path $slozka 'program\start.jar')*" -and $lnk.WorkingDirectory -eq (Join-Path $slozka "program")) `
        "ve složce vznikl zástupce GeoKuk.lnk: $($lnk.TargetPath) $($lnk.Arguments)"

    foreach ($d in "data\tmp", "data\log\geokuk.log", "data\gpx", "data\ikony\moje", "data\ikony\ostatni", "data\mapy") {
        Ocekavej (Test-Path (Join-Path $slozka $d)) "vzniklo $d"
    }
    Ocekavej (Konec $o $beh) "program po Soubor > Konec skončil"
    $xml = [xml](Get-Content -Raw -Encoding utf8 (Join-Path $slozka "data\nastaveni.xml"))
    Ocekavej ($null -ne $xml.preferences.root) "data\nastaveni.xml je po ukončení platné"
    $chyby = @(Get-ChildItem (Join-Path $slozka "data\log\chyby") -ErrorAction SilentlyContinue)
    Ocekavej ($chyby.Count -eq 0) "bez výpisů chyb v data\log\chyby: $($chyby.Name -join ', ')"
    $mimo = @(ZapsanoMimo $predSpustenim $slozka)
    # Zápisy kamkoli do profilu uživatele mimo AppData, i do existujících složek.
    $vProfilu = @(Get-ChildItem $env:USERPROFILE -Recurse -Force -File -ErrorAction SilentlyContinue |
        Where-Object { $_.LastWriteTime -gt $predSpustenim -and $_.FullName -notlike "$env:USERPROFILE\AppData\*" -and $_.Name -notlike "NTUSER*" -and $_.Name -notlike "ntuser*" } |
        ForEach-Object FullName)
    Write-Host "Zapsáno v profilu mimo AppData:"; $vProfilu | Select-Object -First 30 | ForEach-Object { Write-Host "  $_" }
    Ocekavej ($vProfilu.Count -eq 0) "do profilu uživatele se nic nezapsalo: $(($vProfilu | Select-Object -First 5) -join ', ')"
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
    # Výslovný zákaz i na data, zděděný by přebilo výslovné povolení pro správce.
    foreach ($d in $slozka, (Join-Path $slozka "data")) { icacls $d /deny "$($env:USERNAME):(OI)(CI)(AD,WD)" | Out-Null }
    Start-Process -FilePath (Join-Path $slozka "program\runtime\bin\javaw.exe") -ArgumentList "-jar", "`"$(Join-Path $slozka 'program\start.jar')`"" -WorkingDirectory $slozka | Out-Null
    $beh = Cekej 60 {
        Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe'" | Where-Object { $_.CommandLine -like "*-jar*$(Join-Path $slozka 'program\geokuk.jar')*" }
    }
    Ocekavej ($null -ne $beh) "program ze složky bez práva zápisu se spustil"
    if ($beh) {
        $titulky = Cekej 60 { $t = [Okna]::Titulky([uint32]$beh.ProcessId); if ($t -like "Geokuk: Chyba") { $t } }
        Ocekavej ($null -ne $titulky) "chyba o složce bez práva zápisu: okna $([Okna]::Titulky([uint32]$beh.ProcessId) -join ', ')"
        if (-not $titulky) {
            icacls (Join-Path $slozka "data")
            Get-ChildItem -Recurse -Force (Join-Path $slozka "data") | ForEach-Object FullName
            Get-Content -Tail 40 -ErrorAction SilentlyContinue (Join-Path $slozka "data\log\geokuk.log")
        }
    }
} finally {
    Ukonci $slozka
    icacls $slozka /remove:d $env:USERNAME /T /C | Out-Null
}

# 4. Přesun složky: zástupce ve složce i jeho kopie na ploše se opraví a GeoKuk jde z kopie spustit.
$puvodni = Rozbal (Join-Path $koren "presun")
$plocha = [Environment]::GetFolderPath("Desktop")
New-Item -ItemType Directory -Force $plocha | Out-Null
$naPlose = Join-Path $plocha "GeoKuk zkouska.lnk"
$slozka = Join-Path $koren "presunuto\GeoKuk"
try {
    $beh = Spust $puvodni @("--ovladani=0", "--ovladani-devel")
    $o = Ovladani $puvodni
    $lnk = Cekej 60 { Zastupce (Join-Path $puvodni "GeoKuk.lnk") }
    Ocekavej ($null -ne $lnk) "před přesunem vznikl zástupce"
    $ulozeno = Cekej 30 { (Get-Content -Raw -Encoding utf8 (Join-Path $puvodni "data\nastaveni.xml")) -like "*zastupcePro*" }
    Ocekavej ($ulozeno -eq $true) "GeoKuk si uložil, pro kterou složku zástupce vytvořil"
    Ocekavej (Konec $o $beh) "program před přesunem skončil"
    Copy-Item (Join-Path $puvodni "GeoKuk.lnk") $naPlose
    Remove-Item (Join-Path $puvodni "data\ovladani.properties") -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force (Split-Path $slozka) | Out-Null
    Move-Item $puvodni $slozka
    New-Item -ItemType Directory -Force (Join-Path $slozka "data\gpx") | Out-Null
    Set-Content -Encoding ascii (Join-Path $slozka "data\gpx\presun.gpx") '<?xml version="1.0" encoding="UTF-8"?><gpx version="1.1" creator="zkouska" xmlns="http://www.topografix.com/GPX/1/1"><wpt lat="50.1" lon="14.4"><name>GCPRESUN</name><sym>Geocache</sym></wpt></gpx>'

    $beh = Spust $slozka @("--ovladani=0", "--ovladani-devel")
    $o = Ovladani $slozka
    $nacteno = Cekej 60 { $s = Volej $o GET "/stav"; if ($s.waypointu -ge 1) { $s } }
    Ocekavej ($null -ne $nacteno) "po přesunu se načtou keše z data\gpx v nové složce"
    $javaw = Join-Path $slozka "program\runtime\bin\javaw.exe"
    $veSlozce = Cekej 60 { $z = Zastupce (Join-Path $slozka "GeoKuk.lnk"); if ($z.TargetPath -eq $javaw) { $z } }
    Ocekavej ($null -ne $veSlozce) "zástupce ve složce po přesunu vede na $javaw"
    $kopie = Cekej 30 { $z = Zastupce $naPlose; if ($z.TargetPath -eq $javaw) { $z } }
    Ocekavej ($null -ne $kopie) "zástupce na ploše po přesunu opraven: $((Zastupce $naPlose).TargetPath)"
    Ocekavej (Konec $o $beh) "program po přesunu skončil"
    $nastaveni = Get-Content -Raw -Encoding utf8 (Join-Path $slozka "data\nastaveni.xml")
    Ocekavej (-not $nastaveni.Contains($puvodni)) "nastavení po přesunu neodkazuje na původní složku"
    if ($kopie) {
        $zKopie = $null
        try { $zKopie = Spust $slozka @() $naPlose } catch { }
        Ocekavej ($null -ne $zKopie) "GeoKuk se spustil ze zástupce na ploše"
    }
} finally {
    Ukonci $puvodni
    Ukonci $slozka
    Remove-Item $naPlose -ErrorAction SilentlyContinue
}

# 5. Restart po aktualizaci: spouštěč počká, až GeoKuk skončí, vymění jar a spustí novou verzi.
$slozka = Rozbal (Join-Path $koren "restart")
try {
    $beh = Spust $slozka @("--ovladani=0", "--ovladani-devel")
    $o = Ovladani $slozka
    Cekej 60 { Volej $o GET "/stav" } | Out-Null
    $jar = Join-Path $slozka "program\geokuk.jar"
    Copy-Item $jar "$jar.new"
    Volej $o POST "/restart" | Out-Null
    $stary = Get-Process -Id $beh.Proces.ProcessId -ErrorAction SilentlyContinue
    Ocekavej ((-not $stary) -or $stary.WaitForExit(30000)) "původní GeoKuk po restartu skončil"
    $novy = Cekej 60 {
        Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe'" | Where-Object { $_.CommandLine -like "*-jar*$jar*" -and $_.ProcessId -ne $beh.Proces.ProcessId }
    }
    Ocekavej ($null -ne $novy) "po restartu běží nová instance"
    if ($novy) {
        $okno = Cekej 60 { $t = [Okna]::Titulky([uint32]@($novy)[0].ProcessId); if ($t -contains "GeoKuk") { $t } }
        Ocekavej ($null -ne $okno) "nová instance po restartu otevřela hlavní okno: $([Okna]::Titulky([uint32]@($novy)[0].ProcessId) -join ', ')"
    }
    Ocekavej (Test-Path "$jar.bak") "při restartu se jar vyměnil, starý zůstal jako .bak"
    Ocekavej (-not (Test-Path "$jar.new")) "geokuk.jar.new po restartu nezůstal"
} finally {
    Ukonci $slozka
}

if ($env:GITHUB_STEP_SUMMARY) {
    @("### Zkouška zipu na Windows", "", "| | |", "|---|---|") + $souhrn + @("") + ($problemy | ForEach-Object { "- CHYBA: $_" }) |
        Add-Content -Encoding utf8 $env:GITHUB_STEP_SUMMARY
}
if ($problemy.Count -gt 0) {
    throw "Zkouška zipu selhala:`n$($problemy -join "`n")"
}
