package com.sankailife.core.extensions

import com.sankailife.core.data.archive.BoundedZipReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

class ExtensionPackException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class ExtensionPackLimits(
    val maxArchiveBytes: Long = 64L * 1024 * 1024,
    val maxTotalBytes: Long = 64L * 1024 * 1024,
    val maxEntryBytes: Long = 16L * 1024 * 1024,
    val maxEntries: Int = 1_024,
    val maxManifestBytes: Int = 512 * 1024
) {
    init {
        require(maxArchiveBytes > 0L)
        require(maxTotalBytes > 0L)
        require(maxEntryBytes > 0L)
        require(maxEntries > 0)
        require(maxManifestBytes > 0 && maxManifestBytes <= maxEntryBytes)
    }
}

/**
 * Valide une archive de donnees `.sankaipack` sans jamais charger de code.
 *
 * Les chemins et formats suivent une liste blanche passive. Les scripts,
 * classes, bibliotheques natives, archives imbriquees, SVG/HTML et executables
 * restent interdits meme si le manifeste tente de les declarer.
 */
class ExtensionPackInspector(
    private val limits: ExtensionPackLimits = ExtensionPackLimits()
) {

    fun inspect(packageFile: File, appVersionCode: Int): ValidatedExtensionPack {
        requirePackName(packageFile.name)
        if (!packageFile.isFile) throw ExtensionPackException("Paquet d'extension introuvable.")
        if (packageFile.length() > limits.maxArchiveBytes) {
            throw ExtensionPackException("Archive compressee trop lourde.")
        }
        return FileInputStream(packageFile).use { inspect(it, packageFile.name, appVersionCode) }
    }

    fun inspect(bytes: ByteArray, fileName: String, appVersionCode: Int): ValidatedExtensionPack {
        if (bytes.size.toLong() > limits.maxArchiveBytes) {
            throw ExtensionPackException("Archive compressee trop lourde.")
        }
        return ByteArrayInputStream(bytes).use { inspect(it, fileName, appVersionCode) }
    }

    fun inspect(source: InputStream, fileName: String, appVersionCode: Int): ValidatedExtensionPack {
        requirePackName(fileName)
        if (appVersionCode <= 0) throw ExtensionPackException("VersionCode de l'application invalide.")

        val archiveDigest = MessageDigest.getInstance("SHA-256")
        val boundedSource = BoundedDigestInputStream(source, archiveDigest, limits.maxArchiveBytes)
        val foldedPaths = mutableSetOf<String>()
        val files = try {
            BoundedZipReader.read(
                source = boundedSource,
                limits = BoundedZipReader.Limits(
                    maxTotalBytes = limits.maxTotalBytes,
                    maxEntryBytes = limits.maxEntryBytes,
                    maxEntries = limits.maxEntries
                ),
                accepter = { path ->
                    ExtensionPathPolicy.validateArchivePath(path)
                    if (!foldedPaths.add(ExtensionPathPolicy.fold(path))) {
                        throw ExtensionPackException("Entree dupliquee, y compris par la casse : $path.")
                    }
                    true
                }
            )
                .also {
                    // ZipInputStream s'arrete au repertoire central. On draine
                    // le reste pour que le checksum couvre chaque octet du
                    // fichier `.sankaipack`, pas seulement les entrees utiles.
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (boundedSource.read(buffer) >= 0) Unit
                }
        } catch (error: ExtensionPackException) {
            throw error
        } catch (error: IOException) {
            throw ExtensionPackException(error.message ?: "Archive d'extension illisible.", error)
        }

        val manifestBytes = files[ExtensionManifestCodec.MANIFEST_FILE]
            ?: throw ExtensionPackException("extension.json manque dans le paquet.")
        if (manifestBytes.size > limits.maxManifestBytes) {
            throw ExtensionPackException("Le manifeste est trop volumineux.")
        }
        val manifest = try {
            ExtensionManifestCodec.parse(decodeUtf8(manifestBytes, ExtensionManifestCodec.MANIFEST_FILE))
        } catch (error: ExtensionPackException) {
            throw error
        } catch (error: Exception) {
            throw ExtensionPackException("Manifeste d'extension invalide.", error)
        }
        if (manifest.assets.size + 1 > limits.maxEntries) {
            throw ExtensionPackException("Le manifeste declare trop d'assets.")
        }
        ExtensionManifestValidator.validate(manifest, appVersionCode) { asset ->
            ExtensionPathPolicy.validateAsset(asset)
            if (asset.sizeBytes > limits.maxEntryBytes) {
                throw ExtensionPackException("Asset trop lourd : ${asset.path}.")
            }
        }
        if (manifest.payloadSizeBytes > limits.maxTotalBytes) {
            throw ExtensionPackException("Taille decompressee declaree trop importante.")
        }

        val expectedFiles = manifest.assets.map { it.path }.toSet() + ExtensionManifestCodec.MANIFEST_FILE
        if (files.keys != expectedFiles) {
            val undeclared = files.keys - expectedFiles
            val missing = expectedFiles - files.keys
            throw ExtensionPackException(
                buildString {
                    append("Le contenu ne correspond pas au manifeste.")
                    if (undeclared.isNotEmpty()) append(" Non declares : ${undeclared.sorted()}.")
                    if (missing.isNotEmpty()) append(" Manquants : ${missing.sorted()}.")
                }
            )
        }

        var actualPayloadSize = 0L
        manifest.assets.forEach { asset ->
            val bytes = files.getValue(asset.path)
            actualPayloadSize += bytes.size
            if (bytes.size.toLong() != asset.sizeBytes) {
                throw ExtensionPackException("Taille incorrecte pour ${asset.path}.")
            }
            val actualHash = ExtensionChecksums.sha256(bytes)
            if (!ExtensionChecksums.sameHex(asset.sha256, actualHash)) {
                throw ExtensionPackException("SHA-256 incorrect pour ${asset.path}.")
            }
            ExtensionPathPolicy.validatePassiveContent(asset, bytes)
        }
        if (actualPayloadSize != manifest.payloadSizeBytes) {
            throw ExtensionPackException("Taille decompressee reelle incorrecte.")
        }

        return ValidatedExtensionPack(
            manifest = manifest,
            archiveChecksumSha256 = archiveDigest.digest().toHex(),
            // `files` vient d'etre construit par le lecteur ZIP et n'est expose
            // nulle part ailleurs : eviter ici une seconde copie du payload.
            archiveFiles = files
        )
    }

    private fun requirePackName(fileName: String) {
        if (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) != "sankaipack") {
            throw ExtensionPackException("Une extension locale doit utiliser le suffixe .sankaipack.")
        }
    }

    private fun decodeUtf8(bytes: ByteArray, label: String): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
            .removePrefix("\uFEFF")
    } catch (error: Exception) {
        throw ExtensionPackException("$label doit etre un fichier UTF-8 valide.", error)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /** Source compressee bornee dont close() reste neutre pour permettre le drain final. */
    private class BoundedDigestInputStream(
        private val delegate: InputStream,
        private val digest: MessageDigest,
        private val maximum: Long
    ) : InputStream() {
        private var count = 0L

        override fun read(): Int {
            val value = delegate.read()
            if (value >= 0) {
                account(1)
                digest.update(value.toByte())
            }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = delegate.read(buffer, offset, length)
            if (read > 0) {
                account(read)
                digest.update(buffer, offset, read)
            }
            return read
        }

        override fun skip(count: Long): Long {
            if (count <= 0L) return 0L
            val buffer = ByteArray(minOf(DEFAULT_BUFFER_SIZE.toLong(), count).toInt())
            var remaining = count
            var skipped = 0L
            while (remaining > 0L) {
                val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) break
                remaining -= read
                skipped += read
            }
            return skipped
        }

        override fun close() {
            // Le proprietaire de `source` reste responsable de sa fermeture.
        }

        private fun account(read: Int) {
            if (count > maximum - read) throw ExtensionPackException("Archive compressee trop lourde.")
            count += read
        }
    }
}

