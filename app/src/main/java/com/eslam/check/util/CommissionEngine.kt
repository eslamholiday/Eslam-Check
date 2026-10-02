package com.eslam.check.util

import com.eslam.check.data.CommissionRule
import com.eslam.check.data.RuleKind
import kotlin.math.abs

data class CommissionResult(
    val expected: Double?,
    val difference: Double?,
    val isWithinTolerance: Boolean,
    val explanation: String
)

object CommissionEngine {
    fun calculate(
        baseFare: Double?,
        actualDiscount: Double,
        passengerCount: Int,
        rule: CommissionRule?,
        tolerance: Double
    ): CommissionResult {
        if (rule == null) {
            return CommissionResult(null, null, false, "لا توجد قاعدة عمولة محددة")
        }
        val expected = when (rule.kind) {
            RuleKind.PERCENT_BASE -> baseFare?.times(rule.value / 100.0)
            RuleKind.FIXED_PER_PASSENGER -> rule.value * passengerCount.coerceAtLeast(1)
            RuleKind.PRIVATE_MANUAL -> null
            RuleKind.NONE -> 0.0
        }
        if (expected == null) {
            return CommissionResult(null, null, false, "العمولة تحتاج إدخالًا يدويًا")
        }
        val diff = actualDiscount - expected
        return CommissionResult(
            expected = expected,
            difference = diff,
            isWithinTolerance = abs(diff) <= tolerance,
            explanation = if (abs(diff) <= tolerance) "مطابق ضمن هامش السماح" else "يوجد فرق يحتاج مراجعة"
        )
    }
}
