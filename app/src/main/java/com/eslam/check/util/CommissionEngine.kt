package com.eslam.check.util

import com.eslam.check.data.CommissionRule
import com.eslam.check.data.RuleKind
import com.eslam.check.data.TxPassengerDetail
import kotlin.math.abs

data class PassengerCommission(
    val passengerId: String,
    val baseFare: Double?,
    val expectedCommission: Double?
)

data class CommissionResult(
    val expectedCommission: Double?,
    val expectedSettlement: Double?,
    val difference: Double?,
    val isWithinTolerance: Boolean,
    val needsInput: Boolean,
    val explanation: String,
    val rows: List<PassengerCommission> = emptyList()
)

object CommissionEngine {
    fun calculate(
        passengers: List<TxPassengerDetail>,
        actualSettlement: Double,
        actualDiscount: Double,
        referenceTotal: Double?,
        rule: CommissionRule?,
        tolerance: Double,
        route: String?
    ): CommissionResult {
        if (rule == null) {
            return CommissionResult(null, null, null, false, true, "اختر شركة الطيران أولًا")
        }

        val count = passengers.size.coerceAtLeast(1)
        return when (rule.kind) {
            RuleKind.PERCENT_BASE -> {
                val rows = passengers.map {
                    PassengerCommission(
                        passengerId = it.passenger.id,
                        baseFare = it.baseFare,
                        expectedCommission = it.baseFare?.times(rule.value / 100.0)
                    )
                }
                if (rows.isEmpty() || rows.any { it.baseFare == null }) {
                    CommissionResult(
                        expectedCommission = rows.mapNotNull { it.expectedCommission }.takeIf { it.isNotEmpty() }?.sum(),
                        expectedSettlement = null,
                        difference = null,
                        isWithinTolerance = false,
                        needsInput = true,
                        explanation = "أدخل Base Fare لكل مسافر حتى تكتمل مراجعة العمولة",
                        rows = rows
                    )
                } else {
                    val expected = rows.sumOf { it.expectedCommission ?: 0.0 }
                    val gross = passengers.mapNotNull { it.amount }.sum()
                    val settlement = if (gross > 0.0) gross - expected else null
                    val discountDiff = actualDiscount - expected
                    val settlementDiff = settlement?.let { actualSettlement - it }
                    val within = abs(discountDiff) <= tolerance && (settlementDiff == null || abs(settlementDiff) <= tolerance)
                    CommissionResult(
                        expectedCommission = expected,
                        expectedSettlement = settlement,
                        difference = discountDiff,
                        isWithinTolerance = within,
                        needsInput = false,
                        explanation = if (within) "العمولة والتسديد مطابقان ضمن هامش السماح" else "يوجد فرق بين العمولة المتوقعة وDiscount المصدر",
                        rows = rows
                    )
                }
            }

            RuleKind.FIXED_PER_PASSENGER -> {
                val isRoundTrip = route.orEmpty().split("-", " ").filter { it.isNotBlank() }.let {
                    it.size >= 3 && it.first().equals(it.last(), true)
                }
                val feeEach = if (isRoundTrip) rule.roundTripValue ?: rule.value else rule.value
                val totalFee = feeEach * count
                val expectedSettlement = referenceTotal?.plus(totalFee)
                if (referenceTotal == null) {
                    CommissionResult(
                        expectedCommission = totalFee,
                        expectedSettlement = null,
                        difference = null,
                        isWithinTolerance = false,
                        needsInput = true,
                        explanation = "رسم الإصدار ${format(feeEach)} لكل مسافر. أدخل سعر التذاكر قبل رسم الإصدار للتدقيق."
                    )
                } else {
                    val diff = actualSettlement - expectedSettlement!!
                    CommissionResult(
                        expectedCommission = totalFee,
                        expectedSettlement = expectedSettlement,
                        difference = diff,
                        isWithinTolerance = abs(diff) <= tolerance,
                        needsInput = false,
                        explanation = if (abs(diff) <= tolerance) "التسديد مطابق مع رسم الإصدار" else "يوجد فرق في التسديد بعد رسم الإصدار"
                    )
                }
            }

            RuleKind.NONE -> {
                val gross = passengers.mapNotNull { it.amount }.sum().takeIf { it > 0.0 } ?: referenceTotal
                val diff = gross?.let { actualSettlement - it }
                CommissionResult(
                    expectedCommission = 0.0,
                    expectedSettlement = gross,
                    difference = diff,
                    isWithinTolerance = diff == null || abs(diff) <= tolerance,
                    needsInput = false,
                    explanation = if (diff == null || abs(diff) <= tolerance) "بدون عمولة" else "يوجد فرق في التسديد"
                )
            }

            RuleKind.PRIVATE_MANUAL -> CommissionResult(
                expectedCommission = null,
                expectedSettlement = null,
                difference = null,
                isWithinTolerance = false,
                needsInput = true,
                explanation = rule.note?.takeIf { it.isNotBlank() } ?: "العمولة خاصة/متغيرة وتحتاج اختيار القاعدة الصحيحة"
            )
        }
    }

    private fun format(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.2f", value)
}
