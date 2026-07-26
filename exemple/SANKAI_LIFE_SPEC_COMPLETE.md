# SANKAI LIFE — SPÉCIFICATION COMPLÈTE ANDROID
### Document de production V1 — Pour Android Studio + Cursor (Kotlin + Jetpack Compose)

---

## TABLE DES MATIÈRES
1. Concept & Piliers
2. Stack technique
3. Architecture projet
4. Design System
5. Navigation
6. Écrans détaillés (UI + Fonctions)
7. Systèmes core (XP, Économie, Coffres, Streak)
8. Module Mémo (Clipboard System)
9. Module Focus Timer
10. Module Objectifs
11. Shop & Économie
12. Thèmes & Personnalisation
13. Notifications
14. Stockage local (Room + DataStore)
15. Mode Offline/Online
16. Monétisation (AdMob)
17. Paramètres
18. Anti-abus & Équilibre
19. Flux utilisateur complet

---

## 1. CONCEPT & PILIERS

**Nom app :** Sankai Life
**Tagline :** "Transforme tes habitudes en progression"
**Type :** App productivité gamifiée, offline-first, Android native

### Piliers fondamentaux
- **Utilité réelle** — mémo intelligent, focus timer, objectifs concrets
- **Gamification intelligente** — XP, niveaux, coffres, streak, défis
- **Offline total** — 100% fonctionnel sans connexion internet
- **Léger & rapide** — stockage local, animations légères, démarrage < 1s
- **Monétisation non intrusive** — pubs récompensées uniquement (jamais forcées)

### Style visuel inspiré de
- **Image 1 (fitness app)** : grandes cartes bold, couleurs vives contrastées, typographie massive, layout aéré, stats visuelles avec barres/pourcentages, header avec avatar + stats, cards interactives avec toggle
- **Image 2 (Clash Royale)** : header ressources (monnaie + gemmes + XP), coffres avec timers en bas d'écran, boutons CTA larges et colorés, bottom navigation avec icônes grandes, badges de notification, système de quêtes avec indicateurs rouges

---

## 2. STACK TECHNIQUE

```
Langage         : Kotlin
UI Framework    : Jetpack Compose (Material 3)
Navigation      : Navigation Compose (NavController + NavHost)
DB locale       : Room (SQLite) — entités : User, Module, Memo, Chest, Challenge, Stats
Préférences     : DataStore (Preferences) — settings, thème, état offline
Notifications   : WorkManager + NotificationManager (locales uniquement)
AdMob           : Google Mobile Ads SDK (rewarded ads uniquement)
Architecture    : MVVM (ViewModel + StateFlow + Repository pattern)
DI              : Hilt (injection dépendances)
Tests           : JUnit + Compose Testing
Min SDK         : API 26 (Android 8.0)
Target SDK      : API 35
Orientation     : Portrait uniquement (V1)
```

---

## 3. ARCHITECTURE PROJET

```
sankai_life/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/sankailife/
│   │   │   ├── MainActivity.kt                  ← Entry point, thème, nav host
│   │   │   ├── SankaiApplication.kt             ← Hilt Application
│   │   │   │
│   │   │   ├── core/
│   │   │   │   ├── data/
│   │   │   │   │   ├── db/
│   │   │   │   │   │   ├── SankaiDatabase.kt    ← Room DB instance
│   │   │   │   │   │   ├── dao/
│   │   │   │   │   │   │   ├── UserDao.kt
│   │   │   │   │   │   │   ├── MemoDao.kt
│   │   │   │   │   │   │   ├── ChestDao.kt
│   │   │   │   │   │   │   ├── ChallengeDao.kt
│   │   │   │   │   │   │   ├── StatsDao.kt
│   │   │   │   │   │   │   └── ModuleDao.kt
│   │   │   │   │   │   └── entities/
│   │   │   │   │   │       ├── UserEntity.kt
│   │   │   │   │   │       ├── MemoProfileEntity.kt
│   │   │   │   │   │       ├── MemoLineEntity.kt
│   │   │   │   │   │       ├── ChestEntity.kt
│   │   │   │   │   │       ├── ChallengeEntity.kt
│   │   │   │   │   │       └── StatsEntity.kt
│   │   │   │   │   ├── preferences/
│   │   │   │   │   │   └── AppPreferences.kt    ← DataStore wrapper
│   │   │   │   │   └── repository/
│   │   │   │   │       ├── UserRepository.kt
│   │   │   │   │       ├── MemoRepository.kt
│   │   │   │   │       ├── ChestRepository.kt
│   │   │   │   │       ├── ChallengeRepository.kt
│   │   │   │   │       └── StatsRepository.kt
│   │   │   │   │
│   │   │   │   ├── domain/
│   │   │   │   │   ├── engine/
│   │   │   │   │   │   ├── XpEngine.kt          ← calcul XP + level up
│   │   │   │   │   │   ├── EconomyEngine.kt     ← pièces, gemmes, transactions
│   │   │   │   │   │   ├── ChestEngine.kt       ← timers, drops, ouverture
│   │   │   │   │   │   ├── StreakEngine.kt      ← streak journalier
│   │   │   │   │   │   ├── ChallengeEngine.kt  ← reset, progression, claim
│   │   │   │   │   │   ├── MemoEngine.kt        ← random intelligent, anti-répétition
│   │   │   │   │   │   └── ActionEngine.kt      ← "Action du jour" dynamique
│   │   │   │   │   └── model/
│   │   │   │   │       ├── User.kt
│   │   │   │   │       ├── MemoProfile.kt
│   │   │   │   │       ├── Chest.kt
│   │   │   │   │       ├── Challenge.kt
│   │   │   │   │       ├── Reward.kt
│   │   │   │   │       └── Theme.kt
│   │   │   │   │
│   │   │   │   └── notifications/
│   │   │   │       ├── NotificationScheduler.kt ← WorkManager jobs
│   │   │   │       ├── MemoNotificationWorker.kt
│   │   │   │       └── ReminderNotificationWorker.kt
│   │   │   │
│   │   │   ├── ui/
│   │   │   │   ├── theme/
│   │   │   │   │   ├── Color.kt                 ← toutes les couleurs + thèmes
│   │   │   │   │   ├── Type.kt                  ← typographie
│   │   │   │   │   ├── Shape.kt                 ← border radius
│   │   │   │   │   └── Theme.kt                 ← MaterialTheme dark/light/auto
│   │   │   │   │
│   │   │   │   ├── components/                  ← composants réutilisables
│   │   │   │   │   ├── SankaiCard.kt
│   │   │   │   │   ├── ResourceBar.kt           ← coins + gems header
│   │   │   │   │   ├── XpProgressBar.kt
│   │   │   │   │   ├── ChestSlot.kt
│   │   │   │   │   ├── ChallengeCard.kt
│   │   │   │   │   ├── ModuleCard.kt
│   │   │   │   │   ├── RewardPopup.kt
│   │   │   │   │   ├── LevelUpDialog.kt
│   │   │   │   │   ├── StreakBadge.kt
│   │   │   │   │   ├── StatCard.kt
│   │   │   │   │   ├── SankaiButton.kt          ← bouton primaire/secondaire
│   │   │   │   │   ├── SankaiToggle.kt
│   │   │   │   │   ├── FloatingPlusButton.kt
│   │   │   │   │   └── ToastFeedback.kt         ← "+5 coins" animation flottante
│   │   │   │   │
│   │   │   │   ├── navigation/
│   │   │   │   │   ├── NavGraph.kt              ← toutes les routes
│   │   │   │   │   ├── BottomNavBar.kt          ← bottom navigation custom
│   │   │   │   │   └── Screen.kt                ← sealed class routes
│   │   │   │   │
│   │   │   │   └── screens/
│   │   │   │       ├── home/
│   │   │   │       │   ├── HomeScreen.kt
│   │   │   │       │   └── HomeViewModel.kt
│   │   │   │       ├── life/
│   │   │   │       │   ├── LifeScreen.kt
│   │   │   │       │   ├── LifeViewModel.kt
│   │   │   │       │   ├── memo/
│   │   │   │       │   │   ├── MemoScreen.kt
│   │   │   │       │   │   ├── MemoViewModel.kt
│   │   │   │       │   │   └── MemoEditorScreen.kt
│   │   │   │       │   ├── focus/
│   │   │   │       │   │   ├── FocusScreen.kt
│   │   │   │       │   │   └── FocusViewModel.kt
│   │   │   │       │   └── objectives/
│   │   │   │       │       ├── ObjectivesScreen.kt
│   │   │   │       │       └── ObjectivesViewModel.kt
│   │   │   │       ├── challenges/
│   │   │   │       │   ├── ChallengesScreen.kt
│   │   │   │       │   └── ChallengesViewModel.kt
│   │   │   │       ├── shop/
│   │   │   │       │   ├── ShopScreen.kt
│   │   │   │       │   └── ShopViewModel.kt
│   │   │   │       ├── profile/
│   │   │   │       │   ├── ProfileScreen.kt
│   │   │   │       │   └── ProfileViewModel.kt
│   │   │   │       └── settings/
│   │   │   │           ├── SettingsScreen.kt
│   │   │   │           └── SettingsViewModel.kt
│   │   │   │
│   │   │   └── ads/
│   │   │       ├── AdsManager.kt                ← AdMob rewarded
│   │   │       └── AdsViewModel.kt
│   │   │
│   │   └── res/
│   │       ├── drawable/                        ← icônes SVG/XML
│   │       ├── values/
│   │       │   ├── strings.xml
│   │       │   └── themes.xml
│   │       └── font/                            ← polices custom
│
└── build.gradle.kts
```

