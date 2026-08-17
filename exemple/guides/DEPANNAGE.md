# 🔧 Dépannage

---

## La compilation

### « L'exécution de scripts est désactivée sur ce système »

Windows bloque PowerShell par défaut. Les fichiers `.bat` contournent déjà le
problème (`-ExecutionPolicy Bypass`) : **utilise-les plutôt que les `.ps1`**.

Pour débloquer durablement, dans un PowerShell utilisateur :

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

### Le téléchargement des outils échoue

Le script reprend là où il s'est arrêté : relance-le, il ne retélécharge pas ce
qui est déjà présent.

Si un fichier est corrompu, supprime `outils\_telechargements\` et relance.

Antivirus ou proxy d'entreprise peuvent bloquer `dl.google.com` et
`services.gradle.org`.

### « SDK location not found »

Le fichier `SankaiLife\local.properties` manque. Relance
`scripts\00-installer-outils.ps1`, il le régénère.

### Le build échoue après une modification du code

Relance avec un nettoyage complet :

```powershell
.\scripts\01-compiler-apk.ps1 -Propre
```

### Repartir totalement de zéro

Supprime `outils\`, `dist\`, et `SankaiLife\app\build\`, puis relance
`COMPILER-APK.bat`. Rien d'irremplaçable là-dedans.

> ⚠️ Ne supprime **jamais** `cles\` : c'est ta clé de signature.

---

## L'installation sur le téléphone

### « Application non installée »

| Cause | Solution |
|---|---|
| Sources inconnues bloquées | Paramètres → Sécurité → autoriser l'installation depuis cette source |
| Une version signée différemment est déjà là | Désinstalle l'ancienne d'abord |
| Play Protect bloque | Play Store → Play Protect → Analyser → « Installer quand même » |

Debug et release ont des identifiants différents (`.debug` en suffixe) : elles
peuvent coexister sur le même téléphone.

### `adb` ne voit pas le téléphone

1. Options développeur activées ? (taper 7 fois sur « Numéro de build »)
2. Débogage USB activé ?
3. La fenêtre « Autoriser le débogage USB ? » a bien été acceptée sur le téléphone ?
4. Le câble transmet-il des données ? Beaucoup de câbles ne font que charger.
5. Pilote USB manquant sur Windows : installe celui du constructeur.

---

## L'application

### Les notifications mémo n'arrivent pas

Dans l'ordre :

1. La permission de notification a-t-elle été acceptée au premier lancement ?
   Sinon : Paramètres Android → Applications → Sankai Life → Notifications
2. Le module mémo est-il **activé** et contient-il des lignes ?
3. **Optimisation de la batterie** — c'est la cause n°1. Paramètres Android →
   Applications → Sankai Life → Batterie → **Sans restriction**.
   Xiaomi, Huawei, Oppo et Samsung sont particulièrement agressifs et tuent les
   tâches de fond.
4. Le premier envoi peut prendre jusqu'à 15 minutes : c'est l'intervalle
   minimum imposé par WorkManager.

### L'app plante au lancement

Branche le téléphone et lis la vraie erreur :

```powershell
.\outils\android-sdk\platform-tools\adb.exe logcat -s AndroidRuntime
```

### Repartir d'une progression vierge

Dans l'app : Profil → Paramètres → **Réinitialiser la progression**.
Ou désinstaller / réinstaller.

---

## Le Play Store

### « You need to use a different version code »

`versionCode` doit augmenter à chaque envoi. Dans
`SankaiLife/app/build.gradle.kts` :

```kotlin
versionCode = 2
```

### « Upload a signed AAB »

Tu as envoyé l'APK. Google veut le `.aab` :
`dist\SankaiLife-playstore-dernier.aab`.

### J'ai perdu ma clé de signature

Si l'app **n'est pas encore publiée** : supprime `cles\` et
`SankaiLife\keystore.properties`, relance
`COMPILER-VERSION-PLAY-STORE.bat`, une nouvelle clé est créée.

Si l'app **est déjà publiée** et que « Play App Signing » était activé, Google
peut réinitialiser ta clé de téléversement (formulaire dans l'aide Play Console).
Sans Play App Signing, la seule issue est de republier sous un nouveau nom de
package. D'où l'insistance sur la sauvegarde.
