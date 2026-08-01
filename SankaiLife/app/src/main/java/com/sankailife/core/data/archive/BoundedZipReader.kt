package com.sankailife.core.data.archive

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Lit une archive ZIP sans laisser une entrée compressée épuiser la mémoire.
 *
 * Les limites sont contrôlées pendant la décompression, avant chaque écriture
 * dans le tampon extensible. Les entrées ignorées par [accepter] comptent elles
 * aussi : un chemin refusé ne doit pas permettre de contourner le plafond.
 */
object BoundedZipReader {

    data class Limits(
        val maxTotalBytes: Long,
        val maxEntryBytes: Long,
        val maxEntries: Int
    ) {
        init {
            require(maxTotalBytes > 0) { "La limite globale doit être positive." }
            require(maxEntryBytes > 0) { "La limite par entrée doit être positive." }
            require(maxEntries > 0) { "Le nombre maximal d'entrées doit être positif." }
        }
    }

    class LimitExceededException(message: String) : IOException(message)

    fun read(
        source: InputStream,
        limits: Limits,
        accepter: (String) -> Boolean = { true }
    ): Map<String, ByteArray> {
        val resultat = linkedMapOf<String, ByteArray>()
        val tamponLecture = ByteArray(DEFAULT_BUFFER_SIZE)
        var octetsTotaux = 0L
        var nombreEntrees = 0

        ZipInputStream(source).use { zip ->
            while (true) {
                val entree = zip.nextEntry ?: break
                nombreEntrees++
                if (nombreEntrees > limits.maxEntries) {
                    throw LimitExceededException(
                        "Archive trop complexe : plus de ${limits.maxEntries} entrées."
                    )
                }

                val conserver = !entree.isDirectory && accepter(entree.name)
                if (conserver && resultat.containsKey(entree.name)) {
                    throw IOException("Archive invalide : entrée dupliquée ${entree.name}.")
                }

                val contenu = if (conserver) ByteArrayOutputStream() else null
                var octetsEntree = 0L

                while (true) {
                    val lus = zip.read(tamponLecture)
                    if (lus < 0) break

                    if (octetsEntree > limits.maxEntryBytes - lus) {
                        throw LimitExceededException(
                            "Entrée trop lourde : ${entree.name} dépasse " +
                                "${limits.maxEntryBytes / 1024 / 1024} Mo."
                        )
                    }
                    if (octetsTotaux > limits.maxTotalBytes - lus) {
                        throw LimitExceededException(
                            "Archive trop lourde : maximum " +
                                "${limits.maxTotalBytes / 1024 / 1024} Mo."
                        )
                    }

                    contenu?.write(tamponLecture, 0, lus)
                    octetsEntree += lus
                    octetsTotaux += lus
                }

                if (conserver) resultat[entree.name] = contenu!!.toByteArray()
                zip.closeEntry()
            }
        }

        return resultat
    }
}
