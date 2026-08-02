#!/usr/bin/env python3
"""Construit les paquets et le catalogue a partir des modules du depot.

Ce que fait ce script
---------------------
Il lit tous les dossiers de `examples/`, quelle que soit leur origine — ecrits
a la main, generes depuis `contenus/*.py`, ou fournis par quelqu'un d'autre —
et en tire :

* `paquets/<id>.zip` — l'archive installable ;
* `paquets/<collection>.zip` — la collection entiere en un fichier ;
* `catalogue.json` — l'index que l'application lit.

Pourquoi les collections
------------------------
Six niveaux de portugais donnaient six themes independants : on installait A1
sans savoir que A2 existait, et rien ne disait par ou continuer une fois A1
termine. Une collection les relie, s'installe d'un seul geste — ou niveau par
niveau, parce que quelqu'un qui debute n'a pas besoin du C2.

Le catalogue porte **taille et empreinte** de chaque paquet : la taille permet
d'annoncer le telechargement avant de le lancer, l'empreinte de verifier qu'on
a recu ce qu'on attendait.

Usage : python modules/generer-contenus.py
"""

from pathlib import Path
import hashlib
import importlib.util
import json
import sys
import zipfile

RACINE = Path(__file__).resolve().parent
CONTENUS = RACINE / "contenus"
EXEMPLES = RACINE / "examples"
PAQUETS = RACINE / "paquets"

DEPOT = "N7T0-OF/sankai-life"
BASE_URL = f"https://raw.githubusercontent.com/{DEPOT}/main/modules/paquets"

SEPARATEUR = " | "
DATE_FIXE = (2026, 1, 1, 0, 0, 0)


# --- Modules ecrits en Python -------------------------------------------------

def generer_depuis_python():
    """Ecrit dans `examples/` les modules decrits dans `contenus/*.py`."""
    for fichier in sorted(CONTENUS.glob("*.py")):
        if fichier.name.startswith("_"):
            continue
        spec = importlib.util.spec_from_file_location(fichier.stem, fichier)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        for paquet in getattr(module, "PAQUETS", []):
            erreurs = verifier(paquet)
            if erreurs:
                print(f"REFUS {paquet['id']} :")
                for e in erreurs:
                    print("   -", e)
                return False
            ecrire_module(paquet)
    return True


def verifier(paquet):
    """Refuse un paquet incoherent plutot que de le publier.

    Une carte sans reponse casserait le decoupage recto/verso a l'installation,
    et l'erreur ne se verrait que sur le telephone de quelqu'un d'autre.
    """
    erreurs = []
    vus = set()
    for recto, verso in paquet["cartes"]:
        if not recto.strip() or not verso.strip():
            erreurs.append(f"carte vide : {recto!r} / {verso!r}")
        if SEPARATEUR.strip() in recto or SEPARATEUR.strip() in verso:
            erreurs.append(f"separateur present dans la carte : {recto!r}")
        if recto in vus:
            erreurs.append(f"recto en double : {recto!r}")
        vus.add(recto)
    if len(paquet["cartes"]) < 10:
        erreurs.append("moins de dix cartes : trop peu pour un parcours")
    return erreurs


