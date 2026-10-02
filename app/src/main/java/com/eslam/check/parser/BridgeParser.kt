package com.eslam.check.parser

import com.eslam.check.data.BridgeLedgerMeta
import com.eslam.check.data.BridgeParseResult
import com.eslam.check.data.Currency
import com.eslam.check.data.ParsedPassenger
import com.eslam.check.data.ParsedTransaction
import com.eslam.check.data.TxType

object BridgeParser {
    private data class TxDraft(
        val externalId: String? = null,
        val ledgerId: String? = null,
        val documentId: String? = null,
        val snapshotId: String? = null,
        val currency: Currency,
        val seq: Int?,
        val op: String,
        val date: String,
        val sourceCode: String,
        val amount: Double,
        val effect: Double?,
        val discount: Double,
        val balance: Double?,
        val pnr: String?,
        val route: String?,
        val airline: String? = null,
        val visaCountry: String? = null,
        val flags: String?,
        val rawLine: String
    )

    private data class PersonDraft(
        val currency: Currency,
        val op: String,
        val passenger: ParsedPassenger,
        val rawLine: String
    )

    fun parse(text: String): BridgeParseResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val ledgers = mutableListOf<BridgeLedgerMeta>()
        val txDrafts = mutableListOf<TxDraft>()
        val people = mutableListOf<PersonDraft>()
        val notes = mutableMapOf<Pair<Currency, String>, MutableList<String>>()
        var version = 0
        var batchId = ""
        var documentId = ""
        var snapshotId = ""
        var mode = "CUMULATIVE"
        val ledgerIds = mutableMapOf<Currency, String>()
        var footerUsd: Int? = null
        var footerIqd: Int? = null

        val rawLines = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lines()

