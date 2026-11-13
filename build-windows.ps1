<#
  build-windows.ps1 - reproducible Pan-ya Ya packaging (Windows x64).

  Produces in target\dist:
    Pan-ya Ya\Pan-ya Ya.exe              application launcher (app image, bundled Java runtime)
    Pan-ya Ya-<version>-portable.zip     the same app image zipped (no installation needed)
    Pan-ya Ya-<version>.exe              installer (WiX), Start menu + desktop shortcut
    SHA256SUMS.txt

  Prerequisites (see README): JDK 25 with jmods, JavaFX 25 Windows jmods, Maven 3.9+, WiX Toolset 5
  (dotnet tool) with the UI and Util extensions. Paths can be passed as parameters.

  Usage:  powershell -ExecutionPolicy Bypass -File build-windows.ps1
#>
param(
    [string]$Version     = "1.0.1",                     # MSI rule: MAJOR.MINOR.BUILD, each <= 255/255/65535
    [string]$JdkHome     = "$env:USERPROFILE\tools\jdk-25.0.5+8",
    [string]$JavafxJmods = "$env:USERPROFILE\tools\javafx-jmods-25.0.5",
    [string]$MavenHome   = "$env:USERPROFILE\tools\apache-maven-3.9.16",
    [string]$WixDir      = "$env:USERPROFILE\tools\wix",
    [string]$UpgradeUuid = "3b256572-ddf4-4a7b-b67b-754cc07654a8",  # NEVER change: lets upgrades replace older versions
    [switch]$SkipTests,
    [switch]$SkipInstaller
)
# Native tools (java, mvn, jlink...) write progress to stderr; in Windows PowerShell 5.1 that must
# not abort the script, so failures are detected through $LASTEXITCODE and -ErrorAction Stop instead.
$ErrorActionPreference = "Continue"
$AppName   = "Pan-ya Ya"
$MainClass = "app.panyaya.PanyaLauncher"
$MainJar   = "panya-ya-$Version.jar"
$Target    = Join-Path $PSScriptRoot "target"
$PkgInput  = Join-Path $Target "package-input"
$Runtime   = Join-Path $Target "runtime"
$Dist      = Join-Path $Target "dist"

function Step($text) { Write-Host "`n==> $text" -ForegroundColor Cyan }

# ---- 1. preconditions --------------------------------------------------------------------
Step "Checking tools"
$env:JAVA_HOME = $JdkHome
$env:Path = "$JdkHome\bin;$MavenHome\bin;$WixDir;$env:Path"
foreach ($tool in "java", "jlink", "jpackage", "jdeps", "mvn") {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "$tool not found (check -JdkHome / -MavenHome)" }
}
$javaVersion = (cmd /c "java -version 2>&1" | Select-Object -First 1)
if ($javaVersion -notmatch '"25') { throw "JDK 25 required, found: $javaVersion" }
# Some JDK 25 builds (e.g. Temurin) have no jmods folder: jlink then links the JDK modules from the
# JDK's own run-time image (JEP 493). JavaFX always comes from its jmods.
$jlinkModulePath = $JavafxJmods
if (Test-Path "$JdkHome\jmods\java.base.jmod") { $jlinkModulePath = "$JdkHome\jmods;$JavafxJmods" }
if (-not (Test-Path "$JavafxJmods\javafx.controls.jmod")) { throw "JavaFX jmods not found at $JavafxJmods" }
if (-not $SkipInstaller -and -not (Get-Command wix -ErrorAction SilentlyContinue)) {
    throw "WiX Toolset not found (install: dotnet tool install wix --version 5.0.2 --tool-path $WixDir)"
}
Write-Host $javaVersion

# ---- 2. build + tests --------------------------------------------------------------------
Step "Maven build"
Push-Location $PSScriptRoot
try {
    # jpackage marks the installer read-only, which would make "mvn clean" fail on the next build
    Get-ChildItem $Target -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.IsReadOnly } | ForEach-Object { $_.IsReadOnly = $false }
    $mvnArgs = @("-B", "clean", "package")
    if ($SkipTests) { $mvnArgs += "-DskipTests" }
    & mvn @mvnArgs
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }
} finally { Pop-Location }

