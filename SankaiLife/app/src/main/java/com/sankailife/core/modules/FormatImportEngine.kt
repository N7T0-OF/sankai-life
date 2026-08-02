package com.sankailife.core.modules

/**
 * Reconnaît et lit les formats qu'on peut importer.
 *
 * **Pourquoi élargir.** L'import n'acceptait qu'une archive ZIP construite pour
 * l'application. C'est le format le plus complet, et le plus pénible à obtenir
 * sur un téléphone : il faut trouver le fichier, le télécharger, le retrouver
 * dans le gestionnaire de fichiers, revenir. Les gens ont déjà leurs cartes —
 * dans un export Anki, une feuille de calcul, un fichier texte, un message.
 * Refuser ces formats revient à leur demander de refaire ce qu'ils ont déjà.
 *
 * Le format est déduit du **contenu**, pas de l'extension : un fichier partagé
 * depuis une autre application arrive souvent sans nom utilisable, et un
 * `.txt` qui contient du JSON reste du JSON.
 */
object FormatImportEngine {

    enum class Format {
        /** Archive complète : manifeste, cartes, éventuellement plusieurs modules. */
        ARCHIVE,
        /** Un manifeste seul, ou une collection, ou un catalogue. */
        JSON,
        /** Texte : une carte par ligne, recto et verso séparés. */
        TEXTE,
        /** Tableur exporté : deux colonnes au moins. */
        CSV,
        INCONNU
    }

    /** Séparateurs acceptés entre recto et verso, du plus explicite au moins. */
    private val SEPARATEURS = listOf(" :: ", "::", " | ", "\t", " — ", ";", "|")

    /** Signature ZIP. Deux octets suffisent et ne se confondent avec rien. */
    private val SIGNATURE_ZIP = byteArrayOf(0x50, 0x4B)

    /**
     * Devine le format.
     *
     * L'ordre suit la certitude : la signature ZIP est un fait, le JSON se
     * reconnaît à sa première accolade, et le reste se décide sur la structure
     * des lignes. Deviner en premier ce qui est le moins sûr ferait passer une
     * archive pour du texte.
     */
    fun detecter(octets: ByteArray, nom: String = ""): Format {
        if (octets.size >= 2 && octets[0] == SIGNATURE_ZIP[0] && octets[1] == SIGNATURE_ZIP[1]) {
            return Format.ARCHIVE
        }
        val texte = String(octets.take(4096).toByteArray()).trimStart().removePrefix("\uFEFF")
        if (texte.startsWith("{") || texte.startsWith("[")) return Format.JSON

        val lignes = texte.lines().filter { it.isNotBlank() }.take(20)
        if (lignes.isEmpty()) return Format.INCONNU

        // Le CSV se distingue du texte par la virgule, et seulement si elle est
        // présente sur presque toutes les lignes : une virgule isolée dans une
        // traduction ne fait pas d'un fichier texte un tableur.
        val avecVirgule = lignes.count { it.contains(',') }
        if (avecVirgule >= lignes.size * 0.8 && lignes.none { separateurDe(it) != null }) {
            return Format.CSV
        }
        if (lignes.any { separateurDe(it) != null }) return Format.TEXTE

        // Reste le cas d'un texte sans séparateur : des phrases à se remémorer.
        // C'est un usage réel du Mémo, pas une erreur.
        return if (nom.endsWith(".csv", true)) Format.CSV else Format.TEXTE
    }

    private fun separateurDe(ligne: String): String? =
        SEPARATEURS.firstOrNull { ligne.contains(it) && ligne.substringBefore(it).isNotBlank() }

