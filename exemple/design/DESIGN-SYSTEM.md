# 🎨 Design system Sankai Life

Source de vérité dans le code : `SankaiLife/app/src/main/java/com/sankailife/ui/theme/`

---

## Règle d'or

**Aucune couleur codée en dur dans un écran.** Tout passe par
`MaterialTheme.sankaiColors`, sinon le thème clair casse.

```kotlin
val c = MaterialTheme.sankaiColors

Text("Bonjour", color = c.textPrimary)   // ✅
Text("Bonjour", color = Color.White)     // ❌ illisible en thème clair
```

Les couleurs d'accent (`AccentGold`, `SuccessGreen`, `DangerRed`…) sont
identiques dans les deux thèmes : elles s'importent directement.

---

## Palette — thème sombre (par défaut)

| Rôle | Jeton | Hex |
|---|---|---|
| Fond de l'app | `c.background` | `#080808` |
| Barre de ressources | `c.surface1` | `#111111` |
| Cartes | `c.surface2` | `#1A1A1A` |
| Éléments surélevés | `c.surface3` | `#222222` |
| Bordures | `c.border` | `#2A2A2A` |
| Texte principal | `c.textPrimary` | `#F2F2F2` |
| Texte secondaire | `c.textSecondary` | `#888888` |
| Texte désactivé | `c.textDisabled` | `#3A3A3A` |

Fond quasi noir : sur écran AMOLED les pixels sont réellement éteints, ce qui
économise la batterie — cohérent avec le mode économie d'énergie de l'app.

## Palette — thème clair

| Rôle | Hex |
|---|---|
| Fond | `#F5F5F5` |
| Cartes | `#FFFFFF` |
| Surface 2 | `#F0F0F0` |
| Bordures | `#DDDDDD` |
| Texte principal | `#111111` |
| Texte secondaire | `#555555` |

Le choix se fait dans Paramètres : **sombre**, **clair**, ou **automatique**
(suit le réglage du téléphone).

---

## Accents

| Usage | Nom | Hex |
|---|---|---|
| Accent principal, pièces | `AccentGold` | `#F5A623` |
| Focus, boosts | `AccentViolet` | `#7B6CF6` |
| Secondaire | `AccentCyan` | `#22D3EE` |
| Succès, objectifs | `SuccessGreen` | `#4ADE80` |
| Avertissement, streak | `WarningAmber` | `#F59E0B` |
| Danger, suppression | `DangerRed` | `#F87171` |
| Information | `InfoBlue` | `#60A5FA` |
| Gemmes | `GemColor` | `#A78BFA` |

## Raretés de coffres

| Rareté | Couleur | Hex |
|---|---|---|
| Commun | gris | `#6B7280` |
| Rare | bleu | `#3B82F6` |
| Épique | violet | `#8B5CF6` |
| Légendaire | ambre | `#F59E0B` |
| Quotidien | vert | `#4ADE80` |

---

## Formes et espacements

| Élément | Valeur |
|---|---|
| Rayon des cartes | 16 dp |
| Rayon des boutons | 12 dp |
| Rayon des petits éléments | 10 dp |
| Padding d'écran | 16 dp |
| Espace entre cartes | 8 dp |
| Espace avant un titre de section | 20 dp |
| Icône dans une carte | 44–48 dp |

## Tailles de texte

| Usage | Taille | Graisse |
|---|---|---|
| Titre d'écran | 24 sp | Bold |
| Titre de carte | 15 sp | SemiBold |
| Corps de texte | 13–14 sp | Normal |
| Texte secondaire | 12 sp | Normal |
| Titre de section | 11 sp | SemiBold, majuscules, interlettrage 1.2 |

---

## Composants réutilisables

Dans `ui/components/CommonComponents.kt` :

| Composant | Signature | Rôle |
|---|---|---|
| `ResourceBar` | `(level, xp, xpNext, coins, gems)` | Bandeau permanent façon Clash Royale |
| `SankaiCard` | `(modifier, onClick?) { }` | La carte standard |
| `SankaiButton` | `(text, onClick, modifier, enabled, secondary, small)` | Le bouton standard |
| `SectionTitle` | `(text)` | Titre de section en majuscules |
| `StreakBadge` | `(streak)` | Pastille 🔥 du streak |
| `LevelUpDialog` | `(level, coins, onDismiss)` | Popup de montée de niveau |
| `ChestRewardDialog` | `(titre, coins, gems, xp, onDismiss)` | Popup d'ouverture de coffre |

Créer un nouvel écran = assembler ces composants. N'ajoute un composant au
fichier commun que s'il sert dans au moins deux écrans.

---

## États d'interface

| État | Traitement |
|---|---|
| Bouton désactivé | Opacité 50 %, non cliquable |
| Hors ligne | Bouton grisé + libellé explicite (« Hors ligne », « Connexion requise ») |
| Liste vide | Emoji + phrase + action suggérée, jamais un écran blanc |
| Chargement | Indicateur minimal, jamais de plein écran bloquant |

---

## Animations

Discrètes et courtes. Le mode économie de batterie (Paramètres) les réduit
encore.

| Interaction | Effet |
|---|---|
| Appui sur un bouton | Léger scale + vibration courte |
| Gain de pièces | `+5 🪙` flottant |
| Ouverture de coffre | Scale + fondu, items un par un |
| Montée de niveau | Halo violet + vibration plus marquée + popup |
| Onglet actif (barre du bas) | Icône pleine + halo d'accent |

---

## Navigation

Barre du bas fixe, l'Accueil au centre :

```
🛒 Shop  |  🧠 Mode Vie  |  🏠 Accueil  |  🎯 Défis  |  👤 Profil
```

- Icône pleine + couleur d'accent quand l'onglet est actif, contour gris sinon
- Les libellés texte sont désactivables dans Paramètres
- Vibration légère au changement d'onglet
- Masquée sur les sous-écrans : Paramètres, Éditeur de mémo, Objectifs

---

## Principes UX

1. **Un écran = un objectif.** Trois actions visibles au maximum.
2. **Toujours montrer une récompense en attente** — un coffre, un défi
   réclamable, une barre d'XP qui avance.
3. **Tout doit répondre en moins d'une seconde.**
4. **Le hors-ligne n'est pas un cas dégradé** : c'est le mode nominal.
