package com.sankailife.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Rend une couleur d'accent lisible sur son fond.
 *
 * **Le problème mesuré, pas supposé.** Les huit thèmes cosmétiques ont été
 * choisis pour l'interface sombre d'origine, et ils y sont tous confortables :
 * de 5,9 à 11,3 pour un minimum de 4,5. Sur le fond clair, aucun ne passe —
 * de 1,44 à 2,77. Le doré légendaire, la plus rare des récompenses, est le
 * pire des huit.
 *
 * Les brancher tels quels aurait donc réglé un défaut en en créant un autre :
 * un thème enfin actif, et un texte qu'on ne peut plus lire en plein jour.
 *
 * La correction préserve la **teinte** et ne touche qu'à la clarté. C'est la
 * teinte qui fait l'identité d'un thème : un doré plus sombre reste doré, un
 * doré illisible n'est plus rien.
 */
object Contraste {

    /** Minimum WCAG AA pour du texte de taille normale. */
    const val CIBLE_AA = 4.5

    /**
     * Lit une couleur « #RRGGBB » ou « #AARRGGBB ».
     *
     * Ecrit ici plutot que d'appeler `android.graphics.Color.parseColor` : cette
     * methode n'existe pas dans les tests unitaires, et une couleur qu'on ne
     * peut pas verifier hors appareil est une couleur qu'on ne verifie pas.
     *
     * Rend `null` sur une valeur illisible plutot que de lever : une couleur
     * mal ecrite dans un catalogue ne doit pas faire tomber l'application.
     */
    fun depuisHex(hex: String): Color? {
        val net = hex.trim().removePrefix("#")
        if (net.length != 6 && net.length != 8) return null
        val valeur = net.toLongOrNull(16) ?: return null
        return if (net.length == 6) Color(valeur or 0xFF000000L.toLong())
        else Color(valeur)
    }

    /** Luminance relative, définition WCAG. */
    fun luminance(couleur: Color): Double {
        fun canal(v: Float): Double {
            val x = v.toDouble()
            return if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * canal(couleur.red) +
            0.7152 * canal(couleur.green) +
            0.0722 * canal(couleur.blue)
    }

    /** Rapport de contraste entre deux couleurs, de 1 à 21. */
    fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /**
     * Ajuste un accent jusqu'à atteindre la cible sur ce fond.
     *
     * Assombrit sur fond clair, éclaircit sur fond sombre. Recherche
     * dichotomique sur un facteur multiplicatif : douze passes suffisent à
     * approcher au millième, et c'est un calcul, pas une boucle d'essais.
     *
     * Rend la couleur inchangée quand elle passe déjà — le cas de tous les
     * thèmes en mode sombre, où il ne faut surtout rien toucher.
     */
    fun ajuster(accent: Color, fond: Color, cible: Double = CIBLE_AA): Color {
        if (ratio(accent, fond) >= cible) return accent

        val fondClair = luminance(fond) > 0.5
        // Vers le noir sur fond clair, vers le blanc sur fond sombre.
        val extreme = if (fondClair) Color.Black else Color.White

        // Si même l'extrême ne suffit pas, le fond est à mi-chemin : on rend le
        // plus contrasté possible plutôt que de boucler pour rien.
        if (ratio(extreme, fond) < cible) return extreme

        var bas = 0f      // = extrême
        var haut = 1f     // = accent d'origine
        var resultat = extreme
        repeat(12) {
            val milieu = (bas + haut) / 2f
            val essai = melanger(extreme, accent, milieu)
            if (ratio(essai, fond) >= cible) {
                resultat = essai
                bas = milieu
            } else {
                haut = milieu
            }
        }
        return resultat
    }

    /** Interpolation linéaire, [t] = 0 rend [a], 1 rend [b]. */
    private fun melanger(a: Color, b: Color, t: Float): Color = Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = 1f
    )
}
