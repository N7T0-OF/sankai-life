# Importe les PNG source dans les ressources Android.
#
# Les originaux font 2048x2048 et jusqu'a 6 Mo piece. Les embarquer tels quels
# ajouterait plus de 30 Mo a un APK qui en fait 3,5. Ce script les reduit a la
# taille reellement affichee, en conservant la transparence.
#
# Les originaux restent dans assets_source/original_png : c'est la source, on
# n'y touche pas. Relancer ce script regenere les ressources.

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$racine  = Split-Path -Parent $MyInvocation.MyCommand.Path
$source  = Join-Path $racine "original_png"
$projet  = Split-Path -Parent $racine
$res     = Join-Path $projet "SankaiLife\app\src\main\res"

function Redimensionner {
    param([string]$Entree, [string]$Sortie, [int]$Taille)

    $img = [System.Drawing.Image]::FromFile($Entree)
    try {
        $bmp = New-Object System.Drawing.Bitmap($Taille, $Taille,
            [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        try {
            $g.CompositingMode    = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
            $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
            $g.InterpolationMode  = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $g.SmoothingMode      = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
            $g.PixelOffsetMode    = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $g.DrawImage($img, 0, 0, $Taille, $Taille)
        } finally { $g.Dispose() }

        $dossier = Split-Path -Parent $Sortie
        if (-not (Test-Path $dossier)) { New-Item -ItemType Directory -Force $dossier | Out-Null }
        $bmp.Save($Sortie, [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()
    } finally { $img.Dispose() }

    $ko = [math]::Round((Get-Item $Sortie).Length / 1KB)
    "  {0,-46} {1,4}x{1,-4} {2,5} ko" -f (Split-Path -Leaf $Sortie), $Taille, $ko
}

# --- Parcelles et plantes -------------------------------------------------
# drawable-nodpi : ces images ont une taille unique, Android ne doit pas les
# redimensionner par densite. Une case de jardin fait environ 76 dp, donc
# 256 px couvrent meme un ecran a forte densite.
"Parcelles et plantes"
$nodpi = Join-Path $res "drawable-nodpi"
foreach ($n in @("plot_empty", "plot_prepared", "plot_watered")) {
    Redimensionner (Join-Path $source "$n.png") (Join-Path $nodpi "$n.png") 256
}
foreach ($n in @("plant_stage_0_seed", "plant_stage_1", "plant_stage_2",
                 "plant_stage_3", "plant_stage_4", "plant_stage_5_ready")) {
    Redimensionner (Join-Path $source "$n.png") (Join-Path $nodpi "$n.png") 224
}

# --- Ressources -----------------------------------------------------------
"Ressources"
Redimensionner (Join-Path $source "currency_coin.png") (Join-Path $nodpi "currency_coin.png") 128

# --- Icone de l'application ----------------------------------------------
#
# La premiere version mettait l'illustration en couche de FOND, pleine page.
# Resultat : Android rogne 18 dp de chaque cote sur 108, soit un tiers de
# l'image, et le visage arrivait enorme et coupe.
#
# L'illustration passe donc en couche AVANT, reduite a 62 % du canevas et
# centree, avec des marges transparentes. Elle tient alors entierement dans la
# zone sure quel que soit le masque du telephone : cercle, carre arrondi,
# goutte, ou icone thematique.
function IconeAvantPlan {
    param([string]$Entree, [string]$Sortie, [int]$Taille)

    $img = [System.Drawing.Image]::FromFile($Entree)
    try {
        $bmp = New-Object System.Drawing.Bitmap($Taille, $Taille,
            [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        try {
            $g.Clear([System.Drawing.Color]::Transparent)
            $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
            $g.InterpolationMode  = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $g.SmoothingMode      = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality

            # 62 % : la zone sure d'Android fait 66/108, on garde une marge.
            $interieur = [int]($Taille * 0.62)
            $decalage  = [int](($Taille - $interieur) / 2)
            $g.DrawImage($img, $decalage, $decalage, $interieur, $interieur)
        } finally { $g.Dispose() }

        $dossier = Split-Path -Parent $Sortie
        if (-not (Test-Path $dossier)) { New-Item -ItemType Directory -Force $dossier | Out-Null }
        $bmp.Save($Sortie, [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()
    } finally { $img.Dispose() }

    $ko = [math]::Round((Get-Item $Sortie).Length / 1KB)
    "  {0,-46} {1,4}x{1,-4} {2,5} ko" -f (Split-Path -Leaf $Sortie), $Taille, $ko
}

"Icone de l'application"
$densites = @{ "mdpi" = 1; "hdpi" = 1.5; "xhdpi" = 2; "xxhdpi" = 3; "xxxhdpi" = 4 }
foreach ($d in $densites.Keys) {
    $f = $densites[$d]
    # Icone carree classique, Android 7 et anterieur : pas de rognage, donc
    # l'illustration peut occuper toute la surface.
    Redimensionner (Join-Path $source "app_icon.png") `
        (Join-Path $res "mipmap-$d\ic_launcher.png") ([int](48 * $f))
    # Couche avant de l'icone adaptative.
    IconeAvantPlan (Join-Path $source "app_icon.png") `
        (Join-Path $res "mipmap-$d\ic_launcher_avant.png") ([int](108 * $f))
}
# L'ancienne couche de fond pleine page n'a plus lieu d'etre.
foreach ($d in $densites.Keys) {
    $vieux = Join-Path $res "mipmap-$d\ic_launcher_background.png"
    if (Test-Path $vieux) { Remove-Item $vieux -Force; "  supprime : mipmap-$d/ic_launcher_background.png" }
}
# Play Store : 512 x 512, hors APK.
Redimensionner (Join-Path $source "app_icon.png") `
    (Join-Path $racine "ic_launcher-playstore.png") 512

""
"Termine."