---

## 4. DESIGN SYSTEM

### 4.1 Thème par défaut : Dark AMOLED
```kotlin
// Fond principal — AMOLED pur
Background         = #080808
Surface            = #111111
SurfaceVariant     = #1A1A1A
SurfaceElevated    = #222222
SurfaceBorder      = #2A2A2A

// Texte
TextPrimary        = #F2F2F2
TextSecondary      = #888888
TextDisabled       = #3A3A3A

// Accents par défaut (thème OR)
AccentPrimary      = #F5A623   // orange/or — boutons principaux
AccentSecondary    = #7B6CF6   // violet — sélection, XP bar
AccentTertiary     = #22D3EE  // cyan — info, coffres

// Statuts
Success            = #4ADE80
Warning            = #F59E0B
Danger             = #F87171
Info               = #60A5FA

// Monnaies
CoinColor          = #F5A623   // pièces or
GemColor           = #A78BFA   // gemmes violet

// Coffres
ChestCommon        = #6B7280   // gris
ChestRare          = #3B82F6   // bleu
ChestEpic          = #8B5CF6   // violet
ChestLegendary     = #F59E0B   // or
```

### 4.2 Thème clair (Light)
```kotlin
Background         = #F5F5F5
Surface            = #FFFFFF
SurfaceVariant     = #F0F0F0
TextPrimary        = #111111
TextSecondary      = #555555
AccentPrimary      = #E8960D
AccentSecondary    = #5B4CF0
```

### 4.3 Thème Auto
- Suit le paramètre système Android (`isSystemInDarkTheme()`)
- Détecté automatiquement au lancement
- Changeable dans Paramètres > Apparence

### 4.4 Typographie
```kotlin
// Font principale : Outfit (Google Fonts)
DisplayLarge    = Outfit Bold, 32sp
DisplayMedium   = Outfit Bold, 26sp
HeadlineLarge   = Outfit SemiBold, 22sp
HeadlineMedium  = Outfit SemiBold, 18sp
TitleLarge      = Outfit Medium, 16sp
TitleMedium     = Outfit Medium, 14sp
BodyLarge       = Outfit Regular, 16sp
BodyMedium      = Outfit Regular, 14sp
LabelSmall      = Outfit Regular, 11sp, LetterSpacing +0.08
```

### 4.5 Formes & Spacing
```kotlin
// Border radius
ShapeSmall      = 8.dp
ShapeMedium     = 14.dp
ShapeLarge      = 20.dp
ShapeXLarge     = 28.dp  // cards principales
ShapeRound      = 50.dp  // badges, pills

// Spacing
SpacingXS       = 4.dp
SpacingS        = 8.dp
SpacingM        = 14.dp
SpacingL        = 20.dp
SpacingXL       = 28.dp

// Padding page
PaddingScreen   = 16.dp (small screen < 360dp → 12.dp)
```

### 4.6 Composants UI critiques

#### SankaiCard
- Background: SurfaceVariant
- Border: 0.5dp, SurfaceBorder
- Radius: ShapeLarge (20dp)
- Elevation: 0 (flat design)
- Padding: 16dp intérieur

#### SankaiButton (Primaire)
- Background: AccentPrimary (orange/or)
- Texte: Bold, blanc
- Radius: ShapeMedium (14dp)
- Height: 52dp
- Animation: scale(0.96) on press + haptic

#### SankaiButton (Secondaire)
- Background: SurfaceElevated
- Border: 1dp SurfaceBorder
- Texte: TextPrimary

#### ResourceBar (Header)
- Inspiré Clash Royale : barre fixe en haut
- Gauche : [Niveau badge] [XP bar mini]
- Droite : [pièces] [gemmes]
- Fond: Surface semi-transparent
- Toujours visible dans toutes les pages

---

## 5. NAVIGATION

### 5.1 Bottom Navigation Bar

Ordre gauche → droite, inspiré Clash Royale :
```
[🛒 Shop] [⚡ Vie] [🏠 Accueil] [🎯 Défis] [👤 Profil]
```

**Détails techniques :**
```kotlin
// Hauteur : 72dp (inclut padding système)
// Fond : Surface avec blur léger (#111111 / 95% opacité)
// Bordure top : 0.5dp SurfaceBorder
// Icône active : filled + couleur AccentPrimary + scale(1.1)
// Icône inactive : outline + TextSecondary
// Label : visible par défaut (toggle dans Paramètres)
// Badge rouge : sur Défis si challenge claimable
// Vibration : HapticFeedbackType.TextHandleMove à chaque tap
// Transition : CrossFade 200ms
```

