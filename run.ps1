<#
  run.ps1 - starts Pan-ya Ya. No JDK, Maven or build is needed.

  Order:
    1. a local build             target\dist\Pan-ya Ya\Pan-ya Ya.exe   (from build-windows.ps1)
    2. a local runtime + jar     target\runtime + target\panya-ya-*.jar
    3. the portable app          portable\Pan-ya Ya\Pan-ya Ya.exe      (downloaded earlier)
    4. otherwise: download the portable app from the GitHub release once, check its SHA-256,
       unpack it into portable\ (git-ignored) and start it.

  Usage:  powershell -ExecutionPolicy Bypass -File run.ps1      (or double-click run.bat)
  Env:    PANYAYA_RELEASE_URL overrides the download folder (e.g. a fork or a mirror).
#>
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"   # Invoke-WebRequest is very slow with a progress bar on PS 5.1

$Root        = $PSScriptRoot
$AppName     = "Pan-ya Ya"
$ReleaseUrl  = "https://github.com/AiChan277/panpan/releases/download/a1.0.1"
if ($env:PANYAYA_RELEASE_URL) { $ReleaseUrl = $env:PANYAYA_RELEASE_URL.TrimEnd("/") }
$ZipAsset    = "Pan-ya.Ya-1.0.1-portable.zip"
$SumsAsset   = "SHA256SUMS.txt"
$PortableDir = Join-Path $Root "portable"
$AppArgs     = @($args)

function Write-Title($text) {
    Write-Host "===================================================" -ForegroundColor Cyan
    Write-Host $text -ForegroundColor Cyan
    Write-Host "===================================================" -ForegroundColor Cyan
}

function Start-Exe($exe) {
    Write-Host "Starting $exe"
    $options = @{ FilePath = $exe; WorkingDirectory = (Split-Path $exe) }
    if ($AppArgs.Count -gt 0) { $options.ArgumentList = $AppArgs }
    Start-Process @options
}

function Install-Portable {
    Write-Host "No local build found. Downloading the ready-made app (about 45 MB, once)..."
    Write-Host "  from $ReleaseUrl"
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $work = Join-Path ([IO.Path]::GetTempPath()) ("panyaya-" + [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Force $work | Out-Null
    try {
        $zip  = Join-Path $work $ZipAsset
        $sums = Join-Path $work $SumsAsset
        Invoke-WebRequest -UseBasicParsing -Uri "$ReleaseUrl/$ZipAsset" -OutFile $zip
        Invoke-WebRequest -UseBasicParsing -Uri "$ReleaseUrl/$SumsAsset" -OutFile $sums

        # the checksum file lists the zip under its original name ("Pan-ya.Ya-1.0.1-portable.zip")
        $line = Get-Content $sums | Where-Object { $_ -match "portable\.zip\s*$" } | Select-Object -First 1
        if (-not $line) { throw "$SumsAsset has no entry for the portable zip." }
        $expected = ($line -split "\s+")[0].ToLower()
        $actual = (Get-FileHash -Algorithm SHA256 $zip).Hash.ToLower()
        if ($expected -ne $actual) { throw "Checksum mismatch for $ZipAsset - the download is damaged. Please try again." }
        Write-Host "Checksum OK. Unpacking to $PortableDir"

        $unpacked = Join-Path $work "unpacked"
        Expand-Archive -Path $zip -DestinationPath $unpacked -Force
        if (-not (Test-Path (Join-Path $unpacked "$AppName\$AppName.exe"))) { throw "The zip does not contain $AppName\$AppName.exe." }
        if (Test-Path $PortableDir) { Remove-Item -Recurse -Force $PortableDir }
        Move-Item $unpacked $PortableDir
    } finally {
        Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
    }
}

Write-Title "Membuka $AppName..."

# 1. local build (newest code)
$builtExe = Join-Path $Root "target\dist\$AppName\$AppName.exe"
if (Test-Path $builtExe) { Start-Exe $builtExe; exit 0 }

# 2. local runtime image + jar (build-windows.ps1 stopped before jpackage)
$runtimeJava = Join-Path $Root "target\runtime\bin\java.exe"
$jar = Get-ChildItem (Join-Path $Root "target") -Filter "panya-ya-*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if ((Test-Path $runtimeJava) -and $jar) {
    $classPath = "$($jar.FullName);$(Join-Path $Root 'target\libs\*')"
    & $runtimeJava -cp $classPath app.panyaya.PanyaLauncher @AppArgs
    exit $LASTEXITCODE
}

# 3. + 4. portable app, downloaded once if needed
$portableExe = Join-Path $PortableDir "$AppName\$AppName.exe"
if (-not (Test-Path $portableExe)) {
    try {
        Install-Portable
    } catch {
        Write-Host ""
        Write-Host "Could not get the ready-made app: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "Check the internet connection and run this again, or download the portable zip by hand:"
        Write-Host "  https://github.com/AiChan277/panpan/releases"
        Write-Host "and unzip it so that this file exists: $portableExe"
        exit 1
    }
}
Start-Exe $portableExe
exit 0
