# ---------------------------------------------------------------------------
# Installe le dernier APK compile sur un telephone Android branche en USB.
#
# Prerequis sur le telephone :
#   Parametres > A propos > taper 7 fois sur "Numero de build"
#   Parametres > Options pour developpeurs > Debogage USB : ACTIVE
# ---------------------------------------------------------------------------

param([ValidateSet("debug", "release")][string]$Variante = "debug")

. "$PSScriptRoot\_env.ps1"
Set-SankaiEnv

if (-not (Test-Path $Adb)) {
    Ecrire-Erreur "adb absent. Lance scripts\00-installer-outils.ps1"
    exit 1
}

$apk = Join-Path $Dist "SankaiLife-$Variante-dernier.apk"
if (-not (Test-Path $apk)) {
    Ecrire-Erreur "Aucun APK '$Variante' dans dist\. Compile-le d'abord."
    exit 1
}

Ecrire-Titre "SANKAI LIFE - Installation sur telephone"
Ecrire-Etape "Recherche d'appareils ..."
Exec $Adb @("start-server") -IgnorerCode
$appareils = & $Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" }

if (-not $appareils) {
    Ecrire-Erreur "Aucun telephone detecte."
    Write-Host "  - branche le telephone en USB"
    Write-Host "  - active le Debogage USB dans les options developpeur"
    Write-Host "  - accepte la fenetre 'Autoriser le debogage USB ?' sur le telephone"
    exit 1
}
Ecrire-Ok "Appareil detecte : $($appareils[0])"

Ecrire-Etape "Installation de $(Split-Path -Leaf $apk) ..."
Exec $Adb @("install", "-r", $apk)

Ecrire-Ok "Installe. Cherche 'Sankai Life' dans ton tiroir d'applications."
