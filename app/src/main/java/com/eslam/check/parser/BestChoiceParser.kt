package com.eslam.check.parser

import com.eslam.check.data.Currency
import com.eslam.check.data.ParsedPassenger
import com.eslam.check.data.ParsedTransaction
import com.eslam.check.data.TxType
import java.util.Locale

object BestChoiceParser {
    private val markerRegex = Regex(
        "(Sale\\s+Tickets|Sale\\s+Visa|Visa\\s+Sale|TicketOperation\\s+(?:New\\s+)?(?:Change|Refund)|ID\\s+Voucher\\s+Receipt|Receipt\\s+Voucher(?:\\s+ID)?)",
        RegexOption.IGNORE_CASE
    )

    private val pnrRegex = Regex("PNR\\s*:\\s*([A-Z0-9]{4,10})", RegexOption.IGNORE_CASE)
    private val routeRegex = Regex("Route\\s*:\\s*([^\\n\\r]+)", RegexOption.IGNORE_CASE)
    private val discountRegex = Regex("Discount\\s*:\\s*([0-9,]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
    private val dateOpRegex = Regex("(20\\d{2}-\\d{2}-\\d{2})\\s+(\\d{3,})")
    private val voucherOpRegex = Regex(
        "(?:Receipt\\s+Voucher\\s+ID|ID\\s+Voucher\\s+Receipt|Receipt\\s+Voucher)\\s*(\\d+?)(?=20\\d{2}-\\d{2}-\\d{2}|\\s|$)",
        RegexOption.IGNORE_CASE
    )
    private val simpleMoneyRegex = Regex("([0-9][0-9,]*(?:\\.[0-9]+)?)")
    private val passportRegex = Regex("\\b[A-Z]?[0-9]{7,10}[A-Z]?\\b", RegexOption.IGNORE_CASE)

    fun parse(text: String, forcedCurrency: Currency? = null): List<ParsedTransaction> {
        val normalized = text
            .replace('\u00A0', ' ')
            .replace("\r", "\n")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")

        val markers = markerRegex.findAll(normalized).toList()
        if (markers.isEmpty()) return emptyList()

        val blocks = markers.mapIndexed { index, match ->
            val lineStart = normalized.lastIndexOf('\n', match.range.first)
                .let { if (it < 0) 0 else it + 1 }

            val nextStart = if (index + 1 < markers.size) {
                normalized.lastIndexOf('\n', markers[index + 1].range.first)
                    .let { if (it < 0) markers[index + 1].range.first else it + 1 }
            } else {
                normalized.length
            }

            normalized.substring(lineStart, nextStart.coerceAtLeast(lineStart)).trim()
        }

        return blocks.mapNotNull { parseBlock(it, forcedCurrency) }
    }

    private fun parseBlock(block: String, forcedCurrency: Currency?): ParsedTransaction? {
        val marker = markerRegex.find(block)?.value ?: return null

        val type = when {
            marker.contains("Receipt Voucher", true) || marker.contains("ID Voucher Receipt", true) -> TxType.PAYMENT
            marker.contains("Refund", true) -> TxType.REFUND
            marker.contains("Change", true) -> TxType.CHANGE
            marker.contains("Visa", true) -> if (isZeroVisaCancellation(block)) TxType.VOID else TxType.VISA
            marker.contains("Sale Tickets", true) -> {
                if (block.contains("Note", true) && block.contains("تغيير")) TxType.UNKNOWN else TxType.TICKET
            }
            else -> TxType.UNKNOWN
        }

        val currency = forcedCurrency ?: when {
            block.contains("$") -> Currency.USD
            block.contains("د.ع") || block.contains("ع.د") -> Currency.IQD
            else -> Currency.USD
        }

        val operationNo = operationNumber(block, type)
        if (operationNo.isNullOrBlank()) return null

        val pnr = pnrRegex.find(block)?.groupValues?.getOrNull(1)?.uppercase(Locale.ROOT)
        val route = routeRegex.find(block)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        val discount = discountRegex.find(block)?.groupValues?.getOrNull(1)?.toNumber() ?: 0.0
        val amount = extractTransactionAmount(block, marker, type, currency)
        val passengers = extractPassengers(block, type)

        return ParsedTransaction(
            operationNo = operationNo,
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

    private fun operationNumber(block: String, type: TxType): String? {
        if (type == TxType.PAYMENT) {
            voucherOpRegex.find(block)?.groupValues?.getOrNull(1)?.let { return it }
        }

        return dateOpRegex.findAll(block)
            .toList()
            .lastOrNull()
            ?.groupValues
            ?.getOrNull(2)
    }

    private fun extractTransactionAmount(
        block: String,
        marker: String,
        type: TxType,
        currency: Currency
    ): Double {
        val firstLine = block.lineSequence().firstOrNull().orEmpty()
        val markerIndex = firstLine.indexOf(marker, ignoreCase = true)

        if (type == TxType.PAYMENT && currency == Currency.IQD) {
            val all = simpleMoneyRegex.findAll(firstLine)
                .mapNotNull { it.groupValues.getOrNull(1)?.toNumberOrNull() }
                .filter { it > 0 }
                .toList()
            if (all.isNotEmpty()) {
                return all.last()
            }
        }

        if (markerIndex > 0) {
            val prefix = firstLine.substring(0, markerIndex)
            val amount = simpleMoneyRegex.findAll(prefix)
                .toList()
                .lastOrNull()
                ?.groupValues
                ?.getOrNull(1)
                ?.toNumberOrNull()
            if (amount != null) return amount
        }

        if (type == TxType.VISA || type == TxType.VOID) {
            val passengerAmounts = extractVisaLineAmounts(block)
            if (passengerAmounts.isNotEmpty()) return passengerAmounts.sum()
            if (Regex("\\$\\s*0(?:\\.0+)?").containsMatchIn(block)) return 0.0
        }

        val firstMonetary = simpleMoneyRegex.findAll(firstLine)
            .mapNotNull { it.groupValues.getOrNull(1)?.toNumberOrNull() }
            .firstOrNull { it >= 0.0 }

        return firstMonetary ?: 0.0
    }

    private fun extractVisaLineAmounts(block: String): List<Double> {
        val amounts = mutableListOf<Double>()
        block.lines().forEach { line ->
            if (line.contains("Visa", true) || line.contains("فيزا")) {
                val parts = line.split("\\").map { it.trim() }.filter { it.isNotBlank() }
                parts.forEach { part ->
                    val value = Regex("(?:\\$|د\\.?ع|ع\\.?د)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)")
                        .find(part)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toNumberOrNull()
                    if (value != null && (part.contains("$") || part.contains("د.ع") || part.contains("ع.د"))) {
                        amounts += value
                    }
                }
            }
        }
        return amounts
    }

    private fun extractPassengers(block: String, type: TxType): List<ParsedPassenger> {
        val results = mutableListOf<ParsedPassenger>()

        if (type == TxType.TICKET) {
            block.lines().forEach { line ->
                if (line.contains("Adult", true) || line.contains("Child", true) || line.contains("Infant", true)) {
                    val clean = line.replace("-.", "").trim()
                    val parts = clean.split("\\").map { it.trim() }.filter { it.isNotBlank() }
                    val typeIdx = parts.indexOfFirst {
                        it.equals("Adult", true) || it.equals("Child", true) || it.equals("Infant", true)
                    }
                    if (typeIdx >= 1) {
                        val name = parts[typeIdx - 1].removePrefix(".").trim()
                        val amount = parts.getOrNull(typeIdx + 1)
                            ?.let { simpleMoneyRegex.find(it)?.groupValues?.getOrNull(1)?.toNumberOrNull() }

                        if (name.isNotBlank()) {
                            results += ParsedPassenger(name = name, amount = amount)
                        }
                    }
                }
            }
        } else if (type == TxType.VISA || type == TxType.VOID) {
            block.lines().forEach { line ->
                if (line.contains("Visa", true) || line.contains("فيزا")) {
                    val parts = line.split("\\").map { it.trim() }.filter { it.isNotBlank() }
                    if (parts.isNotEmpty()) {
                        val possiblePassport = parts.firstNotNullOfOrNull { passportRegex.find(it)?.value }
                        val nameCandidate = parts
                            .filterNot {
                                it.contains("VISA", true) ||
                                    it.contains("فيزا") ||
                                    passportRegex.find(it) != null
                            }
                            .firstOrNull { it.any(Char::isLetter) }

                        val amount = parts.asReversed().firstNotNullOfOrNull {
                            simpleMoneyRegex.find(it)?.groupValues?.getOrNull(1)?.toNumberOrNull()
                        }

                        if (!nameCandidate.isNullOrBlank()) {
                            results += ParsedPassenger(name = nameCandidate, amount = amount, passport = possiblePassport)
                        }
                    }
                }
            }
        } else if (type == TxType.CHANGE || type == TxType.REFUND) {
            val name = Regex(
                "Name\\s*:\\s*([^\\n]+?)(?:E-TICKET|E\\s*-?TICKET|$)",
                RegexOption.IGNORE_CASE
            ).find(block)?.groupValues?.getOrNull(1)?.trim()

            if (!name.isNullOrBlank()) results += ParsedPassenger(name)
        }

        return results.distinctBy { it.name.lowercase() }
    }

    private fun isZeroVisaCancellation(block: String): Boolean {
        if (!block.contains("Sale Visa", true) && !block.contains("Visa Sale", true)) return false

        return Regex("\\$\\s*0(?:\\.0+)?").containsMatchIn(block) ||
            block.contains("الغاء") ||
            block.contains("إلغاء")
    }

    private fun String.toNumber(): Double =
        replace(",", "").trim().toDoubleOrNull() ?: 0.0

    private fun String.toNumberOrNull(): Double? =
        replace(",", "").replace("$", "").trim().toDoubleOrNull()
}
