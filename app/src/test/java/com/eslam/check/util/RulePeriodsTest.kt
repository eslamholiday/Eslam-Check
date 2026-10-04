package com.eslam.check.util

import com.eslam.check.data.*
import org.junit.Assert.*
import org.junit.Test

class RulePeriodsTest {
    private fun rule(id: String, from: String?, to: String?, value: Double = 5.0) = CommissionRule(
        id, "Iraqi Airways", RuleKind.PERCENT_BASE, value, effectiveFrom = from, effectiveTo = to)
    private fun select(rules: List<CommissionRule>, day: String?, route: String? = "BGW-AMM-BGW", currency: Currency = Currency.IQD) =
        RulePeriods.commission(rules, "Iraqi Airways", route, currency, day)
    @Test fun switchesAtInclusiveBoundary() {
        val a = rule("a", "2026-01-16", "2026-06-15")
        val b = rule("b", "2026-06-16", "2026-12-31", 7.0)
        assertEquals("a", select(listOf(a,b), "2026-06-15")?.id)
        assertEquals("b", select(listOf(a,b), "16-06-2026")?.id)
        assertNull(select(listOf(a,b), "2027-01-01"))
    }
    @Test fun supportsOneDayAndOpenEnd() {
        assertTrue(RulePeriods.includes("2026-06-16", "2026-06-16", "2026-06-16"))
        assertFalse(RulePeriods.includes("2026-06-16", "2026-06-16", "2026-06-17"))
        assertTrue(RulePeriods.includes("2026-06-16", null, "2030-01-01"))
    }
    @Test fun validatesRealDatesAndMissingDate() {
        assertFalse(RulePeriods.validRange("2026-02-30", null))
        assertFalse(RulePeriods.validRange("2026-06-17", "2026-06-16"))
        assertNull(select(listOf(rule("a", "2026-01-01", null)), null))
    }
    @Test fun rejectsOverlapButAllowsAdjacent() {
        val a = rule("a", "2026-01-01", "2026-06-16")
        assertTrue(RulePeriods.conflicts(a, rule("b", "2026-06-16", null)))
        assertFalse(RulePeriods.conflicts(a, rule("b", "2026-06-17", null)))
        assertNull(select(listOf(a, rule("b", "2026-06-16", null)), "2026-06-16"))
    }
    @Test fun destinationDirectionAndCurrencyAreRespected() {
        val general = rule("general", null, null)
        val specific = rule("specific", "2026-01-01", null).copy(destination = "AMM", direction = "NORMAL", currency = Currency.IQD)
        assertEquals("specific", select(listOf(general, specific), "2026-02-01")?.id)
        assertEquals("general", select(listOf(general, specific), "2026-02-01", "AMM-BGW")?.id)
        assertNull(select(listOf(specific), "2026-02-01", currency = Currency.USD))
        assertNull(select(listOf(specific), "2026-02-01", route = null))
        assertNull(select(listOf(specific.copy(direction = "REVERSE")), "2026-02-01"))
    }
    @Test fun datedRulesDoNotFallBackToStaleTimelessPrice() {
        assertNull(select(listOf(rule("legacy", null, null), rule("dated", "2026-01-01", "2026-06-16")), "2026-07-01"))
    }
    @Test fun visaUsesIssueDateTypeAndCurrency() {
        val a = VisaPriceRule("a", "UAE", price = 75.0, effectiveFrom = "2026-01-01", effectiveTo = "2026-06-15")
        val b = a.copy(id = "b", price = 80.0, effectiveFrom = "2026-06-16", effectiveTo = null)
        assertEquals(80.0, RulePeriods.visa(listOf(a,b), "UAE", null, Currency.USD, "2026-06-16")?.price)
        assertNull(RulePeriods.visa(listOf(a,b), "UAE", null, Currency.IQD, "2026-06-16"))
        assertNull(RulePeriods.visa(listOf(a,b), "UAE", null, Currency.USD, null))
        val special = b.copy(id = "special", visaType = "Tourist", price = 95.0)
        assertEquals("special", RulePeriods.visa(listOf(b,special), "UAE", "Tourist", Currency.USD, "2026-07-01")?.id)
        assertTrue(RulePeriods.conflicts(a, b.copy(effectiveFrom = "2026-06-15")))
    }
}
