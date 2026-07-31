#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Générateur d'assets « au crayon » pour Sankai Life.

Pourquoi un générateur plutôt que des fichiers dessinés à la main : le style
doit être identique sur cent quarante éléments. Une constante de tremblement
changée ici se répercute partout ; cent quarante fichiers écrits séparément
divergeraient dès la première retouche.

Sort deux formats depuis la même définition :

  art/svg/<nom>.svg                          — pour un graphiste, éditable
  app/src/main/res/drawable/art_<nom>.xml    — VectorDrawable Android

Le VectorDrawable est natif : pas de conversion, pas de PNG par densité, et
quelques kilo-octets au lieu de plusieurs mégaoctets dans l'APK.

L'effet crayon vient de trois choses, appliquées automatiquement :
  1. les contours tremblent — chaque point est décalé d'un bruit déterministe ;
  2. ils sont tracés deux fois, légèrement décalés, comme un trait repassé ;
  3. les aplats sont hachurés au lieu d'être pleins.

Le bruit est déterministe (graine fixe) : relancer le script ne redessine pas
tout différemment, sinon chaque exécution polluerait le dépôt.
"""

import math
import os
import random

RACINE = os.path.dirname(os.path.abspath(__file__))
PROJET = os.path.dirname(RACINE)
SORTIE_SVG = os.path.join(RACINE, "svg")
SORTIE_XML = os.path.join(PROJET, "SankaiLife", "app", "src", "main", "res", "drawable")

TAILLE = 96.0

# Palette. Volontairement désaturée et terreuse : un crayon de couleur sur
# papier, pas un aplat numérique.
C = {
    "trait":      "#3B3025",
    "trait_clair":"#6B5B49",
    "vert":       "#6E9B57",
    "vert_fonce": "#41703C",
    "vert_clair": "#A8C97F",
    "terre":      "#8A6A45",
    "terre_fonce":"#573F28",
    "sable":      "#D8C08A",
    "eau":        "#6FA8C7",
    "eau_fonce":  "#3D7A9B",
    "or":         "#D9A441",
    "or_clair":   "#F0CE7E",
    "bois":       "#9A6E43",
    "pierre":     "#9AA0A6",
    "violet":     "#8A6BB0",
    "rouge":      "#C4614B",
    "blanc":      "#F2EADB",
    "nuit":       "#3A4670",
    "cristal":    "#8FD4D9",
}

random.seed(20260731)          # graine fixe : sortie reproductible
_bruit = [random.uniform(-1.0, 1.0) for _ in range(4096)]
_curseur = [0]


def _n(amplitude):
    """Prochain décalage de bruit. Cyclique, donc stable d'un run à l'autre."""
    v = _bruit[_curseur[0] % len(_bruit)]
    _curseur[0] += 1
    return v * amplitude


def polyligne(points, amplitude=1.0, fermer=True):
    """Trace une polyligne tremblée. C'est la brique de tout le reste."""
    if not points:
        return ""
    d = []
    for i, (x, y) in enumerate(points):
        px = x + _n(amplitude)
        py = y + _n(amplitude)
        d.append(("M" if i == 0 else "L") + "%.1f,%.1f" % (px, py))
    if fermer:
        d.append("Z")
    return "".join(d)


def cercle(cx, cy, r, amplitude=1.0, segments=18, aplat_y=1.0):
    """Cercle ou ellipse au trait tremblé."""
    pts = []
    for i in range(segments):
        a = 2 * math.pi * i / segments
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r * aplat_y))
    return polyligne(pts, amplitude)


def arc(cx, cy, r, a0, a1, amplitude=0.8, segments=10):
    pts = []
    for i in range(segments + 1):
        a = math.radians(a0 + (a1 - a0) * i / segments)
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return polyligne(pts, amplitude, fermer=False)


