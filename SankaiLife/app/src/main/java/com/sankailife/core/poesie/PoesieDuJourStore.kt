package com.sankailife.core.poesie

import android.content.Context
import com.sankailife.core.culture.CultureJson
import com.sankailife.core.culture.jsonArray
import com.sankailife.core.culture.jsonObject
import com.sankailife.core.culture.optionalString
import com.sankailife.core.culture.requiredString

/**
 * Lit le catalogue des découvertes littéraires, embarqué dans l'application.
 *
 * Tout est hors-ligne : un fichier dans `assets/`, lu une fois à l'écran. Un
 * fichier illisible ou manquant ne fait pas tomber l'application — l'écran
 * dira simplement qu'il n'y a rien aujourd'hui.
 */
object PoesieDuJourStore {

    private const val ASSET = "poesie_du_jour.json"

    fun lire(contexte: Context): List<PoesieDuJour> = runCatching {
        val texte = contexte.assets.open(ASSET)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        parse(texte)
    }.getOrDefault(emptyList())

    /** Testable sur la JVM, sans Android. */
    fun parse(texte: String): List<PoesieDuJour> {
        val racine = CultureJson.parse(texte).jsonArray(ASSET)
        return racine.mapIndexed { index, element ->
            val objet = element.jsonObject("$ASSET[$index]")
            PoesieDuJour(
                id = objet.requiredString("id"),
                type = if (objet.requiredString("type") == "poeme") TypeTexte.POEME
                else TypeTexte.PROVERBE,
                texte = objet.requiredString("texte"),
                auteur = objet.optionalString("auteur"),
                oeuvre = objet.optionalString("oeuvre"),
                annee = objet.optionalString("annee"),
                langue = objet.optionalString("langue") ?: "fr",
                drapeau = objet.optionalString("drapeau") ?: "🇫🇷",
                contexte = objet.optionalString("contexte")
            )
        }
    }
}
