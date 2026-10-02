package com.eslam.check.parser

import com.eslam.check.data.BridgeLedgerMeta
import com.eslam.check.data.BridgeParseResult
import com.eslam.check.data.Currency
import com.eslam.check.data.ParsedPassenger
import com.eslam.check.data.ParsedTransaction
import com.eslam.check.data.TxType

object BridgeParser {
    private data class TxDraft(
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
        var mode = "CUMULATIVE"
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
                    if (version != 1) errors += "السطر " + lineNo + ": إصدار ECX غير مدعوم (" + f.getOrNull(1).orEmpty() + ")"
                }
                "B" -> {
                    batchId = unescape(f.getOrNull(1).orEmpty()).trim()
                    mode = unescape(f.getOrNull(3).orEmpty()).trim().ifBlank { "CUMULATIVE" }.uppercase()
                    if (batchId.isBlank()) warnings += "السطر " + lineNo + ": BATCH بدون معرف"
                }
                "L" -> {
                    if (f.size < 9) {
                        errors += "السطر " + lineNo + ": سجل L ناقص"
                        return@forEachIndexed
                    }
                    val currency = parseCurrency(f[1], lineNo, errors) ?: return@forEachIndexed
                    val open = number(f[5])
                    val close = number(f[6])
                    val count = f[7].trim().toIntOrNull()
                    if (open == null || close == null || count == null) {
                        errors += "السطر " + lineNo + ": قيم L غير صالحة"
                        return@forEachIndexed
                    }
                    ledgers += BridgeLedgerMeta(
                        currency = currency,
                        rangeFrom = f[2].trim(),
                        rangeTo = f[3].trim(),
                        openingDate = f[4].trim(),
                        openingBalance = open,
                        closingBalance = close,
                        expectedCount = count
                    )
                }
                "T" -> {
                    if (f.size < 14) {
                        errors += "السطر " + lineNo + ": سجل T ناقص (" + f.size + "/14)"
                        return@forEachIndexed
                    }
                    val currency = parseCurrency(f[1], lineNo, errors) ?: return@forEachIndexed
                    val seq = f[2].trim().toIntOrNull()
                    val op = f[3].trim()
                    val date = f[4].trim()
                    val src = f[5].trim().uppercase()
                    val amount = number(f[6])
                    val effect = numberOrBlank(f[7])
                    val discount = numberOrBlank(f[8]) ?: 0.0
                    val balance = numberOrBlank(f[9])
                    val pnr = unescape(f[10]).trim().uppercase().ifBlank { null }
                    val route = unescape(f[11]).trim().replace(Regex("\\s+"), "-").uppercase().ifBlank { null }
                    val flags = unescape(f[12]).trim().uppercase().ifBlank { null }
                    val reserved = f[13].trim()

                    if (op.isBlank()) errors += "السطر " + lineNo + ": رقم العملية فارغ"
                    if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) errors += "السطر " + lineNo + ": التاريخ غير صالح (" + date + ")"
                    if (amount == null) errors += "السطر " + lineNo + ": مبلغ العملية غير صالح"
                    if (reserved.isNotBlank()) warnings += "السطر " + lineNo + ": الحقل المحجوز غير فارغ وسيتم تجاهله"

                    if (op.isNotBlank() && amount != null) {
                        txDrafts += TxDraft(
                            currency, seq, op, date, src, amount, effect, discount,
                            balance, pnr, route, flags, line
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

        if (version == 0) errors += "رأس ECX|1 غير موجود"
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
            "TS" -> when {
                "VOID" in flagSet -> TxType.VOID
                "REISSUE" in flagSet -> TxType.REISSUE
                "ZERO" in flagSet -> TxType.UNKNOWN
                else -> TxType.TICKET
            }
            "VS" -> when {
                "CANCEL" in flagSet -> TxType.VOID
                "ZERO" in flagSet -> TxType.UNKNOWN
                else -> TxType.VISA
            }
            "TC", "TNC" -> TxType.CHANGE
            "TR", "TNR" -> TxType.REFUND
            "PAY" -> TxType.PAYMENT
            "VOID" -> TxType.VOID
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