def hachures(x0, y0, x1, y1, angle=45, ecart=6.0, amplitude=0.7):
    """Hachures parallèles couvrant une boîte. Découpées ensuite par clip."""
    d = []
    diagonale = math.hypot(x1 - x0, y1 - y0)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    rad = math.radians(angle)
    dx, dy = math.cos(rad), math.sin(rad)
    nx, ny = -dy, dx
    n = int(diagonale / ecart) + 2
    for i in range(-n, n):
        ox, oy = cx + nx * i * ecart, cy + ny * i * ecart
        d.append(polyligne(
            [(ox - dx * diagonale / 2, oy - dy * diagonale / 2),
             (ox + dx * diagonale / 2, oy + dy * diagonale / 2)],
            amplitude, fermer=False))
    return "".join(d)


# --------------------------------------------------------------------------
# Un élément = une liste de couches. Chaque couche est un dict :
#   d       : chemin
#   fill    : remplissage (ou None)
#   stroke  : couleur du trait (ou None)
#   w       : épaisseur
#   hach    : (chemin de hachures, couleur, épaisseur) découpées par `d`
# --------------------------------------------------------------------------

def forme(d, fill=None, stroke=C["trait"], w=2.0, hach=None, alpha=1.0):
    return {"d": d, "fill": fill, "stroke": stroke, "w": w, "hach": hach,
            "alpha": alpha}


def sol(couleur, hach_couleur=None, motif=None, dy=0.0):
    """
    Une motte de terre : la base de toutes les cases de parcelle.

    `dy` la fait descendre. Les vignettes de croissance en ont besoin : posée
    au milieu du cadre, la motte ne laissait pas la place à la plante, et les
    six stades finissaient identiques.
    """
    dessus = [(12, 46), (30, 38), (50, 36), (70, 39), (84, 47),
              (84, 60), (66, 68), (44, 70), (24, 66), (12, 58)]
    dessus = [(x, y + dy) for (x, y) in dessus]
    couches = [forme(polyligne(dessus, 1.2), fill=couleur, w=2.4,
                     hach=(hachures(12, 36 + dy, 84, 70 + dy, 52, 7),
                           hach_couleur or C["trait_clair"], 1.0))]
    if motif == "cailloux":
        for (cx, cy, r) in ((30, 52, 5), (50, 58, 4), (66, 50, 6)):
            couches.append(forme(cercle(cx, cy + dy, r, 0.8), fill=C["pierre"], w=1.6))
    if motif == "sillons":
        for y in (48, 56, 63):
            couches.append(forme(polyligne(
                [(20, y + dy), (40, y - 2 + dy), (62, y + 1 + dy), (78, y - 1 + dy)],
                1.0, fermer=False), stroke=C["terre_fonce"], w=1.6))
    if motif == "flaques":
        for (cx, cy, r) in ((34, 55, 7), (62, 52, 5)):
            couches.append(forme(cercle(cx, cy + dy, r, 0.9, aplat_y=0.5),
                                 fill=C["eau"], w=1.6, alpha=0.85))
    if motif == "etoiles":
        for (cx, cy) in ((28, 48), (46, 44), (62, 52), (74, 46)):
            cy += dy
            couches.append(forme(polyligne([(cx, cy - 3), (cx + 1, cy), (cx + 3, cy),
                                            (cx + 1, cy + 1), (cx, cy + 4),
                                            (cx - 1, cy + 1), (cx - 3, cy),
                                            (cx - 1, cy)], 0.5),
                                 fill=C["blanc"], stroke=None))
    if motif == "cristaux":
        for (cx, cy, h) in ((34, 50, 12), (52, 46, 16), (66, 52, 10)):
            cy += dy
            couches.append(forme(polyligne([(cx, cy - h), (cx + 5, cy), (cx, cy + 5),
                                            (cx - 5, cy)], 0.7),
                                 fill=C["cristal"], w=1.6))
    return couches


