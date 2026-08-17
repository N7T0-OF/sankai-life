package com.sankailife.core.extensions

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

internal object ExtensionManifestCodec {
    const val CURRENT_SCHEMA_VERSION = 1
    const val MANIFEST_FILE = "extension.json"

    private val rootKeys = setOf(
        "schemaVersion", "id", "version", "displayName", "dataOnly",
        "payloadSizeBytes", "compatibility", "assets", "capabilities",
        "entryScreen", "settings", "notifications", "saveSchema",
        "payloadChecksumSha256"
    )

    fun parse(text: String): ExtensionManifest {
        val root = ExtensionJson.parse(text).asExtensionJsonObject(MANIFEST_FILE)
        root.requireOnlyKeys(MANIFEST_FILE, rootKeys)

        val compatibility = root["compatibility"].asExtensionJsonObject("compatibility")
        compatibility.requireOnlyKeys(
            "compatibility",
            required = setOf("minAppVersionCode", "maxAppVersionCode")
        )

        val entry = root["entryScreen"].asExtensionJsonObject("entryScreen")
        entry.requireOnlyKeys("entryScreen", setOf("hostScreenId"))

        val save = root["saveSchema"].asExtensionJsonObject("saveSchema")
        save.requireOnlyKeys("saveSchema", setOf("kind", "version"))

        val assets = root["assets"].asExtensionJsonArray("assets").mapIndexed { index, raw ->
            val asset = raw.asExtensionJsonObject("assets[$index]")
            asset.requireOnlyKeys(
                "assets[$index]", setOf("path", "sizeBytes", "sha256", "mediaType")
            )
            ExtensionAsset(
                path = asset.extensionString("path"),
                sizeBytes = asset.extensionLong("sizeBytes"),
                sha256 = asset.extensionString("sha256"),
                mediaType = asset.extensionString("mediaType")
            )
        }

        val settings = root["settings"].asExtensionJsonArray("settings").mapIndexed { index, raw ->
            val setting = raw.asExtensionJsonObject("settings[$index]")
            setting.requireOnlyKeys(
                "settings[$index]",
                setOf("id", "type", "defaultValue", "allowedValues")
            )
            ExtensionSettingSpec(
                id = setting.extensionString("id"),
                type = enumValue("type", setting.extensionString("type")),
                defaultValue = setting.extensionNullableString("defaultValue"),
                allowedValues = setting.extensionStringList("allowedValues")
            )
        }

        val notifications = root["notifications"].asExtensionJsonArray("notifications")
            .mapIndexed { index, raw ->
                val notification = raw.asExtensionJsonObject("notifications[$index]")
                notification.requireOnlyKeys(
                    "notifications[$index]", setOf("id", "defaultEnabled")
                )
                ExtensionNotificationSpec(
                    id = notification.extensionString("id"),
                    defaultEnabled = notification.extensionBoolean("defaultEnabled")
                )
            }

        val capabilities = root.extensionStringList("capabilities")
        if (capabilities.distinct().size != capabilities.size) {
            throw IOException("Les capabilities ne doivent pas etre dupliquees.")
        }

        return ExtensionManifest(
            schemaVersion = root.extensionInt("schemaVersion"),
            id = root.extensionString("id"),
            version = root.extensionString("version"),
            displayName = root.extensionString("displayName"),
            dataOnly = root.extensionBoolean("dataOnly"),
            payloadSizeBytes = root.extensionLong("payloadSizeBytes"),
            compatibility = ExtensionCompatibility(
                minAppVersionCode = compatibility.extensionInt("minAppVersionCode"),
                maxAppVersionCode = compatibility.extensionNullableInt("maxAppVersionCode")
            ),
            assets = assets,
            capabilities = capabilities.toSet(),
            entryScreen = ExtensionEntryScreen(entry.extensionString("hostScreenId")),
            settings = settings,
            notifications = notifications,
            saveSchema = ExtensionSaveSchema(
                kind = enumValue("saveSchema.kind", save.extensionString("kind")),
                version = save.extensionInt("version")
            ),
            payloadChecksumSha256 = root.extensionString("payloadChecksumSha256")
        )
    }

