package com.eslam.check.util
import com.eslam.check.data.*
import org.junit.Assert.*
import org.junit.Test
class PhoneNumbersTest {
    @Test fun internationalAndIraqiNumbers() {
        assertEquals("+971501234567", normalizePhoneOrNull("00971 50-123-4567"))
        assertEquals("+447700900123", normalizePhoneOrNull("+44 (7700) 900123"))
        assertEquals("07701234567", normalizePhoneOrNull("+964 770 123 4567"))
        assertEquals("07701234567", normalizePhoneOrNull("٠٧٧٠١٢٣٤٥٦٧"))
        assertEquals("971501234567", iraqPhoneForWhatsApp("00971 50 1234567"))
        assertEquals("9647701234567", iraqPhoneForWhatsApp("07701234567"))
        assertNull(normalizePhoneOrNull("call +971501234567"))
        assertNull(normalizePhoneOrNull("+123"))
        assertNull(normalizeIraqPhoneForStorage(""))
    }
}
