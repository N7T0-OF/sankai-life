package com.sankailife.core.culture

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Stockage local injecté, sans dépendance à Android ni serveur.
 *
 * L'appelant fournit normalement `File(context.filesDir, "culture")`. Le
 * paquet original, déjà validé, est conservé tel quel : aucune entrée ZIP
 * n'est extraite dans le système de fichiers, ce qui supprime toute surface de
 * traversée de chemin lors de la lecture des médias.
 */
class CulturePackStore(
    private val root: File,
    private val appVersionCode: Int
) {
    init {
        require(appVersionCode > 0) { "La version de l'application doit être positive." }
    }

    data class Installed(
        val manifest: CulturePackManifest,
        val entries: List<DailyCultureEntry>,
        val archiveBytes: Long
    )

    sealed interface State {
        data class Ready(val pack: Installed) : State
        data class Invalid(val fileName: String, val reason: String) : State
    }

    /** Valide complètement avant d'écrire, puis remplace atomiquement. */
    @Synchronized
    fun install(source: InputStream): Installed {
        val bytes = readCompressedArchive(source)
        val pack = CulturePackImporter.inspect(bytes, appVersionCode)
        ensureRoot()
        val target = packFile(pack.manifest.id)
        val temporary = File(root, ".${pack.manifest.id}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
        return Installed(pack.manifest, pack.entries, bytes.size.toLong())
    }

    @Synchronized
    fun uninstall(packId: String): Boolean {
        requireSafeId(packId)
        return packFile(packId).delete()
    }

    @Synchronized
    fun load(packId: String): ImportedCulturePack? {
        requireSafeId(packId)
        val file = packFile(packId)
        if (!file.isFile) return null
        return file.inputStream().buffered().use { CulturePackImporter.inspect(it, appVersionCode) }
    }

    /** Inclut les fichiers invalides dans le diagnostic au lieu de les masquer. */
    @Synchronized
    fun states(): List<State> {
        if (!root.isDirectory) return emptyList()
        return root.listFiles { file -> file.isFile && file.name.endsWith(EXTENSION) }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                try {
                    val pack = file.inputStream().buffered().use {
                        CulturePackImporter.inspect(it, appVersionCode)
                    }
                    State.Ready(Installed(pack.manifest, pack.entries, file.length()))
                } catch (error: Exception) {
                    State.Invalid(file.name, error.message ?: "Paquet illisible.")
                }
            }
    }

    private fun ensureRoot() {
        if (root.exists() && !root.isDirectory) {
            throw IOException("Le stockage culturel n'est pas un dossier.")
        }
        if (!root.exists() && !root.mkdirs()) {
            throw IOException("Impossible de créer le stockage culturel.")
        }
    }

    private fun packFile(packId: String): File = File(root, "$packId$EXTENSION")

    private fun requireSafeId(packId: String) {
        require(SAFE_ID.matches(packId)) { "Identifiant de paquet invalide." }
    }

    private fun readCompressedArchive(source: InputStream): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = source.read(buffer)
            if (read < 0) break
            if (total > CulturePackImporter.MAX_ARCHIVE_BYTES - read) {
                throw CulturePackException("Le fichier compressé dépasse 32 Mo.")
            }
            result.write(buffer, 0, read)
            total += read
        }
        return result.toByteArray()
    }

    private companion object {
        const val EXTENSION = ".culturepack"
        val SAFE_ID = Regex("^[a-z0-9]+(?:[._-][a-z0-9]+)*$")
    }
}