    fun encode(manifest: ExtensionManifest): String = ExtensionJson.stringify(
        linkedMapOf(
            "schemaVersion" to manifest.schemaVersion,
            "id" to manifest.id,
            "version" to manifest.version,
            "displayName" to manifest.displayName,
            "dataOnly" to manifest.dataOnly,
            "payloadSizeBytes" to manifest.payloadSizeBytes,
            "compatibility" to linkedMapOf(
                "minAppVersionCode" to manifest.compatibility.minAppVersionCode,
                "maxAppVersionCode" to manifest.compatibility.maxAppVersionCode
            ),
            "assets" to manifest.assets.sortedBy { it.path }.map { asset ->
                linkedMapOf(
                    "path" to asset.path,
                    "sizeBytes" to asset.sizeBytes,
                    "sha256" to asset.sha256.lowercase(Locale.ROOT),
                    "mediaType" to asset.mediaType
                )
            },
            "capabilities" to manifest.capabilities.sorted(),
            "entryScreen" to linkedMapOf("hostScreenId" to manifest.entryScreen.hostScreenId),
            "settings" to manifest.settings.sortedBy { it.id }.map { setting ->
                linkedMapOf(
                    "id" to setting.id,
                    "type" to setting.type.name,
                    "defaultValue" to setting.defaultValue,
                    "allowedValues" to setting.allowedValues
                )
            },
            "notifications" to manifest.notifications.sortedBy { it.id }.map { notification ->
                linkedMapOf(
                    "id" to notification.id,
                    "defaultEnabled" to notification.defaultEnabled
                )
            },
            "saveSchema" to linkedMapOf(
                "kind" to manifest.saveSchema.kind.name,
                "version" to manifest.saveSchema.version
            ),
            "payloadChecksumSha256" to manifest.payloadChecksumSha256.lowercase(Locale.ROOT)
        )
    )

    private inline fun <reified T : Enum<T>> enumValue(label: String, raw: String): T =
        runCatching { enumValueOf<T>(raw) }
            .getOrElse { throw IOException("Valeur inconnue pour $label : $raw.", it) }
}

internal object ExtensionManifestValidator {
    private val identifier = Regex("^[a-z0-9]+(?:[._-][a-z0-9]+)*$")
    private val version = Regex("^[0-9A-Za-z][0-9A-Za-z._+-]{0,63}$")
    private val sha256 = Regex("^[a-fA-F0-9]{64}$")

    fun validate(
        manifest: ExtensionManifest,
        appVersionCode: Int,
        validateAsset: (ExtensionAsset) -> Unit
    ) {
        if (manifest.schemaVersion != ExtensionManifestCodec.CURRENT_SCHEMA_VERSION) {
            throw ExtensionPackException(
                if (manifest.schemaVersion > ExtensionManifestCodec.CURRENT_SCHEMA_VERSION) {
                    "Extension creee pour un format plus recent de Sankai Life."
                } else "Ancien format d'extension non pris en charge."
            )
        }
        if (!manifest.dataOnly) {
            throw ExtensionPackException("Une extension locale doit declarer dataOnly=true ; aucun code n'est accepte.")
        }
        validateIdentifier(manifest.id, "identifiant d'extension")
        if (!version.matches(manifest.version)) throw ExtensionPackException("Version d'extension invalide.")
        requireText(manifest.displayName, "displayName", 120)
        if (manifest.payloadSizeBytes < 0L) throw ExtensionPackException("Taille de paquet invalide.")

        val compatibility = manifest.compatibility
        if (compatibility.minAppVersionCode <= 0 ||
            (compatibility.maxAppVersionCode != null &&
                compatibility.maxAppVersionCode < compatibility.minAppVersionCode)
        ) {
            throw ExtensionPackException("Plage de compatibilite invalide.")
        }
        if (appVersionCode < compatibility.minAppVersionCode ||
            (compatibility.maxAppVersionCode != null && appVersionCode > compatibility.maxAppVersionCode)
        ) {
            throw ExtensionPackException("Extension incompatible avec cette version de Sankai Life.")
        }

        if (manifest.assets.isEmpty()) throw ExtensionPackException("Une extension doit declarer ses assets.")
        val assetIds = mutableSetOf<String>()
        var total = 0L
        manifest.assets.forEach { asset ->
            val folded = ExtensionPathPolicy.fold(asset.path)
            if (!assetIds.add(folded)) throw ExtensionPackException("Asset duplique : ${asset.path}.")
            if (asset.sizeBytes < 0L || total > Long.MAX_VALUE - asset.sizeBytes) {
                throw ExtensionPackException("Taille d'asset invalide : ${asset.path}.")
            }
            total += asset.sizeBytes
            if (!sha256.matches(asset.sha256)) {
                throw ExtensionPackException("SHA-256 invalide pour ${asset.path}.")
            }
            requireText(asset.mediaType, "mediaType (${asset.path})", 100)
            validateAsset(asset)
        }
        if (total != manifest.payloadSizeBytes) {
            throw ExtensionPackException(
                "Le manifeste annonce ${manifest.payloadSizeBytes} octets, ses assets en totalisent $total."
            )
        }
        if (!sha256.matches(manifest.payloadChecksumSha256) ||
            !ExtensionChecksums.sameHex(
                manifest.payloadChecksumSha256,
                ExtensionChecksums.payloadChecksum(manifest.assets)
            )
        ) {
            throw ExtensionPackException("Checksum global des assets invalide.")
        }

        if (manifest.capabilities.isEmpty() || manifest.capabilities.size > 32) {
            throw ExtensionPackException("Capabilities absentes ou trop nombreuses.")
        }
        manifest.capabilities.forEach { validateIdentifier(it, "capability") }
        validateIdentifier(manifest.entryScreen.hostScreenId, "ecran hote")

        validateDistinct(manifest.settings.map { it.id }, "reglage")
        if (manifest.settings.size > 64) throw ExtensionPackException("Trop de reglages declares.")
        manifest.settings.forEach { setting ->
            validateIdentifier(setting.id, "reglage")
            if (setting.allowedValues.size > 100 || setting.allowedValues.distinct().size != setting.allowedValues.size) {
                throw ExtensionPackException("Valeurs autorisees invalides pour ${setting.id}.")
            }
            setting.allowedValues.forEach { requireText(it, "allowedValues (${setting.id})", 100) }
            when (setting.type) {
                ExtensionSettingType.BOOLEAN -> if (setting.defaultValue !in setOf("true", "false")) {
                    throw ExtensionPackException("Valeur booleenne invalide pour ${setting.id}.")
                }
                ExtensionSettingType.INTEGER -> if (setting.defaultValue?.toLongOrNull() == null) {
                    throw ExtensionPackException("Valeur entiere invalide pour ${setting.id}.")
                }
                ExtensionSettingType.ENUM -> if (setting.allowedValues.isEmpty() ||
                    setting.defaultValue !in setting.allowedValues
                ) {
                    throw ExtensionPackException("Valeur enumeree invalide pour ${setting.id}.")
                }
                ExtensionSettingType.STRING -> if (setting.defaultValue != null && setting.defaultValue.length > 500) {
                    throw ExtensionPackException("Valeur texte trop longue pour ${setting.id}.")
                }
            }
        }

        validateDistinct(manifest.notifications.map { it.id }, "notification")
        if (manifest.notifications.size > 32) throw ExtensionPackException("Trop de notifications declarees.")
        manifest.notifications.forEach { notification ->
            validateIdentifier(notification.id, "notification")
            if (notification.defaultEnabled) {
                throw ExtensionPackException("Les notifications d'une extension doivent etre desactivees par defaut.")
            }
        }

        when (manifest.saveSchema.kind) {
            ExtensionSaveKind.NONE -> if (manifest.saveSchema.version != 0) {
                throw ExtensionPackException("Un schema NONE doit avoir la version 0.")
            }
            ExtensionSaveKind.GARDEN_SUMMARY ->
                if (manifest.saveSchema.version != GardenExtensionSnapshot.CURRENT_SCHEMA_VERSION) {
                    throw ExtensionPackException("Version de snapshot Garden non prise en charge.")
                }
        }
        val isGarden = manifest.id == SankaiExtensionContract.GARDEN_ID
        if (isGarden != (manifest.saveSchema.kind == ExtensionSaveKind.GARDEN_SUMMARY)) {
            throw ExtensionPackException("Le snapshot Garden est reserve a l'extension Jardin Sankai.")
        }
        if (isGarden && manifest.entryScreen.hostScreenId != SankaiExtensionContract.GARDEN_ENTRY_SCREEN_ID) {
            throw ExtensionPackException("Ecran hote Garden inconnu.")
        }
    }

