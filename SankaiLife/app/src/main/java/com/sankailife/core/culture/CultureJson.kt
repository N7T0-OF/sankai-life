package com.sankailife.core.culture

import java.io.IOException

/**
 * Petit lecteur JSON sans dépendance Android.
 *
 * Le format de pack reste ainsi testable sur la JVM. Le lecteur limite la
 * profondeur et le nombre de valeurs afin qu'un JSON minuscule mais
 * pathologique ne monopolise ni la pile ni le processeur.
 */
internal object CultureJson {
    const val MAX_DEPTH = 32
    const val MAX_VALUES = 100_000

    fun parse(text: String): Any? = Parser(text).parse()

    private class Parser(private val source: String) {
        private var index = 0
        private var values = 0

        fun parse(): Any? {
            val value = readValue(0)
            skipWhitespace()
            if (index != source.length) fail("Caractères inattendus après le JSON")
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
                if (index >= source.length || source[index] != '"') {
                    fail("Clé JSON attendue")
                }
                val key = readString()
                if (result.containsKey(key)) fail("Clé JSON dupliquée : $key")
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
                    char.code < 0x20 -> fail("Caractère de contrôle dans une chaîne JSON")
                    else -> result.append(char)
                }
            }
            fail("Chaîne JSON incomplète")
        }

        private fun readEscape(): Char {
            if (index >= source.length) fail("Échappement JSON incomplet")
            return when (val escaped = source[index++]) {
                '"', '\\', '/' -> escaped
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> readUnicodeEscape()
                else -> fail("Échappement JSON invalide")
            }
        }

        private fun readUnicodeEscape(): Char {
            if (index + 4 > source.length) fail("Échappement Unicode incomplet")
            val code = source.substring(index, index + 4).toIntOrNull(16)
                ?: fail("Échappement Unicode invalide")
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
                val decimalStart = index
                while (index < source.length && source[index].isDigit()) index++
                if (index == decimalStart) fail("Nombre JSON invalide")
            }
            if (index < source.length && source[index] in "eE") {
                decimal = true
                index++
                if (index < source.length && source[index] in "+-") index++
                val exponentStart = index
                while (index < source.length && source[index].isDigit()) index++
                if (index == exponentStart) fail("Exposant JSON invalide")
            }
            val raw = source.substring(start, index)
            return if (decimal) {
                raw.toDoubleOrNull()?.takeIf { it.isFinite() }
                    ?: fail("Nombre JSON hors limites")
            } else {
                raw.toLongOrNull() ?: fail("Entier JSON hors limites")
            }
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
            if (!take(expected)) fail("Caractère '$expected' attendu")
        }

        private fun fail(message: String): Nothing =
            throw IOException("$message à la position $index.")
    }
}

internal fun Any?.jsonObject(label: String): Map<String, Any?> {
    val source = this as? Map<*, *>
        ?: throw IOException("$label doit être un objet JSON.")
    return source.entries.associate { (key, value) ->
        val stringKey = key as? String
            ?: throw IOException("$label contient une clé non textuelle.")
        stringKey to value
    }
}

internal fun Any?.jsonArray(label: String): List<Any?> =
    this as? List<Any?> ?: throw IOException("$label doit être un tableau JSON.")

internal fun Map<String, Any?>.requiredString(key: String): String =
    (this[key] as? String)?.takeIf { it.isNotBlank() }
        ?: throw IOException("Le champ $key est obligatoire.")

internal fun Map<String, Any?>.optionalString(key: String): String? = when (val value = this[key]) {
    null -> null
    is String -> value.takeIf { it.isNotBlank() }
    else -> throw IOException("Le champ $key doit être une chaîne.")
}

internal fun Map<String, Any?>.requiredInt(key: String): Int {
    val value = this[key] as? Long ?: throw IOException("Le champ $key doit être un entier.")
    return value.toInt().takeIf { it.toLong() == value }
        ?: throw IOException("Le champ $key est hors limites.")
}

internal fun Map<String, Any?>.optionalInt(key: String): Int? {
    if (this[key] == null) return null
    return requiredInt(key)
}

internal fun Map<String, Any?>.stringList(key: String): List<String> =
    (this[key] ?: emptyList<Any?>()).jsonArray(key).mapIndexed { index, value ->
        (value as? String)?.takeIf { it.isNotBlank() }
            ?: throw IOException("$key[$index] doit être une chaîne non vide.")
    }