**Routes NavGraph :**
```kotlin
sealed class Screen(val route: String) {
    object Home        : Screen("home")
    object Life        : Screen("life")
    object Challenges  : Screen("challenges")
    object Shop        : Screen("shop")
    object Profile     : Screen("profile")
    object Settings    : Screen("settings")
    object Memo        : Screen("memo/{profileId}")
    object MemoEditor  : Screen("memo_editor/{profileId}")
    object Focus       : Screen("focus")
    object Objectives  : Screen("objectives")
    object ChestOpen   : Screen("chest_open/{chestId}")
}
```

### 5.2 Adaptivité écrans
```
< 360dp width  → padding 12dp, texte -1sp, 1 colonne stricte
360–599dp      → padding 16dp, layout normal (mode téléphone standard)
600–839dp      → padding 24dp, grilles 2 colonnes pour stats/cards
≥ 840dp        → sidebar gauche remplace BottomBar (V2 future)
              → pour V1 : contenu centré, maxWidth = 600dp
```

---

## 6. ÉCRANS DÉTAILLÉS

---

### 6.1 HOME SCREEN

**Fichier :** `HomeScreen.kt` + `HomeViewModel.kt`

**Layout :** LazyColumn, scroll vertical, PaddingScreen 16dp

#### Structure complète (top → bottom)

**A. StatusBar transparente + ResourceBar fixe**
```
[LVL 12 badge] [████░░ 2400/3000 XP]     [🪙 1240] [💎 8]
```
- ResourceBar : Row, fond transparent, height 52dp
- LVL badge : Box arrondie, fond AccentSecondary, texte Bold blanc
- XP bar : LinearProgressIndicator custom, hauteur 6dp, arrondie
- Pièces + Gemmes : Row avec icône + texte Bold

**B. Header greeting**
```
"Bonjour, Souanpt 👋"          [🔥 7 jours]
```
- Texte DisplayMedium (26sp Bold)
- Streak badge : fond orange semi-transparent, flamme + nombre

**C. Carte "Action du jour" — BLOC PRINCIPAL**
```
╔══════════════════════════════════════╗
║  🎯  ACTION DU JOUR                 ║
║                                      ║
║  • Compléter 1 session Focus         ║
║  • Lire ton mémo Motivation          ║
║  • Terminer 1 défi quotidien         ║
║                                      ║
║  [▶  COMMENCER]   [Passer →]        ║
╚══════════════════════════════════════╝
```
- Card full-width, fond gradient subtil (SurfaceVariant → SurfaceElevated)
- Bouton "COMMENCER" : AccentPrimary, height 52dp, full-width
- Logique : ActionEngine détermine dynamiquement quelle action afficher
- Si focus en cours → bouton "Reprendre Focus ⏱"
- Si coffre dispo → priorité au coffre

**D. Rangée coffres (style Clash Royale)**
```
[ 🟦 3H ]  [ 🟪 6H ]  [ ⏳ 1H30 ]  [ 🎁 OPEN ]
  Commun     Rare      En cours     Quotidien
```
- Row horizontal, 4 slots fixes, height 110dp
- Chaque slot = Box clickable avec animation press
- Timer : countdown en temps réel (coroutine/Flow)
- Slot plein verrouillé → tap = dialog "Déjà plein"
- Coffre quotidien prêt → bordure pulsante AccentPrimary
- Coffre verrouillé → cadenas + countdown + option gemmes

**E. Actions rapides (3 boutons)**
```
[ ⚡ Pub +5🪙 ]   [ 🎯 Défis 2/3 ]   [ 🛒 Shop ]
```
- Row 3 colonnes égales
- Chaque bouton = Card cliquable, icône + label + sous-info
- "Pub" affiche countdown cooldown si actif

**F. Stats du jour (3 StatCards)**
```
[ +120 XP  ]  [ +340 🪙 ]  [ 2 Focus ]
 Aujourd'hui   Aujourd'hui   terminés
```
- Grid 3 colonnes
- Reset à minuit local

**G. Suggestion intelligente (dynamique)**
```
╔══════════════════════════════════════╗
║  💡 Tu es en bonne progression !    ║
║  Continue avec un défi ce soir.     ║
╚══════════════════════════════════════╝
```
- ActionEngine.getSuggestion(userState) → texte contextuel
- Logiques : inactif, actif, streak à risque, focus non utilisé, défi disponible

**Fonctions HomeViewModel :**
```kotlin
fun getTodayAction(): StateFlow<TodayAction>
fun getDailyChestStatus(): StateFlow<ChestStatus>
fun getActiveChests(): StateFlow<List<Chest>>  // max 4
fun getTodayStats(): StateFlow<DayStats>
fun getStreak(): StateFlow<Int>
fun getSuggestion(): StateFlow<String>
fun watchAdForCoins()          // déclenche AdMob rewarded
fun openDailyChest()
fun navigateToFocus()
fun claimChestReward(chestId: Long)
```

---

### 6.2 LIFE SCREEN (Mode Vie)

**Fichier :** `LifeScreen.kt` + `LifeViewModel.kt`

**Layout :** Scaffold + LazyColumn + FloatingActionButton

#### Structure

**A. Header section**
```
MODE VIE                          [Slots : 1/3]
```

**B. Modules actifs (cartes)**
Chaque module actif = grande SankaiCard :
```
╔══════════════════════════════════════╗
║  📖  Mémo Motivation              ON ║
║  12 phrases • 1×/jour • 18h00        ║
║  Prochaine notif dans 2h15           ║
║                                      ║
║  [✏️ Modifier]    [🔕 Désactiver]   ║
╚══════════════════════════════════════╝
```
- Toggle ON/OFF dans le header de la carte
- Tap "Modifier" → navigue vers MemoScreen ou FocusScreen

```
╔══════════════════════════════════════╗
║  ⏱️  Focus Timer                  OFF ║
║  25 min • Pomodoro                    ║
║  Débloque pauses au niveau 5          ║
║                                      ║
║  [▶ Démarrer]     [⚙️ Régler]      ║
╚══════════════════════════════════════╝
```

**C. Bouton ajout module**
- FAB (FloatingActionButton) bas-droite : "+"
- Tap → BottomSheet "Choisir un module"
```
Choisir module :
[ 📖 Mémo ]   [ ⏱️ Focus ]   [ 🎯 Objectifs ]
```

**D. Barre slots (bas)**
```
Slots utilisés : 1 / 3
[████░░░]
[Acheter slot — 1200 🪙]
```

**Fonctions LifeViewModel :**
```kotlin
fun getActiveModules(): StateFlow<List<ModuleState>>
fun toggleModule(moduleId: Long, enabled: Boolean)
fun getSlotInfo(): StateFlow<SlotInfo>   // used/max
fun getNextMemoNotification(): StateFlow<MemoNotifInfo>
fun addModule(type: ModuleType)
fun removeModule(moduleId: Long)
fun buySlot(): Result<Unit>              // coût 1200 pièces
```

---

### 6.3 MEMO SCREEN

**Fichier :** `MemoScreen.kt` + `MemoEditorScreen.kt` + `MemoViewModel.kt`

**Layout :** Scaffold + LazyColumn

#### MemoScreen — Liste des profils

