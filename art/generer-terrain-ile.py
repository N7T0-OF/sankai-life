#!/usr/bin/env python3
"""Textures de terrain de l'Ile, generees.

Pourquoi ce script existe
-------------------------
L'eau, le sable, le bois et le rocher n'avaient aucune illustration : ils
etaient dessines en aplats de couleur, ce qui se voyait. Les illustrations
peintes du projet viennent de son auteur et je ne sais pas en produire.

Une texture de terrain, en revanche, est procedurale : du bruit, des teintes et
un raccord. Ces trois-la sont donc generees, et c'est assume — elles ne
pretendent pas etre du dessin.

Ce qui a ete rejete
-------------------
Le **rocher**. Les essais donnaient des taches quantifiees qui ressemblaient a
du camouflage, nettement pires que le gris uni. Il reste un aplat.

Une premiere version de l'eau utilisait un nombre d'ondes non entier : les
bandes ne se refermaient pas sur la tuile et laissaient une couture verticale,
en plus de ressembler a de la tole ondulee. Les houles sont desormais a compte
entier et de faible amplitude.

Usage : python art/generer-terrain-ile.py
"""

from PIL import Image, ImageFilter
import math
import os
import random

TAILLE = 256
SORTIE = os.path.join(os.path.dirname(__file__), "genere")
DESTINATION = os.path.join(
    os.path.dirname(__file__), "..", "SankaiLife", "app", "src", "main",
    "res", "drawable-nodpi"
)


def bruit(taille, cellules, graine, octaves=3):
    """Bruit de valeur qui se raccorde bord a bord.

    Le modulo sur les indices de grille est ce qui referme la tuile : sans lui,
    deux tuiles voisines montrent une couture nette.
    """
    rnd = random.Random(graine)
    champ = [[0.0] * taille for _ in range(taille)]
    amplitude, total = 1.0, 0.0
    for octave in range(octaves):
        c = cellules * (2 ** octave)
        grille = [[rnd.random() for _ in range(c)] for _ in range(c)]
        for y in range(taille):
            for x in range(taille):
                fx, fy = x / taille * c, y / taille * c
                x0, y0 = int(fx) % c, int(fy) % c
                x1, y1 = (x0 + 1) % c, (y0 + 1) % c
                tx, ty = fx - int(fx), fy - int(fy)
                sx, sy = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
                haut = grille[y0][x0] * (1 - sx) + grille[y0][x1] * sx
                bas = grille[y1][x0] * (1 - sx) + grille[y1][x1] * sx
                champ[y][x] += (haut * (1 - sy) + bas * sy) * amplitude
        total += amplitude
        amplitude *= 0.5
    return [[v / total for v in ligne] for ligne in champ]


def melanger(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def eau(nom, clair, sombre, graine, ondes):
    """Deux houles croisees, a compte entier pour se refermer sur la tuile."""
    n1 = bruit(TAILLE, 4, graine, 3)
    n2 = bruit(TAILLE, 8, graine + 100, 2)
    im = Image.new("RGBA", (TAILLE, TAILLE))
    px = im.load()
    for y in range(TAILLE):
        for x in range(TAILLE):
            houle = (
                math.sin((x / TAILLE * ondes + n1[y][x] * 0.35) * 2 * math.pi)
                + math.sin((y / TAILLE * (ondes + 1) + n2[y][x] * 0.35) * 2 * math.pi)
            ) * 0.25 + 0.5
            t = houle * 0.22 + n1[y][x] * 0.58 + n2[y][x] * 0.20
            px[x, y] = melanger(sombre, clair, max(0.0, min(1.0, t))) + (255,)
    return im.filter(ImageFilter.SMOOTH_MORE)


def sable(graine):
    """Grain fin. Le bruit par pixel evite un motif que l'oeil retrouverait."""
    n = bruit(TAILLE, 6, graine, 4)
    rnd = random.Random(graine + 7)
    im = Image.new("RGBA", (TAILLE, TAILLE))
    px = im.load()
    for y in range(TAILLE):
        for x in range(TAILLE):
            t = n[y][x] * 0.75 + 0.15 + (rnd.random() - 0.5) * 0.10
            px[x, y] = melanger((0xD8, 0xBE, 0x88), (0xF2, 0xE2, 0xB8),
                                max(0.0, min(1.0, t))) + (255,)
    return im


def enregistrer(image, nom):
    os.makedirs(SORTIE, exist_ok=True)
    # Palette reduite : ces textures ont peu de teintes, et 64 couleurs
    # divisent leur poids par dix sans difference visible.
    reduite = image.quantize(colors=64, method=Image.FASTOCTREE).convert("RGBA")
    reduite.save(os.path.join(SORTIE, nom + ".png"), optimize=True)
    cible = os.path.join(DESTINATION, nom + ".png")
    reduite.save(cible, optimize=True)
    print(f"{nom:24} {os.path.getsize(cible) // 1024} ko")


def main():
    enregistrer(eau("", (0x2A, 0x6E, 0x8E), (0x12, 0x40, 0x58), 11, 2),
                "island_deep_water")
    enregistrer(eau("", (0x6F, 0xC8, 0xE2), (0x2E, 0x8C, 0xAE), 23, 3),
                "island_shallow_water")
    enregistrer(sable(37), "island_beach")


if __name__ == "__main__":
    main()
