#!/usr/bin/env python3
"""Construit les paquets .zip installables a partir des dossiers d'exemples.

Pourquoi ce script existe
-------------------------
L'application importe un module sous forme d'archive ZIP. Les modules vivent
ici sous forme de dossiers, parce qu'un dossier se relit, se corrige et se
compare dans une revue — un ZIP, non.

GitHub ne sait pas telecharger un sous-dossier : quelqu'un qui ouvre
`modules/examples/portugais-debutant` depuis un telephone n'a aucun moyen d'en
recuperer le contenu en un fichier. Sans les paquets construits ici, le bouton
« Voir les modules disponibles » menerait a une page ou rien n'est installable.

Usage : python modules/construire-paquets.py
"""

from pathlib import Path
import json
import zipfile

RACINE = Path(__file__).resolve().parent
SOURCES = RACINE / "examples"
SORTIE = RACINE / "paquets"


def construire(dossier: Path) -> Path:
    manifeste = json.loads((dossier / "module.json").read_text(encoding="utf-8"))
    nom_cartes = manifeste.get("flashcardsFile", "flashcards.txt")

    if not (dossier / nom_cartes).exists():
        raise SystemExit(
            f"{dossier.name} : le manifeste annonce « {nom_cartes} », absent du dossier."
        )

    SORTIE.mkdir(exist_ok=True)
    cible = SORTIE / f"{dossier.name}.zip"

    # Les entrees sont ecrites a plat, sans le dossier parent : l'application
    # sait retrouver un fichier dans un sous-dossier, mais une archive plate ne
    # depend d'aucun comportement particulier de l'outil de compression.
    with zipfile.ZipFile(cible, "w", zipfile.ZIP_DEFLATED) as zf:
        for nom in ("module.json", nom_cartes):
            zf.write(dossier / nom, arcname=nom)

    # Meme regle que ModuleEngine.nettoyerLigne : seules les lignes vides sont
    # ecartees. Le format n'a pas de ligne de commentaire, et compter comme si
    # `#` en etait une annoncerait un nombre de cartes que l'application ne
    # retrouverait pas.
    lignes = [
        ligne
        for ligne in (dossier / nom_cartes).read_text(encoding="utf-8").splitlines()
        if ligne.strip()
    ]
    print(f"{cible.name} — {len(lignes)} cartes, {cible.stat().st_size} octets")
    return cible


def main() -> None:
    dossiers = sorted(d for d in SOURCES.iterdir() if (d / "module.json").exists())
    if not dossiers:
        raise SystemExit("Aucun module a construire dans modules/examples.")
    for dossier in dossiers:
        construire(dossier)


if __name__ == "__main__":
    main()
