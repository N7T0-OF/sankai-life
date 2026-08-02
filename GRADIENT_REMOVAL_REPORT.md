# Suppression des dégradés d'interface

Audit et correction du 2 août 2026, après signalement : les couleurs
dynamiques fonctionnaient, mais d'anciens dégradés fixes restaient peints
par-dessus, donnant à un même composant deux identités visuelles.

**18 dégradés trouvés, 15 supprimés, 3 conservés.**

## Supprimés

| Fichier | Composant | Ancien dégradé | Remplacement |
|---|---|---|---|
| `components/CommonComponents.kt` | Bouton principal | `RewardGold → RewardGoldDark` **codé en dur** | `colorScheme.primary` |
| `components/CommonComponents.kt` | Bouton désactivé | `surface3 → surface2` | `surface3` |
| `components/CommonComponents.kt` | Bouton secondaire | `surface3 → surface2` | `surface2` |
| `components/LiquidGlass.kt` | Fond de verre | deux teintes de surface | `surfaceContainerHigh` |
| `components/LiquidGlass.kt` | Liseré | blanc dégradé **fixe** | `outlineVariant` |
| `screens/home/HomeScreen.kt` | **Bouton « Entrer dans le Jardin »** | vert + or + violet, trois teintes | `colorScheme.primary` |
| `screens/home/HomeScreen.kt` | Cercle d'avatar | `accentSecondary → accent` | `primaryContainer` |
| `screens/home/HomeScreen.kt` | Halo de coffre | radial d'accent | accent uni à 18 % |
| `screens/home/HomeScreen.kt` | Fond d'écran | `background → accentSecondary → background` | `background` |
| `screens/arenas/ArenasScreen.kt` | Fond d'écran | idem | `background` |
| `screens/arenas/ArenasScreen.kt` | Voile de carte (×2) | accent → transparent | accent uni à 6-8 % |
| `screens/life/memo/MemoScreen.kt` | Fond d'écran | `GameNavyTop → GameNavyBottom` **codé en dur** | `background` |
| `screens/onboarding/OnboardingScreen.kt` | Fond d'écran | `0xFF0E1A13` **codé en dur** | `background` |
| `screens/garden/HudJardin.kt` | Bouton rond du HUD | deux verts **codés en dur** | `surfaceContainerHigh` |

Le plus visible était le **bouton principal** : un dégradé or codé en dur, donc
identique quelle que soit la palette du téléphone. Toute l'application pouvait
virer au jaune ou au vert, les boutons restaient or.

## Conservés — et pourquoi

| Fichier | Rôle |
|---|---|
| `screens/garden/GrilleJardin.kt:240` | Fond du terrain |
| `screens/garden/GrilleJardin.kt:626` | Lumière météo |
| `screens/garden/GrilleJardin.kt:646` | Halo d'éclairage |

Ce sont des **effets de monde**, pas des fonds de composant. Un ciel ou une
lumière rasante n'a aucune raison de suivre la palette d'un fond d'écran, et
les aplatir donnerait un Jardin en carton.

## Le rectangle sous la barre de navigation

Cause trouvée : `Scaffold` était appelé **sans `containerColor`**. Il peignait
donc `colorScheme.background` sur toute sa surface — barre basse comprise —
pendant que chaque écran peignait son propre fond par-dessus. Les deux ne
coïncidant pas, un bandeau d'une autre couleur apparaissait derrière la barre
flottante.

Corrigé en deux temps : `containerColor = Color.Transparent` sur le `Scaffold`,
et le fond peint **une seule fois** par la `Surface` racine. Les écrans qui
repeignaient le leur produisaient des raccords visibles dès que la palette
changeait.
