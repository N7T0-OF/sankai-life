# Refonte de l'apprentissage — analyse avant modification

État au 2 août 2026, avant la première ligne de code. Document exigé par la
consigne permanente : architecture existante, écrans, entités, migrations,
boucle, économie, risques, tests, fichiers.

---

## 1. Ce qui existe déjà, et qu'il ne faut surtout pas réécrire

L'analyse a changé le plan. Une partie de la spécification décrit des choses
**déjà construites**, sous d'autres noms.

| Demande de la spec | État réel |
|---|---|
| §18 Notifications réglables par module | **Fait.** `memo_profile` porte `scheduledHour`, `scheduledMinute`, `randomMode`, `randomStartHour/Minute`, `randomEndHour/Minute`, `activeDays`, `frequencyPerDay`, `isActive`, `nextTriggerAtMillis`. Chaque profil est déjà indépendant. |
| §7 Flashcards, 4 jugements, gestes + boutons | **Fait.** `FlashcardEngine.Jugement` = `A_REVOIR / DIFFICILE / CORRECT / FACILE`, glissement dans les 4 directions, boutons conservés pour l'accessibilité. |
| §12 Révision intelligente, erreurs récentes | **Partiellement fait.** Leitner à 5 boîtes (10 min → 1 j → 3 j → 7 j → 21 j) et mode « Mes erreurs » (`PROFIL_ERREURS = -2L`). |
| §7 QCM avec leurres | **Fait.** Les leurres sont tirés du même module pour ne jamais être hors sujet. |
| §7 Réécriture du mot | **Fait.** Saisie avec validation. |
| §33 Renommer `MemoTheme` → `MemoProfile` | **Déjà fait.** L'entité s'appelle `MemoProfileEntity` depuis longtemps. Aucune migration nécessaire. |
| Prononciation, langue | Base présente : `memo_profile.langue` en BCP-47, TTS branché. |

**Conséquence :** la refonte n'est pas une reconstruction. C'est l'ajout d'une
**hiérarchie** et d'un **parcours** au-dessus d'un moteur de révision qui
fonctionne, plus une série de nouveaux types d'exercices.

Ce qui manque réellement :

- la hiérarchie Module → Niveau → Chapitre → Unité → Leçon (aujourd'hui : plat,
  profil → lignes) ;
- l'écran Académie et le parcours visuel à nœuds ;
- le planificateur de session qui mélange les types d'exercices ;
- 11 des 14 types d'exercices ;
- le diagnostic de départ ;
- le regroupement des notifications ;
- le déplacement des thèmes visuels (§30–32).

---

## 2. Le risque principal : il n'y a pas de contenu

C'est le point le plus important de ce document, et ce n'est pas un problème de
code.

La spécification décrit un parcours de portugais A1 avec chapitres, unités,
dialogues, images et audio. **Ce contenu n'existe pas et je ne peux pas le
produire honnêtement** : écrire un cours de langue est un travail d'auteur,
pas de génération. Créer les tables `LearningChapterEntity` et
`LearningUnitEntity` sans rien dedans donnerait une Académie vide — un écran
qui promet un parcours et affiche zéro leçon. Ce serait pire que l'existant.

**La seule fondation honnête est le contenu que l'utilisateur possède déjà :**
ses profils Mémo, ses lignes, ses decks. Le parcours doit donc pouvoir être
**dérivé** de ce contenu quand aucune structure n'est déclarée, et **explicite**
quand un module en déclare une (import, module créé, contenu fourni plus tard).

C'est aussi ce qui satisfait §35 « aucune donnée existante n'est perdue » sans
migration risquée : le module *enveloppe* le profil Mémo, il ne le remplace pas.

---

## 3. Deux points de la spec que je ne peux pas tenir tels quels

**§7 Prononciation hors ligne.** `SpeechRecognizer` d'Android dépend en
pratique du moteur Google et, sur beaucoup d'appareils, d'une connexion. Il
n'existe aucune garantie de reconnaissance vocale hors ligne. Je peux
implémenter l'exercice en déclarant la dépendance et en le désactivant quand le
moteur est absent — je ne peux pas le promettre hors ligne.

**§7 Image vers mot, « illustrations cohérentes plutôt que les emojis ».** Je ne
sais pas produire ces illustrations. L'exercice fonctionnera avec les emojis, et
le dira.

---

## 4. Architecture retenue

```
core/learning/
  domain/    AcademieEngine, ParcoursEngine, SessionPlanEngine,
             ExerciceEngine, DiagnosticEngine        (purs, testables)
  data/      LearningEntities, LearningDao, LearningRepository
ui/screens/academie/
             AcademieScreen (accueil), ParcoursScreen (nœuds),
             SessionScreen (exercices), + un composable par type d'exercice
```

