package com.eslam.check.util

import com.eslam.check.data.*
import org.junit.Assert.*
import org.junit.Test

class OperationFiltersTest {
    @Test fun ticketProfitUsesActualDiscountOnlyAndSeparatesCurrencies() {
        val ticket = Transaction("t", type = TxType.TICKET, discount = 10.0, amount = 900.0)
        val rows = listOf(ticket, ticket.copy(id = "i", currency = Currency.IQD, discount = 15000.0),
            ticket.copy(id = "v", type = TxType.VISA, discount = 99.0), ticket.copy(id = "p", type = TxType.PAYMENT, discount = 999.0),
            ticket.copy(id = "r", type = TxType.REFUND, discount = 500.0))
        assertEquals(10.0, OperationFilters.ticketProfit(rows, Currency.USD), 0.001)
        assertEquals(15000.0, OperationFilters.ticketProfit(rows, Currency.IQD), 0.001)
    }
    @Test fun inclusiveDatesExcludeMissingDatesWhenFiltered() {
        val tx = Transaction("t", transactionDate = "2026-10-05")
        assertTrue(OperationFilters.inPeriod(tx, "2026-10-05", "2026-10-05"))
        assertFalse(OperationFilters.inPeriod(tx.copy(transactionDate = null), "2026-10-05", ""))
        assertFalse(OperationFilters.inPeriod(tx, "2026-10-06", "2026-10-01"))
        assertTrue(OperationFilters.inPeriod(tx.copy(transactionDate = null), "", ""))
    }
    @Test fun amountArabicDigitsPassengerAndPnrCanBeCombined() {
        val tx = Transaction("t", pnr = "AB12CD", amount = 1200.5, transactionDate = "2026-10-05")
        assertTrue(OperationFilters.matches(tx, "١٬٢٠٠٫٥"))
        assertTrue(OperationFilters.matches(tx, "ab12 eslam", "Eslam Ali"))
        assertTrue(OperationFilters.matches(tx, "2026-10-05"))
        assertFalse(OperationFilters.matches(tx, "different"))
        assertTrue(OperationFilters.matches(tx, ""))
    }
    @Test fun sortingUsesOperationDateAndLeavesUndatedLast() {
        val old = Transaction("old", transactionDate = "2026-01-01")
        val new = Transaction("new", transactionDate = "05/10/2026")
        val missing = Transaction("missing")
        assertEquals(listOf("new", "old", "missing"), OperationFilters.sorted(listOf(old, missing, new), true).map { it.id })
        assertEquals(listOf("old", "new", "missing"), OperationFilters.sorted(listOf(old, missing, new), false).map { it.id })
    }
}
