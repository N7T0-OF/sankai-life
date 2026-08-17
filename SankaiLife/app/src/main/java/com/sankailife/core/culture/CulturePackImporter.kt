package com.sankailife.core.culture

import com.sankailife.core.data.archive.BoundedZipReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Locale

class CulturePackException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Inspecte un paquet culturel avant toute installation.
 *
 * Un paquet n'est qu'une archive de données. La liste blanche interdit le
 * code, les chemins absolus, les traversées de répertoires, les doublons de
 * casse et les formats actifs tels que HTML ou SVG. Chaque fichier est en
 * outre lié au manifeste par SHA-256.
 */
object CulturePackImporter {
    const val SCHEMA_VERSION = 1
    const val MAX_ARCHIVE_BYTES = 32 * 1024 * 1024
    const val MAX_ENTRY_BYTES = 10 * 1024 * 1024
    const val MAX_ENTRIES = 128
    const val MAX_CULTURE_ENTRIES = 5_000

    private const val MAX_MANIFEST_BYTES = 128 * 1024
    private const val MAX_ENTRIES_JSON_BYTES = 8 * 1024 * 1024
    private val identifier = Regex("^[a-z0-9]+(?:[._-][a-z0-9]+)*$")
    private val version = Regex("^[0-9A-Za-z][0-9A-Za-z._+-]{0,63}$")
    private val language = Regex("^[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-[A-Z]{2}|-[0-9]{3})?$")
    private val country = Regex("^[A-Z]{2}$")
    private val sha256 = Regex("^[a-fA-F0-9]{64}$")
    private val mediaExtensions = setOf(
        "png", "jpg", "jpeg", "webp", "gif",
        "mp3", "ogg", "wav", "m4a", "opus"
    )
    private val requiredFiles = setOf("pack.json", "entries.json", "README.md", "LICENSE")

    fun inspect(bytes: ByteArray, appVersionCode: Int): ImportedCulturePack =
        ByteArrayInputStream(bytes).use { inspect(it, appVersionCode) }

    fun inspect(source: InputStream, appVersionCode: Int): ImportedCulturePack {
        require(appVersionCode > 0) { "La version de l'application doit être positive." }
        val seen = mutableSetOf<String>()
        val archive = try {
            BoundedZipReader.read(
                source = source,
                limits = BoundedZipReader.Limits(
                    maxTotalBytes = MAX_ARCHIVE_BYTES.toLong(),
                    maxEntryBytes = MAX_ENTRY_BYTES.toLong(),
                    maxEntries = MAX_ENTRIES
                ),
                accepter = { path ->
                    validateArchivePath(path)
                    val folded = path.lowercase(Locale.ROOT)
                    if (!seen.add(folded)) {
                        throw CulturePackException("Entrée dupliquée dans le paquet : $path.")
                    }
                    true
                }
            )
        } catch (error: CulturePackException) {
            throw error
        } catch (error: IOException) {
            throw CulturePackException(error.message ?: "Archive culturelle illisible.", error)
        }

        val missing = requiredFiles - archive.keys
        if (missing.isNotEmpty()) {
            throw CulturePackException("Fichiers obligatoires manquants : ${missing.sorted().joinToString()}.")
        }
        val manifestBytes = archive.getValue("pack.json")
        if (manifestBytes.size > MAX_MANIFEST_BYTES) {
            throw CulturePackException("Le manifeste est trop volumineux.")
        }
        if (archive.getValue("entries.json").size > MAX_ENTRIES_JSON_BYTES) {
            throw CulturePackException("Le catalogue d'entrées est trop volumineux.")
        }

        val manifest = parseManifest(decodeUtf8(manifestBytes, "pack.json"))
        validateManifest(manifest, appVersionCode)

        val payload = archive.keys - "pack.json"
        if (payload != manifest.files.keys) {
            val undeclared = payload - manifest.files.keys
            val absent = manifest.files.keys - payload
            val details = buildList {
                if (undeclared.isNotEmpty()) add("non déclarés : ${undeclared.sorted().joinToString()}")
                if (absent.isNotEmpty()) add("absents : ${absent.sorted().joinToString()}")
            }.joinToString(" ; ")
            throw CulturePackException("Le contenu ne correspond pas au manifeste ($details).")
        }
        val actualPayloadBytes = payload.sumOf { archive.getValue(it).size.toLong() }
        if (actualPayloadBytes != manifest.payloadBytes) {
            throw CulturePackException(
                "Taille décompressée incorrecte : $actualPayloadBytes octets au lieu de " +
                    "${manifest.payloadBytes}."
            )
        }
        manifest.files.forEach { (path, expectedHash) ->
            val actual = digest(archive.getValue(path))
            if (!MessageDigest.isEqual(
                    expectedHash.lowercase(Locale.ROOT).toByteArray(),
                    actual.toByteArray()
                )
            ) {
                throw CulturePackException("Empreinte incorrecte pour $path.")
            }
        }

        val entries = parseEntries(decodeUtf8(archive.getValue("entries.json"), "entries.json"))
        validateEntries(entries, manifest, archive.keys)
        return ImportedCulturePack(
            manifest = manifest,
            entries = entries,
            files = archive.filterKeys { it != "pack.json" }.mapValues { (_, bytes) -> bytes.copyOf() }
        )
    }