**A. Header**
```
← Mémo Intelligent               [+ Nouveau profil]
```

**B. Profils (cartes)**
```
╔══════════════════════════════════════╗
║  📖 Motivation                     ║
║  124 phrases • 1×/jour             ║
║  Notif : 18h00 • Actif ✓           ║
║                                      ║
║  [Modifier]   [Activer/Désactiver]  ║
╚══════════════════════════════════════╝
```

**C. Import clipboard**
```
╔══════════════════════════════════════╗
║  📋 Importer depuis presse-papier   ║
║  Colle un texte multi-lignes        ║
║  → chaque ligne = 1 phrase          ║
║  [COLLER & IMPORTER]               ║
╚══════════════════════════════════════╝
```

**D. Intent Share Android**
- App enregistrée comme cible de partage (Manifest intent-filter)
- Quand texte partagé vers app → popup :
```
Ajouter ce texte à :
[ Motivation ]   [ Travail ]   [ + Nouveau ]
```

#### MemoEditorScreen — Éditeur complet

**Layout :** Scaffold avec TopAppBar et actions

**A. Champ nom profil**
- TextField, placeholder "Nom du profil"

**B. Paramètres notif**
```
Fréquence :  [ 1×/jour  ▼ ]
Heure :      [ 18 : 00  picker ]
```
- DropdownMenu fréquence : 1×/jour, 2×/jour, 3×/jour, Aléatoire
- TimePicker Material 3

**C. Zone lignes (LazyColumn)**
- Affichage : max 5 lignes visibles + scroll
- Compteur : "124 phrases"
- Chaque ligne : Row [numéro] [texte] [🗑️ supprimer]
- Bouton "Ajouter ligne" sous la liste

**D. Bouton Coller (clipboard)**
- Lit android.content.ClipboardManager
- Parse par "\n", nettoie lignes vides, trim
- Dédoublonne automatiquement
- Dialog confirm : "Ajouter 37 lignes ? [Ajouter] [Remplacer] [Annuler]"

**E. Sauvegarder**
- Bouton TopAppBar "💾"
- AutoSave toutes les 30 secondes

**Fonctions MemoViewModel :**
```kotlin
fun getProfiles(): StateFlow<List<MemoProfile>>
fun createProfile(name: String): Long
fun updateProfile(profile: MemoProfile)
fun deleteProfile(profileId: Long)
fun addLine(profileId: Long, text: String)
fun addLinesFromClipboard(profileId: Long, rawText: String): Int
fun deleteLine(lineId: Long)
fun toggleProfile(profileId: Long, active: Boolean)
fun getRandomLine(profileId: Long): MemoLine    // anti-répétition
fun getLineCount(profileId: Long): StateFlow<Int>
fun cleanDuplicates(profileId: Long): Int       // retourne nb supprimés
fun importFromShareIntent(text: String)
```

**Algorithme MemoEngine.getRandomLine() :**
```
1. Charger toutes les lignes du profil
2. Charger l'historique des 10 dernières lignes envoyées
3. Filtrer : exclure lignes dans l'historique si liste > 10 lignes
4. Sélection aléatoire parmi les restantes (Random.nextInt)
5. Enregistrer la ligne choisie dans l'historique
6. Retourner la ligne
```

---

### 6.4 FOCUS SCREEN

**Fichier :** `FocusScreen.kt` + `FocusViewModel.kt`

**Layout :** Scaffold centré verticalement

#### Interface principale

**A. Picker durée (style roue)**
```
          ▲           ▲
        [ 00 ]heure [ 25 ]min
          ▼           ▼
```
- NumberPicker custom via LazyColumn avec snap
- Heures : 0–4, Minutes : 5–120 par pas de 5
- Réglage disponible uniquement quand timer arrêté

**B. Timer display**
```
         25:00
    [████████░░░░░░░] 67%
```
- Texte DisplayLarge (48sp, font monospace tabular)
- CircularProgressIndicator custom ou LinearProgress sous le timer
- Animation fluide chaque seconde

**C. Boutons contrôle**
```
[▶ DÉMARRER]                          (état : arrêté)
[⏸ PAUSE]  [⏹ ARRÊTER]               (état : en cours)
[▶ REPRENDRE]  [⏹ ARRÊTER]           (état : pausé)
```

**D. Infos session**
```
Pauses disponibles : 1  (débloqué niveau 5)
Session complète → +50 XP
```

**E. Fin de session → Dialog**
```
╔══════════════════════════════╗
║  ✅ Session terminée !       ║
║                              ║
║  Durée : 25:00               ║
║  +50 XP gagné                ║
║  +10 🪙 bonus                ║
║                              ║
║  [OK]    [Relancer]          ║
╚══════════════════════════════╝
```

**Fonctions FocusViewModel :**
```kotlin
fun getTimerState(): StateFlow<TimerState>   // IDLE / RUNNING / PAUSED / FINISHED
fun getTimeRemaining(): StateFlow<Long>      // millisecondes
fun getSessionConfig(): StateFlow<FocusConfig>  // durée, pauses max
fun startTimer()
fun pauseTimer()
fun resumeTimer()
fun stopTimer()                              // annule, pas de XP
fun finishSession()                          // XP + pièces accordés
fun setDuration(hours: Int, minutes: Int)
fun getPausesAvailable(): StateFlow<Int>     // selon niveau user
fun usePause(): Result<Unit>
```

**FocusConfig selon niveau :**
```
Niveau 1–4  : 1 session, 0 pauses, durée max 60 min
Niveau 5–9  : 1 session, 1 pause
Niveau 10–19: 2 pauses, durée max 120 min
Niveau 20+  : 3 pauses, sessions longues, multi-queue
```

**Service de fond :**
- ForegroundService quand timer actif
- Notification sticky : "⏱ Focus en cours — 18:42 restantes"
- Notification cliquable → ouvre FocusScreen

---

### 6.5 OBJECTIVES SCREEN

**Fichier :** `ObjectivesScreen.kt` + `ObjectivesViewModel.kt`

**Layout :** LazyColumn + FAB

**A. Objectifs actifs**
```
╔══════════════════════════════════════╗
║  📌 Lire 20 pages par jour           ║
║  [████████░░] 80%                   ║
║  Progression : 16 / 20               ║
║  [✅ Valider +30 XP]                ║
╚══════════════════════════════════════╝
```

**B. Créer objectif (FAB +)**
- BottomSheet
- Nom : TextField
- Valeur cible : NumberField
- Unité : TextField ("pages", "km", "fois"...)
- Bouton "Créer"

**C. Objectifs terminés**
- Section séparée "Complétés" collapsable
- Historique 7 derniers jours

**Fonctions ObjectivesViewModel :**
```kotlin
fun getObjectives(): StateFlow<List<Objective>>
fun createObjective(name: String, target: Int, unit: String): Long
fun updateProgress(objectiveId: Long, amount: Int)
fun validateObjective(objectiveId: Long): Reward
fun deleteObjective(objectiveId: Long)
fun getCompletedToday(): StateFlow<Int>
```

---

### 6.6 CHALLENGES SCREEN

**Fichier :** `ChallengesScreen.kt` + `ChallengesViewModel.kt`

