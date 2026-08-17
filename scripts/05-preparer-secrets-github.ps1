# ---------------------------------------------------------------------------
# Encode la clé de signature en base64 pour pouvoir la confier à GitHub
# Actions sous forme de secret chiffré.
#
# Le résultat est ECRIT DANS UN FICHIER et jamais affiché à l'écran : une clé
# privée n'a rien à faire dans un historique de terminal.
# ---------------------------------------------------------------------------

. "$PSScriptRoot\_env.ps1"

Ecrire-Titre "SANKAI LIFE - Préparer les secrets GitHub"

$jks = Join-Path $Racine "cles\sankai-release.jks"
if (-not (Test-Path $jks)) {
    Ecrire-Erreur "Aucune clé de signature trouvée."
    Write-Host "  Lance d'abord : COMPILER-VERSION-PLAY-STORE.bat"
    exit 1
}

$sortie = Join-Path $Racine "cles\keystore-base64.txt"
$octets = [System.IO.File]::ReadAllBytes($jks)
[System.Convert]::ToBase64String($octets) | Out-File -FilePath $sortie -Encoding ascii -NoNewline
Ecrire-Ok "Clé encodée dans cles\keystore-base64.txt"

# Le mot de passe se trouve déjà dans keystore.properties : on rappelle où le
# lire plutôt que de le réafficher ici.
$props = Join-Path $Projet "keystore.properties"
$alias = "sankai"
if (Test-Path $props) {
    $ligne = Get-Content $props | Where-Object { $_ -like "keyAlias=*" }
    if ($ligne) { $alias = ($ligne -replace "^keyAlias=", "").Trim() }
}

Ecrire-Titre "À créer sur GitHub"
Write-Host "  Settings > Secrets and variables > Actions > New repository secret"
Write-Host ""
Write-Host "  KEYSTORE_BASE64    = tout le contenu de cles\keystore-base64.txt"
Write-Host "  KEYSTORE_PASSWORD  = le storePassword lu dans SankaiLife\keystore.properties"
Write-Host "  KEY_ALIAS          = $alias"
Write-Host ""
Ecrire-Alerte "Une fois les secrets créés sur GitHub, SUPPRIME le fichier :"
Write-Host "     Remove-Item `"$sortie`"" -ForegroundColor Yellow
Write-Host ""
Write-Host "  Guide complet : exemple\guides\GITHUB-RECUPERER-APK.md" -ForegroundColor Cyan
