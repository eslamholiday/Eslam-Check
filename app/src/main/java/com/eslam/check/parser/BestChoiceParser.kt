package com.eslam.check.parser

import com.eslam.check.data.Currency
import com.eslam.check.data.ParsedPassenger
import com.eslam.check.data.ParsedTransaction
import com.eslam.check.data.TxType
import java.util.Locale

object BestChoiceParser {
    private val dateRegex = Regex("\\b20\\d{2}-\\d{2}-\\d{2}\\b")
    private val pnrRegex = Regex("PNR\\s*:\\s*([A-Z0-9]{4,10})", RegexOption.IGNORE_CASE)
    private val routeRegex = Regex("Route\\s*:\\s*([^\\n\\r]+)", RegexOption.IGNORE_CASE)
    private val discountRegex = Regex("Discount\\s*:\\s*([0-9,]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
    private val operationBeforeDate = Regex("\\b(\\d{3,})\\s+(20\\d{2}-\\d{2}-\\d{2})\\b")
    private val operationAfterDate = Regex("\\b(20\\d{2}-\\d{2}-\\d{2})\\s+(\\d{3,})\\b")
    private val moneyRegex = Regex("(?:\\$|د\\.?ع|ع\\.?د)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)")
    private val passportRegex = Regex("\\b[A-Z]?[0-9]{7,10}[A-Z]?\\b", RegexOption.IGNORE_CASE)

    fun parse(text: String, forcedCurrency: Currency? = null): List<ParsedTransaction> {
        val normalized = text.replace('\u00A0', ' ')
            .replace("\r", "\n")
            .replace(Regex("\n{2,}"), "\n")
        val lines = normalized.lines().map { it.trim() }.filter { it.isNotBlank() }
        val blocks = mutableListOf<String>()
        val current = mutableListOf<String>()

        fun looksLikeTxMarker(line: String): Boolean =
            line.contains("Sale Tickets", true) || line.contains("Visa Sale", true) || line.contains("Sale Visa", true) ||
            line.contains("TicketOperation", true) || line.contains("Receipt Voucher", true) ||
            line.contains("ID Voucher Receipt", true)

        fun isStart(line: String): Boolean =
            looksLikeTxMarker(line) && dateRegex.containsMatchIn(line) && operationNumber(line) != null

        for (line in lines) {
            if (isStart(line)) {
                if (current.isNotEmpty()) {
                    blocks += current.joinToString("\n")
                    current.clear()
                }
                current += line
            } else if (current.isNotEmpty()) {
                if (!line.contains("Page ", true) && !line.contains("best choise", true) && !line.contains("Helium", true)) {
                    current += line
                }
            }
        }
        if (current.isNotEmpty()) blocks += current.joinToString("\n")

        return blocks.mapNotNull { parseBlock(it, forcedCurrency) }
    }

    private fun parseBlock(block: String, forcedCurrency: Currency?): ParsedTransaction? {
        val type = when {
            block.contains("ID Voucher Receipt", true) || block.contains("Receipt Voucher", true) -> TxType.PAYMENT
            block.contains("TicketOperation New Refund", true) || block.contains("TicketOperation Refund", true) -> TxType.REFUND
            block.contains("TicketOperation New Change", true) || block.contains("TicketOperation Change", true) -> TxType.CHANGE
            (block.contains("Visa Sale", true) || block.contains("Sale Visa", true)) -> if (isZeroVisaCancellation(block)) TxType.VOID else TxType.VISA
            block.contains("Sale Tickets", true) -> {
                if (block.contains("Note", true) && block.contains("تغيير")) TxType.UNKNOWN else TxType.TICKET
            }
            else -> TxType.UNKNOWN
        }
        val currency = forcedCurrency ?: when {
            block.contains("$") -> Currency.USD
            block.contains("د.ع") || block.contains("ع.د") -> Currency.IQD
            else -> Currency.USD
        }
        val pnr = pnrRegex.find(block)?.groupValues?.getOrNull(1)?.uppercase(Locale.ROOT)
        val route = routeRegex.find(block)?.groupValues?.getOrNull(1)
            ?.substringBefore("\\")?.substringBefore("-")?.trim()?.takeIf { it.isNotBlank() }
        val discount = discountRegex.find(block)?.groupValues?.getOrNull(1)?.toNumber() ?: 0.0
        val op = operationNumber(block.lineSequence().firstOrNull().orEmpty())
        val amount = extractTransactionAmount(block, type, currency)
        val passengers = extractPassengers(block, type)
        return ParsedTransaction(
            operationNo = op,
            currency = currency,
            type = type,
            pnr = pnr,
            route = route,
            amount = amount,
            discount = discount,
            passengers = passengers,
            rawText = block,
            note = if (type == TxType.UNKNOWN) "تصنيف غير مؤكد" else null
        )
    }

    private fun operationNumber(text: String): String? {
        operationAfterDate.find(text)?.let { return it.groupValues[2] }
        operationBeforeDate.find(text)?.let { return it.groupValues[1] }
        val voucher = Regex("(?:ID\\s*)?Voucher(?:\\s+Receipt)?(?:\\s+ID)?\\s*(\\d{3,})", RegexOption.IGNORE_CASE).find(text)
        return voucher?.groupValues?.getOrNull(1)
    }

    private fun extractTransactionAmount(block: String, type: TxType, currency: Currency): Double {
        val firstLine = block.lineSequence().firstOrNull().orEmpty()
        val marker = when (type) {
            TxType.TICKET -> "Sale Tickets"
            TxType.VISA, TxType.VOID -> if (firstLine.contains("Sale Visa", true)) "Sale Visa" else "Visa Sale"
            TxType.CHANGE, TxType.REFUND -> "TicketOperation"
            TxType.PAYMENT -> when {
                firstLine.contains("ID Voucher Receipt", true) -> "ID Voucher Receipt"
                else -> "Receipt Voucher"
            }
            else -> ""
        }
        var tail = if (marker.isNotBlank()) firstLine.substringAfter(marker, "") else firstLine
        if (type == TxType.PAYMENT) {
            tail = tail.replaceFirst(Regex("^\\s*(?:ID\\s*)?\\d{3,}\\b"), " ")
        }
        val formattedMoney = Regex("(?:\\$|د\\.?ع|ع\\.?د)?\\s*([0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]+)?|[0-9]+\\.[0-9]{2})")
        val first = formattedMoney.find(tail)?.groupValues?.getOrNull(1)?.toNumberOrNull()
        if (first != null) return first
        if (type == TxType.VISA || type == TxType.VOID) {
            if (Regex("\\$\\s*0(?:\\.0+)?").containsMatchIn(firstLine)) return 0.0
        }
        return 0.0
    }

    private fun extractPassengers(block: String, type: TxType): List<ParsedPassenger> {
        val results = mutableListOf<ParsedPassenger>()
        if (type == TxType.TICKET) {
            block.lines().forEach { line ->
                if (line.contains("Adult", true) || line.contains("Child", true) || line.contains("Infant", true)) {
                    val clean = line.replace("-.", "").trim()
                    val parts = clean.split("\\").map { it.trim() }.filter { it.isNotBlank() }
                    val typeIdx = parts.indexOfFirst { it.equals("Adult", true) || it.equals("Child", true) || it.equals("Infant", true) }
                    if (typeIdx >= 1) {
                        val name = parts[typeIdx - 1].removePrefix(".").trim()
                        val amount = parts.getOrNull(typeIdx + 1)?.let { moneyRegex.find(it)?.groupValues?.getOrNull(1)?.toNumberOrNull() }
                        if (name.isNotBlank()) results += ParsedPassenger(name = name, amount = amount)
                    }
                }
            }
        } else if (type == TxType.VISA || type == TxType.VOID) {
            block.lines().forEach { line ->
                if (line.contains("Visa", true) || line.contains("فيزا")) {
                    val parts = line.split("\\").map { it.trim() }.filter { it.isNotBlank() }
                    if (parts.size >= 2) {
                        val possiblePassport = parts.firstNotNullOfOrNull { passportRegex.find(it)?.value }
                        val nameCandidate = parts
                            .filterNot { it.contains("VISA", true) || it.contains("فيزا") }
                            .firstOrNull { it.any(Char::isLetter) && passportRegex.find(it) == null }
                        val amount = parts.asReversed().firstNotNullOfOrNull { moneyRegex.find(it)?.groupValues?.getOrNull(1)?.toNumberOrNull() }
                        if (!nameCandidate.isNullOrBlank()) results += ParsedPassenger(nameCandidate, possiblePassport, amount)
                    }
                }
            }
        } else if (type == TxType.CHANGE || type == TxType.REFUND) {
            val name = Regex("Name\\s*:\\s*([^\\n]+?)(?:E-TICKET|E\\s*-?TICKET|$)", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.getOrNull(1)?.trim()
            if (!name.isNullOrBlank()) results += ParsedPassenger(name)
        }
        return results.distinctBy { it.name.lowercase() }
    }

    private fun isZeroVisaCancellation(block: String): Boolean {
        if (!block.contains("Visa Sale", true) && !block.contains("Sale Visa", true)) return false
        val nums = Regex("\\$\\s*0(?:\\.0+)?").findAll(block).count()
        return nums > 0 || (block.contains("الغاء") || block.contains("إلغاء"))
    }

    private fun String.toNumber(): Double = replace(",", "").trim().toDoubleOrNull() ?: 0.0
    private fun String.toNumberOrNull(): Double? = replace(",", "").replace("$", "").trim().toDoubleOrNull()
}