**Layout :** TabRow + Pager + LazyColumn

#### Tabs
- "Quotidien" / "Hebdo" / "Spécial"

#### Barre progression globale
```
Quotidiens : 2 / 3    [████████░░]   +80 XP bonus
```

#### Carte défi
```
╔══════════════════════════════════════╗
║  🎯 Regarder 2 pubs                 ║
║  Type : Quotidien                    ║
║  Progression : [█████░] 1 / 2       ║
║  Récompense : +50 🪙 +20 XP         ║
║                                      ║
║  [▶ Regarder pub]  [✅ Réclamer]    ║
╚══════════════════════════════════════╝
```
- Bouton "Réclamer" : visible uniquement si progression complète
- Défi complété : card grisée, coche verte, texte "Complété"

#### Types de défis quotidiens (exemples hardcodés V1)
```
1. Regarder 2 pubs          → +50 🪙
2. Compléter 1 objectif     → +80 XP
3. Faire 1 session Focus    → +40 🪙 +30 XP
4. Ouvrir 1 coffre          → +20 XP
5. Ouvrir l'app             → +15 XP  (auto-complété)
```

#### Types de défis hebdomadaires
```
1. Streak 7 jours           → +1 Coffre Rare
2. Ouvrir 3 coffres         → +150 XP
3. 3 sessions Focus         → +100 🪙 +100 XP
4. Regarder 10 pubs         → +1 Coffre Commun
5. Valider 5 objectifs      → +200 🪙
```

**Fonctions ChallengesViewModel :**
```kotlin
fun getDailyChallenges(): StateFlow<List<Challenge>>
fun getWeeklyChallenges(): StateFlow<List<Challenge>>
fun getSpecialChallenges(): StateFlow<List<Challenge>>
fun updateChallengeProgress(challengeId: Long, amount: Int)
fun claimChallengeReward(challengeId: Long): Reward
fun getGlobalProgress(): StateFlow<ChallengeProgress>
fun resetDailyChallenges()                         // appelé à minuit
fun hasClaimableChallenges(): StateFlow<Boolean>   // pour badge BottomBar
```

---

### 6.7 SHOP SCREEN

**Fichier :** `ShopScreen.kt` + `ShopViewModel.kt`

**Layout :** TabRow + LazyColumn

#### Header shop
```
🛒 BOUTIQUE
[🪙 1240]  [💎 8]
```

#### Tab 1 — Coffres
```
╔══════════════════╗  ╔══════════════════╗
║  🟦 Commun       ║  ║  🟪 Rare          ║
║  Pièces, items   ║  ║  Gemmes, boosts  ║
║  basiques        ║  ║                  ║
║  ──────────────  ║  ║  ──────────────  ║
║  200 🪙          ║  ║  500 🪙          ║
║  [ACHETER]       ║  ║  [ACHETER]       ║
╚══════════════════╝  ╚══════════════════╝

╔══════════════════╗
║  💜 Épique        ║
║  Gemmes, thèmes,  ║
║  améliorations   ║
║  ──────────────  ║
║  3 💎            ║
║  [ACHETER]       ║
╚══════════════════╝
```

#### Section pubs (dans onglet coffres)
```
╔══════════════════════════════════════╗
║  🎥 Regarder une pub → +5 🪙        ║
║  Pubs aujourd'hui : 32 / 50         ║
║  Cooldown : [18s ░░░░░░░░░]        ║
║                                      ║
║  Bonus : 5 pubs → +10 🪙 bonus     ║
║  Bonus : 20 pubs → Coffre Commun   ║
║                                      ║
║  [▶ REGARDER]                       ║
╚══════════════════════════════════════╝
```

#### Tab 2 — Boosts
```
╔══════════════════════════════════════╗
║  ⚡ ×2 Pièces — 30 min             ║
║  Double les pièces gagnées          ║
║  200 🪙  [ACHETER]                  ║
╚══════════════════════════════════════╝

╔══════════════════════════════════════╗
║  ⏩ Skip Cooldown                   ║
║  Retire le cooldown actif           ║
║  1 💎   [ACHETER]                   ║
╚══════════════════════════════════════╝

╔══════════════════════════════════════╗
║  🎁 Double Récompense — 1h          ║
║  Double les pièces des coffres      ║
║  2 💎   [ACHETER]                   ║
╚══════════════════════════════════════╝
```

#### Tab 3 — Améliorations permanentes
```
Scaling de prix :
Niveau 1→2 : 200 🪙
Niveau 2→3 : 500 🪙
Niveau 3→4 : 1200 🪙
Niveau 4→5 : 2500 🪙

╔══════════════════════════════════════╗
║  🧩 +1 Slot Module                  ║
║  Activer un module supplémentaire   ║
║  Actuel : 1/3    → 1200 🪙          ║
║  [AMÉLIORER]                        ║
╚══════════════════════════════════════╝

╔══════════════════════════════════════╗
║  ⏱️ +1 Slot Focus                   ║
║  Session Focus supplémentaire       ║
║  Actuel : 1/3    → 1200 🪙          ║
║  [AMÉLIORER]                        ║
╚══════════════════════════════════════╝

╔══════════════════════════════════════╗
║  ⭐ +25% Chance Coffre Rare         ║
║  Meilleure chance au drop          ║
║  5 💎   [ACHETER]                   ║
╚══════════════════════════════════════╝
```

**Fonctions ShopViewModel :**
```kotlin
fun getCofferItems(): StateFlow<List<ShopItem>>
fun getBoostItems(): StateFlow<List<ShopItem>>
fun getUpgradeItems(): StateFlow<List<ShopItem>>
fun purchaseItem(itemId: String): Result<Reward>
fun watchAdForCoins(): Result<Unit>
fun getAdState(): StateFlow<AdState>    // READY / COOLDOWN / LIMIT_REACHED
fun getAdCooldown(): StateFlow<Long>    // secondes restantes
fun getAdCount(): StateFlow<AdDayCount> // vues/max aujourd'hui
fun canAfford(itemId: String): Boolean
fun getUpgradeNextCost(upgradeId: String): Int
```

---

### 6.8 PROFILE SCREEN

**Fichier :** `ProfileScreen.kt` + `ProfileViewModel.kt`

**Layout :** LazyColumn

**A. Header user**
```
╔══════════════════════════════════════╗
║         [Avatar initiale]            ║
║              SOUANPT                 ║
║          NIVEAU 12  🔥 7j           ║
║                                      ║
║  [████████████░░░] 2400 / 3000 XP   ║
║  Level 13 dans 600 XP               ║
╚══════════════════════════════════════╝
```
- Avatar : Box circulaire 80dp, fond AccentSecondary, initiale
- Niveau : badge + XP bar animée

**B. Stats grid 2×3**
```
[  23      ] [  12      ] [  3h20    ]
[ Pubs vues] [Coffres   ] [Focus     ]

[  1240    ] [  860     ] [  7       ]
[Pièces    ] [Pièces    ] [Streak    ]
[ gagnées  ] [dépensées ] [ max      ]
```

**C. Badges / Achievements**
Section horizontale scrollable :
```
[🔥 7 jours] [⏱️ 3h Focus] [📦 10 coffres] [🔒 ???]
```
- Badge verrouillé = cadenas, grisé
- Tap → dialog description

