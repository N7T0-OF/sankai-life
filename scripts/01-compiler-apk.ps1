# ---------------------------------------------------------------------------
# Compile l'APK de DEBUG et le depose dans dist\.
# APK de debug = installable immediatement sur ton telephone, pas publiable
# sur le Play Store (voir 02-compiler-release.ps1 pour ca).
# ---------------------------------------------------------------------------

param([switch]$Propre)

. "$PSScriptRoot\_env.ps1"

if (-not (Test-Path "$JavaHome\bin\java.exe")) {
    Ecrire-Erreur "La chaine de compilation n'est pas installee."
    Write-Host "  Lance d'abord : scripts\00-installer-outils.ps1"
    exit 1
}
Set-SankaiEnv
New-Item -ItemType Directory -Force -Path $Dist | Out-Null

Ecrire-Titre "SANKAI LIFE - Compilation de l'APK (debug)"

$gradlew = Join-Path $Projet "gradlew.bat"
$outil = if (Test-Path $gradlew) { $gradlew } else { $GradleBin }

Push-Location $Projet
try {
    if ($Propre) {
        Ecrire-Etape "Nettoyage ..."
        & $outil clean --no-daemon
    }
    Ecrire-Etape "assembleDebug (la premiere fois, Gradle telecharge ses dependances : 3 a 10 min)"
    & $outil assembleDebug --no-daemon --stacktrace
    if ($LASTEXITCODE -ne 0) { Ecrire-Erreur "La compilation a echoue."; exit 1 }
} finally { Pop-Location }

$apk = Get-ChildItem -Recurse -Filter "*.apk" (Join-Path $Projet "app\build\outputs\apk\debug") |
       Select-Object -First 1
if (-not $apk) { Ecrire-Erreur "Aucun APK produit."; exit 1 }

$horodatage = Get-Date -Format "yyyy-MM-dd_HHmm"
$cible = Join-Path $Dist "SankaiLife-debug-$horodatage.apk"
Copy-Item $apk.FullName $cible -Force
Copy-Item $apk.FullName (Join-Path $Dist "SankaiLife-debug-dernier.apk") -Force

Ecrire-Titre "APK genere"
Write-Host "  $cible"
Write-Host "  Taille : $([math]::Round($apk.Length / 1MB, 2)) Mo"
Write-Host ""
Write-Host "  Pour l'installer sur ton telephone :" -ForegroundColor Cyan
Write-Host "    - copie le .apk sur le telephone et ouvre-le (autoriser sources inconnues)"
Write-Host "    - ou branche le telephone en USB et lance scripts\04-installer-telephone.ps1"