internal object ExtensionPathPolicy {
    private val reservedWindowsNames = setOf("con", "prn", "aux", "nul") +
        (1..9).flatMap { listOf("com$it", "lpt$it") }

    private val allowedMediaTypes = mapOf(
        "json" to setOf("application/json"),
        "txt" to setOf("text/plain"),
        "md" to setOf("text/markdown", "text/plain"),
        "png" to setOf("image/png"),
        "jpg" to setOf("image/jpeg"),
        "jpeg" to setOf("image/jpeg"),
        "webp" to setOf("image/webp"),
        "gif" to setOf("image/gif"),
        "mp3" to setOf("audio/mpeg"),
        "ogg" to setOf("audio/ogg"),
        "wav" to setOf("audio/wav"),
        "m4a" to setOf("audio/mp4"),
        "opus" to setOf("audio/opus", "audio/ogg"),
        "ttf" to setOf("font/ttf"),
        "otf" to setOf("font/otf")
    )

    fun fold(path: String): String = Normalizer.normalize(path, Normalizer.Form.NFC)
        .lowercase(Locale.ROOT)

    fun validateArchivePath(path: String) {
        val normalized = Normalizer.normalize(path, Normalizer.Form.NFC)
        val segments = path.split('/')
        val safe = path.isNotBlank() &&
            path == normalized &&
            path.length <= 240 &&
            !path.startsWith('/') &&
            !path.startsWith('\\') &&
            !path.contains('\\') &&
            !path.contains(':') &&
            !path.contains('\u0000') &&
            path.none { it.isISOControl() || Character.isSurrogate(it) } &&
            segments.none { segment ->
                segment.isBlank() || segment == "." || segment == ".." ||
                    segment.startsWith('.') || segment.length > 100 ||
                    segment.endsWith(' ') || segment.endsWith('.') ||
                    segment.substringBefore('.').lowercase(Locale.ROOT) in reservedWindowsNames
            }
        if (!safe) throw ExtensionPackException("Chemin interdit dans l'extension : $path.")

        if (path == ExtensionManifestCodec.MANIFEST_FILE) return
        val allowedRootDocument = path in setOf("README.md", "LICENSE", "LICENSE.txt")
        val allowedPayload = path.startsWith("assets/") || path.startsWith("data/")
        val extension = if (path == "LICENSE") "txt"
        else path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if ((!allowedRootDocument && !allowedPayload) || extension !in allowedMediaTypes) {
            throw ExtensionPackException(
                "Type actif ou emplacement interdit : $path. Seuls des assets passifs sont acceptes."
            )
        }
    }