**D. Récompense Ko-fi**
```
╔══════════════════════════════════════╗
║  ☕ Clé Ko-fi                        ║
║  Accès produit exclusif Souanpt     ║
║  Débloqué au niveau 20              ║
║  [🔒 Niveau 8 restant]              ║
╚══════════════════════════════════════╝
```
- Si niveau 20+ et connexion internet : bouton "RÉCLAMER" actif
- Si offline : bouton grisé "Connexion requise"

**E. Thème équipé**
```
Thème actuel : 🌑 Default (Or)
[Changer dans Paramètres →]
```

**F. Bouton Paramètres**
- Icône engrenage TopAppBar → navigue vers Settings

**Fonctions ProfileViewModel :**
```kotlin
fun getUserProfile(): StateFlow<UserProfile>
fun getStats(): StateFlow<UserStats>
fun getBadges(): StateFlow<List<Badge>>
fun getThemeEquipped(): StateFlow<Theme>
fun checkKofiEligibility(): Boolean
fun openKofiLink()   // si connecté + niveau suffisant
```

---

### 6.9 SETTINGS SCREEN

**Fichier :** `SettingsScreen.kt` + `SettingsViewModel.kt`

**Layout :** LazyColumn avec sections

**A. Apparence**
```
Thème UI
  ○ Sombre (AMOLED)    ← par défaut
  ○ Clair
  ○ Automatique (système)

Afficher labels navigation
  [Toggle ON]

Mode économie batterie
  [Toggle OFF]
  ↳ Désactive animations complexes
```

**B. Thèmes débloqués**
Section liste :
```
Thèmes possédés : 2 / 8

[🌑 Default Or]     Équipé ✓   [Appliquer]
[🔵 Blue Neon]      Débloqué   [Appliquer]
[🟣 Purple]         Niveau 10  [🔒 Verrouillé]
[🔴 Red Energy]     Niveau 20  [🔒 Verrouillé]
[⚡ Cyan Storm]     Coffre     [🔒 Rare drop]
[👑 Legendary Gold] Coffre     [🔒 Épique drop]
```

**C. Notifications**
```
Notifications actives    [Toggle ON]
Rappel défis quotidiens  [Toggle OFF]
Rappel streak            [Toggle ON]
Vibrations interface     [Toggle ON]
```

**D. Focus**
```
Notification fin session [Toggle ON]
Garder écran allumé      [Toggle ON]
```

**E. Liens (état online/offline)**
```
🌐 Site haunt.gg/souanpt  [Ouvrir ↗]   ← grisé si offline
☕ Ko-fi ko-fi.com/souanpt [Ouvrir ↗]   ← grisé si offline
```

**F. Données**
```
[Réinitialiser progression]  ← Dialog confirm 3×
[Exporter données (JSON)]
[Supprimer compte]           ← Dialog confirm + saisie "SUPPRIMER"
```

**G. À propos**
```
Version : 1.0.0
Build : 2025
Par Souanpt
```

**Fonctions SettingsViewModel :**
```kotlin
fun getSettings(): StateFlow<AppSettings>
fun setThemeMode(mode: ThemeMode)          // DARK / LIGHT / AUTO
fun setEquippedTheme(themeId: String)
fun toggleNotifications(enabled: Boolean)
fun toggleVibrations(enabled: Boolean)
fun toggleNavLabels(enabled: Boolean)
fun toggleBatterySaver(enabled: Boolean)
fun resetProgress(): Result<Unit>
fun exportData(): Uri                      // JSON export
fun deleteAccount(): Result<Unit>
fun isOnline(): StateFlow<Boolean>
fun openExternalLink(url: String)
```

---

## 7. SYSTÈMES CORE

### 7.1 XP Engine

**Règle fondamentale : XP non boostable — progression honnête uniquement**

**Sources XP :**
```kotlin
const val XP_OPEN_APP         = 5    // 1x par jour
const val XP_DAILY_STREAK     = 10   // par jour de streak
const val XP_MEMO_NOTIF_OPEN  = 15   // ouvrir notification mémo
const val XP_FOCUS_COMPLETE   = 50   // session focus complète (25 min)
const val XP_FOCUS_LONG       = 80   // session > 45 min
const val XP_OBJECTIVE_DONE   = 30   // valider objectif
const val XP_CHALLENGE_DAILY  = 20   // défi quotidien
const val XP_CHALLENGE_WEEKLY = 80   // défi hebdo
const val XP_CHEST_OPEN       = 10   // ouvrir coffre
```

**Courbe XP (exponentielle douce) :**
```kotlin
fun xpRequiredForLevel(level: Int): Int {
    return when {
        level <= 1  -> 0
        level <= 5  -> 100 * level
        level <= 10 -> 150 * level
        level <= 20 -> 250 * level
        level <= 30 -> 400 * level
        else        -> 600 * level
    }
}
// Exemples :
// Lvl 2  → 200 XP
// Lvl 5  → 500 XP
// Lvl 10 → 1500 XP
// Lvl 20 → 5000 XP
// Lvl 30 → 12000 XP
```

**Level Up Dialog :**
```
╔══════════════════════════════╗
║  🎉 NIVEAU 10 ATTEINT !      ║
║  ✨ Animation glow + vibration║
║                              ║
║  Récompenses :               ║
║  +500 🪙  +1 Coffre Rare    ║
║  Débloqué : Slot Focus ×2   ║
║                              ║
║  [ OK ]                      ║
╚══════════════════════════════╝
```

**Milestones niveaux :**
```
Niveau 5  → +1 Slot Module, Pause Focus débloquée
Niveau 10 → +1 Slot Focus, Coffre Rare drop amélioré
Niveau 12 → Thème Blue Neon débloqué
Niveau 15 → +1 Mémo Profile slot
Niveau 20 → Clé Ko-fi active, Thème Purple débloqué
Niveau 25 → Coffres Épiques disponibles
Niveau 30 → Multi-sessions Focus, Upgrades avancés
```

---

### 7.2 Economy Engine

**Monnaies :**
```kotlin
// Pièces (soft currency)
data class Coins(val amount: Int)

// Gemmes (hard currency)
data class Gems(val amount: Int)
```

**Sources pièces :**
```kotlin
COINS_PER_AD        = 5       // pub regardée
COINS_AD_BONUS_5    = 10      // bonus après 5 pubs consécutives
COINS_CHALLENGE     = 20–100  // selon défi
COINS_LEVEL_UP      = level * 50  // récompense montée niveau
COINS_CHEST_MIN     = 10      // coffre commun min
COINS_CHEST_MAX     = 200     // coffre épique max
```

**Coûts :**
```kotlin
COST_CHEST_COMMON   = 200 🪙
COST_CHEST_RARE     = 500 🪙
COST_CHEST_EPIC     = 3 💎
COST_BOOST_2X_COINS = 200 🪙
COST_BOOST_SKIP_CD  = 1 💎
COST_BOOST_2X_CHEST = 2 💎
COST_SLOT_MODULE    = [200, 500, 1200, 2500] 🪙  // scaling
COST_SLOT_FOCUS     = [200, 500, 1200, 2500] 🪙
COST_RARE_CHANCE    = 5 💎
```

