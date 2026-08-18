package com.sankailife.ui.theme

import androidx.compose.ui.graphics.Color

// ── DARK AMOLED ──────────────────────────────────────────────────
// Bleu nuit plutôt que noir pur : les écrans gardent un contraste AMOLED
// élevé, mais partagent désormais la profondeur visuelle d'un hub de jeu.
val Background      = Color(0xFF061521)
val Surface1        = Color(0xFF0B2234)
val Surface2        = Color(0xFF12334A)
val Surface3        = Color(0xFF1B4560)
val BorderColor     = Color(0xFF2D5D76)

val TextPrimary     = Color(0xFFF5F8FC)
val TextSecondary   = Color(0xFFABC1D2)
val TextDisabled    = Color(0xFF587184)

// Accents
val AccentGold      = Color(0xFFF5A623)
val AccentViolet    = Color(0xFF7B6CF6)
val AccentCyan      = Color(0xFF22D3EE)

// Intentions propres au jeu. Elles évitent que chaque écran invente son bleu,
// son vert d'action ou son or de récompense.
val GameNavyTop     = Color(0xFF0B2A44)
val GameNavyBottom  = Color(0xFF04111C)
val GameBlue        = Color(0xFF2A8FD5)
val GardenGreen     = Color(0xFF55B85A)
val RewardGold      = Color(0xFFFFC247)
val RewardGoldDark  = Color(0xFFB86708)

// Status
val SuccessGreen    = Color(0xFF4ADE80)
val WarningAmber    = Color(0xFFF59E0B)
val DangerRed       = Color(0xFFF87171)
val InfoBlue        = Color(0xFF60A5FA)

// Currencies
val CoinColor       = Color(0xFFF5A623)
val GemColor        = Color(0xFFA78BFA)

// Chests
val ChestCommon     = Color(0xFF6B7280)
val ChestRare       = Color(0xFF3B82F6)
val ChestEpic       = Color(0xFF8B5CF6)
val ChestLegendary  = Color(0xFFF59E0B)
val ChestDaily      = Color(0xFF4ADE80)

// ── LIGHT ────────────────────────────────────────────────────────
val LightBackground = Color(0xFFF5F5F5)
val LightSurface1   = Color(0xFFFFFFFF)
val LightSurface2   = Color(0xFFF0F0F0)
val LightSurface3   = Color(0xFFE8E8E8)
val LightBorder     = Color(0xFFDDDDDD)
val LightTextPrimary   = Color(0xFF111111)
val LightTextSecondary = Color(0xFF555555)

// ── PROFIL (maquette « Swann ») ───────────────────────────────────
// La structure du profil vient de la maquette ; seuls les accents restent
// fixes, le reste suit le thème comme les autres écrans.
val ProfileAvatar    = Color(0xFFD38260) // pastel pêche de l'avatar

// ── CULTURE (maquette 194059 · 194232) ───────────────────────────
// La lecture culturelle est volontairement lumineuse, même en thème
// sombre : papier chaud, carte blanche, encre et accent bleu profond.
val CultureBg       = Color(0xFFF6F2E9)
val CultureCard     = Color(0xFFFFFFFF)
val CultureInk      = Color(0xFF2B241E)
val CultureInkSoft  = Color(0xFF6B625A)
val CultureBorder   = Color(0xFFE7E0D2)
val CultureBlue     = Color(0xFF1208CC)
