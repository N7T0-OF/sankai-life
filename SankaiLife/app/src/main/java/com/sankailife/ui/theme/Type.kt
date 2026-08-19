package com.sankailife.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sankailife.R

/**
 * La police d'identité de Sankai Life, tirée des maquettes.
 *
 * C'est une police manuscrite, dessinée à la main : elle donne la
 * personnalité, elle ne porte pas la lecture. Elle est réservée aux
 * éléments d'identité — titres, chiffres, noms — et jamais aux paragraphes.
 * Les caractères accentués manquants retombent automatiquement sur la police
 * système : un texte reste toujours lisible, quelle que soit sa langue.
 */
val Drawxsouanpt = FontFamily(
    Font(R.font.drawxsouanpt, FontWeight.Normal)
)

/**
 * Hiérarchie typographique Sankai.
 *
 * Display, Headline et Title portent l'identité (Drawxsouanpt) ; Body et
 * Label restent sur la police système, lisible — c'est là que vivent les
 * définitions, les exercices et les réglages, qui doivent se lire sans
 * effort. Letter-spacing resserré sur les grandes tailles, où une police
 * manuscrite respire.
 */
val SankaiTypography = Typography(
    displayLarge  = TextStyle(fontFamily = Drawxsouanpt, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 40.sp),
    displayMedium = TextStyle(fontFamily = Drawxsouanpt, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(fontFamily = Drawxsouanpt, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 30.sp),
    headlineMedium= TextStyle(fontFamily = Drawxsouanpt, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge    = TextStyle(fontFamily = Drawxsouanpt, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 23.sp),
    titleMedium   = TextStyle(fontFamily = Drawxsouanpt, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyLarge     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,  fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,  fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,  fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.08.sp)
)
