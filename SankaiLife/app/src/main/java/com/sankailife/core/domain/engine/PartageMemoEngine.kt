package com.sankailife.core.domain.engine

/**
 * Mise en forme d'un module mémo pour le partage.
 *
 * Le format est du texte brut, séparé par des barres verticales. C'est le seul
 * qui se colle partout — message, courriel, note — et que quelqu'un peut lire
 * sans installer Sankai Life.
 *
 * **Rien de personnel ne sort.** Ni identifiants internes, ni historique de
 * réponses, ni statistiques, ni boîtes de révision. Partager un module, c'est
 * partager son contenu, pas ce qu'on en a fait. Cette limite est appliquée ici
 * plutôt que laissée à l'appelant : un écran qui se tromperait exposerait des
 * données sans que personne ne s'en aperçoive.
 */
object PartageMemoEngine {

    /** Séparateur utilisé à l'export. Le premier de ceux que l'import accepte. */
    const val SEPARATEUR = " | "

    /**
     * Texte du module, avec ou sans en-tête.
     *
     * Sans en-tête, le résultat se recolle directement dans un nouveau module :
     * c'est le format d'échange. Avec, il se lit tout seul.
     */
    fun exporter(
        nomModule: String,
        lignes: List<String>,
        avecEntete: Boolean = true
    ): String {
        val corps = lignes
            .map { normaliser(it) }
            .filter { it.isNotBlank() }
            .joinToString("\n")

        if (!avecEntete) return corps

        return buildString {
            append("Thème : ").append(nomModule.ifBlank { "Mémo" }).append('\n')
            append("Nombre de lignes : ").append(lignes.count { it.isNotBlank() }).append('\n')
            append('\n')
            append(corps)
        }
    }

    /**
     * Ramène une ligne au séparateur d'export.
     *
     * Les lignes en base peuvent utiliser plusieurs séparateurs selon la façon
     * dont elles ont été saisies. Les uniformiser à l'export évite de produire
     * un texte que l'import de Sankai Life relirait de travers.
     */
    private fun normaliser(ligne: String): String {
        val (recto, verso) = FlashcardEngine.decouper(ligne)
        return if (verso == null) recto else "$recto$SEPARATEUR$verso"
    }

    /**
     * Nom de fichier proposé à l'export.
     *
     * Les accents sont **translittérés**, pas supprimés : sans cette étape,
     * « Été 2026 » donnait « t-2026 », parce que le filtre effaçait la lettre
     * accentuée en même temps que son accent.
     */
    fun nomFichier(nomModule: String): String {
        val sansAccent = java.text.Normalizer
            .normalize(nomModule.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

        val base = sansAccent
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "memo" }
        return "$base.txt"
    }
}