    fun validateAsset(asset: ExtensionAsset) {
        validateArchivePath(asset.path)
        if (asset.path == ExtensionManifestCodec.MANIFEST_FILE) {
            throw ExtensionPackException("extension.json ne peut pas se declarer lui-meme comme asset.")
        }
        val extension = if (asset.path == "LICENSE") "txt"
        else asset.path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (asset.mediaType.lowercase(Locale.ROOT) !in allowedMediaTypes.getValue(extension)) {
            throw ExtensionPackException("Media type incoherent pour ${asset.path}.")
        }
    }

    fun validatePassiveContent(asset: ExtensionAsset, bytes: ByteArray) {
        val extension = if (asset.path == "LICENSE") "txt"
        else asset.path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val valid = when (extension) {
            "json" -> validateJson(bytes)
            "txt", "md" -> validateText(bytes)
            "png" -> bytes.startsWith(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
            "jpg", "jpeg" -> bytes.startsWith(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()))
            "gif" -> bytes.startsWith("GIF87a".toByteArray()) || bytes.startsWith("GIF89a".toByteArray())
            "webp" -> bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
                bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())
            "wav" -> bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
                bytes.copyOfRange(8, 12).contentEquals("WAVE".toByteArray())
            "ogg", "opus" -> bytes.startsWith("OggS".toByteArray())
            "m4a" -> bytes.size >= 12 && bytes.copyOfRange(4, 8).contentEquals("ftyp".toByteArray())
            "mp3" -> bytes.startsWith("ID3".toByteArray()) ||
                (bytes.size >= 2 && bytes[0] == 0xff.toByte() && (bytes[1].toInt() and 0xe0) == 0xe0)
            "ttf" -> bytes.startsWith(byteArrayOf(0, 1, 0, 0))
            "otf" -> bytes.startsWith("OTTO".toByteArray())
            else -> false
        }
        if (!valid) {
            throw ExtensionPackException(
                "Le contenu de ${asset.path} ne correspond pas a un format passif autorise."
            )
        }
    }

    private fun validateJson(bytes: ByteArray): Boolean = runCatching {
        val text = strictUtf8(bytes)
        ExtensionJson.parse(text)
        true
    }.getOrDefault(false)

    private fun validateText(bytes: ByteArray): Boolean = runCatching {
        val text = strictUtf8(bytes)
        !text.contains('\u0000')
    }.getOrDefault(false)

    private fun strictUtf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && indices.take(prefix.size).all { this[it] == prefix[it] }
}