def tige(hauteur, feuilles=0, fleur=None, base_y=76.0):
    """
    Tige avec un nombre variable de feuilles.

    Les feuilles sont réparties sur toute la hauteur et grossissent avec elle :
    c'est ce qui rend les six stades distinguables à trente pixels, ce qui est
    la taille réelle sur une case de jardin.
    """
    sommet = base_y - hauteur
    couches = [forme(polyligne([(48, base_y), (47, base_y - hauteur * 0.5),
                                (48, sommet)], 0.8, fermer=False),
                     stroke=C["vert_fonce"], w=3.0)]
    for i in range(feuilles):
        cote = -1 if i % 2 == 0 else 1
        part = (i + 1) / (feuilles + 1.0)
        y = base_y - hauteur * (0.25 + 0.7 * part)
        envergure = 10 + hauteur * 0.32
        couches.append(forme(polyligne([(48, y), (48 + cote * envergure, y - envergure * 0.5),
                                        (48 + cote * envergure * 1.15, y + 2),
                                        (48 + cote * envergure * 0.35, y + 5)], 0.9),
                             fill=C["vert"], w=1.8,
                             hach=(hachures(20, y - 16, 76, y + 8, 30, 5),
                                   C["vert_fonce"], 0.8)))
    if fleur:
        couleur, rayon = fleur
        for i in range(8):
            a = 2 * math.pi * i / 8
            couches.append(forme(cercle(48 + math.cos(a) * rayon,
                                        sommet + math.sin(a) * rayon,
                                        rayon * 0.62, 0.7),
                                 fill=couleur, w=1.6))
        couches.append(forme(cercle(48, sommet, rayon * 0.62, 0.6),
                             fill=C["terre_fonce"], w=1.8))
    return couches


def coffre(couleur, ferrure=C["or"], ouvert=False):
    corps = [(22, 52), (74, 52), (76, 78), (20, 78)]
    couvercle = [(22, 52), (28, 34), (68, 34), (74, 52)]
    if ouvert:
        couvercle = [(22, 50), (26, 26), (66, 22), (74, 44)]
    return [
        forme(polyligne(corps, 1.0), fill=couleur, w=2.4,
              hach=(hachures(20, 52, 76, 78, 60, 7), C["trait_clair"], 0.9)),
        forme(polyligne(couvercle, 1.0), fill=couleur, w=2.4,
              hach=(hachures(20, 22, 76, 52, 60, 7), C["trait_clair"], 0.9)),
        forme(polyligne([(20, 50), (76, 50), (76, 56), (20, 56)], 0.8),
              fill=ferrure, w=1.8),
        forme(polyligne([(43, 54), (53, 54), (53, 66), (43, 66)], 0.7),
              fill=ferrure, w=1.8),
    ]


def goutte(couleur=C["eau"]):
    return [forme(polyligne([(48, 22), (64, 48), (62, 62), (48, 72),
                             (34, 62), (32, 48)], 1.0),
                  fill=couleur, w=2.4,
                  hach=(hachures(32, 22, 64, 72, 70, 7), C["eau_fonce"], 1.0))]


def piece():
    return [forme(cercle(48, 48, 26, 1.0), fill=C["or"], w=2.4,
                  hach=(hachures(22, 22, 74, 74, 40, 7), C["trait_clair"], 0.9)),
            forme(cercle(48, 48, 18, 0.8), stroke=C["or_clair"], w=1.8),
            forme(polyligne([(48, 36), (52, 45), (61, 45), (54, 51),
                             (57, 60), (48, 55), (39, 60), (42, 51),
                             (35, 45), (44, 45)], 0.7), fill=C["or_clair"], w=1.4)]


def sac(couleur, etiquette=None):
    couches = [
        forme(polyligne([(30, 40), (66, 40), (72, 76), (24, 76)], 1.0),
              fill=couleur, w=2.4,
              hach=(hachures(24, 40, 72, 76, 65, 7), C["trait_clair"], 0.9)),
        forme(polyligne([(34, 40), (38, 28), (58, 28), (62, 40)], 0.9),
              fill=couleur, w=2.0),
        forme(polyligne([(34, 38), (62, 38)], 0.6, fermer=False), w=2.2),
    ]
    if etiquette:
        couches.append(forme(cercle(48, 58, 9, 0.8), fill=etiquette, w=1.8))
    return couches


def outil_manche(x0, y0, x1, y1, largeur=4.0):
    return forme(polyligne([(x0, y0), (x1, y1)], 0.7, fermer=False),
                 stroke=C["bois"], w=largeur)


