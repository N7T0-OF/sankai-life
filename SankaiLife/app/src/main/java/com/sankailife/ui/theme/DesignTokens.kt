package com.sankailife.ui.theme

import androidx.compose.ui.unit.dp

/** Espacements partagés par les interfaces Sankai. */
object SankaiSpacing {
    val Xxs = 2.dp
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp
}

/** Rayons communs : les composants choisissent une intention, pas une valeur locale. */
object SankaiRadius {
    val Small = 10.dp
    val Medium = 16.dp
    val Large = 24.dp
    val Navigation = 28.dp
    val Pill = 999.dp
}

/** Élévations réservées aux rares éléments qui doivent quitter le plan du contenu. */
object SankaiElevation {
    val None = 0.dp
    val Low = 2.dp
    val Medium = 6.dp
    val High = 12.dp
}

/** Durées en millisecondes, utilisables directement par les specs Compose. */
object SankaiMotion {
    const val Fast = 170
    const val Standard = 240
    const val Emphasis = 420
    const val RewardPulse = 1_050
}

/** Opacités des surfaces de verre selon leur rôle. */
object SankaiGlass {
    const val ChipIntensity = 0.88f
    const val CardIntensity = 0.92f
    const val FloatingIntensity = 0.90f
    const val NavigationIntensity = 0.94f
    const val SelectedHighlight = 0.18f
}