def ecrire_module(paquet):
    dossier = EXEMPLES / paquet["id"]
    dossier.mkdir(parents=True, exist_ok=True)
    manifeste = {
        "schemaVersion": 1,
        "id": paquet["id"],
        "name": paquet["nom"],
        "version": "1.0.0",
        "language": paquet.get("langue", ""),
        "sourceLanguage": "fr" if paquet.get("langue") else "",
        "author": "Sankai Life",
        "description": paquet["description"],
        "license": "CC BY 4.0",
        "offline": True,
        "flashcardsFile": "flashcards.txt",
    }
    (dossier / "module.json").write_text(
        json.dumps(manifeste, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    lignes = [f"{r}{SEPARATEUR}{v}" for r, v in paquet["cartes"]]
    (dossier / "flashcards.txt").write_text("\n".join(lignes) + "\n", encoding="utf-8")


# --- Lecture des modules ------------------------------------------------------

def lire_modules():
    """Lit tous les dossiers de `examples/` contenant un module valide."""
    modules = {}
    for dossier in sorted(EXEMPLES.iterdir()):
        manifeste_path = dossier / "module.json"
        if not dossier.is_dir() or not manifeste_path.exists():
            continue
        manifeste = json.loads(manifeste_path.read_text(encoding="utf-8"))
        nom_cartes = manifeste.get("flashcardsFile", "flashcards.txt")
        cartes_path = dossier / nom_cartes
        if not cartes_path.exists():
            print(f"IGNORE {dossier.name} : {nom_cartes} absent")
            continue
        lignes = [
            l for l in cartes_path.read_text(encoding="utf-8").splitlines() if l.strip()
        ]
        modules[manifeste["id"]] = {
            "dossier": dossier,
            "manifeste": manifeste,
            "cartes": len(lignes),
        }
    return modules


# --- Paquets ------------------------------------------------------------------

def zipper(chemin, fichiers):
    """Archive deterministe : meme contenu, meme fichier.

    Sans date fixe, chaque execution produirait une archive differente et le
    depot se remplirait de modifications qui n'en sont pas.
    """
    with zipfile.ZipFile(chemin, "w", zipfile.ZIP_DEFLATED) as archive:
        for nom, donnees in sorted(fichiers.items()):
            info = zipfile.ZipInfo(nom, date_time=DATE_FIXE)
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, donnees)
    return chemin


def empreinte(chemin):
    return hashlib.sha256(chemin.read_bytes()).hexdigest()


def construire_module(entree):
    dossier = entree["dossier"]
    fichiers = {
        f.name: f.read_bytes()
        for f in dossier.iterdir()
        if f.is_file() and f.suffix in (".json", ".txt", "") or f.name == "LICENSE"
    }
    return zipper(PAQUETS / f"{entree['manifeste']['id']}.zip", fichiers)


def construire_collection(collection, modules):
    """Une collection entiere dans une seule archive.

    Chaque module garde son dossier a l'interieur : l'application y retrouve
    plusieurs modules et les installe tous, relies entre eux.
    """
    fichiers = {"collection.json": json.dumps(
        {
            "schemaVersion": 1,
            "type": "collection",
            "id": collection["id"],
            "name": collection["nom"],
            "language": collection.get("langue", ""),
            "description": collection.get("description", ""),
            "author": collection.get("auteur", ""),
            "modules": [
                {"id": m["id"], "level": m.get("niveau", ""), "order": i}
                for i, m in enumerate(collection["modules"])
            ],
        },
        ensure_ascii=False, indent=2
    ).encode("utf-8")}

    for m in collection["modules"]:
        entree = modules.get(m["id"])
        if entree is None:
            continue
        for f in entree["dossier"].iterdir():
            if f.is_file():
                fichiers[f"{m['id']}/{f.name}"] = f.read_bytes()
    return zipper(PAQUETS / f"collection-{collection['id']}.zip", fichiers)


def main():
    if not generer_depuis_python():
        return 1

    PAQUETS.mkdir(parents=True, exist_ok=True)
    modules = lire_modules()
    if not modules:
        print("Aucun module trouve dans examples/")
        return 1

    collections_src = json.loads((RACINE / "collections.json").read_text(encoding="utf-8"))
    # Le module appartient a la collection qui le cite : c'est la collection qui
    # decide, pas le module. Un module peut donc etre repris ailleurs sans
    # modification.
    appartenance = {
        m["id"]: (c, m.get("niveau", ""))
        for c in collections_src["collections"]
        for m in c["modules"]
    }

    entrees_modules = []
    for identifiant, entree in modules.items():
        chemin = construire_module(entree)
        collection, niveau = appartenance.get(identifiant, (None, ""))
        man = entree["manifeste"]
        entrees_modules.append({
            "id": identifiant,
            "nom": man.get("name", identifiant),
            "description": man.get("description", ""),
            "langue": man.get("language", ""),
            "niveau": niveau,
            "collection": collection["id"] if collection else "",
            "cartes": entree["cartes"],
            "octets": chemin.stat().st_size,
            "sha256": empreinte(chemin),
            "licence": man.get("license", ""),
            "auteur": man.get("author", ""),
            "url": f"{BASE_URL}/{identifiant}.zip",
        })
        print(f"{identifiant:26} {entree['cartes']:4} cartes  {chemin.stat().st_size:6} o")

    entrees_collections = []
    for collection in collections_src["collections"]:
        presents = [m for m in collection["modules"] if m["id"] in modules]
        if not presents:
            continue
        chemin = construire_collection(collection, modules)
        entrees_collections.append({
            "id": collection["id"],
            "nom": collection["nom"],
            "description": collection.get("description", ""),
            "langue": collection.get("langue", ""),
            "auteur": collection.get("auteur", ""),
            "modules": [m["id"] for m in presents],
            "niveaux": [m.get("niveau", "") for m in presents],
            "cartes": sum(modules[m["id"]]["cartes"] for m in presents),
            "octets": chemin.stat().st_size,
            "sha256": empreinte(chemin),
            "url": f"{BASE_URL}/collection-{collection['id']}.zip",
        })
        print(f"[collection] {collection['id']:14} {len(presents)} modules  "
              f"{chemin.stat().st_size:6} o")

    catalogue = {
        "schemaVersion": 2,
        "collections": sorted(entrees_collections, key=lambda e: e["nom"]),
        "modules": sorted(
            entrees_modules, key=lambda e: (e["collection"], e["niveau"], e["nom"])
        ),
    }
    (RACINE / "catalogue.json").write_text(
        json.dumps(catalogue, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    cartes = sum(e["cartes"] for e in entrees_modules)
    print(f"\n{len(entrees_modules)} modules, {len(entrees_collections)} collections, "
          f"{cartes} cartes.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