def meteo_soleil(disque=None, rayons=None):
    couches = [forme(cercle(48, 46, 20, 1.0), fill=disque or C["or_clair"], w=2.4,
                     hach=(hachures(28, 26, 68, 66, 45, 8), rayons or C["or"], 1.0))]
    for i in range(10):
        a = 2 * math.pi * i / 10
        couches.append(forme(polyligne([(48 + math.cos(a) * 25, 46 + math.sin(a) * 25),
                                        (48 + math.cos(a) * 34, 46 + math.sin(a) * 34)],
                                       0.8, fermer=False), stroke=rayons or C["or"], w=2.4))
    return couches


def nuage(couleur=C["blanc"], y=44):
    return [forme(polyligne([(24, y + 14), (28, y), (42, y - 8), (58, y - 6),
                             (68, y + 2), (74, y + 14)], 1.2),
                  fill=couleur, w=2.4,
                  hach=(hachures(24, y - 10, 74, y + 16, 20, 8), C["pierre"], 0.9))]


def pluie(gouttes=4, couleur=C["eau"]):
    couches = nuage(C["blanc"], 38)
    for i in range(gouttes):
        x = 30 + i * 12
        couches.append(forme(polyligne([(x, 60), (x - 3, 76)], 0.7, fermer=False),
                             stroke=couleur, w=2.6))
    return couches


def eclair():
    couches = nuage("#C9CBD1", 36)
    couches.append(forme(polyligne([(50, 54), (42, 70), (50, 70), (44, 86),
                                    (62, 66), (53, 66), (60, 54)], 0.8),
                         fill=C["or"], w=1.8))
    return couches


def lune():
    return [forme(cercle(52, 46, 21, 1.0), fill=C["blanc"], w=2.4,
                  hach=(hachures(30, 24, 74, 68, 35, 9), C["nuit"], 0.9)),
            forme(cercle(62, 40, 17, 1.0), fill=C["nuit"], stroke=None),
            forme(polyligne([(24, 22), (25, 26), (29, 27), (25, 28), (24, 32),
                             (23, 28), (19, 27), (23, 26)], 0.5),
                  fill=C["blanc"], stroke=None)]


def aube():
    couches = [forme(arc(48, 62, 22, 180, 360, 1.0), fill=C["or_clair"], w=2.4)]
    couches.append(forme(polyligne([(16, 62), (80, 62)], 0.8, fermer=False), w=2.6))
    for a in (200, 225, 250, 290, 315, 340):
        r = math.radians(a)
        couches.append(forme(polyligne(
            [(48 + math.cos(r) * 27, 62 + math.sin(r) * 27),
             (48 + math.cos(r) * 35, 62 + math.sin(r) * 35)], 0.7, fermer=False),
            stroke=C["or"], w=2.2))
    return couches


# --------------------------------------------------------------------------
# Le catalogue.
# --------------------------------------------------------------------------