        rawLines.forEachIndexed { index, sourceLine ->
            val lineNo = index + 1
            val line = sourceLine.trim()
            if (line.isBlank() || line.startsWith("#") || line.startsWith("//")) return@forEachIndexed
            val f = splitEscaped(line)
            when (f.firstOrNull()?.uppercase()) {
                "ECX" -> {
                    version = f.getOrNull(1)?.toIntOrNull() ?: 0
                    if (version !in setOf(1, 2)) errors += "السطر " + lineNo + ": إصدار ECX غير مدعوم (" + f.getOrNull(1).orEmpty() + ")"
                }
                "B" -> {
                    batchId = unescape(f.getOrNull(1).orEmpty()).trim()
                    snapshotId = batchId
                    mode = unescape(f.getOrNull(3).orEmpty()).trim().ifBlank { "CUMULATIVE" }.uppercase()
                    if (version >= 2) documentId = unescape(f.getOrNull(4).orEmpty()).trim()
                    if (batchId.isBlank()) warnings += "السطر " + lineNo + ": SNAPSHOT/BATCH بدون معرف"
                    if (version >= 2 && documentId.isBlank()) warnings += "السطر " + lineNo + ": DOC_ID غير موجود"
                }
                "L" -> {
                    val minSize = if (version >= 2) 9 else 8
                    if (f.size < minSize) {
                        errors += "السطر " + lineNo + ": سجل L ناقص"
                        return@forEachIndexed
                    }
                    val offset = if (version >= 2) 1 else 0
                    val ledgerId = if (version >= 2) unescape(f[1]).trim() else ""
                    val currency = parseCurrency(f[1 + offset], lineNo, errors) ?: return@forEachIndexed
                    val open = number(f[5 + offset])
                    val close = number(f[6 + offset])
                    val count = f[7 + offset].trim().toIntOrNull()
                    if (open == null || close == null || count == null) {
                        errors += "السطر " + lineNo + ": قيم L غير صالحة"
                        return@forEachIndexed
                    }
                    if (ledgerId.isNotBlank()) ledgerIds[currency] = ledgerId
                    ledgers += BridgeLedgerMeta(
                        ledgerId = ledgerId,
                        currency = currency,
                        rangeFrom = f[2 + offset].trim(),
                        rangeTo = f[3 + offset].trim(),
                        openingDate = f[4 + offset].trim(),
                        openingBalance = open,
                        closingBalance = close,
                        expectedCount = count
                    )
                }
                "T" -> {
                    val minSize = if (version >= 2) 17 else 14
                    if (f.size < minSize) {
                        errors += "السطر " + lineNo + ": سجل T ناقص (" + f.size + "/" + minSize + ")"
                        return@forEachIndexed
                    }
                    val o = if (version >= 2) 1 else 0
                    val externalId = if (version >= 2) unescape(f[1]).trim().ifBlank { null } else null
                    val currency = parseCurrency(f[1 + o], lineNo, errors) ?: return@forEachIndexed
                    val seq = f[2 + o].trim().toIntOrNull()
                    val op = f[3 + o].trim()
                    val date = f[4 + o].trim()
                    val src = f[5 + o].trim().uppercase()
                    val amount = number(f[6 + o])
                    val effect = numberOrBlank(f[7 + o])
                    val discount = numberOrBlank(f[8 + o]) ?: 0.0
                    val balance = numberOrBlank(f[9 + o])
                    val pnr = unescape(f[10 + o]).trim().uppercase().ifBlank { null }
                    val route = unescape(f[11 + o]).trim().replace(Regex("\\s+"), "-").uppercase().ifBlank { null }
                    val airline = if (version >= 2) unescape(f[13]).trim().ifBlank { null } else null
                    val visaCountry = if (version >= 2) unescape(f[14]).trim().uppercase().ifBlank { null } else null
                    val flags = unescape(if (version >= 2) f[15] else f[12]).trim().uppercase().ifBlank { null }
                    val reserved = if (version >= 2) f[16].trim() else f[13].trim()

                    if (op.isBlank()) errors += "السطر " + lineNo + ": رقم العملية فارغ"
                    if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) errors += "السطر " + lineNo + ": التاريخ غير صالح (" + date + ")"
                    if (amount == null) errors += "السطر " + lineNo + ": مبلغ العملية غير صالح"
                    if (reserved.isNotBlank()) warnings += "السطر " + lineNo + ": الحقل المحجوز غير فارغ وسيتم تجاهله"

                    if (op.isNotBlank() && amount != null) {
                        txDrafts += TxDraft(
                            externalId = externalId,
                            ledgerId = ledgerIds[currency],
                            documentId = documentId.ifBlank { null },
                            snapshotId = snapshotId.ifBlank { null },
                            currency = currency, seq = seq, op = op, date = date, sourceCode = src,
                            amount = amount, effect = effect, discount = discount, balance = balance,
                            pnr = pnr, route = route, airline = airline, visaCountry = visaCountry,
                            flags = flags, rawLine = line
                        )
                    }
                }
                "P" -> {
                    if (f.size < 10) {
                        errors += "السطر " + lineNo + ": سجل P ناقص (" + f.size + "/10)"
                        return@forEachIndexed
                    }
                    val currency = parseCurrency(f[1], lineNo, errors) ?: return@forEachIndexed
                    val op = f[2].trim()
                    val name = unescape(f[3]).trim()
                    if (op.isBlank() || name.isBlank()) {
                        errors += "السطر " + lineNo + ": P يحتاج رقم عملية واسم"
                        return@forEachIndexed
                    }
                    people += PersonDraft(
                        currency = currency,
                        op = op,
                        passenger = ParsedPassenger(
                            name = name,
                            passengerType = unescape(f[4]).trim().uppercase().ifBlank { null },
                            documentNo = unescape(f[5]).trim().ifBlank { null },
                            amount = numberOrBlank(f[6]),
                            passport = unescape(f[7]).trim().ifBlank { null },
                            product = unescape(f[8]).trim().ifBlank { null },
                            flags = unescape(f[9]).trim().uppercase().ifBlank { null }
                        ),
                        rawLine = line
                    )
                }
                "N" -> {
                    if (f.size < 4) {
                        errors += "السطر " + lineNo + ": سجل N ناقص"
                        return@forEachIndexed
                    }
                    val currency = parseCurrency(f[1], lineNo, errors) ?: return@forEachIndexed
                    val op = f[2].trim()
                    val note = unescape(f.drop(3).joinToString("|")).trim()
                    if (op.isNotBlank() && note.isNotBlank()) {
                        notes.getOrPut(currency to op) { mutableListOf() } += note
                    }
                }
                "Z" -> {
                    footerUsd = f.getOrNull(1)?.trim()?.toIntOrNull()
                    footerIqd = f.getOrNull(2)?.trim()?.toIntOrNull()
                }
                else -> warnings += "السطر " + lineNo + ": سجل غير معروف وسيتم تجاهله"
            }
        }

        if (version == 0) errors += "رأس ECX غير موجود"
        if (txDrafts.isEmpty()) errors += "لا توجد عمليات T داخل النص"

        val duplicateKeys = txDrafts.groupBy { it.currency to it.op }.filterValues { it.size > 1 }.keys
        duplicateKeys.forEach { key ->
            errors += "عملية مكررة داخل النص: " + key.first.name + "/" + key.second
        }

        val txKeys = txDrafts.map { it.currency to it.op }.toSet()
        people.forEach { p ->
            if ((p.currency to p.op) !in txKeys) {
                errors += "P بدون عملية أصل: " + p.currency.name + "/" + p.op
            }
        }

        ledgers.forEach { ledger ->
            val actual = txDrafts.count { it.currency == ledger.currency }
            if (actual != ledger.expectedCount) {
                errors += ledger.currency.name + ": COUNT=" + ledger.expectedCount + " لكن الموجود " + actual
            }
        }

        footerUsd?.let { expected ->
            val actual = txDrafts.count { t -> t.currency == Currency.USD }
            if (expected != actual) errors += "Z: عدد USD المتوقع " + expected + " والموجود " + actual
        }
        footerIqd?.let { expected ->
            val actual = txDrafts.count { t -> t.currency == Currency.IQD }
            if (expected != actual) errors += "Z: عدد IQD المتوقع " + expected + " والموجود " + actual
        }

        val transactions = txDrafts.map { d ->
            val pxDrafts = people.filter { it.currency == d.currency && it.op == d.op }
            val px = pxDrafts.map { it.passenger }
            val note = notes[d.currency to d.op]?.joinToString(" | ")
            val effectiveFlags = buildList {
                d.flags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.let { addAll(it) }
                if (d.amount == 0.0 && none { it == "ZERO" }) add("ZERO")
            }.distinct().joinToString(",").ifBlank { null }

            val type = mapType(d.sourceCode, effectiveFlags)
            if (type == TxType.UNKNOWN && d.sourceCode !in setOf("UNK", "TS", "VS")) {
                warnings += d.currency.name + "/" + d.op + ": كود مصدر غير معروف " + d.sourceCode
            }

            ParsedTransaction(
                externalId = d.externalId,
                ledgerId = d.ledgerId,
                documentId = d.documentId,
                snapshotId = d.snapshotId,
                operationNo = d.op,
                transactionDate = d.date,
                currency = d.currency,
                type = type,
                sourceCode = d.sourceCode,
                statementSeq = d.seq,
                batchId = batchId.ifBlank { null },
                pnr = d.pnr,
                route = d.route,
                amount = d.amount,
                ledgerEffect = d.effect,
                discount = d.discount,
                balanceAfter = d.balance,
                passengers = px,
                airline = d.airline,
                visaCountry = d.visaCountry,
                rawText = buildString {
                    append(d.rawLine)
                    pxDrafts.forEach { append("\n").append(it.rawLine) }
                    note?.let { append("\nN|").append(d.currency.name).append("|").append(d.op).append("|").append(it) }
                },
                note = note,
                flags = effectiveFlags
            )
        }

        return BridgeParseResult(
            version = if (version == 0) 1 else version,
            batchId = batchId,
            documentId = documentId,
            snapshotId = snapshotId,
            mode = mode,
            ledgers = ledgers,
            transactions = transactions,
            errors = errors.distinct(),
            warnings = warnings.distinct()
        )
    }

    private fun mapType(sourceCode: String, flags: String?): TxType {
        val flagSet = flags.orEmpty().split(",").map { it.trim().uppercase() }.toSet()
        return when (sourceCode.uppercase()) {
            "TK", "TS" -> when {
                "VOID" in flagSet -> TxType.VOID
                "REISSUE" in flagSet -> TxType.REISSUE
                "ZERO" in flagSet -> TxType.UNKNOWN
                else -> TxType.TICKET
            }
            "VI", "VS" -> when {
                "CANCEL" in flagSet -> TxType.VOID
                "ZERO" in flagSet -> TxType.UNKNOWN
                else -> TxType.VISA
            }
            "CH", "TC", "TNC" -> TxType.CHANGE
            "RF", "TR", "TNR" -> TxType.REFUND
            "PAY" -> TxType.PAYMENT
            "VO", "VOID" -> TxType.VOID
            "REI" -> TxType.REISSUE
            else -> TxType.UNKNOWN
        }
    }

    private fun parseCurrency(value: String, lineNo: Int, errors: MutableList<String>): Currency? {
        return when (value.trim().uppercase()) {
            "USD" -> Currency.USD
            "IQD" -> Currency.IQD
            else -> {
                errors += "السطر " + lineNo + ": عملة غير معروفة (" + value + ")"
                null
            }
        }
    }

    private fun number(value: String): Double? =
        value.trim().replace(",", "").replace("+", "").toDoubleOrNull()

    private fun numberOrBlank(value: String): Double? =
        value.trim().takeIf { it.isNotBlank() }?.replace(",", "")?.replace("+", "")?.toDoubleOrNull()

    private fun splitEscaped(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        line.forEach { ch ->
            when {
                escaped -> {
                    when (ch) {
                        '|' -> current.append('|')
                        'n' -> current.append('\n')
                        '\\' -> current.append('\\')
                        else -> { current.append('\\'); current.append(ch) }
                    }
                    escaped = false
                }
                ch == '\\' -> escaped = true
                ch == '|' -> {
                    out += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        if (escaped) current.append('\\')
        out += current.toString()
        return out
    }

    private fun unescape(value: String): String = value
}
