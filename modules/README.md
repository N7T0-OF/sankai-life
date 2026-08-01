# Modules d'apprentissage

Des paquets de cartes prêts à installer dans Sankai Life. Ils fonctionnent
entièrement hors ligne une fois importés.

---

## Un module ne contient que des données

Pas de script, pas de classe, pas d'expression évaluée. Uniquement du texte et
des médias.

Ce n'est pas une promesse : **le format n'a aucun champ exécutable**, et
l'application n'appelle aucun interpréteur en les lisant. Il n'y a rien à
désactiver parce qu'il n'y a rien à activer. C'est ce qui permet d'installer
le module d'un inconnu sans lui donner les clés du téléphone.

---

## Structure

```
mon-module/
├── module.json       obligatoire
├── flashcards.txt    obligatoire
├── README.md         facultatif
└── LICENSE           facultatif
```

Pour partager, compresse le dossier en `.zip`.

### `module.json`

```json
{
  "schemaVersion": 1,
  "id": "portugais-debutant-a1",
  "name": "Portugais débutant",
  "version": "1.0.0",
  "language": "pt",
  "sourceLanguage": "fr",
  "author": "Ton nom",
  "description": "Premières phrases du quotidien.",
  "license": "CC BY 4.0",
  "offline": true,
  "flashcardsFile": "flashcards.txt"
}
```

Le schéma complet est dans [schema/module.schema.json](schema/module.schema.json).

`id` doit rester **stable entre les versions** : c'est ce qui permet de
reconnaître une mise à jour du même module plutôt qu'un module différent.

### `flashcards.txt`

Une carte par ligne, question et réponse séparées par une barre verticale.

```
Olá | Bonjour
Obrigado | Merci
```

C'est le format le plus simple qui existe : il se lit dans n'importe quel
éditeur, se relit sans l'application, et se colle depuis un tableur.

Une ligne sans séparateur devient une carte à une seule face — utile pour une
phrase à se remémorer plutôt qu'une traduction.

---

## Installer un module

Dans l'application : **Mode Vie → Mémo → Importer un module**.

Avant d'installer, un aperçu montre le nom, l'auteur, la version, le nombre de
cartes, la taille et la licence. Rien n'est écrit en base avant que tu aies
confirmé.

Un module portant le nom d'un module existant est installé comme **copie
numérotée**, jamais en remplacement. Remplacer par erreur détruit un travail ;
ajouter par erreur crée un doublon qu'on supprime en deux gestes.

---

## Limites appliquées à l'import

| Limite | Valeur | Pourquoi |
|---|---|---|
| Cartes par module | 20 000 | Au-delà, la révision espacée n'a plus de sens |
| Longueur d'une ligne | 2 000 caractères | Une carte plus longue ne se lit pas sur un téléphone |
| Taille de l'archive | 20 Mo | Garde l'import instantané |

Une ligne trop longue est **tronquée**, pas rejetée : une seule ligne aberrante
dans un module de mille cartes ne doit pas faire perdre les neuf cent
quatre-vingt-dix-neuf autres.

Les chemins d'archive contenant `..`, `\` ou commençant par `/` sont écartés
avant lecture. Une archive vient de l'extérieur.

---

## Modules prêts à installer

Télécharge le `.zip`, puis importe-le depuis l'application.

| Module | Cartes | Licence | Fichier |
|---|---|---|---|
| Portugais débutant | 20 | CC BY 4.0 | [portugais-debutant.zip](paquets/portugais-debutant.zip) |
| Raccourcis Blender | 20 | CC BY 4.0 | [raccourcis-blender.zip](paquets/raccourcis-blender.zip) |

Depuis un téléphone : ouvre le lien, puis **Download raw file**. Le fichier
arrive dans les téléchargements, où le sélecteur de l'application le trouve.

Les sources correspondantes sont dans [examples/](examples) — un dossier se
relit et se corrige, un `.zip` non. Après toute modification :

```bash
python modules/construire-paquets.py
```

Sans cette étape, les `.zip` publiés ne correspondraient plus à leurs sources,
et personne ne s'en apercevrait avant l'import.

---

## Proposer un module

Ouvre une *pull request* qui ajoute un dossier dans `community/`.

Ce qui est demandé :

- un `module.json` valide, avec un `author` et une `license` réels ;
- du contenu que tu as le droit de partager — pas de copie d'une méthode
  commerciale ;
- pas de fichier exécutable, pas d'archive imbriquée.

Ce qui sera refusé : contenu sous droits, contenu haineux, contenu trompeur
présenté comme pédagogique.
