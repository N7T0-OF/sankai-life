# ---------------------------------------------------------------------------
# Chaine complete : installe les outils si besoin, puis compile l'APK.
# C'est le script a lancer si tu ne sais pas par ou commencer.
# ---------------------------------------------------------------------------

. "$PSScriptRoot\_env.ps1"

Ecrire-Titre "SANKAI LIFE - Tout en un"

if (-not (Test-Path "$JavaHome\bin\java.exe") -or -not (Test-Path $SdkManager)) {
    & "$PSScriptRoot\00-installer-outils.ps1"
    if ($LASTEXITCODE -ne 0) { Ecrire-Erreur "Installation des outils echouee."; exit 1 }
} else {
    Ecrire-Ok "Chaine de compilation deja en place."
}

& "$PSScriptRoot\01-compiler-apk.ps1"
exit $LASTEXITCODE
