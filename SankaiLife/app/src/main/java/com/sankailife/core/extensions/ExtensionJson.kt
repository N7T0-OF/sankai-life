package com.sankailife.core.extensions

import java.io.IOException

/** Lecteur/ecrivain JSON borne et sans dependance Android. */
internal object ExtensionJson {
    const val MAX_DEPTH = 24
    const val MAX_VALUES = 50_000

    fun parse(text: String): Any? = Parser(text).parse()

    fun stringify(value: Any?): String = buildString { writeJson(value, this) }

    private fun writeJson(value: Any?, out: StringBuilder) {
        when (value) {
            null -> out.append("null")
            is String -> writeString(value, out)
            is Boolean -> out.append(value)
            is Byte, is Short, is Int, is Long -> out.append(value)
            is Float -> {
                require(value.isFinite()) { "Nombre JSON non fini." }
                out.append(value)
            }
            is Double -> {
                require(value.isFinite()) { "Nombre JSON non fini." }
                out.append(value)
            }
            is Map<*, *> -> {
                out.append('{')
                value.entries.forEachIndexed { index, (key, item) ->
                    if (index > 0) out.append(',')
                    writeString(key as? String ?: error("Cle JSON non textuelle."), out)
                    out.append(':')
                    writeJson(item, out)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    writeJson(item, out)
                }
                out.append(']')
            }
            else -> error("Valeur JSON non prise en charge : ${value::class.java.name}.")
        }
    }

    private fun writeString(value: String, out: StringBuilder) {
        out.append('"')
        value.forEach { char ->
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (char.code < 0x20) {
                    out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                } else out.append(char)
            }
        }
        out.append('"')
    }

    private class Parser(private val source: String) {
        private var index = 0
        private var values = 0

        fun parse(): Any? {
            val value = readValue(0)
            skipWhitespace()
            if (index != source.length) fail("Caracteres inattendus apres le JSON")
            return value
        }

        private fun readValue(depth: Int): Any? {
            if (depth > MAX_DEPTH) fail("JSON trop profond")
            if (++values > MAX_VALUES) fail("JSON trop complexe")
            skipWhitespace()
            if (index >= source.length) fail("JSON incomplet")
            return when (source[index]) {
                '{' -> readObject(depth + 1)
                '[' -> readArray(depth + 1)
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                '-', in '0'..'9' -> readNumber()
                else -> fail("Valeur JSON invalide")
            }
        }

        private fun readObject(depth: Int): Map<String, Any?> {
            index++
            skipWhitespace()
            val result = linkedMapOf<String, Any?>()
            if (take('}')) return result
            while (true) {
                skipWhitespace()
                if (index >= source.length || source[index] != '"') fail("Cle JSON attendue")
                val key = readString()
                if (result.containsKey(key)) fail("Cle JSON dupliquee : $key")
                skipWhitespace()
                expect(':')
                result[key] = readValue(depth)
                skipWhitespace()
                if (take('}')) return result
                expect(',')
            }
        }

        private fun readArray(depth: Int): List<Any?> {
            index++
            skipWhitespace()
            val result = mutableListOf<Any?>()
            if (take(']')) return result
            while (true) {
                result += readValue(depth)
                skipWhitespace()
                if (take(']')) return result
                expect(',')
            }
        }

        private fun readString(): String {
            expect('"')
            val result = StringBuilder()
            while (index < source.length) {
                val char = source[index++]
                when {
                    char == '"' -> return result.toString()
                    char == '\\' -> result.append(readEscape())
                    char.code < 0x20 -> fail("Caractere de controle dans une chaine JSON")
                    else -> result.append(char)
                }
            }
            fail("Chaine JSON incomplete")
        }

        private fun readEscape(): Char {
            if (index >= source.length) fail("Echappement JSON incomplet")
            return when (val escaped = source[index++]) {
                '"', '\\', '/' -> escaped
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> readUnicodeEscape()
                else -> fail("Echappement JSON invalide")
            }
        }

        private fun readUnicodeEscape(): Char {
            if (index + 4 > source.length) fail("Echappement Unicode incomplet")
            val code = source.substring(index, index + 4).toIntOrNull(16)
                ?: fail("Echappement Unicode invalide")
            index += 4
            return code.toChar()
        }

        private fun readNumber(): Number {
            val start = index
            if (source[index] == '-') index++
            if (index >= source.length) fail("Nombre JSON incomplet")
            when (source[index]) {
                '0' -> index++
                in '1'..'9' -> while (index < source.length && source[index].isDigit()) index++
                else -> fail("Nombre JSON invalide")
            }
            var decimal = false
            if (index < source.length && source[index] == '.') {
                decimal = true
                index++
                val startDecimal = index
                while (index < source.length && source[index].isDigit()) index++
                if (index == startDecimal) fail("Nombre JSON invalide")
            }
            if (index < source.length && source[index] in "eE") {
                decimal = true
                index++
                if (index < source.length && source[index] in "+-") index++
                val startExponent = index
                while (index < source.length && source[index].isDigit()) index++
                if (index == startExponent) fail("Exposant JSON invalide")
            }
            val raw = source.substring(start, index)
            return if (decimal) {
                raw.toDoubleOrNull()?.takeIf { it.isFinite() } ?: fail("Nombre hors limites")
            } else raw.toLongOrNull() ?: fail("Entier hors limites")
        }

        private fun <T> readLiteral(literal: String, value: T): T {
            if (!source.regionMatches(index, literal, 0, literal.length)) {
                fail("Valeur JSON invalide")
            }
            index += literal.length
            return value
        }

        private fun skipWhitespace() {
            while (index < source.length && source[index] in " \t\r\n") index++
        }

        private fun take(expected: Char): Boolean {
            if (index < source.length && source[index] == expected) {
                index++
                return true
            }
            return false
        }

        private fun expect(expected: Char) {
            if (!take(expected)) fail("Caractere '$expected' attendu")
        }

        private fun fail(message: String): Nothing =
            throw IOException("$message a la position $index.")
    }
}

