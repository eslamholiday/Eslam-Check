package com.eslam.check.util

/** Only explicitly edited passenger IDs are returned. An empty field clears that passenger alone. */
object PassengerFareEdits {
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
