package com.example.security

/**
 * Masks sensitive personal identifiers (phone numbers, ID/passport numbers, bank accounts,
 * and explicit party names) before sending OCR text to cloud AI, and restores them locally.
 */
object PiiMasker {
    data class MaskResult(
        val maskedText: String,
        val replacements: Map<String, String>,
    )

    private val ethiopianPhoneRegex = Regex("""(?:\+251|0)(?:9|7)\d{8}\b""")
    private val longNumberRegex = Regex("""\b\d{10,16}\b""")
    private val idPatternRegex = Regex("""(?i)(?:መ/ቁ|መታወቂያ|ID|TIN|Passport)\s*[:፡\-]?\s*([A-Za-z0-9/\-]{5,18})""")

    fun mask(
        text: String,
        knownPartyNames: List<String> = emptyList(),
        existingMap: Map<String, String> = emptyMap(),
    ): MaskResult {
        if (text.isBlank()) return MaskResult(text, existingMap)
        val replacements = existingMap.toMutableMap()
        val reverseLookup = existingMap.entries.associate { (k, v) -> v to k }.toMutableMap()
        var current = text
        var nameCounter = replacements.keys.count { it.startsWith("[ስም-") } + 1
        var phoneCounter = replacements.keys.count { it.startsWith("[ስልክ-") } + 1
        var idCounter = replacements.keys.count { it.startsWith("[መታወቂያ-") } + 1

        // 1. Mask known party names (longest first to avoid partial collisions)
        knownPartyNames
            .map { it.trim() }
            .filter { it.length >= 4 }
            .distinct()
            .sortedByDescending { it.length }
            .forEach { name ->
                if (current.contains(name)) {
                    val token = reverseLookup[name] ?: "[ስም-${nameCounter++}]".also {
                        replacements[it] = name
                        reverseLookup[name] = it
                    }
                    current = current.replace(name, token)
                }
            }

        // 2. Mask Ethiopian phone numbers
        current = ethiopianPhoneRegex.replace(current) { match ->
            val value = match.value
            reverseLookup[value] ?: "[ስልክ-${phoneCounter++}]".also {
                replacements[it] = value
                reverseLookup[value] = it
            }
        }

        // 3. Mask long numeric IDs / bank account numbers (10-16 digits)
        current = longNumberRegex.replace(current) { match ->
            val value = match.value
            reverseLookup[value] ?: "[መታወቂያ-${idCounter++}]".also {
                replacements[it] = value
                reverseLookup[value] = it
            }
        }

        // 4. Mask labeled ID strings
        current = idPatternRegex.replace(current) { match ->
            val full = match.value
            val captured = match.groupValues.getOrNull(1) ?: return@replace full
            if (captured.startsWith("[")) return@replace full
            val token = reverseLookup[captured] ?: "[መታወቂያ-${idCounter++}]".also {
                replacements[it] = captured
                reverseLookup[captured] = it
            }
            full.replace(captured, token)
        }

        return MaskResult(current, replacements)
    }

    fun unmask(text: String, replacements: Map<String, String>): String {
        if (text.isBlank() || replacements.isEmpty()) return text
        var restored = text
        replacements.forEach { (token, original) ->
            restored = restored.replace(token, original)
        }
        return restored
    }
}
