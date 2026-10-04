package com.eslam.check.util

import org.junit.Assert.*
import org.junit.Test

class PassengerFareEditsTest {
    @Test fun copiesTwoAdultsTwoChildrenAndTwoInfantsSeparately() {
        val classes = mapOf("a1" to "ADT", "a2" to "ADT", "c1" to "CHD", "c2" to "CHD", "i1" to "INF", "i2" to "INF")
        var drafts = PassengerFareEdits.copyWithinClass(emptyMap(), classes, "a1", "444840")
        drafts = PassengerFareEdits.copyWithinClass(drafts, classes, "c1", "300000")
        drafts = PassengerFareEdits.copyWithinClass(drafts, classes, "i1", "45000")
        val saved = PassengerFareEdits.values(drafts)
        assertEquals(444840.0, saved["a2"])
        assertEquals(300000.0, saved["c2"])
        assertEquals(45000.0, saved["i2"])
        assertEquals(6, saved.size)
    }
    @Test fun unknownCategoryNeverCopiesToAnotherPassenger() {
        val result = PassengerFareEdits.copyWithinClass(emptyMap(), mapOf("x" to null, "y" to null), "x", "100")
        assertEquals(setOf("x"), result.keys)
    }
    @Test fun adultChildAndInfantRemainIndependent() {
        val saved = PassengerFareEdits.values(mapOf("ADT-1" to "444840", "CHD-1" to "300000", "INF-1" to "45000"))
        assertEquals(444840.0, saved["ADT-1"])
        assertEquals(300000.0, saved["CHD-1"])
        assertEquals(45000.0, saved["INF-1"])
    }
    @Test fun editingOneChildDoesNotWriteOtherPassengers() {
        val saved = PassengerFareEdits.values(mapOf("CHD-1" to "250000"))
        assertEquals(setOf("CHD-1"), saved.keys)
        assertFalse(saved.containsKey("ADT-1"))
        assertFalse(saved.containsKey("INF-1"))
    }
    @Test fun clearingIsExplicitAndDoesNotCopyAdultFare() {
        val saved = PassengerFareEdits.values(mapOf("CHD-1" to "", "ADT-1" to "444840"))
        assertTrue(saved.containsKey("CHD-1"))
        assertNull(saved["CHD-1"])
        assertEquals(444840.0, saved["ADT-1"])
    }
    @Test fun supportsArabicDigitsAndDecimalValues() {
        assertEquals(300000.5, PassengerFareEdits.parse("٣٠٠٬٠٠٠٫٥"))
        assertEquals(0.0, PassengerFareEdits.parse("0"))
    }
    @Test fun invalidEditRejectsTheWholeBatch() {
        for (bad in listOf("-1", "NaN", "Infinity", "wrong", ".")) {
            try {
                PassengerFareEdits.values(mapOf("ADT-1" to "444840", "CHD-1" to bad))
                fail("Invalid passenger value accepted: $bad")
            } catch (_: IllegalArgumentException) { }
        }
    }
}
