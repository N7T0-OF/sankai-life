# ---------------------------------------------------------------------------
# Compile la version RELEASE :
#   - un APK signe   -> installation directe / distribution hors Play Store
#   - un AAB signe   -> format exige par le Google Play Store
#
# Si aucune cle de signature n'existe, elle est creee automatiquement.
# ---------------------------------------------------------------------------

param([switch]$Propre)

. "$PSScriptRoot\_env.ps1"

if (-not (Test-Path "$JavaHome\bin\java.exe")) {
    Ecrire-Erreur "Chaine de compilation absente. Lance scripts\00-installer-outils.ps1"
    exit 1
}
Set-SankaiEnv
New-Item -ItemType Directory -Force -Path $Dist | Out-Null

Ecrire-Titre "SANKAI LIFE - Compilation RELEASE (APK + AAB signes)"

if (-not (Test-Path (Join-Path $Projet "keystore.properties"))) {
    Ecrire-Alerte "Aucune cle de signature trouvee : creation automatique."
    & "$PSScriptRoot\03-generer-keystore.ps1"
    if ($LASTEXITCODE -ne 0) { exit 1 }
}

$gradlew = Join-Path $Projet "gradlew.bat"
$outil = if (Test-Path $gradlew) { $gradlew } else { $GradleBin }

Push-Location $Projet
try {
    if ($Propre) { Exec $outil @("clean", "--no-daemon") }
    Ecrire-Etape "assembleRelease + bundleRelease ..."
    Exec $outil @("assembleRelease", "bundleRelease", "--no-daemon")
} finally { Pop-Location }

$horodatage = Get-Date -Format "yyyy-MM-dd_HHmm"
$resultats = @()

$apk = Get-ChildItem -Recurse -Filter "*.apk" (Join-Path $Projet "app\build\outputs\apk\release") -ErrorAction SilentlyContinue |
       Where-Object { $_.Name -notlike "*unsigned*" } | Select-Object -First 1
if ($apk) {
    $cible = Join-Path $Dist "SankaiLife-release-$horodatage.apk"
    Copy-Item $apk.FullName $cible -Force
    Copy-Item $apk.FullName (Join-Path $Dist "SankaiLife-release-dernier.apk") -Force
    $resultats += "APK : $cible  ($([math]::Round($apk.Length/1MB,2)) Mo)"
}

$aab = Get-ChildItem -Recurse -Filter "*.aab" (Join-Path $Projet "app\build\outputs\bundle") -ErrorAction SilentlyContinue |
       Select-Object -First 1
if ($aab) {
    $cible = Join-Path $Dist "SankaiLife-release-$horodatage.aab"
    Copy-Item $aab.FullName $cible -Force
    Copy-Item $aab.FullName (Join-Path $Dist "SankaiLife-playstore-dernier.aab") -Force
    $resultats += "AAB : $cible  ($([math]::Round($aab.Length/1MB,2)) Mo)"
}

Ecrire-Titre "Build release termine"
$resultats | ForEach-Object { Write-Host "  $_" }
Write-Host ""
Write-Host "  Le .aab est le fichier a envoyer sur la Google Play Console." -ForegroundColor Cyan
Write-Host "  Guide : exemple\guides\PUBLIER-SUR-PLAY-STORE.md"
