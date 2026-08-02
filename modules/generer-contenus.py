#!/usr/bin/env python3
"""Genere les dossiers de modules, les paquets ZIP et le catalogue.

Pourquoi ce script existe
-------------------------
Le contenu vit dans `contenus/*.py` sous forme de listes de paires, parce que
c'est la forme qui se relit et se corrige : une faute de traduction se voit
dans une revue, pas dans un ZIP.

Ce script en tire trois choses :

* `examples/<id>/` — le module en clair, versionne, diffable ;
* `paquets/<id>.zip` — l'archive installable, celle que l'application telecharge ;
* `catalogue.json` — l'index que l'application lit pour savoir ce qui existe.

Le catalogue porte la **taille et l'empreinte** de chaque paquet. La taille
permet d'annoncer le telechargement avant de le lancer ; l'empreinte permet de
verifier qu'on a recu ce qu'on attendait. Sans elle, un fichier tronque
s'installerait a moitie sans que rien ne le signale.

Rien n'est embarque dans l'application : un catalogue de quinze langues
pesant chacune quelques dizaines de kilo-octets ferait grossir l'APK pour du
contenu que la plupart n'ouvriront jamais. On telecharge ce qu'on veut, et ce
qu'on a telecharge fonctionne ensuite entierement hors ligne.

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

AUTEUR = "Sankai Life"
LICENCE = "CC BY 4.0"
SEPARATEUR = " | "


def charger_paquets():
    """Lit tous les fichiers de contenu du dossier `contenus`."""
    paquets = []
    for fichier in sorted(CONTENUS.glob("*.py")):
        if fichier.name.startswith("_"):
            continue
        spec = importlib.util.spec_from_file_location(fichier.stem, fichier)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        paquets.extend(getattr(module, "PAQUETS", []))
    return paquets


def verifier(paquet):
    """Refuse un paquet incoherent plutot que de le publier.

    Une carte sans reponse ou un separateur present dans le texte casserait le
    decoupage recto/verso a l'installation, et l'erreur ne se verrait que sur
    le telephone de quelqu'un d'autre.
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
        "author": AUTEUR,
        "description": paquet["description"],
        "license": LICENCE,
        "offline": True,
        "flashcardsFile": "flashcards.txt",
    }
    (dossier / "module.json").write_text(
        json.dumps(manifeste, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    lignes = [f"{r}{SEPARATEUR}{v}" for r, v in paquet["cartes"]]
    (dossier / "flashcards.txt").write_text(
        "\n".join(lignes) + "\n", encoding="utf-8"
    )
    return dossier


def construire_zip(dossier, identifiant):
    PAQUETS.mkdir(parents=True, exist_ok=True)
    chemin = PAQUETS / f"{identifiant}.zip"
    # Deterministe : meme contenu, meme archive. Sans date fixe, chaque
    # execution produirait un fichier different et le depot se remplirait de
    # modifications qui n'en sont pas.
    with zipfile.ZipFile(chemin, "w", zipfile.ZIP_DEFLATED) as archive:
        for fichier in sorted(dossier.iterdir()):
            info = zipfile.ZipInfo(fichier.name, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, fichier.read_bytes())
    return chemin


def main():
    paquets = charger_paquets()
    if not paquets:
        print("Aucun contenu trouve dans contenus/")
        return 1

    entrees = []
    for paquet in paquets:
        erreurs = verifier(paquet)
        if erreurs:
            print(f"REFUS {paquet['id']} :")
            for e in erreurs:
                print("   -", e)
            return 1

        dossier = ecrire_module(paquet)
        chemin = construire_zip(dossier, paquet["id"])
        octets = chemin.stat().st_size
        empreinte = hashlib.sha256(chemin.read_bytes()).hexdigest()

        entrees.append({
            "id": paquet["id"],
            "nom": paquet["nom"],
            "description": paquet["description"],
            "langue": paquet.get("langue", ""),
            "niveau": paquet.get("niveau", ""),
            "cartes": len(paquet["cartes"]),
            "octets": octets,
            "sha256": empreinte,
            "licence": LICENCE,
            "auteur": AUTEUR,
            "url": f"{BASE_URL}/{paquet['id']}.zip",
        })
        print(f"{paquet['id']:26} {len(paquet['cartes']):4} cartes  {octets:6} o")

    catalogue = {
        "schemaVersion": 1,
        "modules": sorted(entrees, key=lambda e: (e["langue"] == "", e["nom"])),
    }
    (RACINE / "catalogue.json").write_text(
        json.dumps(catalogue, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    total = sum(e["octets"] for e in entrees)
    print(f"\n{len(entrees)} modules, {sum(e['cartes'] for e in entrees)} cartes, "
          f"{total // 1024} ko au total.")
    print("catalogue.json ecrit.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
