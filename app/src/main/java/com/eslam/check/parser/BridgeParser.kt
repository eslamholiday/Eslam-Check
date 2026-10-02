package com.eslam.check.parser

import com.eslam.check.data.*
import java.security.MessageDigest

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
        val first = normalizedLines(text).firstOrNull().orEmpty()
        return if (first.startsWith("X3|", ignoreCase = true)) parseV3(text) else parseLegacy(text)
    }

    private fun parseV3(text: String): BridgeParseResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val ledgers = mutableListOf<BridgeLedgerMeta>()
        val txDrafts = mutableListOf<TxDraft>()
        val people = mutableListOf<PersonDraft>()
        val notes = mutableMapOf<Pair<Currency, String>, MutableList<String>>()

        val lines = normalizedLines(text)
        val header = splitEscaped(lines.firstOrNull().orEmpty())
        val dictionary = header.getOrNull(1).orEmpty().ifBlank { "K1" }
        val account = header.getOrNull(2).orEmpty().uppercase().ifBlank { "TY" }
        val documentId = unescape(header.getOrNull(3).orEmpty()).trim()
        val snapshotId = unescape(header.getOrNull(4).orEmpty()).trim().ifBlank { documentId }
        val mode = if (header.getOrNull(5).orEmpty().uppercase() == "D") "DELTA" else "CUMULATIVE"
        if (dictionary != "K1") warnings += "قاموس ECX v3 غير معروف: " + dictionary
        if (account != "TY") warnings += "رمز الحساب غير معروف: " + account
        if (documentId.isBlank()) errors += "ECX v3 يحتاج DOC_ID"

        var footerUsd: Int? = null
        var footerIqd: Int? = null
        var footerPax: Int? = null
        var footerChecksum: String? = null
        var footerIndex = -1

        lines.forEachIndexed { index, line ->
            if (index == 0) return@forEachIndexed
            val lineNo = index + 1
            val f = splitEscaped(line)
            when (f.firstOrNull()?.uppercase()) {
                "L" -> {
                    if (f.size < 8) {
                        errors += "السطر " + lineNo + ": سجل L ناقص"
                        return@forEachIndexed
                    }
                    val cur = parseCompactCurrency(f[1], lineNo, errors) ?: return@forEachIndexed
                    val open = number(f[5])
                    val close = number(f[6])
                    val count = f[7].trim().toIntOrNull()
                    if (open == null || close == null || count == null) {
                        errors += "السطر " + lineNo + ": قيم L غير صالحة"
                        return@forEachIndexed
                    }
                    ledgers += BridgeLedgerMeta(
                        ledgerId = ledgerId(cur, account),
                        currency = cur,
                        rangeFrom = f[2].trim(),
                        rangeTo = f[3].trim(),
                        openingDate = f[4].trim(),
                        openingBalance = open,
                        closingBalance = close,
                        expectedCount = count
                    )
                }

                "O" -> {
                    if (f.size < 14) {
                        errors += "السطر " + lineNo + ": سجل O ناقص (" + f.size + "/14)"
                        return@forEachIndexed
                    }
                    val cur = parseCompactCurrency(f[1], lineNo, errors) ?: return@forEachIndexed
                    val op = f[2].trim()
                    val date = f[3].trim()
                    val typeCode = f[4].trim().uppercase()
                    val amount = number(f[5])
                    val balance = numberOrBlank(f[6])
                    val pnr = unescape(f[7]).trim().uppercase().ifBlank { null }
                    val route = unescape(f[8]).trim().replace(Regex("\\s+"), "-").uppercase().ifBlank { null }
                    val airline = unescape(f[9]).trim().takeUnless { it.isBlank() || it == "?" }
                    val visa = unescape(f[10]).trim().uppercase().ifBlank { null }
                    val discount = numberOrBlank(f[11]) ?: 0.0
                    val effect = numberOrBlank(f[12])
                    val flags = unescape(f[13]).trim().uppercase().ifBlank { null }

                    if (op.isBlank()) errors += "السطر " + lineNo + ": رقم العملية فارغ"
                    if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) errors += "السطر " + lineNo + ": التاريخ غير صالح (" + date + ")"
                    if (amount == null) errors += "السطر " + lineNo + ": مبلغ العملية غير صالح"

                    if (op.isNotBlank() && amount != null) {
                        val ledger = ledgerId(cur, account)
                        txDrafts += TxDraft(
                            externalId = ledger + "-" + op,
                            ledgerId = ledger,
                            documentId = documentId,
                            snapshotId = snapshotId,
                            currency = cur,
                            seq = null,
                            op = op,
                            date = date,
                            sourceCode = typeCode,
                            amount = amount,
                            effect = effect,
                            discount = discount,
                            balance = balance,
                            pnr = pnr,
                            route = route,
                            airline = airline,
                            visaCountry = visa,
                            flags = flags,
                            rawLine = line
                        )
                    }
                }

                "Q" -> {
                    if (f.size < 10) {
                        errors += "السطر " + lineNo + ": سجل Q ناقص (" + f.size + "/10)"
                        return@forEachIndexed
                    }
                    val cur = parseCompactCurrency(f[1], lineNo, errors) ?: return@forEachIndexed
                    val op = f[2].trim()
                    val name = unescape(f[3]).trim()
                    if (op.isBlank() || name.isBlank()) {
                        errors += "السطر " + lineNo + ": Q يحتاج رقم عملية واسم"
                        return@forEachIndexed
                    }
                    people += PersonDraft(
                        currency = cur,
                        op = op,
                        passenger = ParsedPassenger(
                            name = name,
                            passengerType = expandPassengerType(f[4]),
                            documentNo = unescape(f[5]).trim().ifBlank { null },
                            amount = numberOrBlank(f[6]),
                            passport = unescape(f[7]).trim().ifBlank { null },
                            product = unescape(f[8]).trim().ifBlank { null },
                            flags = unescape(f[9]).trim().uppercase().ifBlank { null }
                        ),
                        rawLine = line
                    )
                }

                "M" -> {
                    if (f.size < 4) {
                        errors += "السطر " + lineNo + ": سجل M ناقص"
                        return@forEachIndexed
                    }
                    val cur = parseCompactCurrency(f[1], lineNo, errors) ?: return@forEachIndexed
                    val op = f[2].trim()
                    val note = unescape(f.drop(3).joinToString("|")).trim()
                    if (op.isNotBlank() && note.isNotBlank()) notes.getOrPut(cur to op) { mutableListOf() } += note
                }

                "H" -> {
                    footerIndex = index
                    footerUsd = f.getOrNull(1)?.toIntOrNull()
                    footerIqd = f.getOrNull(2)?.toIntOrNull()
                    footerPax = f.getOrNull(3)?.toIntOrNull()
                    footerChecksum = f.getOrNull(4)?.trim()?.uppercase()?.ifBlank { null }
                }

                else -> warnings += "السطر " + lineNo + ": سجل v3 غير معروف وتم تجاهله"
            }
        }

        if (txDrafts.isEmpty()) errors += "لا توجد عمليات O داخل النص"
        val duplicateKeys = txDrafts.groupBy { it.currency to it.op }.filterValues { it.size > 1 }.keys
        duplicateKeys.forEach { errors += "عملية مكررة داخل النص: " + it.first.name + "/" + it.second }

        val txKeys = txDrafts.map { it.currency to it.op }.toSet()
        people.forEach { if ((it.currency to it.op) !in txKeys) errors += "Q بدون عملية أصل: " + it.currency.name + "/" + it.op }

        ledgers.forEach { ledger ->
            val actual = txDrafts.count { it.currency == ledger.currency }
            if (actual != ledger.expectedCount) errors += ledger.currency.name + ": COUNT=" + ledger.expectedCount + " لكن الموجود " + actual
        }

        footerUsd?.let { expected ->
            if (expected != txDrafts.count { t -> t.currency == Currency.USD }) errors += "H: عدد USD لا يطابق"
        }
        footerIqd?.let { expected ->
            if (expected != txDrafts.count { t -> t.currency == Currency.IQD }) errors += "H: عدد IQD لا يطابق"
        }
        footerPax?.let { expected ->
            if (expected != people.size) errors += "H: عدد المسافرين/الأشخاص لا يطابق"
        }

        val checksumVerified = if (footerChecksum != null && footerIndex > 0) {
            val canonical = lines.take(footerIndex).joinToString("\n")
            val actual = sha256(canonical).take(12).uppercase()
            if (actual != footerChecksum) {
                errors += "Checksum غير مطابق: النص ناقص أو تغير أثناء النسخ"
                false
            } else true
        } else {
            warnings += "ECX v3 بدون Checksum"
            null
        }

        val transactions = txDrafts.map { d ->
            val pxDrafts = people.filter { it.currency == d.currency && it.op == d.op }
            val note = notes[d.currency to d.op]?.joinToString(" | ")
            ParsedTransaction(
                externalId = d.externalId,
                ledgerId = d.ledgerId,
                documentId = d.documentId,
                snapshotId = d.snapshotId,
                operationNo = d.op,
                transactionDate = d.date,
                currency = d.currency,
                type = mapCompactType(d.sourceCode),
                sourceCode = d.sourceCode,
                statementSeq = null,
                batchId = snapshotId,
                pnr = d.pnr,
                route = d.route,
                amount = d.amount,
                ledgerEffect = d.effect,
                discount = d.discount,
                balanceAfter = d.balance,
                passengers = pxDrafts.map { it.passenger },
                airline = d.airline,
                visaCountry = expandVisa(d.visaCountry),
                rawText = buildString {
                    append(d.rawLine)
                    pxDrafts.forEach { append("\n").append(it.rawLine) }
                    note?.let { append("\nM|").append(compactCurrency(d.currency)).append("|").append(d.op).append("|").append(it) }
                },
                note = note,
                flags = d.flags
            )
        }

        return BridgeParseResult(
            version = 3,
            dictionaryVersion = dictionary,
            batchId = snapshotId,
            documentId = documentId,
            snapshotId = snapshotId,
            mode = mode,
            ledgers = ledgers,
            transactions = transactions,
            errors = errors.distinct(),
            warnings = warnings.distinct(),
            checksumVerified = checksumVerified
        )
    }

    private fun parseLegacy(text: String): BridgeParseResult {
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

        val rawLines = text.replace("\r\n", "\n").replace("\r", "\n").lines()

        rawLines.forEachIndexed { index, sourceLine ->
            val lineNo = index + 1
            val line = sourceLine.trim()
            if (line.isBlank() || line.startsWith("#") || line.startsWith("//")) return@forEachIndexed
            val f = splitEscaped(line)
            when (f.firstOrNull()?.uppercase()) {
                "ECX" -> {
                    version = f.getOrNull(1)?.toIntOrNull() ?: 0
                    if (version !in setOf(1, 2)) errors += "السطر " + lineNo + ": إصدار ECX غير مدعوم"
                }
                "B" -> {
                    batchId = unescape(f.getOrNull(1).orEmpty()).trim()
                    snapshotId = batchId
                    mode = unescape(f.getOrNull(3).orEmpty()).trim().ifBlank { "CUMULATIVE" }.uppercase()
                    if (version >= 2) documentId = unescape(f.getOrNull(4).orEmpty()).trim()
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
                        ledgerId = ledgerId, currency = currency,
                        rangeFrom = f[2 + offset].trim(), rangeTo = f[3 + offset].trim(),
                        openingDate = f[4 + offset].trim(), openingBalance = open,
                        closingBalance = close, expectedCount = count
                    )
                }
                "T" -> {
                    val minSize = if (version >= 2) 17 else 14
                    if (f.size < minSize) {
                        errors += "السطر " + lineNo + ": سجل T ناقص"
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
                    if (op.isBlank()) errors += "السطر " + lineNo + ": رقم العملية فارغ"
                    if (amount == null) errors += "السطر " + lineNo + ": مبلغ العملية غير صالح"
                    if (op.isNotBlank() && amount != null) {
                        txDrafts += TxDraft(
                            externalId = externalId, ledgerId = ledgerIds[currency],
                            documentId = documentId.ifBlank { null }, snapshotId = snapshotId.ifBlank { null },
                            currency = currency, seq = seq, op = op, date = date, sourceCode = src,
                            amount = amount, effect = effect, discount = discount, balance = balance,
                            pnr = pnr, route = route, airline = airline, visaCountry = visaCountry,
                            flags = flags, rawLine = line
                        )
                    }
                }
                "P" -> {
                    if (f.size < 10) {
                        errors += "السطر " + lineNo + ": سجل P ناقص"
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
                    if (f.size >= 4) {
                        val currency = parseCurrency(f[1], lineNo, errors) ?: return@forEachIndexed
                        val op = f[2].trim()
                        val note = unescape(f.drop(3).joinToString("|")).trim()
                        if (op.isNotBlank() && note.isNotBlank()) notes.getOrPut(currency to op) { mutableListOf() } += note
                    }
                }
                "Z" -> {
                    footerUsd = f.getOrNull(1)?.trim()?.toIntOrNull()
                    footerIqd = f.getOrNull(2)?.trim()?.toIntOrNull()
                }
                else -> warnings += "السطر " + lineNo + ": سجل غير معروف وتم تجاهله"
            }
        }

        if (version == 0) errors += "رأس ECX غير موجود"
        if (txDrafts.isEmpty()) errors += "لا توجد عمليات T داخل النص"

        val duplicateKeys = txDrafts.groupBy { it.currency to it.op }.filterValues { it.size > 1 }.keys
        duplicateKeys.forEach { errors += "عملية مكررة داخل النص: " + it.first.name + "/" + it.second }
        val txKeys = txDrafts.map { it.currency to it.op }.toSet()
        people.forEach { if ((it.currency to it.op) !in txKeys) errors += "P بدون عملية أصل: " + it.currency.name + "/" + it.op }

        ledgers.forEach { ledger ->
            val actual = txDrafts.count { it.currency == ledger.currency }
            if (actual != ledger.expectedCount) errors += ledger.currency.name + ": COUNT=" + ledger.expectedCount + " لكن الموجود " + actual
        }
        footerUsd?.let { expected -> if (expected != txDrafts.count { it.currency == Currency.USD }) errors += "Z: عدد USD لا يطابق" }
        footerIqd?.let { expected -> if (expected != txDrafts.count { it.currency == Currency.IQD }) errors += "Z: عدد IQD لا يطابق" }

        val transactions = txDrafts.map { d ->
            val pxDrafts = people.filter { it.currency == d.currency && it.op == d.op }
            val note = notes[d.currency to d.op]?.joinToString(" | ")
            val effectiveFlags = buildList {
                d.flags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.let { addAll(it) }
                if (d.amount == 0.0 && none { it == "ZERO" }) add("ZERO")
            }.distinct().joinToString(",").ifBlank { null }

            ParsedTransaction(
                externalId = d.externalId, ledgerId = d.ledgerId, documentId = d.documentId, snapshotId = d.snapshotId,
                operationNo = d.op, transactionDate = d.date, currency = d.currency,
                type = mapLegacyType(d.sourceCode, effectiveFlags), sourceCode = d.sourceCode,
                statementSeq = d.seq, batchId = batchId.ifBlank { null }, pnr = d.pnr, route = d.route,
                amount = d.amount, ledgerEffect = d.effect, discount = d.discount, balanceAfter = d.balance,
                passengers = pxDrafts.map { it.passenger }, airline = d.airline, visaCountry = d.visaCountry,
                rawText = buildString {
                    append(d.rawLine)
                    pxDrafts.forEach { append("\n").append(it.rawLine) }
                    note?.let { append("\nN|").append(d.currency.name).append("|").append(d.op).append("|").append(it) }
                },
                note = note, flags = effectiveFlags
            )
        }

        return BridgeParseResult(
            version = if (version == 0) 1 else version,
            batchId = batchId, documentId = documentId, snapshotId = snapshotId,
            mode = mode, ledgers = ledgers, transactions = transactions,
            errors = errors.distinct(), warnings = warnings.distinct()
        )
    }

    private fun mapCompactType(code: String): TxType = when (code.trim().uppercase()) {
        "T" -> TxType.TICKET
        "V" -> TxType.VISA
        "C" -> TxType.CHANGE
        "R" -> TxType.REFUND
        "P" -> TxType.PAYMENT
        "X" -> TxType.VOID
        "E" -> TxType.REISSUE
        "F" -> TxType.FEE
        else -> TxType.UNKNOWN
    }

    private fun mapLegacyType(sourceCode: String, flags: String?): TxType {
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

    private fun parseCompactCurrency(value: String, lineNo: Int, errors: MutableList<String>): Currency? =
        when (value.trim().uppercase()) {
            "I", "IQD" -> Currency.IQD
            "U", "USD" -> Currency.USD
            else -> {
                errors += "السطر " + lineNo + ": رمز عملة غير معروف (" + value + ")"
                null
            }
        }

    private fun parseCurrency(value: String, lineNo: Int, errors: MutableList<String>): Currency? =
        when (value.trim().uppercase()) {
            "USD" -> Currency.USD
            "IQD" -> Currency.IQD
            else -> {
                errors += "السطر " + lineNo + ": عملة غير معروفة (" + value + ")"
                null
            }
        }

    private fun compactCurrency(currency: Currency) = if (currency == Currency.IQD) "I" else "U"

    private fun ledgerId(currency: Currency, account: String): String {
        val accountName = if (account.equals("TY", true)) "TAJALYAMAMA" else account.uppercase()
        return "BC-" + accountName + "-" + currency.name
    }

    private fun expandPassengerType(value: String): String? = when (value.trim().uppercase()) {
        "A", "ADT" -> "ADT"
        "C", "CHD" -> "CHD"
        "I", "INF" -> "INF"
        "" -> null
        else -> value.trim().uppercase()
    }

    private fun expandVisa(value: String?): String? = when (value?.trim()?.uppercase()) {
        "AE", "UAE" -> "UAE"
        "JO", "JORDAN" -> "JORDAN"
        "EG", "EGYPT" -> "EGYPT"
        "SA", "SAUDI" -> "SAUDI"
        null, "" -> null
        else -> value.trim().uppercase()
    }

    private fun number(value: String): Double? =
        value.trim().replace(",", "").replace("+", "").toDoubleOrNull()

    private fun numberOrBlank(value: String): Double? =
        value.trim().takeIf { it.isNotBlank() }?.replace(",", "")?.replace("+", "")?.toDoubleOrNull()

    private fun normalizedLines(text: String): List<String> =
        text.replace("\r\n", "\n").replace("\r", "\n").lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("//") }

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
                ch == '|' -> { out += current.toString(); current.clear() }
                else -> current.append(ch)
            }
        }
        if (escaped) current.append('\\')
        out += current.toString()
        return out
    }

    private fun unescape(value: String): String = value

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