# ---- 3. stage the app jar + Jackson jars (JavaFX comes from the jmods) ------------------
Step "Staging jars"
Remove-Item -Recurse -Force $PkgInput, $Runtime, $Dist -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $PkgInput -ErrorAction Stop | Out-Null
Copy-Item (Join-Path $Target $MainJar) $PkgInput -ErrorAction Stop
Copy-Item (Join-Path $Target "libs\*.jar") $PkgInput -ErrorAction Stop

# ---- 4. jlink: a minimal Java runtime with JavaFX inside ---------------------------------
Step "Creating runtime image (jdeps + jlink)"
$classPath = (Get-ChildItem "$PkgInput\*.jar" | ForEach-Object { $_.FullName }) -join ";"
$jdkModules = (& jdeps --multi-release 25 --ignore-missing-deps --print-module-deps `
        --class-path $classPath (Join-Path $PkgInput $MainJar)).Trim()
if ($LASTEXITCODE -ne 0 -or -not $jdkModules) { throw "jdeps failed" }
# java.sql: Jackson's optional java.sql.Date support; jdk.localedata: locale data for date pickers
$modules = "$jdkModules,java.sql,jdk.localedata,javafx.controls,javafx.fxml"
Write-Host "Modules: $modules"
& jlink --module-path $jlinkModulePath --add-modules $modules `
    --include-locales=en,id --strip-debug --no-header-files --no-man-pages `
    --compress=zip-6 --output $Runtime
if ($LASTEXITCODE -ne 0) { throw "jlink failed" }

# ---- 5. app image (portable folder) ------------------------------------------------------
Step "Creating application image"
$icon = Join-Path $PSScriptRoot "src\packaging\windows\panyaya.ico"
& jpackage --type app-image --name $AppName --app-version $Version `
    --vendor "Pan-ya Ya Project" --description "Pan-ya Ya - Bakery Operations Management" `
    --input $PkgInput --main-jar $MainJar --main-class $MainClass `
    --runtime-image $Runtime --icon $icon `
    --java-options "-Xmx768m" --java-options "--enable-native-access=javafx.graphics" `
    --dest $Dist
if ($LASTEXITCODE -ne 0) { throw "jpackage app-image failed" }
$appImage = Join-Path $Dist $AppName
Compress-Archive -Path $appImage -DestinationPath (Join-Path $Dist "$AppName-$Version-portable.zip") -Force -ErrorAction Stop

# ---- 6. smoke test of the packaged runtime (no system Java involved) ---------------------
Step "Self-test with the bundled runtime"
$bundledJava = Join-Path $appImage "runtime\bin\java.exe"
$appDir = Join-Path $appImage "app"
$cp = (Get-ChildItem "$appDir\*.jar" | ForEach-Object { $_.FullName }) -join ";"
& $bundledJava -cp $cp $MainClass --self-test
if ($LASTEXITCODE -ne 0) { throw "Self-test failed" }

# ---- 7. installer from the same app image -----------------------------------------------
if (-not $SkipInstaller) {
    Step "Creating installer (WiX)"
    & jpackage --type exe --app-image $appImage --name $AppName --app-version $Version `
        --vendor "Pan-ya Ya Project" --description "Pan-ya Ya - Bakery Operations Management" `
        --win-upgrade-uuid $UpgradeUuid --win-menu --win-menu-group $AppName `
        --win-shortcut --win-dir-chooser --dest $Dist
    if ($LASTEXITCODE -ne 0) { throw "jpackage installer failed" }
}

# ---- 8. checksums ------------------------------------------------------------------------
Step "Checksums"
Get-ChildItem $Dist -File | Where-Object { $_.Name -ne "SHA256SUMS.txt" } | Get-FileHash -Algorithm SHA256 |
    ForEach-Object { "{0}  {1}" -f $_.Hash.ToLower(), (Split-Path $_.Path -Leaf) } |
    Set-Content -Encoding ascii (Join-Path $Dist "SHA256SUMS.txt")
Get-ChildItem $Dist | Format-Table Name, Length
Write-Host "Done. Artefacts in $Dist" -ForegroundColor Green
