# ---------------------------------------------------------------------------
# Cree la cle de signature (keystore) necessaire pour publier sur le Play Store.
#
# ATTENTION : ce fichier .jks et ses mots de passe sont IRREMPLACABLES.
# Si tu les perds, tu ne pourras plus jamais mettre a jour l'app publiee.
# Sauvegarde-les hors de cet ordinateur.
# ---------------------------------------------------------------------------

param(
    [string]$MotDePasse,
    [string]$Alias = "sankai",
    [string]$Nom   = "Sankai Life"
)

. "$PSScriptRoot\_env.ps1"

if (-not (Test-Path "$JavaHome\bin\keytool.exe")) {
    Ecrire-Erreur "JDK absent. Lance d'abord scripts\00-installer-outils.ps1"
    exit 1
}
Set-SankaiEnv

Ecrire-Titre "SANKAI LIFE - Generation de la cle de signature"

$dossierCles = Join-Path $Racine "cles"
New-Item -ItemType Directory -Force -Path $dossierCles | Out-Null
$jks = Join-Path $dossierCles "sankai-release.jks"

if (Test-Path $jks) {
    Ecrire-Alerte "Une cle existe deja : $jks"
    Write-Host "  Elle n'est PAS ecrasee (ce serait irreversible)."
    Write-Host "  Supprime-la manuellement si tu veux vraiment en regenerer une."
    exit 0
}

if (-not $MotDePasse) {
    # Mot de passe genere aleatoirement et ecrit dans keystore.properties.
    $carac = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    $MotDePasse = -join (1..24 | ForEach-Object { $carac[(Get-Random -Maximum $carac.Length)] })
    Ecrire-Alerte "Aucun mot de passe fourni : un mot de passe fort a ete genere."
}

$dn = "CN=$Nom, OU=Sankai, O=Sankai, L=Paris, S=IDF, C=FR"
& "$JavaHome\bin\keytool.exe" -genkeypair -v `
    -keystore $jks -alias $Alias `
    -keyalg RSA -keysize 4096 -validity 10000 `
    -storepass $MotDePasse -keypass $MotDePasse `
    -dname $dn
if ($LASTEXITCODE -ne 0) { Ecrire-Erreur "keytool a echoue."; exit 1 }

$props = @"
# Genere le $(Get-Date -Format 'yyyy-MM-dd HH:mm') par 03-generer-keystore.ps1
# NE JAMAIS COMMITTER CE FICHIER NI LE .jks
storeFile=../cles/sankai-release.jks
storePassword=$MotDePasse
keyAlias=$Alias
keyPassword=$MotDePasse
"@
$props | Out-File -FilePath (Join-Path $Projet "keystore.properties") -Encoding utf8

Ecrire-Titre "Cle creee"
Write-Host "  Fichier      : $jks"
Write-Host "  Alias        : $Alias"
Write-Host "  Mot de passe : $MotDePasse" -ForegroundColor Yellow
Write-Host ""
Ecrire-Alerte "SAUVEGARDE ces deux elements ailleurs (cloud chiffre, gestionnaire"
Ecrire-Alerte "de mots de passe, cle USB). Sans eux, plus aucune mise a jour possible."
