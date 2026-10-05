package com.eslam.check.util

import com.eslam.check.data.*
import kotlin.math.abs

/** Currency, airline, directed route and passenger class are mandatory evidence. */
object FareMemoryMatcher {
    fun choose(candidates: List<FareMemory>, total: Double, base: Double?, issued: String?, tolerance: Double): FareMemoryMatch? {
        if (!total.isFinite() || !tolerance.isFinite()) return null
        val day = RulePeriods.date(issued)?.toEpochDay()
        fun distance(m: FareMemory): Long {
            val sample = RulePeriods.date(m.lastSeenDate)?.toEpochDay() ?: return Long.MAX_VALUE
            return day?.let { abs(it - sample) } ?: 0L
        }
        val best = candidates.filter { !it.blocked && it.totalAmount.isFinite() && it.baseFare.isFinite() &&
            abs(it.totalAmount - total) <= tolerance + 0.000001 }
            .sortedWith(compareByDescending<FareMemory> { it.pinned }
                .thenBy { abs(it.totalAmount - total) }
                .thenBy { if (base != null && base.isFinite()) abs(it.baseFare - base) else 0.0 }
                .thenBy { distance(it) }
                .thenByDescending { RulePeriods.date(it.lastSeenDate)?.toEpochDay() ?: Long.MIN_VALUE }
                .thenByDescending { it.updatedAt }
                .thenByDescending { it.sampleCount }
                .thenBy { it.id }).firstOrNull() ?: return null
        return FareMemoryMatch(best, abs(best.totalAmount - total))
    }
}
