# ---------------------------------------------------------------------------
# Environnement partagé Sankai Life
# A "dot-sourcer" depuis les autres scripts :  . "$PSScriptRoot\_env.ps1"
# Definit : $Racine, $Outils, $Projet, $Dist, $JavaHome, $AndroidHome, $GradleBin
# ---------------------------------------------------------------------------

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$ProgressPreference = "SilentlyContinue"

$Racine  = Split-Path -Parent $PSScriptRoot
$Outils  = Join-Path $Racine "outils"
$Projet  = Join-Path $Racine "SankaiLife"
$Dist    = Join-Path $Racine "dist"

# Versions de la toolchain (modifiables ici, un seul endroit)
$JdkVersion       = "17"
$GradleVersion    = "8.9"
$CmdlineToolsZip  = "commandlinetools-win-11076708_latest.zip"
$AndroidPlatform  = "android-35"
$AndroidBuildTools = "35.0.0"

$JavaHome    = Join-Path $Outils "jdk"
$AndroidHome = Join-Path $Outils "android-sdk"
$GradleHome  = Join-Path $Outils "gradle-$GradleVersion"
$GradleBin   = Join-Path $GradleHome "bin\gradle.bat"
$SdkManager  = Join-Path $AndroidHome "cmdline-tools\latest\bin\sdkmanager.bat"
$Adb         = Join-Path $AndroidHome "platform-tools\adb.exe"

function Set-SankaiEnv {
    <# Applique JAVA_HOME / ANDROID_HOME / PATH au processus courant. #>
    if (Test-Path $JavaHome)    { $env:JAVA_HOME = $JavaHome }
    if (Test-Path $AndroidHome) {
        $env:ANDROID_HOME = $AndroidHome
        $env:ANDROID_SDK_ROOT = $AndroidHome
    }
    $ajouts = @()
    if (Test-Path "$JavaHome\bin")               { $ajouts += "$JavaHome\bin" }
    if (Test-Path "$AndroidHome\platform-tools") { $ajouts += "$AndroidHome\platform-tools" }
    if (Test-Path "$GradleHome\bin")             { $ajouts += "$GradleHome\bin" }
    if ($ajouts.Count -gt 0) {
        $env:PATH = ($ajouts -join ";") + ";" + $env:PATH
    }
    # Gradle doit utiliser NOTRE JDK, pas un JDK système hypothétique.
    $env:GRADLE_OPTS = "-Dorg.gradle.java.home=`"$JavaHome`""
}

function Exec {
    <#
      Lance un executable externe sans que PowerShell 5.1 ne transforme sa
      sortie stderr en erreur bloquante (java, sdkmanager, gradle et adb
      ecrivent tous des informations normales sur stderr).
      Leve une exception si le code de retour n'est pas 0.
    #>
    param(
        [Parameter(Mandatory)][string]$Fichier,
        [string[]]$Arguments = @(),
        [switch]$IgnorerCode
    )
    $ancien = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $code = 1
    try {
        & $Fichier @Arguments
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $ancien
    }
    if (-not $IgnorerCode -and $code -ne 0) {
        throw "Commande en echec (code $code) : $Fichier $($Arguments -join ' ')"
    }
    return $code
}

function Ecrire-Titre($texte) {
    Write-Host ""
    Write-Host ("=" * 72) -ForegroundColor DarkCyan
    Write-Host "  $texte" -ForegroundColor Cyan
    Write-Host ("=" * 72) -ForegroundColor DarkCyan
}

function Ecrire-Etape($texte) { Write-Host "  -> $texte" -ForegroundColor Gray }
function Ecrire-Ok($texte)    { Write-Host "  [OK] $texte" -ForegroundColor Green }
function Ecrire-Alerte($texte){ Write-Host "  [!]  $texte" -ForegroundColor Yellow }
function Ecrire-Erreur($texte){ Write-Host "  [X]  $texte" -ForegroundColor Red }

function Telecharger-Fichier {
    param([Parameter(Mandatory)][string]$Url, [Parameter(Mandatory)][string]$Destination)

    if (Test-Path $Destination) {
        $tailleMo = [math]::Round((Get-Item $Destination).Length / 1MB, 1)
        Ecrire-Ok "Deja telecharge ($tailleMo Mo) : $(Split-Path -Leaf $Destination)"
        return
    }
    $dossier = Split-Path -Parent $Destination
    if (-not (Test-Path $dossier)) { New-Item -ItemType Directory -Force -Path $dossier | Out-Null }

    Ecrire-Etape "Telechargement $(Split-Path -Leaf $Destination) ..."
    $temp = "$Destination.partiel"
    if (Test-Path $temp) { Remove-Item $temp -Force }

    $client = New-Object System.Net.WebClient
    $client.Headers.Add("User-Agent", "SankaiLife-Setup")
    try {
        $client.DownloadFile($Url, $temp)
    } finally {
        $client.Dispose()
    }
    Move-Item $temp $Destination -Force
    $tailleMo = [math]::Round((Get-Item $Destination).Length / 1MB, 1)
    Ecrire-Ok "Telecharge : $(Split-Path -Leaf $Destination) ($tailleMo Mo)"
}

function Extraire-Zip {
    param([Parameter(Mandatory)][string]$Zip, [Parameter(Mandatory)][string]$Vers)
    if (-not (Test-Path $Vers)) { New-Item -ItemType Directory -Force -Path $Vers | Out-Null }
    Ecrire-Etape "Extraction $(Split-Path -Leaf $Zip) ..."
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::ExtractToDirectory($Zip, $Vers)
}