**Règles boosts :**
- Boosts affectent uniquement les pièces et récompenses de coffres
- Boosts N'AFFECTENT PAS l'XP (progression honnête)
- Max 3 boosts actifs simultanément

---

### 7.3 Chest Engine (Coffres style Clash Royale)

**Slots :** 4 emplacements toujours visibles (HomeScreen bas)

**Types & Timers :**
```kotlin
enum class ChestType {
    COMMON    // Timer : 3h, Contenu : 10–50 🪙, 0–5 XP
    RARE      // Timer : 6h, Contenu : 50–150 🪙, 1–2 💎, boost possible
    EPIC      // Timer : 12h, Contenu : 100–300 🪙, 3–5 💎, thème possible (1%)
    DAILY     // Timer : 0 (gratuit 1×/jour), Contenu : 20–80 🪙, 10 XP
    LEGENDARY // Timer : 24h, Contenu : 500+ 🪙, 10+ 💎, thème garanti (rare drop)
}
```

**Logique coffres :**
```kotlin
// Obtention coffres
- Focus complété → Coffre Commun ajouté en slot
- Défi hebdo → Coffre Rare
- Niveau up (milestones) → Coffre selon niveau
- Shop → Coffre acheté → ajouté en slot
- Max 4 slots, si plein → impossible d'ajouter

// Timer
- Countdown en temps réel (WorkManager + coroutine)
- Timer continue si app fermée
- Notification quand coffre prêt à ouvrir

// Ouverture
- Tap coffre prêt → Animation d'ouverture
- Option : payer gemmes pour ignorer timer
  - Common : 1 💎
  - Rare   : 2 💎
  - Epic   : 6 💎
- 1 pub/semaine → accélère coffre -2h

// Contenu aléatoire
fun ChestEngine.generateReward(type: ChestType): Reward {
    val base = type.baseCoins + Random.nextInt(type.coinVariance)
    val gems = if (Random.nextFloat() < type.gemChance) type.gemAmount else 0
    val boost = if (Random.nextFloat() < type.boostChance) randomBoost() else null
    val theme = if (Random.nextFloat() < type.themeChance) rareThemeDrop() else null
    return Reward(coins = base, gems = gems, boost = boost, theme = theme)
}
```

**Popup ouverture coffre :**
```
╔══════════════════════════════╗
║  🎁 Coffre Rare ouvert !    ║
║                              ║
║  +120 🪙                    ║
║  +2 💎                      ║
║  ⚡ Boost ×2 Pièces 30min   ║
║                              ║
║  [OK]   [Ouvrir suivant →]  ║
╚══════════════════════════════╝
```
- Animation : scale(1.0 → 1.15 → 1.0) + glow pulsant selon rareté
- Items apparaissent un par un avec delay 200ms
- Vibration : légère pour commun, forte pour épique
- "Ouvrir suivant" si d'autres coffres prêts

---

### 7.4 Streak Engine

```kotlin
// Logique streak
fun StreakEngine.checkDailyLogin(lastLogin: LocalDate): StreakResult {
    val today = LocalDate.now()
    val diff = ChronoUnit.DAYS.between(lastLogin, today).toInt()
    return when (diff) {
        0    -> StreakResult.ALREADY_DONE
        1    -> StreakResult.STREAK_MAINTAINED
        else -> StreakResult.STREAK_BROKEN
    }
}

// Récompenses streak
fun StreakEngine.getStreakReward(streakDays: Int): Reward? {
    return when (streakDays) {
        1    -> Reward(xp = 10, coins = 20)
        3    -> Reward(xp = 30, coins = 50, chestType = COMMON)
        7    -> Reward(xp = 100, coins = 200, chestType = RARE)
        14   -> Reward(xp = 200, coins = 400, chestType = RARE)
        30   -> Reward(xp = 500, coins = 1000, chestType = EPIC)
        else -> if (streakDays % 7 == 0) Reward(chestType = COMMON) else null
    }
}
```

---

## 8. NOTIFICATIONS

### Architecture
- **WorkManager** : scheduling robuste, survit redémarrages
- **NotificationManager** : création canaux + envoi
- **Tout 100% local**, aucun serveur

### Canaux Android
```kotlin
CHANNEL_MEMO      = "sankai_memo"      // Mémo du jour
CHANNEL_REMINDER  = "sankai_reminder"  // Rappels défis/streak
CHANNEL_REWARD    = "sankai_reward"    // Coffre prêt
CHANNEL_FOCUS     = "sankai_focus"     // Timer focus (foreground)
```

### MemoNotificationWorker
```kotlin
// Planification : PeriodicWorkRequest selon fréquence profil
// Exécution :
1. Charger profil mémo actif
2. MemoEngine.getRandomLine(profileId) → anti-répétition
3. Construire notification :
   Titre : "💡 [nom_profil]"
   Corps : "[phrase aléatoire]"
   Action tap : ouvre app → +15 XP (award)
4. Planifier prochaine occurrence
```

### Paramètres notifications
```kotlin
// Limites
MAX_NOTIFS_PER_DAY_PER_MODULE = 3    // évite spam
```

---

## 9. STOCKAGE LOCAL

### Room Database — Entités

```kotlin
@Entity
data class UserEntity(
    @PrimaryKey val id: Long = 1,
    val pseudo: String,
    val level: Int,
    val xp: Int,
    val coins: Int,
    val gems: Int,
    val streakDays: Int,
    val lastLoginDate: String,   // ISO-8601
    val totalFocusMinutes: Int,
    val totalAdsWatched: Int,
    val totalChestsOpened: Int,
    val equippedThemeId: String,
    val unlockedThemeIds: String,  // JSON array
    val moduleSlots: Int,          // default 1
    val focusSlots: Int,           // default 1
    val memoProfileSlots: Int,     // default 1
    val adCountToday: Int,
    val lastAdDate: String,
    val createdAt: String
)

@Entity
data class MemoProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val frequencyPerDay: Int,
    val scheduledHour: Int,
    val scheduledMinute: Int,
    val isActive: Boolean,
    val lastSentLineId: Long,
    val sentLineHistory: String    // JSON array of last 10 line IDs
)

@Entity
data class MemoLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val text: String,
    val orderIndex: Int
)

@Entity
data class ChestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,              // COMMON/RARE/EPIC/DAILY/LEGENDARY
    val slotIndex: Int,            // 0–3
    val unlocksAtMillis: Long,     // timestamp ouverture
    val isReady: Boolean,
    val isOpened: Boolean,
    val createdAt: Long
)

@Entity
data class ChallengeEntity(
    @PrimaryKey val id: String,    // "daily_1", "weekly_2", etc.
    val type: String,              // DAILY/WEEKLY/SPECIAL
    val titleKey: String,
    val targetAmount: Int,
    val currentProgress: Int,
    val rewardCoins: Int,
    val rewardXp: Int,
    val rewardChestType: String?,
    val isClaimed: Boolean,
    val resetDate: String          // date reset ISO
)

@Entity
data class StatsEntity(
    @PrimaryKey val date: String,  // YYYY-MM-DD
    val xpGained: Int,
    val coinsGained: Int,
    val coinsSpent: Int,
    val focusSessions: Int,
    val focusMinutes: Int,
    val chestsOpened: Int,
    val adsWatched: Int,
    val memoLinesReceived: Int,
    val objectivesCompleted: Int
)
```

