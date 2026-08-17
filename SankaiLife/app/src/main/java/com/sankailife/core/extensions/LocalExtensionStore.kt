package com.sankailife.core.extensions

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

class ExtensionStoreException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Stockage local sous un `filesDir` fourni par l'hote.
 *
 * Une installation ecrit d'abord une version invisible, puis remplace un petit
 * pointeur actif par renommage atomique. Aucun fichier du paquet n'est charge
 * comme classe, script ou bibliotheque. La desinstallation desactive le
 * pointeur avant de retirer uniquement les donnees et assets de cette version.
 */
class LocalExtensionStore(
    filesDir: File,
    private val appVersionCode: Int,
    private val inspector: ExtensionPackInspector = ExtensionPackInspector()
) {
    private val root = filesDir.toPath().toAbsolutePath().normalize().resolve("sankai_extensions")
    private val packagesRoot = root.resolve("packages")
    private val activeRoot = root.resolve("active")
    private val snapshotsRoot = root.resolve("snapshots")

    init {
        require(appVersionCode > 0) { "Le versionCode doit etre positif." }
    }

    fun install(packageFile: File): InstalledExtension {
        val pack = inspector.inspect(packageFile, appVersionCode)
        val manifest = pack.manifest
        val restoredSnapshot = when (manifest.saveSchema.kind) {
            ExtensionSaveKind.NONE -> null
            ExtensionSaveKind.GARDEN_SUMMARY -> loadGardenSnapshot(manifest.id)?.also { snapshot ->
                if (snapshot.schemaVersion != manifest.saveSchema.version) {
                    throw ExtensionStoreException("Le snapshot Garden n'est pas compatible avec ce paquet.")
                }
            }
        }

        ensureDirectory(packagesRoot)
        ensureDirectory(activeRoot)
        ensureDirectory(snapshotsRoot)
        val extensionDirectory = resolveInside(packagesRoot, manifest.id)
        ensureDirectory(extensionDirectory)

        val nonce = UUID.randomUUID().toString().replace("-", "")
        val staging = resolveInside(extensionDirectory, ".staging-$nonce")
        val token = "${pack.archiveChecksumSha256.take(20)}-$nonce"
        val prepared = resolveInside(extensionDirectory, token)
        var activated = false

        try {
            Files.createDirectory(staging)
            pack.archiveFiles.toSortedMap().forEach { (relativePath, bytes) ->
                writePassiveFile(staging, relativePath, bytes)
            }
            movePreparedDirectory(staging, prepared)
            writeActivePointer(manifest.id, token, pack.archiveChecksumSha256)
            activated = true

            // Une version orpheline ne peut jamais etre active. Son nettoyage
            // est donc sans incidence sur l'atomicite de l'installation.
            cleanupOldVersions(extensionDirectory, token)
            return InstalledExtension(
                manifest = manifest,
                directory = prepared.toFile(),
                archiveChecksumSha256 = pack.archiveChecksumSha256,
                restoredGardenSnapshot = restoredSnapshot
            )
        } catch (error: Exception) {
            if (!activated) {
                deleteTreeQuietly(staging)
                deleteTreeQuietly(prepared)
            }
            if (error is ExtensionStoreException) throw error
            throw ExtensionStoreException("Installation atomique de l'extension impossible.", error)
        }
    }

    fun installed(extensionId: String): InstalledExtension? {
        validateExtensionId(extensionId)
        val pointer = activePointer(extensionId)
        if (!Files.exists(pointer, LinkOption.NOFOLLOW_LINKS)) return null
        if (Files.isSymbolicLink(pointer) || !Files.isRegularFile(pointer, LinkOption.NOFOLLOW_LINKS)) {
            throw ExtensionStoreException("Pointeur d'extension invalide.")
        }
        if (Files.size(pointer) > 256L) throw ExtensionStoreException("Pointeur d'extension trop volumineux.")
        val lines = strictUtf8(Files.readAllBytes(pointer)).lines().filter { it.isNotEmpty() }
        if (lines.size != 2) throw ExtensionStoreException("Pointeur d'extension illisible.")
        val token = lines[0]
        val archiveChecksum = lines[1]
        if (!TOKEN.matches(token) || !SHA256.matches(archiveChecksum)) {
            throw ExtensionStoreException("Pointeur d'extension corrompu.")
        }

        val directory = resolveInside(resolveInside(packagesRoot, extensionId), token)
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) {
            throw ExtensionStoreException("Donnees de l'extension absentes.")
        }
        val manifestPath = resolveInside(directory, ExtensionManifestCodec.MANIFEST_FILE)
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS) ||
            Files.isSymbolicLink(manifestPath) || Files.size(manifestPath) > MAX_LOCAL_MANIFEST_BYTES
        ) {
            throw ExtensionStoreException("Manifeste installe absent ou invalide.")
        }
        val manifest = try {
            ExtensionManifestCodec.parse(strictUtf8(Files.readAllBytes(manifestPath))).also { parsed ->
                ExtensionManifestValidator.validate(parsed, appVersionCode, ExtensionPathPolicy::validateAsset)
            }
        } catch (error: Exception) {
            throw ExtensionStoreException("Manifeste installe illisible.", error)
        }
        if (manifest.id != extensionId) throw ExtensionStoreException("Identifiant installe incoherent.")
        verifyInstalledPayload(directory, manifest)

        return InstalledExtension(
            manifest, directory.toFile(), archiveChecksum,
            restoredGardenSnapshot = null
        )
    }

    fun installedExtensions(): List<InstalledExtension> {
        if (!Files.isDirectory(activeRoot, LinkOption.NOFOLLOW_LINKS)) return emptyList()
        val ids = mutableListOf<String>()
        Files.newDirectoryStream(activeRoot, "*.ref").use { entries ->
            entries.forEach { path -> ids += path.fileName.toString().removeSuffix(".ref") }
        }
        return ids.sorted().mapNotNull(::installed)
    }

    /**
     * Desactive une extension puis retire ses donnees/assets.
     *
     * Pour un Jardin, [preserveSnapshot] vaut vrai par defaut. Le retrait est
     * refuse si aucun snapshot courant ou deja conserve n'est disponible : la
     * desinstallation ne peut donc pas effacer silencieusement la progression.
     */
    fun uninstall(
        extensionId: String,
        gardenSnapshot: GardenExtensionSnapshot? = null,
        preserveSnapshot: Boolean = true
    ): ExtensionUninstallResult {
        val current = installed(extensionId)
            ?: return ExtensionUninstallResult(false, payloadDeleted = true, snapshotPreserved = false)

        val snapshotPreserved = when (current.manifest.saveSchema.kind) {
            ExtensionSaveKind.NONE -> {
                if (gardenSnapshot != null) {
                    throw ExtensionStoreException("Cette extension ne declare pas de snapshot Garden.")
                }
                false
            }
            ExtensionSaveKind.GARDEN_SUMMARY -> if (preserveSnapshot) {
                val snapshot = gardenSnapshot ?: loadGardenSnapshot(extensionId)
                    ?: throw ExtensionStoreException(
                        "Un snapshot Garden est obligatoire avant la desinstallation."
                    )
                if (snapshot.extensionId != extensionId ||
                    snapshot.schemaVersion != current.manifest.saveSchema.version
                ) {
                    throw ExtensionStoreException("Snapshot Garden incompatible avec l'extension.")
                }
                saveGardenSnapshot(snapshot)
                true
            } else false
        }

        // Supprimer le pointeur desactive tout de suite l'extension. Un echec
        // de nettoyage ulterieur ne peut pas rendre un paquet partiel actif.
        Files.deleteIfExists(activePointer(extensionId))
        val payloadDeleted = deleteTreeQuietly(resolveInside(packagesRoot, extensionId))
        if (!preserveSnapshot) Files.deleteIfExists(snapshotPath(extensionId))
        return ExtensionUninstallResult(true, payloadDeleted, snapshotPreserved)
    }

    fun saveGardenSnapshot(snapshot: GardenExtensionSnapshot) {
        try {
            GardenSnapshotCodec.validate(snapshot)
            ensureDirectory(snapshotsRoot)
            writeAtomic(
                snapshotPath(snapshot.extensionId),
                GardenSnapshotCodec.encode(snapshot).toByteArray(StandardCharsets.UTF_8)
            )
        } catch (error: Exception) {
            if (error is ExtensionStoreException) throw error
            throw ExtensionStoreException("Impossible de conserver le snapshot Garden.", error)
        }
    }

    fun loadGardenSnapshot(extensionId: String): GardenExtensionSnapshot? {
        validateExtensionId(extensionId)
        val path = snapshotPath(extensionId)
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ||
            Files.isSymbolicLink(path) || Files.size(path) > MAX_SNAPSHOT_BYTES
        ) {
            throw ExtensionStoreException("Snapshot Garden invalide.")
        }
        return try {
            GardenSnapshotCodec.parse(strictUtf8(Files.readAllBytes(path))).also { snapshot ->
                if (snapshot.extensionId != extensionId) {
                    throw ExtensionStoreException("Le snapshot appartient a une autre extension.")
                }
            }
        } catch (error: ExtensionStoreException) {
            throw error
        } catch (error: Exception) {
            throw ExtensionStoreException("Snapshot Garden illisible.", error)
        }
    }

    /** Suppression explicite du resume, separee de la desinstallation sure par defaut. */
    fun deleteGardenSnapshot(extensionId: String): Boolean {
        validateExtensionId(extensionId)
        return Files.deleteIfExists(snapshotPath(extensionId))
    }

    private fun verifyInstalledPayload(directory: Path, manifest: ExtensionManifest) {
        val actualFiles = collectRelativeFiles(directory)
        val expected = manifest.assets.map { it.path }.toSet() + ExtensionManifestCodec.MANIFEST_FILE
        if (actualFiles != expected) throw ExtensionStoreException("Contenu installe inattendu ou incomplet.")
        manifest.assets.forEach { asset ->
            val path = resolveInside(directory, asset.path)
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ||
                Files.size(path) != asset.sizeBytes
            ) {
                throw ExtensionStoreException("Asset installe invalide : ${asset.path}.")
            }
            val bytes = Files.readAllBytes(path)
            if (!ExtensionChecksums.sameHex(asset.sha256, ExtensionChecksums.sha256(bytes))) {
                throw ExtensionStoreException("Asset installe corrompu : ${asset.path}.")
            }
            ExtensionPathPolicy.validatePassiveContent(asset, bytes)
        }
    }

    private fun collectRelativeFiles(directory: Path): Set<String> {
        val result = linkedSetOf<String>()
        Files.walkFileTree(directory, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir != directory && Files.isSymbolicLink(dir)) {
                    throw ExtensionStoreException("Lien symbolique interdit dans une extension installee.")
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (Files.isSymbolicLink(file) || !attrs.isRegularFile) {
                    throw ExtensionStoreException("Fichier special interdit dans une extension installee.")
                }
                result += directory.relativize(file).joinToString("/") { it.toString() }
                return FileVisitResult.CONTINUE
            }
        })
        return result
    }

    private fun writePassiveFile(directory: Path, relativePath: String, bytes: ByteArray) {
        ExtensionPathPolicy.validateArchivePath(relativePath)
        val target = resolveInside(directory, relativePath)
        ensureDirectory(target.parent)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw ExtensionStoreException("Collision pendant l'installation : $relativePath.")
        }
        FileOutputStream(target.toFile()).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        // Defense supplementaire : meme un fichier mal nomme ne devient pas
        // executable au niveau du systeme de fichiers.
        target.toFile().setExecutable(false, false)
    }

    private fun writeActivePointer(extensionId: String, token: String, archiveChecksum: String) {
        val content = "$token\n$archiveChecksum\n".toByteArray(StandardCharsets.US_ASCII)
        writeAtomic(activePointer(extensionId), content)
    }

    private fun writeAtomic(target: Path, bytes: ByteArray) {
        ensureDirectory(target.parent)
        val temporary = resolveInside(target.parent, ".${target.fileName}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary.toFile()).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (error: AtomicMoveNotSupportedException) {
                throw ExtensionStoreException(
                    "Le stockage ne garantit pas le remplacement atomique requis.", error
                )
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun movePreparedDirectory(staging: Path, prepared: Path) {
        try {
            Files.move(staging, prepared, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            // Cette version n'est pas encore referencee par le pointeur actif.
            // Un deplacement non atomique ici reste donc invisible et sur.
            Files.move(staging, prepared)
        }
    }

    private fun cleanupOldVersions(extensionDirectory: Path, keepToken: String) {
        runCatching {
            Files.newDirectoryStream(extensionDirectory).use { entries ->
                entries.forEach { child ->
                    if (child.fileName.toString() != keepToken) deleteTreeQuietly(child)
                }
            }
        }
    }

    private fun deleteTreeQuietly(path: Path): Boolean {
        if (!path.toAbsolutePath().normalize().startsWith(root)) return false
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return true
        return runCatching {
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            })
            true
        }.getOrDefault(false)
    }

    private fun ensureDirectory(directory: Path) {
        val normalized = directory.toAbsolutePath().normalize()
        if (!normalized.startsWith(root.parent)) {
            throw ExtensionStoreException("Dossier d'extension hors filesDir.")
        }
        Files.createDirectories(normalized)
        var current: Path? = normalized
        while (current != null && current.startsWith(root.parent)) {
            if (Files.isSymbolicLink(current)) {
                throw ExtensionStoreException("Lien symbolique interdit dans le stockage d'extensions.")
            }
            if (current == root.parent) break
            current = current.parent
        }
    }

    private fun resolveInside(parent: Path, relative: String): Path {
        val resolved = parent.resolve(relative).toAbsolutePath().normalize()
        if (!resolved.startsWith(parent.toAbsolutePath().normalize())) {
            throw ExtensionStoreException("Chemin local hors du stockage d'extensions.")
        }
        return resolved
    }

    private fun activePointer(extensionId: String): Path =
        resolveInside(activeRoot, "$extensionId.ref")

    private fun snapshotPath(extensionId: String): Path =
        resolveInside(snapshotsRoot, "$extensionId.garden.json")

    private fun validateExtensionId(extensionId: String) {
        try {
            ExtensionManifestValidator.validateIdentifier(extensionId, "identifiant d'extension")
        } catch (error: ExtensionPackException) {
            throw ExtensionStoreException(error.message ?: "Identifiant d'extension invalide.", error)
        }
    }

    private fun strictUtf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()

    private companion object {
        val TOKEN = Regex("^[a-f0-9]{20}-[a-f0-9]{32}$")
        val SHA256 = Regex("^[a-f0-9]{64}$")
        const val MAX_LOCAL_MANIFEST_BYTES = 512L * 1024
        const val MAX_SNAPSHOT_BYTES = 512L * 1024
    }
}
