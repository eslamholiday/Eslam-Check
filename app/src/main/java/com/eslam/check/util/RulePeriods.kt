package com.eslam.check.util

import com.eslam.check.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** All bounds include the specified day. Always uses issue date from the statement. */
object RulePeriods {
    fun date(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        for (pattern in listOf("uuuu-MM-dd", "dd-MM-uuuu", "dd/MM/uuuu", "uuuu/MM/dd")) {
            try { return LocalDate.parse(value.trim(), DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT)) }
            catch (_: Exception) { }
        }
        return null
    }
    fun validRange(from: String?, to: String?): Boolean =
        (from.isNullOrBlank() || date(from) != null) && (to.isNullOrBlank() || date(to) != null) &&
        (date(from) == null || date(to) == null || !date(from)!!.isAfter(date(to)))

    fun includes(from: String?, to: String?, issued: String?): Boolean {
        if (!validRange(from, to)) return false
        if (from.isNullOrBlank() && to.isNullOrBlank()) return true
        val day = date(issued) ?: return false
        return (date(from)?.let { !day.isBefore(it) } ?: true) && (date(to)?.let { !day.isAfter(it) } ?: true)
    }
    private fun dated(from: String?, to: String?) = !from.isNullOrBlank() || !to.isNullOrBlank()
    fun overlaps(a: String?, b: String?, c: String?, d: String?): Boolean =
        (date(b) == null || date(c) == null || !date(b)!!.isBefore(date(c))) &&
        (date(d) == null || date(a) == null || !date(d)!!.isBefore(date(a)))
    private fun key(s: String?) = s.orEmpty().trim().uppercase()
    fun routeParts(route: String?) = route.orEmpty().uppercase().split(Regex("[\\s/→>–—-]+")).filter { it.isNotBlank() }
    fun reverse(route: String?): Boolean? {
        val p = routeParts(route)
        if (p.size < 2) return null
        val iraq = setOf("BGW", "BSR", "NJF", "EBL", "ISU", "KIK")
        return p.first() !in iraq && p.drop(1).any { it in iraq }
    }
    fun direction(rule: CommissionRule) = if (rule.reverseOnly) "REVERSE" else rule.direction
    private fun destinationMatches(destination: String?, route: String?): Boolean {
        if (destination.isNullOrBlank()) return true
        val wanted = routeParts(destination)
        val actual = routeParts(route)
        return if (wanted.size > 1) wanted == actual else wanted.singleOrNull() in actual.drop(1)
    }
    private fun score(r: CommissionRule) = (if (r.destination.isNullOrBlank()) 0 else 4) +
        (if (direction(r) == "ANY") 0 else 2) + (if (r.currency == null) 0 else 1)
    fun commission(rules: List<CommissionRule>, airline: String?, route: String?, currency: Currency, issued: String?): CommissionRule? {
        val rev = reverse(route)
        val scoped = rules.filter { it.active && it.airline.equals(airline, true) &&
            (it.currency == null || it.currency == currency) && destinationMatches(it.destination, route) &&
            (direction(it) == "ANY" || (rev != null && direction(it) == if (rev) "REVERSE" else "NORMAL")) }
        val candidates = scoped.filter { includes(it.effectiveFrom, it.effectiveTo, issued) &&
            (dated(it.effectiveFrom, it.effectiveTo) || scoped.none { other -> score(other) == score(it) && dated(other.effectiveFrom, other.effectiveTo) }) }
        val best = candidates.maxOfOrNull(::score) ?: return null
        return candidates.filter { score(it) == best }.singleOrNull() // Never silently resolve ambiguous overlaps.
    }
    fun conflicts(a: CommissionRule, b: CommissionRule): Boolean = a.id != b.id && a.active && b.active &&
        key(a.airline) == key(b.airline) && key(a.destination) == key(b.destination) && direction(a) == direction(b) && a.currency == b.currency &&
        dated(a.effectiveFrom, a.effectiveTo) == dated(b.effectiveFrom, b.effectiveTo) && overlaps(a.effectiveFrom, a.effectiveTo, b.effectiveFrom, b.effectiveTo)
    fun conflicts(a: VisaPriceRule, b: VisaPriceRule): Boolean = a.id != b.id && a.active && b.active && a.price != null && b.price != null &&
        key(a.country) == key(b.country) && key(a.visaType) == key(b.visaType) && a.currency == b.currency &&
        dated(a.effectiveFrom, a.effectiveTo) == dated(b.effectiveFrom, b.effectiveTo) && overlaps(a.effectiveFrom, a.effectiveTo, b.effectiveFrom, b.effectiveTo)
    fun visa(rules: List<VisaPriceRule>, country: String, type: String?, currency: Currency, issued: String?): VisaPriceRule? {
        val scoped = rules.filter { it.active && it.price != null && it.country.equals(country, true) && it.currency == currency &&
            (it.visaType.isNullOrBlank() || type.orEmpty().trim().equals(it.visaType.trim(), true)) }
        val candidates = scoped.filter { includes(it.effectiveFrom, it.effectiveTo, issued) &&
            (dated(it.effectiveFrom, it.effectiveTo) || scoped.none { other -> key(other.visaType) == key(it.visaType) && dated(other.effectiveFrom, other.effectiveTo) }) }
        val specific = candidates.filter { !it.visaType.isNullOrBlank() }
        return (specific.ifEmpty { candidates }).singleOrNull()
    }
    fun label(from: String?, to: String?) = "من " + (from?.takeIf { it.isNotBlank() } ?: "كل التواريخ") + " إلى " + (to?.takeIf { it.isNotBlank() } ?: "بدون نهاية")
}
