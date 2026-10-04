package com.eslam.check.util

/** Only explicitly edited passenger IDs are returned. An empty field clears that passenger alone. */
object PassengerFareEdits {
    fun copyWithinClass(drafts: Map<String, String>, classes: Map<String, String?>, id: String, text: String): Map<String, String> {
        val category = classes[id]?.trim()?.uppercase()
        val ids = if (category in setOf("ADT", "CHD", "INF") && runCatching { parse(text) }.getOrNull() != null)
            classes.filterValues { it?.trim()?.uppercase() == category }.keys else setOf(id)
        return drafts + ids.associateWith { text }
    }
    fun parse(text: String): Double? {
        if (text.isBlank()) return null
        val clean = text.trim().map { ch ->
            when {
                ch in '٠'..'٩' -> ('0'.code + ch.code - '٠'.code).toChar()
                ch in '۰'..'۹' -> ('0'.code + ch.code - '۰'.code).toChar()
                ch == '٫' -> '.'
                else -> ch
            }
        }.joinToString("").replace(",", "").replace("٬", "")
        val value = clean.toDoubleOrNull()
        require(value != null && value.isFinite() && value >= 0) { "أدخل سعرًا أساسيًا صحيحًا غير سالب لكل مسافر" }
        return value
    }
    fun values(drafts: Map<String, String>): Map<String, Double?> = drafts.mapValues { parse(it.value) }
}
