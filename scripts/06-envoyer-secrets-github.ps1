# ---------------------------------------------------------------------------
# Envoie la clé de signature vers les secrets chiffrés de GitHub Actions,
# pour que les Releases soient signées avec une signature stable.
#
# Le mot de passe n'est jamais affiché : il est lu dans keystore.properties et
# passé directement à l'API GitHub. Les secrets GitHub sont chiffrés côté
# client avant l'envoi (boîte scellée libsodium), y compris pour GitHub.
# ---------------------------------------------------------------------------

. "$PSScriptRoot\_env.ps1"

Ecrire-Titre "SANKAI LIFE - Envoi des secrets de signature vers GitHub"

$depot = "N7T0-OF/sankai-life"
$jks = Join-Path $Racine "cles\sankai-release.jks"
$props = Join-Path $Projet "keystore.properties"

if (-not (Test-Path $jks))   { Ecrire-Erreur "Clé absente : $jks"; exit 1 }
if (-not (Test-Path $props)) { Ecrire-Erreur "keystore.properties absent"; exit 1 }

# --- 1. gh CLI, installé localement comme le reste de la toolchain ---------
# L'archive gh place gh.exe tantôt à la racine, tantôt dans bin\ selon la
# version : on le cherche au lieu de deviner.
function Trouver-Gh {
    $dossier = Join-Path $Outils "gh"
    if (-not (Test-Path $dossier)) { return $null }
    (Get-ChildItem -Recurse -File $dossier -Filter "gh.exe" | Select-Object -First 1).FullName
}

$ghExe = Trouver-Gh
if (-not $ghExe) {
    Ecrire-Etape "Installation de GitHub CLI dans outils\gh ..."
    $api = "https://api.github.com/repos/cli/cli/releases/latest"
    $release = Invoke-RestMethod -Uri $api -UseBasicParsing -TimeoutSec 60
    $asset = $release.assets | Where-Object { $_.name -like "*windows_amd64.zip" } | Select-Object -First 1
    if (-not $asset) { Ecrire-Erreur "Archive gh introuvable."; exit 1 }

    $zip = Join-Path $Outils "_telechargements\gh.zip"
    Telecharger-Fichier -Url $asset.browser_download_url -Destination $zip

    $tmp = Join-Path $Outils "_tmp_gh"
    if (Test-Path $tmp) { Remove-Item -LiteralPath $tmp -Recurse -Force }
    Extraire-Zip -Zip $zip -Vers $tmp
    $interne = Get-ChildItem $tmp -Directory | Select-Object -First 1
    $cible = Join-Path $Outils "gh"
    if (Test-Path $cible) { Remove-Item -LiteralPath $cible -Recurse -Force }
    Move-Item $interne.FullName $cible
    Remove-Item -LiteralPath $tmp -Recurse -Force
    $ghExe = Trouver-Gh
    if (-not $ghExe) { Ecrire-Erreur "gh.exe introuvable après extraction."; exit 1 }
    Ecrire-Ok "GitHub CLI installé."
} else {
    Ecrire-Ok "GitHub CLI déjà présent."
}

# --- 2. Jeton, récupéré du gestionnaire d'identifiants Git ------------------
Ecrire-Etape "Récupération du jeton GitHub ..."
$tmpCred = Join-Path $env:TEMP "sankai_secret_cred.txt"
"protocol=https`nhost=github.com`n" | Out-File -FilePath $tmpCred -Encoding ascii -NoNewline
$res = cmd /c "git credential fill < `"$tmpCred`""
Clear-Content -LiteralPath $tmpCred
$jeton = ((($res -split "`r?`n") | Where-Object { $_ -like "password=*" }) -replace "^password=","")
if (-not $jeton) { Ecrire-Erreur "Impossible de récupérer un jeton GitHub."; exit 1 }
$env:GH_TOKEN = $jeton
Ecrire-Ok "Jeton récupéré."

# --- 3. Lecture des valeurs ------------------------------------------------
$lignes = Get-Content $props
function Lire($cle) {
    $l = $lignes | Where-Object { $_ -like "$cle=*" } | Select-Object -First 1
    if ($l) { ($l -replace "^$cle=", "").Trim() } else { $null }
}
$motDePasse = Lire "storePassword"
$alias = Lire "keyAlias"
if (-not $motDePasse -or -not $alias) { Ecrire-Erreur "keystore.properties incomplet."; exit 1 }

$base64 = [System.Convert]::ToBase64String([System.IO.File]::ReadAllBytes($jks))

# --- 4. Envoi --------------------------------------------------------------
Ecrire-Etape "Envoi des secrets vers $depot ..."
$fichierB64 = Join-Path $env:TEMP "sankai_ks_b64.txt"
$base64 | Out-File -FilePath $fichierB64 -Encoding ascii -NoNewline

Exec $ghExe @("secret", "set", "KEYSTORE_BASE64", "--repo", $depot, "--body", $base64)
Exec $ghExe @("secret", "set", "KEYSTORE_PASSWORD", "--repo", $depot, "--body", $motDePasse)
Exec $ghExe @("secret", "set", "KEY_ALIAS", "--repo", $depot, "--body", $alias)

Clear-Content -LiteralPath $fichierB64
Remove-Item -LiteralPath $fichierB64 -Force
Remove-Item -LiteralPath $tmpCred -Force

Ecrire-Titre "Secrets envoyés"
Write-Host "  KEYSTORE_BASE64    : OK"
Write-Host "  KEYSTORE_PASSWORD  : OK"
Write-Host "  KEY_ALIAS          : $alias"
Write-Host ""
Write-Host "  Les prochaines Releases seront signées avec cette clé." -ForegroundColor Cyan
Write-Host "  Les mises à jour s'installeront alors par-dessus, sans perte de données."