CATALOGUE = {
    # Étapes de croissance — les six stades de CropStage.
    "croissance_graine":      sol(C["terre"], motif="sillons", dy=22) + [
                                  forme(cercle(48, 66, 5, 0.6), fill=C["terre_fonce"], w=1.8)],
    "croissance_germe":       sol(C["terre"], motif="sillons", dy=22) + tige(14, 1),
    "croissance_pousse":      sol(C["terre"], motif="sillons", dy=22) + tige(28, 2),
    "croissance_jeune":       sol(C["terre"], motif="sillons", dy=22) + tige(40, 3),
    "croissance_mature":      sol(C["terre"], motif="sillons", dy=22) + tige(50, 4),
    "croissance_recoltable":  sol(C["terre"], motif="sillons", dy=22) +
                              tige(48, 4, fleur=(C["or"], 13)),

    # Types de sol — SoilType.
    "sol_terre":      sol(C["terre"], motif="sillons"),
    "sol_riche":      sol(C["terre_fonce"], motif="sillons"),
    "sol_sable":      sol(C["sable"]),
    "sol_humide":     sol(C["terre_fonce"], motif="flaques"),
    "sol_nocturne":   sol("#4A4260", motif="etoiles"),
    "sol_cristallin": sol("#6E8C93", motif="cristaux"),

    # États de parcelle — PlotState et Deblocage.
    "parcelle_encombree": sol(C["terre"], motif="cailloux"),
    "parcelle_vide":      sol(C["terre"]),
    "parcelle_preparee":  sol(C["terre"], motif="sillons"),
    "parcelle_brouillard": nuage("#8E9AA6", 48) + nuage("#A9B4BE", 58),

    # Humidité — MoistureEngine.Etat.
    "humidite_sec":          sol("#A98A5E"),
    "humidite_legerement":   sol("#8A6A45"),
    "humidite_humide":       sol("#6F5335"),
    "humidite_bien":         sol("#573F28"),
    "humidite_detrempe":     sol("#3E2C1B", motif="flaques"),

    # Météo — WeatherEngine.Meteo.
    "meteo_soleil":   meteo_soleil(),
    # La canicule doit se distinguer du grand soleil au premier coup d'œil :
    # même dessin dans deux couleurs ne dirait rien au joueur.
    "meteo_canicule": meteo_soleil(disque="#E8A05A", rayons=C["rouge"]) + [
                          forme(arc(48, 84, 24, 200, 340, 1.0), stroke=C["rouge"], w=2.6),
                          forme(arc(48, 92, 24, 200, 340, 1.0), stroke=C["rouge"], w=2.2)],
    "meteo_nuageux":  nuage(),
    "meteo_pluie":    pluie(),
    "meteo_orage":    eclair(),

    # Cycle jour / nuit — DayNightEngine.Phase.
    "phase_aube":       aube(),
    "phase_jour":       meteo_soleil(),
    "phase_crepuscule": aube() + [forme(polyligne([(14, 70), (82, 70)], 0.8, fermer=False),
                                        stroke=C["violet"], w=2.2)],
    "phase_nuit":       lune(),

    # Ressources et monnaies.
    "ressource_piece":   piece(),
    "ressource_eau":     goutte(),
    "ressource_compost": sac(C["terre"], C["vert"]),
    "ressource_cristal": [forme(polyligne([(48, 18), (68, 44), (48, 80), (28, 44)], 0.9),
                                fill=C["violet"], w=2.4,
                                hach=(hachures(28, 18, 68, 80, 60, 7), C["blanc"], 0.9)),
                          forme(polyligne([(48, 18), (48, 80)], 0.6, fermer=False),
                                stroke=C["blanc"], w=1.4)],
    "ressource_graines": sac(C["sable"], C["vert_fonce"]),
    "ressource_bois":    [forme(cercle(36, 52, 12, 0.9, aplat_y=1.0), fill=C["bois"], w=2.2),
                          forme(cercle(60, 58, 12, 0.9), fill=C["bois"], w=2.2),
                          forme(cercle(36, 52, 5, 0.6), stroke=C["terre_fonce"], w=1.4),
                          forme(cercle(60, 58, 5, 0.6), stroke=C["terre_fonce"], w=1.4)],

    # Outils — OutilJardin et améliorations.
    "outil_arrosoir": [
        forme(polyligne([(28, 44), (64, 44), (62, 74), (30, 74)], 1.0),
              fill=C["pierre"], w=2.4,
              hach=(hachures(28, 44, 64, 74, 60, 7), C["trait_clair"], 0.9)),
        forme(polyligne([(64, 50), (82, 40), (86, 44), (66, 58)], 0.9),
              fill=C["pierre"], w=2.0),
        forme(arc(40, 44, 12, 180, 350, 0.9), stroke=C["trait"], w=2.4),
        forme(polyligne([(80, 34), (86, 44)], 0.6, fermer=False), stroke=C["eau"], w=2.2),
    ],
    "outil_panier": [
        forme(polyligne([(24, 48), (72, 48), (66, 76), (30, 76)], 1.0),
              fill=C["sable"], w=2.4,
              hach=(hachures(24, 48, 72, 76, 90, 6), C["bois"], 1.0)),
        forme(hachures(24, 48, 72, 76, 0, 7), stroke=C["bois"], w=1.0),
        forme(arc(48, 48, 22, 180, 360, 1.0), stroke=C["bois"], w=2.6),
    ],
    "outil_pioche": [
        outil_manche(38, 78, 58, 30, 5.0),
        forme(polyligne([(30, 34), (48, 26), (66, 34), (48, 40)], 0.9),
              fill=C["pierre"], w=2.2),
    ],
    "outil_pelle": [
        outil_manche(48, 78, 48, 40, 5.0),
        forme(polyligne([(38, 40), (58, 40), (54, 66), (42, 66)], 0.9),
              fill=C["pierre"], w=2.2),
    ],
    "outil_gants": [
        forme(polyligne([(30, 44), (44, 30), (52, 34), (48, 48), (60, 44),
                         (66, 54), (52, 74), (32, 68)], 1.1),
              fill=C["rouge"], w=2.4,
              hach=(hachures(28, 28, 68, 76, 40, 7), C["trait_clair"], 0.9)),
    ],
    "outil_binette": [
        outil_manche(34, 78, 60, 32, 5.0),
        forme(polyligne([(48, 30), (72, 34), (70, 42), (46, 38)], 0.9),
              fill=C["pierre"], w=2.2),
    ],

    # Coffres — les types de ChestEngine.
    "coffre_commun":      coffre(C["bois"], C["pierre"]),
    "coffre_graines":     coffre(C["vert"], C["or"]),
    "coffre_recolte":     coffre(C["terre"], C["or"]),
    "coffre_rare":        coffre(C["eau"], C["or"]),
    "coffre_epique":      coffre(C["violet"], C["or_clair"]),
    "coffre_legendaire":  coffre(C["or"], C["blanc"]),
    "coffre_ouvert":      coffre(C["or"], C["blanc"], ouvert=True),

    # Repères du jardin.
    "lieu_depot": [
        forme(polyligne([(20, 50), (48, 28), (76, 50)], 1.0), fill=C["rouge"], w=2.4),
        forme(polyligne([(26, 50), (70, 50), (70, 78), (26, 78)], 1.0),
              fill=C["bois"], w=2.4,
              hach=(hachures(26, 50, 70, 78, 90, 7), C["terre_fonce"], 0.9)),
        forme(polyligne([(40, 58), (56, 58), (56, 78), (40, 78)], 0.8),
              fill=C["terre_fonce"], w=1.8),
    ],
    "lieu_magasin": [
        forme(polyligne([(18, 44), (78, 44), (78, 78), (18, 78)], 1.0),
              fill=C["blanc"], w=2.4),
        forme(polyligne([(14, 44), (82, 44), (76, 30), (20, 30)], 1.0),
              fill=C["rouge"], w=2.4,
              hach=(hachures(14, 30, 82, 46, 90, 8), C["blanc"], 1.2)),
        forme(polyligne([(38, 56), (58, 56), (58, 78), (38, 78)], 0.8),
              fill=C["bois"], w=1.8),
    ],
    "lieu_serre": [
        forme(polyligne([(20, 46), (48, 24), (76, 46), (76, 78), (20, 78)], 1.1),
              fill=C["cristal"], w=2.4, alpha=0.9),
        forme(polyligne([(48, 24), (48, 78)], 0.6, fermer=False), w=1.8),
        forme(polyligne([(20, 52), (76, 52)], 0.6, fermer=False), w=1.8),
        forme(polyligne([(20, 66), (76, 66)], 0.6, fermer=False), w=1.8),
    ],
    "lieu_arbre": [
        forme(polyligne([(42, 80), (44, 54), (52, 54), (54, 80)], 0.9),
              fill=C["bois"], w=2.4),
        forme(cercle(48, 40, 24, 1.4), fill=C["vert"], w=2.6,
              hach=(hachures(24, 16, 72, 64, 40, 7), C["vert_fonce"], 1.0)),
        forme(cercle(34, 46, 12, 1.2), fill=C["vert_clair"], w=2.0),
        forme(cercle(62, 44, 11, 1.2), fill=C["vert_clair"], w=2.0),
    ],

    # Badges de progression.
    "badge_graine":   [forme(polyligne([(48, 16), (78, 32), (78, 62), (48, 82),
                                        (18, 62), (18, 32)], 1.0),
                             fill=C["vert"], w=2.6,
                             hach=(hachures(18, 16, 78, 82, 55, 8), C["vert_fonce"], 1.0))],
    "badge_recolte":  [forme(polyligne([(48, 16), (78, 32), (78, 62), (48, 82),
                                        (18, 62), (18, 32)], 1.0),
                             fill=C["or"], w=2.6,
                             hach=(hachures(18, 16, 78, 82, 55, 8), C["terre"], 1.0))],
    "badge_maitre":   [forme(polyligne([(48, 16), (78, 32), (78, 62), (48, 82),
                                        (18, 62), (18, 32)], 1.0),
                             fill=C["violet"], w=2.6,
                             hach=(hachures(18, 16, 78, 82, 55, 8), C["blanc"], 1.0))],
}