    private fun validateArchivePath(path: String) {
        val safe = path.isNotBlank() &&
            path.length <= 240 &&
            !path.startsWith('/') &&
            !path.startsWith('\\') &&
            !path.contains('\\') &&
            !path.contains(':') &&
            !path.contains('\u0000') &&
            path.split('/').none { it.isBlank() || it == "." || it == ".." }
        if (!safe) throw CulturePackException("Chemin interdit dans le paquet : $path.")

        val allowed = path in requiredFiles ||
            path == "sources.json" ||
            (path.startsWith("media/") && path.substringAfterLast('.', "")
                .lowercase(Locale.ROOT) in mediaExtensions)
        if (!allowed) {
            throw CulturePackException("Type de fichier interdit dans un paquet culturel : $path.")
        }
    }

    private fun parseManifest(text: String): CulturePackManifest {
        val json = CultureJson.parse(text).jsonObject("pack.json")
        val fileObject = json["files"].jsonObject("files")
        val files = linkedMapOf<String, String>()
        fileObject.forEach { (path, value) ->
            val hash = value as? String
                ?: throw CulturePackException("L'empreinte de $path doit être une chaîne.")
            files[path] = hash
        }
        return CulturePackManifest(
            schemaVersion = json.requiredInt("schemaVersion"),
            id = json.requiredString("id"),
            version = json.requiredString("version"),
            title = json.requiredString("title"),
            description = json.requiredString("description"),
            languages = json.stringList("languages").toSet(),
            license = json.requiredString("license"),
            sourceLabel = json.requiredString("sourceLabel"),
            minAppVersionCode = json.requiredInt("minAppVersionCode"),
            maxAppVersionCode = json.optionalInt("maxAppVersionCode"),
            entryCount = json.requiredInt("entryCount"),
            payloadBytes = json.requiredInt("payloadBytes").toLong(),
            files = files
        )
    }

    private fun validateManifest(manifest: CulturePackManifest, appVersionCode: Int) {
        if (manifest.schemaVersion != SCHEMA_VERSION) {
            throw CulturePackException(
                if (manifest.schemaVersion > SCHEMA_VERSION) {
                    "Paquet créé pour une version plus récente de Sankai Life."
                } else {
                    "Ancien format de paquet culturel non pris en charge."
                }
            )
        }
        if (!identifier.matches(manifest.id)) throw CulturePackException("Identifiant de paquet invalide.")
        if (!version.matches(manifest.version)) throw CulturePackException("Version de paquet invalide.")
        requireLength(manifest.title, "title", 120)
        requireLength(manifest.description, "description", 1_000)
        requireLength(manifest.license, "license", 200)
        requireLength(manifest.sourceLabel, "sourceLabel", 500)
        if (manifest.languages.isEmpty() || manifest.languages.any { !language.matches(it) }) {
            throw CulturePackException("Le paquet doit déclarer des langues BCP-47 valides.")
        }
        if (manifest.entryCount !in 1..MAX_CULTURE_ENTRIES) {
            throw CulturePackException("Nombre d'entrées invalide : ${manifest.entryCount}.")
        }
        if (manifest.payloadBytes <= 0L || manifest.payloadBytes > MAX_ARCHIVE_BYTES) {
            throw CulturePackException("Taille décompressée déclarée invalide.")
        }
        if (manifest.minAppVersionCode <= 0 ||
            (manifest.maxAppVersionCode != null &&
                manifest.maxAppVersionCode < manifest.minAppVersionCode)
        ) {
            throw CulturePackException("Plage de compatibilité invalide.")
        }
        if (appVersionCode < manifest.minAppVersionCode ||
            (manifest.maxAppVersionCode != null && appVersionCode > manifest.maxAppVersionCode)
        ) {
            throw CulturePackException("Ce paquet n'est pas compatible avec cette version de Sankai Life.")
        }
        if (manifest.files.keys.any { it == "pack.json" }) {
            throw CulturePackException("pack.json ne peut pas s'auto-référencer.")
        }
        val missingChecksums = requiredFiles.minus("pack.json") - manifest.files.keys
        if (missingChecksums.isNotEmpty()) {
            throw CulturePackException("Empreintes obligatoires manquantes : ${missingChecksums.joinToString()}.")
        }
        manifest.files.forEach { (path, hash) ->
            validateArchivePath(path)
            if (!sha256.matches(hash)) throw CulturePackException("Empreinte SHA-256 invalide pour $path.")
        }
    }

