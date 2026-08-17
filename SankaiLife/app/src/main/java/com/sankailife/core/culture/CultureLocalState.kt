package com.sankailife.core.culture

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.time.LocalDate
import java.util.Properties

/**
 * Petit stockage privé, indépendant des réglages globaux de l'application.
 *
 * noBackupFilesDir est volontaire : une réflexion personnelle ne doit pas
 * partir dans une sauvegarde cloud Android sous prétexte qu'elle est stockée
 * dans les préférences de l'application.
 *
 * Partagé entre l'écran Capsules et la notification de découverte : les deux
 * doivent voir la même sélection du jour, sinon la notification annoncerait
 * une capsule et l'écran en ouvrirait une autre.
 */
class CultureLocalState(context: Context) {

    private val stateFile = AtomicFile(
        File(context.noBackupFilesDir, "culture-state/state.properties")
    )

    fun favorites(profileId: String): Set<String> = read()
        .getProperty("favorites.$profileId", "")
        .split(',')
        .filterTo(linkedSetOf()) { it.isNotBlank() }

    fun setFavorite(profileId: String, entryId: String, favorite: Boolean): Boolean {
        val values = favorites(profileId).toMutableSet()
        if (favorite) values += entryId else values -= entryId
        return edit { properties ->
            if (values.isEmpty()) properties.remove("favorites.$profileId")
            else properties.setProperty("favorites.$profileId", values.sorted().joinToString(","))
        }
    }

    fun reflection(profileId: String, entryId: String): String =
        read().getProperty("reflection.$profileId.$entryId", "")

    fun saveReflection(profileId: String, entryId: String, value: String): Boolean {
        return edit { properties ->
            if (value.isBlank()) properties.remove("reflection.$profileId.$entryId")
            else properties.setProperty("reflection.$profileId.$entryId", value)
        }
    }

    fun selection(profileId: String, date: LocalDate): String? {
        val current = read().getProperty("selection.$profileId")
            ?.takeIf { it.startsWith("$date|") }
            ?.substringAfter('|')
        return current ?: history(profileId).firstOrNull { it.selectedOn == date }?.entryId
    }

    fun saveSelection(profileId: String, date: LocalDate, entryId: String): Boolean {
        val updated = (history(profileId).filterNot { it.selectedOn == date } +
            CultureSelectionHistory(entryId, date))
            .filter { !it.selectedOn.isBefore(date.minusDays(HISTORY_DAYS)) }
            .sortedByDescending { it.selectedOn }
        val encoded = updated.joinToString("\n") { "${it.selectedOn}|${it.entryId}" }
        return edit { properties ->
            properties.setProperty("selection.$profileId", "$date|$entryId")
            properties.setProperty("history.$profileId", encoded)
        }
    }

    fun history(profileId: String): List<CultureSelectionHistory> =
        read().getProperty("history.$profileId", "")
            .lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('|')
                if (separator <= 0 || separator == line.lastIndex) return@mapNotNull null
                val date = runCatching { LocalDate.parse(line.substring(0, separator)) }
                    .getOrNull() ?: return@mapNotNull null
                val entryId = line.substring(separator + 1)
                CultureSelectionHistory(entryId, date)
            }
            .toList()

    @Synchronized
    private fun read(): Properties {
        val properties = Properties()
        if (!stateFile.baseFile.isFile) return properties
        return runCatching {
            stateFile.openRead().use { input -> properties.load(input) }
            properties
        }.getOrDefault(Properties())
    }

    @Synchronized
    private fun edit(update: (Properties) -> Unit): Boolean {
        val parent = stateFile.baseFile.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        val properties = read()
        update(properties)
        val output = try {
            stateFile.startWrite()
        } catch (_: Exception) {
            return false
        }
        return try {
            properties.store(output, "Sankai Life culture state - local only")
            stateFile.finishWrite(output)
            true
        } catch (_: Exception) {
            runCatching { stateFile.failWrite(output) }
            false
        }
    }

    private companion object {
        const val HISTORY_DAYS = 90L
    }
}