Les moteurs restent **purs et sans Compose**, comme `IslandGenerator` ou
`FlashcardEngine` : c'est ce qui rend la logique pédagogique testable sans
appareil.

### Entités

Contrairement aux 12 entités proposées en §28, j'en retiens **5**. Les autres
décrivent des niveaux de hiérarchie qui, sans contenu, seraient des tables
vides. Elles s'ajouteront quand du contenu existera.

| Entité | Rôle | Remplace / enveloppe |
|---|---|---|
| `learning_module` | Une matière. Nom, langue, niveau visé, profil Mémo source, plante liée | enveloppe `memo_profile` |
| `learning_unit` | Une unité, explicite ou dérivée. Chapitre et rang portés comme colonnes | nouveau |
| `learning_progress` | Progression par unité : leçons faites, score, dernière session | nouveau |
| `learning_error` | Une erreur datée, par carte et par type d'exercice | nouveau (aujourd'hui déduit des boîtes) |
| `learning_session` | Une session terminée : durée, exercices, réussite | nouveau |

`memo_profile` et `memo_line` **restent la source du contenu**. Aucune copie,
aucune suppression : une ligne Mémo modifiée reste la même carte.

### Migration Room 18 → 19

`CREATE TABLE` pour les cinq nouvelles tables. **Aucun `ALTER` sur les tables
existantes, aucune suppression.** C'est une migration purement additive, donc
sans perte possible. `fallbackToDestructiveMigration` reste interdit.

---

## 5. Boucle de jeu et économie

Inchangée dans son principe, précisée dans son grain — §17 demande de ne plus
récompenser chaque clic :

| Événement | Récompense |
|---|---|
| Carte réussie | XP seulement (déjà le cas : 2 XP) |
| Leçon terminée | eau + XP |
| Unité terminée | graine |
| Chapitre terminé | décoration |
| Niveau terminé | plante spéciale |

L'eau reste la monnaie qui relie l'apprentissage à l'Île : c'est déjà le cas et
c'est le lien à ne pas casser.

**Risque économique identifié :** l'Île consomme de l'eau à chaque arrosage. Si
une leçon en rapporte trop, l'Île devient un jeu de ferme sans révision ; trop
peu, l'Île se bloque. Le barème actuel devra être vérifié par un test qui
simule une semaine type, pas ajusté à vue.

---

## 6. Écrans

| Écran | Sort |
|---|---|
| `LifeScreen` | **Remplacé** par `AcademieScreen`. La route reste, pour ne pas casser la navigation. |
| `MemoScreen`, `MemoEditorScreen` | **Conservés.** Ce sont les éditeurs de contenu, et ils marchent. |
| `FlashcardsScreen` | **Conservé et étendu.** Devient l'hôte des sessions, avec un composable par type d'exercice. |
| `FocusScreen`, `ObjectivesScreen` | **Conservés,** rattachés à l'Académie plutôt qu'à un hub séparé. |
| Thèmes visuels dans Profil | **Déplacés** vers Profil → Personnalisation (§30–32). |

---

## 7. Plan de tests

Les moteurs purs d'abord, comme toujours :

- `SessionPlanEngine` : ne répète pas le même exercice, inclut toujours du
  rappel actif, fait revenir les erreurs, respecte le temps demandé, termine
  par une activité simple, est déterministe à graine fixée ;
- `AcademieEngine` : dérivation des unités depuis un module sans structure,
  respect d'une structure explicite quand elle existe, aucun contenu perdu ;
- `ParcoursEngine` : états des nœuds (terminée / actuelle / disponible /
  verrouillée), l'unité actuelle est toujours atteignable, aucune unité
  orpheline ;
- économie : une semaine simulée ne bloque ni ne noie l'Île.

---

## 8. Ordre d'exécution retenu

La spec propose 5 phases. Je les garde, mais j'inverse l'ordre interne de la
phase 1 : **le parcours dérivé du contenu existant d'abord**, la hiérarchie
explicite ensuite. Sans cela, la première livraison serait une Académie vide.

1. Moteurs purs + tests (parcours dérivé, planificateur de session).
2. Entités, migration 18 → 19, dépôt.
3. Écran Académie + parcours à nœuds.
4. Nouveaux exercices, un par un, du plus utile au moins utile :
   association, phrase à reconstruire, texte à trous, dictée, écoute.
5. Diagnostic, regroupement des notifications, déplacement des thèmes.

---

## 9. Fichiers touchés à la première étape

Créés :

```
core/learning/domain/AcademieEngine.kt
core/learning/domain/SessionPlanEngine.kt
app/src/test/.../AcademieEngineTest.kt
app/src/test/.../SessionPlanEngineTest.kt
```

Aucun fichier existant modifié à l'étape 1 : les moteurs sont purs et ne sont
branchés qu'à l'étape 3. C'est volontaire — cela permet de livrer et de tester
la logique pédagogique sans toucher à une application qui fonctionne.