    fun validateIdentifier(value: String, label: String) {
        if (!identifier.matches(value) || value.length > 120) {
            throw ExtensionPackException("$label invalide : $value.")
        }
    }

    private fun validateDistinct(values: List<String>, label: String) {
        if (values.map { it.lowercase(Locale.ROOT) }.distinct().size != values.size) {
            throw ExtensionPackException("$label duplique.")
        }
    }

    private fun requireText(value: String, label: String, max: Int) {
        if (value.isBlank() || value.length > max || value.any { it.isISOControl() }) {
            throw ExtensionPackException("Champ $label invalide.")
        }
    }
}

internal object ExtensionChecksums {
    private val hex = Regex("^[a-fA-F0-9]{64}$")

    fun sha256(bytes: ByteArray): String = digest().digest(bytes).toHex()

    fun payloadChecksum(assets: List<ExtensionAsset>): String {
        val digest = digest()
        assets.sortedBy { it.path }.forEach { asset ->
            update(digest, asset.path)
            digest.update(0.toByte())
            update(digest, asset.sizeBytes.toString())
            digest.update(0.toByte())
            update(digest, asset.sha256.lowercase(Locale.ROOT))
            digest.update('\n'.code.toByte())
        }
        return digest.digest().toHex()
    }

    fun sameHex(expected: String, actual: String): Boolean =
        hex.matches(expected) && hex.matches(actual) && MessageDigest.isEqual(
            expected.lowercase(Locale.ROOT).toByteArray(StandardCharsets.US_ASCII),
            actual.lowercase(Locale.ROOT).toByteArray(StandardCharsets.US_ASCII)
        )

    private fun digest(): MessageDigest = MessageDigest.getInstance("SHA-256")

    private fun update(digest: MessageDigest, value: String) {
        digest.update(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