internal fun Any?.asExtensionJsonObject(label: String): Map<String, Any?> {
    val source = this as? Map<*, *> ?: throw IOException("$label doit etre un objet JSON.")
    return source.entries.associate { (key, value) ->
        (key as? String ?: throw IOException("$label contient une cle non textuelle.")) to value
    }
}

internal fun Any?.asExtensionJsonArray(label: String): List<Any?> =
    this as? List<Any?> ?: throw IOException("$label doit etre un tableau JSON.")

internal fun Map<String, Any?>.requireOnlyKeys(
    label: String,
    required: Set<String>,
    optional: Set<String> = emptySet()
) {
    val missing = required - keys
    val unknown = keys - required - optional
    if (missing.isNotEmpty()) throw IOException("$label : champs manquants ${missing.sorted()}.")
    if (unknown.isNotEmpty()) throw IOException("$label : champs inconnus ${unknown.sorted()}.")
}

internal fun Map<String, Any?>.extensionString(key: String): String =
    (this[key] as? String)?.takeIf { it.isNotBlank() }
        ?: throw IOException("Le champ $key est obligatoire.")

internal fun Map<String, Any?>.extensionNullableString(key: String): String? = when (val value = this[key]) {
    null -> null
    is String -> value
    else -> throw IOException("Le champ $key doit etre une chaine ou null.")
}

internal fun Map<String, Any?>.extensionBoolean(key: String): Boolean =
    this[key] as? Boolean ?: throw IOException("Le champ $key doit etre booleen.")

internal fun Map<String, Any?>.extensionLong(key: String): Long =
    this[key] as? Long ?: throw IOException("Le champ $key doit etre un entier.")

internal fun Map<String, Any?>.extensionInt(key: String): Int {
    val value = extensionLong(key)
    return value.toInt().takeIf { it.toLong() == value }
        ?: throw IOException("Le champ $key est hors limites.")
}

internal fun Map<String, Any?>.extensionNullableInt(key: String): Int? =
    if (this[key] == null) null else extensionInt(key)

internal fun Map<String, Any?>.extensionStringList(key: String): List<String> =
    this[key].asExtensionJsonArray(key).mapIndexed { index, value ->
        (value as? String)?.takeIf { it.isNotBlank() }
            ?: throw IOException("$key[$index] doit etre une chaine non vide.")
    }