    private fun parseEntries(text: String): List<DailyCultureEntry> {
        val root = CultureJson.parse(text).jsonObject("entries.json")
        return root["entries"].jsonArray("entries").mapIndexed { index, raw ->
            val json = raw.jsonObject("entries[$index]")
            try {
                DailyCultureEntry(
                    id = json.requiredString("id"),
                    type = enumValueOf(json.requiredString("type")),
                    title = json.requiredString("title"),
                    body = json.optionalString("body"),
                    author = json.optionalString("author"),
                    authorBirthYear = json.optionalInt("authorBirthYear"),
                    authorDeathYear = json.optionalInt("authorDeathYear"),
                    workDate = json.optionalString("workDate"),
                    publicationDate = json.optionalString("publicationDate"),
                    countryCode = json.optionalString("countryCode"),
                    languageCode = json.requiredString("languageCode"),
                    context = json.optionalString("context"),
                    sourceLabel = json.optionalString("sourceLabel"),
                    sourceUrl = json.optionalString("sourceUrl"),
                    rightsStatus = enumValueOf(json.requiredString("rightsStatus")),
                    license = json.optionalString("license"),
                    tags = json.stringList("tags"),
                    difficulty = json.optionalString("difficulty")
                        ?.let { enumValueOf<CultureDifficulty>(it) }
                        ?: CultureDifficulty.STANDARD,
                    mediaPath = json.optionalString("mediaPath")
                )
            } catch (error: IllegalArgumentException) {
                throw CulturePackException("Valeur d'énumération invalide dans entries[$index].", error)
            }
        }
    }

    private fun validateEntries(
        entries: List<DailyCultureEntry>,
        manifest: CulturePackManifest,
        archivePaths: Set<String>
    ) {
        if (entries.size != manifest.entryCount) {
            throw CulturePackException(
                "Le manifeste annonce ${manifest.entryCount} entrées, le catalogue en contient ${entries.size}."
            )
        }
        val ids = mutableSetOf<String>()
        entries.forEach { entry ->
            if (!identifier.matches(entry.id) || !ids.add(entry.id)) {
                throw CulturePackException("Identifiant d'entrée invalide ou dupliqué : ${entry.id}.")
            }
            requireLength(entry.title, "title (${entry.id})", 300)
            entry.body?.let { requireLength(it, "body (${entry.id})", 30_000) }
            entry.author?.let { requireLength(it, "author (${entry.id})", 200) }
            entry.context?.let { requireLength(it, "context (${entry.id})", 4_000) }
            entry.sourceLabel?.let { requireLength(it, "sourceLabel (${entry.id})", 500) }
            entry.license?.let { requireLength(it, "license (${entry.id})", 200) }
            if (!language.matches(entry.languageCode) || entry.languageCode !in manifest.languages) {
                throw CulturePackException("Langue invalide ou non déclarée pour ${entry.id}.")
            }
            if (entry.countryCode != null && !country.matches(entry.countryCode)) {
                throw CulturePackException("Code pays invalide pour ${entry.id}.")
            }
            listOfNotNull(entry.authorBirthYear, entry.authorDeathYear).forEach { year ->
                if (year !in -5_000..3_000) throw CulturePackException("Année invalide pour ${entry.id}.")
            }
            if (entry.authorBirthYear != null && entry.authorDeathYear != null &&
                entry.authorDeathYear < entry.authorBirthYear
            ) {
                throw CulturePackException("Dates d'auteur incohérentes pour ${entry.id}.")
            }
            if (entry.tags.size > 20 || entry.tags.distinct().size != entry.tags.size ||
                entry.tags.any { it.length > 60 }
            ) {
                throw CulturePackException("Tags invalides pour ${entry.id}.")
            }
            if (entry.sourceLabel.isNullOrBlank()) {
                throw CulturePackException("La source est obligatoire pour ${entry.id}.")
            }
            if (entry.sourceUrl != null && !entry.sourceUrl.startsWith("https://")) {
                throw CulturePackException("La source de ${entry.id} doit utiliser HTTPS.")
            }
            when (entry.rightsStatus) {
                ContentRightsStatus.METADATA_ONLY -> if (!entry.body.isNullOrBlank()) {
                    throw CulturePackException("${entry.id} est limité aux métadonnées et ne peut contenir le texte.")
                }
                ContentRightsStatus.PUBLIC_DOMAIN,
                ContentRightsStatus.CREATIVE_COMMONS,
                ContentRightsStatus.LICENSED -> if (entry.license.isNullOrBlank()) {
                    throw CulturePackException("La licence est obligatoire pour ${entry.id}.")
                }
            }
            if (entry.rightsStatus != ContentRightsStatus.METADATA_ONLY &&
                entry.type in setOf(
                    CultureEntryType.POEM,
                    CultureEntryType.QUOTE,
                    CultureEntryType.PROVERB,
                    CultureEntryType.WORD
                ) && entry.body.isNullOrBlank()
            ) {
                throw CulturePackException("Le contenu textuel manque pour ${entry.id}.")
            }
            entry.mediaPath?.let { path ->
                if (!path.startsWith("media/") || path !in archivePaths) {
                    throw CulturePackException("Média absent ou invalide pour ${entry.id} : $path.")
                }
            }
        }
    }

    private fun requireLength(value: String, field: String, max: Int) {
        if (value.isBlank() || value.length > max || value.any { it == '\u0000' }) {
            throw CulturePackException("Champ $field invalide.")
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
        throw CulturePackException("$label n'est pas un fichier UTF-8 valide.", error)
    }

    internal fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
