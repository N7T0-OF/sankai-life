#!/usr/bin/env python3
"""Masques de transition entre terrains, generes.

Le probleme
-----------
L'ile se dessine case par case. Chaque case est un carre plein, donc une cote
est un escalier de carres : le terrain se lit comme une grille, pas comme un
paysage. C'est le reproche « trop cubique », et il est fonde.

La solution retenue
-------------------
Le remede habituel est l'autotuilage : au lieu de peindre un carre de sable a
cote d'un carre d'herbe, on peint le sable **puis** l'herbe a travers un masque
dont le bord est irregulier. La frontiere cesse d'etre une droite.

Les seize masques correspondent aux seize combinaisons de voisins de meme
terrain — nord, est, sud, ouest. Le bord n'est pas un arc de cercle mais une
courbe bruitee : un arc parfait remplacerait un motif carre par un motif rond,
et l'oeil retrouverait la grille aussi vite.

Le bruit est **tire de l'indice du masque**, donc identique d'une generation a
l'autre : sans cela, chaque reconstruction changerait la forme des cotes.

Usage : python art/generer-transitions.py
"""

from PIL import Image, ImageDraw, ImageFilter
import math
import os
import random

TAILLE = 64
SORTIE = os.path.join(os.path.dirname(__file__), "genere")
DESTINATION = os.path.join(
    os.path.dirname(__file__), "..", "SankaiLife", "app", "src", "main",
    "res", "drawable-nodpi"
)

# Bits : 1 = nord, 2 = est, 4 = sud, 8 = ouest.
NORD, EST, SUD, OUEST = 1, 2, 4, 8


def bord_bruite(rnd, longueur, amplitude=0.13):
    """Une suite de decalages doux le long d'un bord, en fraction de la tuile."""
    points = [rnd.uniform(-amplitude, amplitude) for _ in range(5)]
    # Lissage : trois passes de moyenne, sinon le bord fait des dents.
    for _ in range(3):
        points = [
            (points[max(0, i - 1)] + points[i] + points[min(len(points) - 1, i + 1)]) / 3
            for i in range(len(points))
        ]
    sortie = []
    for i in range(longueur):
        t = i / max(1, longueur - 1) * (len(points) - 1)
        a, b = int(t), min(int(t) + 1, len(points) - 1)
        sortie.append(points[a] + (points[b] - points[a]) * (t - int(t)))
    return sortie


def masque(code):
    """Masque alpha pour une combinaison de voisins.

    Le terrain superieur occupe la case entiere, moins les cotes ou il n'a pas
    de voisin : de ce cote-la, il se retire vers l'interieur en suivant un bord
    irregulier.
    """
    rnd = random.Random(1000 + code)
    im = Image.new("L", (TAILLE, TAILLE), 255)
    d = ImageDraw.Draw(im)

    # Deux reculs, et c'est un correctif.
    #
    # Un recul unique de 0,34 sur les quatre cotes reduisait une bande d'une
    # case de large a un tiers de sa largeur : les isthmes de sable devenaient
    # des pointes fines qu'on lit comme un defaut d'affichage. Quand le cote
    # oppose recule aussi, chacun recule donc moins.
    OPPOSE = {NORD: SUD, SUD: NORD, EST: OUEST, OUEST: EST}

    for bit, cote in ((NORD, "n"), (EST, "e"), (SUD, "s"), (OUEST, "o")):
        if code & bit:
            continue  # voisin present : rien a retirer de ce cote
        seul = bool(code & OPPOSE[bit])
        marge = TAILLE * (0.34 if seul else 0.22)
        decalages = bord_bruite(rnd, TAILLE)
        for i in range(TAILLE):
            recul = marge * (1 + decalages[i] * 2.2)
            recul = max(2, min(TAILLE * 0.48, recul))
            if cote == "n":
                d.line([(i, 0), (i, recul)], fill=0)
            elif cote == "s":
                d.line([(i, TAILLE - recul), (i, TAILLE)], fill=0)
            elif cote == "o":
                d.line([(0, i), (recul, i)], fill=0)
            else:
                d.line([(TAILLE - recul, i), (TAILLE, i)], fill=0)

    # Un flou leger fait la difference entre un bord decoupe aux ciseaux et une
    # cote. Trop de flou, et le sable bave sur l'eau.
    return im.filter(ImageFilter.GaussianBlur(TAILLE * 0.035))


def main():
    os.makedirs(SORTIE, exist_ok=True)
    total = 0
    for code in range(16):
        m = masque(code)
        # On enregistre en niveaux de gris : Android s'en sert comme alpha via
        # un ColorFilter, ce qui evite seize PNG en couleur.
        rgba = Image.new("RGBA", (TAILLE, TAILLE), (255, 255, 255, 0))
        rgba.putalpha(m)
        nom = f"tuile_transition_{code:02d}.png"
        rgba.save(os.path.join(SORTIE, nom), optimize=True)
        cible = os.path.join(DESTINATION, nom)
        rgba.save(cible, optimize=True)
        total += os.path.getsize(cible)
    print(f"16 masques ecrits, {total // 1024} ko au total.")


if __name__ == "__main__":
    main()