### DataStore Preferences
```kotlin
// Clés stockées :
THEME_MODE           // "dark" | "light" | "auto"
SHOW_NAV_LABELS      // Boolean
VIBRATIONS_ENABLED   // Boolean
NOTIFICATIONS_ENABLED// Boolean
BATTERY_SAVER_MODE   // Boolean
ONBOARDING_DONE      // Boolean
LAST_APP_VERSION     // String
AD_LAST_COOLDOWN_END // Long timestamp
FOCUS_KEEP_SCREEN_ON // Boolean
STREAK_REMINDER_ENABLED // Boolean
```

---

## 10. MODE OFFLINE / ONLINE

### Fonctionnement offline (100% garanti)
```
✅ XP + niveaux
✅ Pièces + gemmes (hors achats IAP)
✅ Mémo (création, édition, notifications)
✅ Focus Timer (inclus foreground service)
✅ Objectifs
✅ Défis (sauf "regarder pub")
✅ Coffres (timers continuent)
✅ Streak
✅ Stats
✅ Shop (pièces et gemmes locaux)
✅ Thèmes
✅ Paramètres
✅ Notifications locales
```

### Fonctionnement online uniquement
```
🌐 Pubs AdMob (rewarded ads)
🌐 Ko-fi (lien externe)
🌐 Site Souanpt (lien externe)
🌐 Achats in-app (gemmes IAP, futur V2)
```

### Gestion UI offline
```kotlin
// ConnectivityObserver
class NetworkConnectivityObserver(context: Context) {
    fun observe(): Flow<Status>  // AVAILABLE / UNAVAILABLE / LOST
}

// Dans chaque ViewModel qui nécessite internet :
val isOnline: StateFlow<Boolean>

// Comportement UI :
- Bouton "Regarder pub" → grayed out + texte "Hors connexion"
- Boutons Ko-fi / Site → grayed out + Badge "Connexion requise"
- Toast discret si tap sur bouton offline : "Connexion internet requise"
```

---

## 11. MONÉTISATION ADMOB

```kotlin
// Uniquement : Rewarded Ads (jamais interstitiels, jamais bannières)
AD_UNIT_REWARDED = "ca-app-pub-XXXX/YYYY"

// Limites anti-abus
MAX_ADS_PER_DAY    = 50
AD_COOLDOWN_SEC    = 25      // entre chaque pub

// Bonus milestone pubs
AD_MILESTONE_5     = +10 🪙 bonus
AD_MILESTONE_10    = +1 Coffre Commun
AD_MILESTONE_20    = +20 🪙 bonus
AD_MILESTONE_50    = +1 Coffre Rare (max journalier)

// Accélération coffre via pub
CHEST_AD_SPEEDUP_HOURS = 2       // réduit timer de 2h
CHEST_AD_SPEEDUP_MAX   = 1       // 1 fois par semaine par coffre

class AdsManager {
    fun isAdReady(): Boolean
    fun showRewardedAd(onReward: (Reward) -> Unit, onDismiss: () -> Unit)
    fun getRemainingAdsToday(): Int
    fun getCooldownRemaining(): Long
    fun canUseChestSpeedup(chestId: Long): Boolean
}
```

---

## 12. THÈMES & PERSONNALISATION

```kotlin
data class Theme(
    val id: String,
    val nameKey: String,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val unlockCondition: UnlockCondition
)

sealed class UnlockCondition {
    data class Level(val level: Int) : UnlockCondition()
    data class ChestDrop(val chestType: ChestType, val probability: Float) : UnlockCondition()
    object Default : UnlockCondition()
}

// Thèmes V1 :
val THEMES = listOf(
    Theme("default",   "Default Or",    #F5A623, #7B6CF6, Default),
    Theme("blue",      "Blue Neon",     #22D3EE, #3B82F6, Level(12)),
    Theme("purple",    "Purple",        #A78BFA, #7C3AED, Level(20)),
    Theme("red",       "Red Energy",    #F87171, #EF4444, Level(25)),
    Theme("cyan",      "Cyan Storm",    #67E8F9, #06B6D4, ChestDrop(RARE, 0.03f)),
    Theme("green",     "Nature",        #4ADE80, #16A34A, Level(15)),
    Theme("gold",      "Legendary Gold",#FCD34D, #F59E0B, ChestDrop(EPIC, 0.01f)),
    Theme("pink",      "Pink Glitch",   #F472B6, #EC4899, ChestDrop(EPIC, 0.02f))
)

// Impact thème sur UI :
- Couleur accent boutons primaires
- Couleur XP bar
- Couleur highlight BottomBar active
- Couleur glow coffres
- Couleur badges
- Couleur streakBadge
- Couleur level badge
```

---

## 13. ANTI-ABUS & ÉQUILIBRE

```
Pubs       : max 50/jour, cooldown 25s, bonus milestones
Coffres    : max 4 slots, timer réel, gemmes pour skip
XP         : non boostable, sources limitées et définies
Streak     : vérifié côté date locale, impossible de fake
Reset      : confirmation 3× pour éviter accidents
Duplication: MemoEngine.cleanDuplicates() au paste
```

---

## 14. FLUX UTILISATEUR TYPE

```
1. Premier lancement → Onboarding (nom, 3 écrans intro)
2. App ouvre → HomeScreen
3. ResourceBar : niveau + pièces + gemmes visibles
4. Coffre quotidien disponible → tap → animation → récompense
5. "Action du jour" → tap COMMENCER → FocusScreen
6. Focus 25 min → fin → +50 XP + Coffre Commun en slot
7. Retour Home → voir XP monter, vérifier coffres
8. Tab Défis → claim défi "1 focus complété" → +40 🪙
9. Tab Shop → regarder 2 pubs → +10 🪙 → acheter boost
10. Tab Profil → voir stats du jour
11. Notification mémo 18h → ouvrir → +15 XP
12. Prochain jour → streak +1 → récompense streak
```

---

## 15. POINTS CLÉS POUR CURSOR

Quand tu donnes ce spec à Cursor, indique :

```
"Crée une app Android Kotlin + Jetpack Compose Material3
 Architecture MVVM + Hilt + Room + DataStore
 Navigation Compose avec BottomNavBar custom
 Thème AMOLED dark par défaut, light + auto disponibles
 100% fonctionnel offline, pubs AdMob rewarded uniquement
 Design inspiré fitness app bold cards + Clash Royale UI
 Police Outfit depuis Google Fonts
 Implémente en priorité : HomeScreen + LifeScreen + FocusTimer + Room DB
 Puis : ChestSystem + ChallengesScreen + ShopScreen
 Ensuite : MemoSystem + Notifications + AdsManager
 Animations légères uniquement (éviter lag sur appareils mid-range)"
```

---

*Fin du document — Sankai Life V1 Spec Complète*
*Généré pour usage avec Android Studio + Cursor*