# --------------------------------------------------------------------------
# Écriture.
# --------------------------------------------------------------------------

def _couches_svg(nom, couches):
    out = []
    for i, ch in enumerate(couches):
        attrs = 'd="%s"' % ch["d"]
        attrs += ' fill="%s"' % (ch["fill"] or "none")
        if ch["alpha"] < 1.0:
            attrs += ' fill-opacity="%.2f"' % ch["alpha"]
        if ch["stroke"]:
            attrs += ' stroke="%s" stroke-width="%.1f" stroke-linecap="round" ' \
                     'stroke-linejoin="round"' % (ch["stroke"], ch["w"])
        out.append("  <path %s/>" % attrs)
        if ch["hach"]:
            chemin, couleur, largeur = ch["hach"]
            cid = "c%s%d" % (nom.replace("_", ""), i)
            out.append('  <clipPath id="%s"><path d="%s"/></clipPath>' % (cid, ch["d"]))
            out.append('  <g clip-path="url(#%s)"><path d="%s" stroke="%s" '
                       'stroke-width="%.1f" fill="none" stroke-opacity="0.55"/></g>'
                       % (cid, chemin, couleur, largeur))
    return "\n".join(out)


def _couches_xml(couches):
    out = []
    for ch in couches:
        p = ['    <path']
        if ch["fill"]:
            p.append('        android:fillColor="%s"' % ch["fill"])
            if ch["alpha"] < 1.0:
                p.append('        android:fillAlpha="%.2f"' % ch["alpha"])
        if ch["stroke"]:
            p.append('        android:strokeColor="%s"' % ch["stroke"])
            p.append('        android:strokeWidth="%.1f"' % ch["w"])
            p.append('        android:strokeLineCap="round"')
            p.append('        android:strokeLineJoin="round"')
        p.append('        android:pathData="%s" />' % ch["d"])
        out.append("\n".join(p))

        if ch["hach"]:
            chemin, couleur, largeur = ch["hach"]
            out.append(
                '    <group>\n'
                '        <clip-path android:pathData="%s" />\n'
                '        <path\n'
                '            android:strokeColor="%s"\n'
                '            android:strokeWidth="%.1f"\n'
                '            android:strokeAlpha="0.55"\n'
                '            android:strokeLineCap="round"\n'
                '            android:pathData="%s" />\n'
                '    </group>' % (ch["d"], couleur, largeur, chemin))
    return "\n".join(out)


def ecrire():
    os.makedirs(SORTIE_SVG, exist_ok=True)
    os.makedirs(SORTIE_XML, exist_ok=True)

    for nom, couches in sorted(CATALOGUE.items()):
        svg = (
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 96 96" '
            'width="96" height="96">\n'
            '<!-- Sankai Life — genere par art/generer-assets.py, ne pas editer a la main -->\n'
            '%s\n</svg>\n' % _couches_svg(nom, couches))
        with open(os.path.join(SORTIE_SVG, "%s.svg" % nom), "w", encoding="utf-8") as f:
            f.write(svg)

        xml = (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Genere par art/generer-assets.py. Ne pas editer a la main. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="48dp"\n'
            '    android:height="48dp"\n'
            '    android:viewportWidth="96"\n'
            '    android:viewportHeight="96">\n'
            '%s\n</vector>\n' % _couches_xml(couches))
        with open(os.path.join(SORTIE_XML, "art_%s.xml" % nom), "w", encoding="utf-8") as f:
            f.write(xml)

    print("%d elements ecrits" % len(CATALOGUE))
    print("  SVG : %s" % SORTIE_SVG)
    print("  XML : %s" % SORTIE_XML)


if __name__ == "__main__":
    ecrire()
