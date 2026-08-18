package com.sankailife.core.motdujour

import android.content.Context
import com.sankailife.core.culture.CultureJson
import com.sankailife.core.culture.jsonArray
import com.sankailife.core.culture.jsonObject
import com.sankailife.core.culture.optionalString
import com.sankailife.core.culture.requiredString

/**
 * Lit le catalogue du mot du jour, embarqué dans l'application.
 *
 * Tout est hors-ligne : un fichier dans `assets/`, lu une fois à l'écran. Un
 * fichier illisible ou manquant ne fait pas tomber l'application — l'écran
 * dira simplement qu'il n'y a pas de mot aujourd'hui.
 */
object MotDuJourStore {

    private const val ASSET = "mot_du_jour.json"

    fun lire(contexte: Context): List<MotDuJour> = runCatching {
        val texte = contexte.assets.open(ASSET)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        parse(texte)
    }.getOrDefault(emptyList())

    /** Testable sur la JVM, sans Android. */
    fun parse(texte: String): List<MotDuJour> {
        val racine = CultureJson.parse(texte).jsonArray(ASSET)
        return racine.mapIndexed { index, element ->
            val objet = element.jsonObject("$ASSET[$index]")
            MotDuJour(
                id = objet.requiredString("id"),
                mot = objet.requiredString("mot"),
                langue = objet.requiredString("langue"),
                definition = objet.requiredString("definition"),
                prononciation = objet.optionalString("prononciation"),
                exemple = objet.optionalString("exemple"),
                origine = objet.optionalString("origine"),
                categorie = objet.optionalString("categorie"),
                niveau = objet.optionalString("niveau")
            )
        }
    }
}
