package com.eslam.check.util

import com.eslam.check.data.*

object OperationFilters {
    fun normalize(value: String): String = value.map { c ->
        if (c.isDigit()) Character.digit(c, 10).digitToChar() else c
    }.joinToString("").trim().lowercase().replace("٬", "").replace(",", "").replace("٫", ".")

    fun matches(tx: Transaction, query: String, passengerText: String = ""): Boolean {
        val q = normalize(query)
        if (q.isBlank()) return true
        val fields = listOf(tx.pnr, tx.operationNo, tx.airline, tx.route, tx.visaCountry, tx.note,
            tx.transactionDate, tx.currency.name, tx.type.name, passengerText, tx.amount.toString(), tx.discount.toString())
        val text = normalize(fields.filterNotNull().joinToString(" "))
        val compact = text.filterNot(Char::isWhitespace)
        return q.split(Regex("\\s+")).all { it in text || it in compact }
    }

    fun inPeriod(tx: Transaction, from: String, to: String): Boolean =
        RulePeriods.includes(from, to, tx.transactionDate)

    fun sorted(rows: List<Transaction>, newest: Boolean): List<Transaction> = rows.sortedWith(
        compareBy<Transaction> { if (RulePeriods.date(it.transactionDate) == null) 1 else 0 }
            .thenBy { (RulePeriods.date(it.transactionDate)?.toEpochDay() ?: 0L) * if (newest) -1 else 1 }
            .thenByDescending { it.importedAt }.thenBy { it.id })

    // Actual statement discount, not the predicted commission or payment amount.
    fun ticketProfit(rows: List<Transaction>, currency: Currency): Double = rows
        .filter { it.type == TxType.TICKET && it.currency == currency }.sumOf { it.discount }
}
