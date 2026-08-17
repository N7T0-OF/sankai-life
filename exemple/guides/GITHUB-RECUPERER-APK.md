# 🐙 GitHub — récupérer l'APK sans rien installer

Dépôt : **https://github.com/N7T0-OF/sankai-life** (privé)

À chaque envoi de code, GitHub compile l'APK sur ses propres serveurs. Tu peux
le télécharger depuis n'importe quel appareil, y compris directement depuis le
navigateur de ton téléphone.

---

## Télécharger l'APK

1. Va sur https://github.com/N7T0-OF/sankai-life/actions
2. Clique sur le run le plus récent (celui avec ✅)
3. En bas de page, section **Artifacts** → **SankaiLife-APK-debug**
4. Le téléchargement donne un `.zip` : décompresse-le, l'APK est dedans

> GitHub force le `.zip` autour des artefacts, c'est normal. Sur Android,
> n'importe quel gestionnaire de fichiers sait l'ouvrir.

Les artefacts sont conservés **90 jours**. Le dépôt étant privé, il faut être
connecté à ton compte GitHub pour les télécharger.

---

## Relancer une compilation à la main

Onglet **Actions** → workflow **Compiler l'APK** → bouton **Run workflow**.

Utile quand tu veux un APK frais sans avoir modifié le code.

---

## Envoyer une modification depuis ton PC

```bash
git add -A
git commit -m "ce que j'ai changé"
git push
```

La compilation démarre toute seule. Environ 3 à 5 minutes.

---

## Obtenir un APK signé depuis GitHub

Par défaut, l'Action ne produit que l'APK de **debug** : la clé de signature
n'est pas dans le dépôt, et c'est voulu.

Pour que GitHub produise aussi l'APK et l'AAB **signés**, il faut lui confier la
clé sous forme de secrets chiffrés.

### 1. Encoder la clé

Depuis le dossier du projet :

```powershell
.\scripts\05-preparer-secrets-github.ps1
```

Le script écrit `cles/keystore-base64.txt`. **Ce fichier est ta clé privée sous
une autre forme : ne l'envoie à personne et supprime-le après usage.**

### 2. Créer les secrets sur GitHub

**Settings → Secrets and variables → Actions → New repository secret**

| Nom du secret | Valeur |
|---|---|
| `KEYSTORE_BASE64` | tout le contenu de `cles/keystore-base64.txt` |
| `KEYSTORE_PASSWORD` | le mot de passe de ta clé |
| `KEY_ALIAS` | `sankai` |

Tant que `KEYSTORE_BASE64` n'existe pas, le job « APK + AAB signés » se termine
en vert sans rien produire. Rien ne casse.

### 3. Supprimer le fichier temporaire

```powershell
Remove-Item cles\keystore-base64.txt
```

---

## Publier une version téléchargeable

Une **Release** donne un lien de téléchargement permanent, bien plus pratique
qu'un artefact qui expire au bout de 90 jours. C'est de là que tu installes
l'app au quotidien.

Tout est automatique : il suffit de poser un tag.

```bash
git tag v1.1.0
git push origin v1.1.0
```

GitHub compile, crée la Release, y attache l'APK et rédige les notes
d'installation. Rien à faire sur le site.

> Avant de taguer, incrémente `versionCode` dans
> `SankaiLife/app/build.gradle.kts`. Android refuse d'installer une mise à jour
> dont le `versionCode` n'a pas augmenté.

Le lien qui pointe toujours vers la dernière version :
https://github.com/N7T0-OF/sankai-life/releases/latest

---

## ⚠️ Signature : pourquoi ça compte vraiment ici

Sans secrets de signature configurés, GitHub compile l'APK avec une **clé de
debug jetable, régénérée à chaque compilation**. Conséquence : deux Releases
n'ont jamais la même signature, et Android refuse d'installer l'une par-dessus
l'autre.

Il faudrait alors **désinstaller avant chaque mise à jour** — et comme Sankai
Life stocke tout en local, désinstaller efface la progression, les mémos, les
statistiques. Il n'y a pas de serveur pour les récupérer.

Configurer les secrets ci-dessous règle définitivement le problème : les mises
à jour s'installent par-dessus, les données sont conservées.

### Quelle clé confier à GitHub ?

Deux approches :

**La même clé que le Play Store.** Simple, mais si ton compte GitHub était
compromis, l'attaquant pourrait signer une fausse mise à jour de ton app
publiée. Acceptable pour un dépôt privé avec l'authentification à deux facteurs
activée.

**Une clé dédiée à GitHub**, distincte de celle du Play Store. Plus sûr :
même en cas de fuite, ton identité Play Store reste intacte. Les APK GitHub et
Play Store ont alors des signatures différentes, ce qui n'est pas gênant
puisque ce sont deux canaux de distribution séparés.

Pour générer une clé dédiée :

```powershell
.\scripts\03-generer-keystore.ps1 -Alias sankai-ci
```

---

## Ce que le dépôt ne contient pas

Volontairement absents, et protégés par `.gitignore` :

| Exclu | Pourquoi |
|---|---|
| `cles/`, `*.jks` | La clé de signature ne doit jamais être versionnée |
| `keystore.properties` | Contient le mot de passe de la clé |
| `outils/` | ~1 Go de toolchain, retéléchargeable en une commande |
| `dist/` | Les APK sont régénérables |

Si tu clones ce dépôt sur une autre machine, lance `COMPILER-APK.bat` : la
toolchain se réinstalle toute seule.

---

## Le dépôt est privé

Personne ne peut le voir sans y être invité.

Pour donner l'accès à quelqu'un : **Settings → Collaborators → Add people**.

Pour le rendre public plus tard : **Settings → General → Danger Zone → Change
visibility**. Vérifie d'abord qu'aucun secret n'a jamais été committé — l'historique
Git garde tout, même ce qui a été supprimé depuis.
