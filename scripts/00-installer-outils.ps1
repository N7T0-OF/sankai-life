# ---------------------------------------------------------------------------
# Installe TOUTE la chaine de compilation Android dans le dossier "outils\".
#   - JDK 17 (Eclipse Temurin)
#   - Android SDK (command-line tools + platform-tools + platform 35 + build-tools)
#   - Gradle 8.9 + generation du wrapper
#
# Rien n'est installe dans Windows : tout reste dans ce dossier, aucun droit
# administrateur necessaire, et supprimer "outils\" desinstalle tout.
# ---------------------------------------------------------------------------

. "$PSScriptRoot\_env.ps1"

$cache = Join-Path $Outils "_telechargements"
New-Item -ItemType Directory -Force -Path $Outils, $cache, $Dist | Out-Null

Ecrire-Titre "SANKAI LIFE - Installation de la chaine de compilation"
Write-Host "  Dossier cible : $Outils"
Write-Host "  Environ 600 Mo a telecharger la premiere fois."

# --- 1. JDK 17 ------------------------------------------------------------
Ecrire-Titre "1/4  JDK $JdkVersion (Eclipse Temurin)"
if (Test-Path "$JavaHome\bin\java.exe") {
    Ecrire-Ok "JDK deja installe."
} else {
    $api = "https://api.adoptium.net/v3/assets/latest/$JdkVersion/hotspot" +
           "?architecture=x64&image_type=jdk&os=windows&vendor=eclipse"
    Ecrire-Etape "Recherche de la derniere version sur adoptium.net ..."
    $assets = Invoke-RestMethod -Uri $api -UseBasicParsing -TimeoutSec 60
    $lien = ($assets | Where-Object { $_.binary.package.link -like "*.zip" } |
             Select-Object -First 1).binary.package.link
    if (-not $lien) { throw "Impossible de trouver un JDK $JdkVersion .zip sur Adoptium." }

    $zip = Join-Path $cache "jdk$JdkVersion.zip"
    Telecharger-Fichier -Url $lien -Destination $zip

    $tmp = Join-Path $Outils "_tmp_jdk"
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Extraire-Zip -Zip $zip -Vers $tmp
    $interne = Get-ChildItem $tmp -Directory | Select-Object -First 1
    if (Test-Path $JavaHome) { Remove-Item $JavaHome -Recurse -Force }
    Move-Item $interne.FullName $JavaHome
    Remove-Item $tmp -Recurse -Force
    Ecrire-Ok "JDK installe dans $JavaHome"
}
Set-SankaiEnv
Exec "$JavaHome\bin\java.exe" @("-version")

# --- 2. Android SDK -------------------------------------------------------
Ecrire-Titre "2/4  Android SDK (command-line tools)"
if (Test-Path $SdkManager) {
    Ecrire-Ok "command-line tools deja installes."
} else {
    $zip = Join-Path $cache $CmdlineToolsZip
    Telecharger-Fichier -Url "https://dl.google.com/android/repository/$CmdlineToolsZip" -Destination $zip

    $tmp = Join-Path $Outils "_tmp_sdk"
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Extraire-Zip -Zip $zip -Vers $tmp
    $cible = Join-Path $AndroidHome "cmdline-tools"
    New-Item -ItemType Directory -Force -Path $cible | Out-Null
    Move-Item (Join-Path $tmp "cmdline-tools") (Join-Path $cible "latest")
    Remove-Item $tmp -Recurse -Force
    Ecrire-Ok "command-line tools installes."
}

# Licences : on ecrit directement les empreintes officielles pour eviter toute
# invite interactive (sdkmanager --licenses bloque sinon en mode non-interactif).
Ecrire-Etape "Acceptation des licences Android SDK ..."
$licDir = Join-Path $AndroidHome "licenses"
New-Item -ItemType Directory -Force -Path $licDir | Out-Null
$licences = @{
    "android-sdk-license"            = @("8933bad161af4178b1185d1a37fbf41ea5269c55",
                                         "d56f5187479451eabf01fb78af6dfcb131a6481e",
                                         "24333f8a63b6825ea9c5514f83c2829b004d1fee")
    "android-sdk-preview-license"    = @("84831b9409646a918e30573bab4c9c91346d8abd")
    "android-sdk-arm-dbt-license"    = @("859f317696f67ef3d7f30a50a5560e7834b43903")
    "google-gdk-license"             = @("33b6a2b64607f11b759f320ef9dff4ae5c47d97a")
    "intel-android-extra-license"    = @("d975f751698a77b662f1254ddbeed3901e976f5a")
    "mips-android-sysimage-license"  = @("e9acab5b5fbb560a72cfaecce8946896ff6aab9d")
}
foreach ($nom in $licences.Keys) {
    ($licences[$nom] -join "`n") | Out-File -FilePath (Join-Path $licDir $nom) -Encoding ascii -NoNewline
}
Ecrire-Ok "Licences acceptees."

Ecrire-Titre "3/4  Composants du SDK (platform $AndroidPlatform, build-tools $AndroidBuildTools)"
$paquets = @("platform-tools", "platforms;$AndroidPlatform", "build-tools;$AndroidBuildTools")
foreach ($p in $paquets) {
    Ecrire-Etape "Installation de $p ..."
    Exec $SdkManager @("--sdk_root=$AndroidHome", $p)
}
Ecrire-Ok "Composants SDK installes."

# local.properties : indique a Gradle ou se trouve le SDK.
$sdkPourGradle = $AndroidHome -replace '\\', '\\'
"sdk.dir=$sdkPourGradle" | Out-File -FilePath (Join-Path $Projet "local.properties") -Encoding ascii
Ecrire-Ok "local.properties genere."

# --- 3. Gradle + wrapper --------------------------------------------------
Ecrire-Titre "4/4  Gradle $GradleVersion + wrapper"
if (Test-Path $GradleBin) {
    Ecrire-Ok "Gradle deja installe."
} else {
    $zip = Join-Path $cache "gradle-$GradleVersion-bin.zip"
    Telecharger-Fichier -Url "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip" -Destination $zip
    Extraire-Zip -Zip $zip -Vers $Outils
    Ecrire-Ok "Gradle installe dans $GradleHome"
}

Set-SankaiEnv
if (-not (Test-Path (Join-Path $Projet "gradle\wrapper\gradle-wrapper.jar"))) {
    Ecrire-Etape "Generation du wrapper Gradle (gradlew) ..."
    Push-Location $Projet
    try {
        Exec $GradleBin @("wrapper", "--gradle-version", $GradleVersion,
                          "--distribution-type", "bin", "--no-daemon")
    } finally { Pop-Location }
    Ecrire-Ok "gradlew genere (Android Studio pourra ouvrir le projet directement)."
} else {
    Ecrire-Ok "Wrapper Gradle deja present."
}

Ecrire-Titre "Installation terminee"
Write-Host "  JAVA_HOME    : $JavaHome"
Write-Host "  ANDROID_HOME : $AndroidHome"
Write-Host "  GRADLE       : $GradleHome"
Write-Host ""
Write-Host "  Etape suivante : scripts\01-compiler-apk.ps1  (ou COMPILER-APK.bat)" -ForegroundColor Cyan