    /**
     * Lit des cartes depuis du texte brut.
     *
     * Chaque ligne devient une carte. Le séparateur est cherché ligne par
     * ligne : un fichier peut mélanger les conventions, notamment quand il a
     * été assemblé à la main depuis plusieurs sources.
     *
     * Les lignes vides et les commentaires `#` sont ignorés — c'est la
     * convention de tous les formats texte, et quelqu'un qui commente son
     * fichier ne veut pas voir ses commentaires en révision.
     */
    fun lireTexte(contenu: String, limite: Int = ModuleEngine.MAX_CARTES): List<String> =
        contenu.lineSequence()
            .map { it.trim().removePrefix("\uFEFF") }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { ligne ->
                val sep = separateurDe(ligne)
                if (sep == null) ligne
                else {
                    val recto = ligne.substringBefore(sep).trim()
                    val verso = ligne.substringAfter(sep).trim()
                    // On réécrit dans le séparateur de l'application : elle sait
                    // en lire plusieurs, mais un fichier homogène se relit.
                    if (verso.isBlank()) recto else "$recto :: $verso"
                }
            }
            .take(limite)
            .toList()

    /**
     * Lit des cartes depuis un CSV.
     *
     * Gère les guillemets, parce que tous les tableurs en produisent dès qu'une
     * traduction contient une virgule — et qu'importer `"maison, foyer"` comme
     * deux colonnes donnerait une carte fausse.
     *
     * Seules les deux premières colonnes sont retenues. Les exports d'Anki et
     * de Quizlet en ajoutent d'autres — étiquettes, statistiques — qui ne
     * veulent rien dire ici.
     */
    fun lireCsv(contenu: String, limite: Int = ModuleEngine.MAX_CARTES): List<String> {
        val cartes = mutableListOf<String>()
        var premiere = true
        for (ligne in contenu.lineSequence()) {
            val net = ligne.trim().removePrefix("\uFEFF")
            if (net.isBlank() || net.startsWith("#")) continue
            val colonnes = decouperCsv(net)
            if (colonnes.size < 2) continue
            val recto = colonnes[0].trim()
            val verso = colonnes[1].trim()
            if (recto.isBlank() || verso.isBlank()) continue

            // En-tête : une première ligne dont les deux colonnes ressemblent à
            // des noms de champs plutôt qu'à une carte.
            if (premiere && estEntete(recto, verso)) {
                premiere = false
                continue
            }
            premiere = false
            cartes += "$recto :: $verso"
            if (cartes.size >= limite) break
        }
        return cartes
    }

    private val MOTS_ENTETE = setOf(
        "front", "back", "recto", "verso", "question", "réponse", "reponse",
        "answer", "term", "definition", "définition", "mot", "traduction", "word"
    )

    private fun estEntete(a: String, b: String): Boolean =
        a.lowercase() in MOTS_ENTETE && b.lowercase() in MOTS_ENTETE

    /** Découpe une ligne CSV en respectant les guillemets doublés. */
    internal fun decouperCsv(ligne: String): List<String> {
        val colonnes = mutableListOf<String>()
        val courant = StringBuilder()
        var dansGuillemets = false
        var i = 0
        while (i < ligne.length) {
            val c = ligne[i]
            when {
                c == '"' && dansGuillemets && i + 1 < ligne.length && ligne[i + 1] == '"' -> {
                    courant.append('"'); i++
                }
                c == '"' -> dansGuillemets = !dansGuillemets
                (c == ',' || c == ';') && !dansGuillemets -> {
                    colonnes += courant.toString(); courant.clear()
                }
                else -> courant.append(c)
            }
            i++
        }
        colonnes += courant.toString()
        return colonnes
    }

    /**
     * Nom de module proposé pour un fichier sans manifeste.
     *
     * Tiré du nom de fichier, nettoyé. Un module qui s'appellerait
     * « export_2024_final_v3.csv » se retrouve tel quel dans la liste des
     * matières, et personne ne saurait ce que c'est.
     */
    fun nomPropose(nomFichier: String): String {
        val base = nomFichier.substringAfterLast('/').substringBeforeLast('.')
        val propre = base
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (propre.isBlank()) "Module importé"
        else propre.replaceFirstChar { it.uppercase() }
    }
}
