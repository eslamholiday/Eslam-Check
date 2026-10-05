package com.eslam.check.util

import com.eslam.check.data.*
import org.junit.Assert.*
import org.junit.Test

class FareMemoryMatcherTest {
    private fun sample(id: String, total: Double, base: Double = 80.0, day: String? = "2026-10-01") =
        FareMemory(id, "Iraqi Airways", "BGW-AMM-BGW", Currency.USD, "ADT", total, base, lastSeenDate = day)
    private fun choose(rows: List<FareMemory>, total: Double = 100.0, day: String? = "2026-10-05") =
        FareMemoryMatcher.choose(rows, total, null, day, 1.0)?.memory?.id
    @Test fun acceptsOneDollarButNotBeyond() {
        assertEquals("edge", choose(listOf(sample("edge", 101.0))))
        assertNull(choose(listOf(sample("outside", 101.01))))
        assertEquals("lower", choose(listOf(sample("lower", 99.0))))
    }
    @Test fun exactAndClosestPricesWinBeforeRecency() {
        assertEquals("exact", choose(listOf(sample("new", 100.5, day = "2026-10-05"), sample("exact", 100.0, day = "2026-01-01"))))
        assertEquals("closer", choose(listOf(sample("farther", 101.0), sample("closer", 100.2))))
    }
    @Test fun closestDateWinsOverFrequencyForDifferentBaseFares() {
        assertEquals("recent", choose(listOf(sample("old", 100.0, 70.0, "2026-01-01").copy(sampleCount = 900), sample("recent", 100.0, 85.0))))
        assertEquals("old", choose(listOf(sample("old", 100.0, 70.0, "2026-01-01"), sample("recent", 100.0, 85.0)), day = "2026-01-02"))
    }
    @Test fun dateTieAndMissingIssueDateChooseLatest() {
        val rows = listOf(sample("old", 100.0, day = "2026-10-01"), sample("new", 100.0, day = "2026-10-03"))
        assertEquals("new", choose(rows, day = "2026-10-02"))
        assertEquals("new", choose(rows, day = null))
    }
    @Test fun respectsExplicitPinAndDeletion() {
        assertEquals("pinned", choose(listOf(sample("pinned", 100.5).copy(pinned = true), sample("exact", 100.0))))
        assertNull(choose(listOf(sample("blocked", 100.0).copy(blocked = true))))
    }
    @Test fun knownBaseIsAdditionalEvidence() {
        val rows = listOf(sample("a", 100.0, 50.0), sample("b", 100.0, 80.0))
        assertEquals("b", FareMemoryMatcher.choose(rows, 100.0, 79.0, "2026-10-05", 1.0)?.memory?.id)
    }
}
