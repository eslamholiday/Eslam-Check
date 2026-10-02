package com.eslam.check.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.util.UUID

class AppDatabase(context: Context) : SQLiteOpenHelper(context, "eslam_check.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE transactions(
                id TEXT PRIMARY KEY,
                external_id TEXT,
                ledger_id TEXT,
                document_id TEXT,
                snapshot_id TEXT,
                operation_no TEXT,
                transaction_date TEXT,
                currency TEXT NOT NULL,
                type TEXT NOT NULL,
                source TEXT NOT NULL,
                source_code TEXT,
                statement_seq INTEGER,
                batch_id TEXT,
                pnr TEXT,
                route TEXT,
                amount REAL NOT NULL DEFAULT 0,
                ledger_effect REAL,
                discount REAL NOT NULL DEFAULT 0,
                balance_after REAL,
                base_fare REAL,
                reference_total REAL,
                airline TEXT,
                visa_country TEXT,
                review_state TEXT NOT NULL,
                warning TEXT,
                note TEXT,
                flags TEXT,
                raw_text TEXT,
                source_hash TEXT,
                imported_at INTEGER NOT NULL,
                reviewed_at INTEGER,
                changed_after_review INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX idx_tx_operation_currency ON transactions(currency, operation_no) WHERE operation_no IS NOT NULL AND operation_no <> ''")
        db.execSQL("CREATE UNIQUE INDEX idx_tx_external_id ON transactions(external_id) WHERE external_id IS NOT NULL AND external_id <> ''")
        db.execSQL("CREATE INDEX idx_tx_pnr ON transactions(pnr)")
        db.execSQL("CREATE INDEX idx_tx_type ON transactions(type)")
        db.execSQL("CREATE INDEX idx_tx_review ON transactions(review_state)")

        db.execSQL("""
            CREATE TABLE passengers(
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                normalized_name TEXT NOT NULL,
                passport TEXT,
                phone TEXT,
                responsible_id TEXT,
                responsible_relation TEXT,
                is_responsible INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_passenger_name ON passengers(normalized_name)")
        db.execSQL("CREATE INDEX idx_passenger_passport ON passengers(passport)")

        db.execSQL("""
            CREATE TABLE responsible_contacts(
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                normalized_name TEXT NOT NULL,
                phone TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE tx_passengers(
                tx_id TEXT NOT NULL,
                passenger_id TEXT NOT NULL,
                amount REAL,
                base_fare REAL,
                passenger_type TEXT,
                document_no TEXT,
                product TEXT,
                flags TEXT,
                PRIMARY KEY(tx_id, passenger_id)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE commission_rules(
                id TEXT PRIMARY KEY,
                airline TEXT NOT NULL,
                kind TEXT NOT NULL,
                value REAL NOT NULL,
                round_trip_value REAL,
                reverse_only INTEGER NOT NULL DEFAULT 0,
                direction TEXT NOT NULL DEFAULT 'ANY',
                effective_from TEXT,
                learned INTEGER NOT NULL DEFAULT 0,
                active INTEGER NOT NULL DEFAULT 1,
                note TEXT,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE airline_prefixes(
                prefix TEXT PRIMARY KEY,
                airline TEXT NOT NULL,
                learned INTEGER NOT NULL DEFAULT 1,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE settings(
                key TEXT PRIMARY KEY,
                value TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE classification_rules(
                id TEXT PRIMARY KEY,
                contains_text TEXT NOT NULL,
                target_type TEXT NOT NULL,
                active INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE audit_log(
                id TEXT PRIMARY KEY,
                entity_type TEXT NOT NULL,
                entity_id TEXT NOT NULL,
                action TEXT NOT NULL,
                details TEXT,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())

        seedDefaults(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN transaction_date TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN source_code TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN statement_seq INTEGER")
            db.execSQL("ALTER TABLE transactions ADD COLUMN batch_id TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN ledger_effect REAL")
            db.execSQL("ALTER TABLE transactions ADD COLUMN balance_after REAL")
            db.execSQL("ALTER TABLE transactions ADD COLUMN flags TEXT")
            db.execSQL("ALTER TABLE tx_passengers ADD COLUMN passenger_type TEXT")
            db.execSQL("ALTER TABLE tx_passengers ADD COLUMN document_no TEXT")
            db.execSQL("ALTER TABLE tx_passengers ADD COLUMN product TEXT")
            db.execSQL("ALTER TABLE tx_passengers ADD COLUMN flags TEXT")
            db.execSQL("DROP INDEX IF EXISTS idx_tx_operation")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_tx_operation_currency ON transactions(currency, operation_no) WHERE operation_no IS NOT NULL AND operation_no <> ''")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN external_id TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN ledger_id TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN document_id TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN snapshot_id TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN visa_country TEXT")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_tx_external_id ON transactions(external_id) WHERE external_id IS NOT NULL AND external_id <> ''")
            db.execSQL("ALTER TABLE passengers ADD COLUMN phone TEXT")
            db.execSQL("ALTER TABLE passengers ADD COLUMN responsible_relation TEXT")
            db.execSQL("ALTER TABLE passengers ADD COLUMN is_responsible INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE tx_passengers ADD COLUMN base_fare REAL")
            db.execSQL("ALTER TABLE commission_rules ADD COLUMN round_trip_value REAL")
            db.execSQL("ALTER TABLE commission_rules ADD COLUMN direction TEXT NOT NULL DEFAULT 'ANY'")
            db.execSQL("ALTER TABLE commission_rules ADD COLUMN effective_from TEXT")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS airline_prefixes(
                    prefix TEXT PRIMARY KEY,
                    airline TEXT NOT NULL,
                    learned INTEGER NOT NULL DEFAULT 1,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())

            // Migrate only the old stock Iraqi default; never overwrite a rule the user has customized.
            val stockIraqiId = db.rawQuery(
                "SELECT id FROM commission_rules WHERE airline=? AND kind=? AND ABS(value-4.0)<0.0001 AND learned=0 AND (note=? OR note IS NULL) LIMIT 1",
                arrayOf("Iraqi Airways", RuleKind.PERCENT_BASE.name, "قابل للتعديل")
            ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            if (stockIraqiId != null) {
                db.update("commission_rules", ContentValues().apply {
                    put("kind", RuleKind.PRIVATE_MANUAL.name)
                    put("value", 0.0)
                    put("note", "النسبة على Base Fare وتتغير حسب القاعدة/الفترة؛ تذاكر كشف IQD تُصنف عراقية تلقائيًا")
                    put("updated_at", System.currentTimeMillis())
                }, "id=?", arrayOf(stockIraqiId))
            }

            val v3Defaults = listOf(
                CommissionRule(UUID.randomUUID().toString(), "Air Arabia", RuleKind.FIXED_PER_PASSENGER, 5.0, note = "رسم إصدار +5 لكل مسافر"),
                CommissionRule(UUID.randomUUID().toString(), "Flydubai", RuleKind.FIXED_PER_PASSENGER, 5.0, note = "رسم إصدار +5 لكل مسافر"),
                CommissionRule(UUID.randomUUID().toString(), "Pegasus", RuleKind.FIXED_PER_PASSENGER, 15.0, roundTripValue = 35.0, note = "15$ اتجاه واحد / 35$ ذهاب وإياب لكل مسافر"),
                CommissionRule(UUID.randomUUID().toString(), "Turkish Airlines", RuleKind.PRIVATE_MANUAL, 0.0),
                CommissionRule(UUID.randomUUID().toString(), "Qatar Airways", RuleKind.PRIVATE_MANUAL, 0.0),
                CommissionRule(UUID.randomUUID().toString(), "Emirates", RuleKind.PRIVATE_MANUAL, 0.0),
                CommissionRule(UUID.randomUUID().toString(), "Royal Jordanian", RuleKind.PRIVATE_MANUAL, 0.0),
                CommissionRule(UUID.randomUUID().toString(), "Fly Baghdad", RuleKind.PRIVATE_MANUAL, 0.0),
                CommissionRule(UUID.randomUUID().toString(), "Middle East Airlines", RuleKind.PRIVATE_MANUAL, 0.0, note = "بيروت → بغداد قد تكون له قاعدة عكسية مختلفة"),
                CommissionRule(UUID.randomUUID().toString(), "SalamAir", RuleKind.PRIVATE_MANUAL, 0.0),
                CommissionRule(UUID.randomUUID().toString(), "Flynas", RuleKind.PRIVATE_MANUAL, 0.0)
            )
            v3Defaults.forEach { rule ->
                val exists = db.rawQuery(
                    "SELECT 1 FROM commission_rules WHERE LOWER(airline)=LOWER(?) LIMIT 1",
                    arrayOf(rule.airline)
                ).use { it.moveToFirst() }
                if (!exists) insertRule(db, rule)
            }

            db.insertWithOnConflict("airline_prefixes", null, ContentValues().apply {
                put("prefix", "073")
                put("airline", "Iraqi Airways")
                put("learned", 0)
                put("updated_at", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun seedDefaults(db: SQLiteDatabase) {
        putSetting(db, "page_size", "20")
        putSetting(db, "usd_tolerance", "1.0")
        putSetting(db, "iqd_tolerance", "1000")
        putSetting(db, "issuer_whatsapp", "")
        putSetting(db, "issuer_contact_type", "GROUP")
        putSetting(db, "issuer_group_url", "https://chat.whatsapp.com/CSubCIjAE5Y0qnzWOI5Z6K?s=cl&p=a&mlu=4&ilr=4")
        putSetting(db, "review_lock", "false")
        putSetting(db, "bridge_reject_on_error", "true")
        putSetting(db, "bridge_pdf_experimental", "false")
        putSetting(db, "bridge_default_mode", "CUMULATIVE")

        val defaults = listOf(
            CommissionRule(UUID.randomUUID().toString(), "Iraqi Airways", RuleKind.PRIVATE_MANUAL, 0.0, note = "النسبة على Base Fare وتتغير حسب القاعدة/الفترة؛ تذاكر كشف IQD تُصنف عراقية تلقائيًا"),
            CommissionRule(UUID.randomUUID().toString(), "Air Arabia", RuleKind.FIXED_PER_PASSENGER, 5.0, note = "رسم إصدار +5 لكل مسافر"),
            CommissionRule(UUID.randomUUID().toString(), "Flydubai", RuleKind.FIXED_PER_PASSENGER, 5.0, note = "رسم إصدار +5 لكل مسافر"),
            CommissionRule(UUID.randomUUID().toString(), "Pegasus", RuleKind.FIXED_PER_PASSENGER, 15.0, roundTripValue = 35.0, note = "15$ اتجاه واحد / 35$ ذهاب وإياب لكل مسافر"),
            CommissionRule(UUID.randomUUID().toString(), "Turkish Airlines", RuleKind.PRIVATE_MANUAL, 0.0),
            CommissionRule(UUID.randomUUID().toString(), "Qatar Airways", RuleKind.PRIVATE_MANUAL, 0.0),
            CommissionRule(UUID.randomUUID().toString(), "Emirates", RuleKind.PRIVATE_MANUAL, 0.0),
            CommissionRule(UUID.randomUUID().toString(), "Royal Jordanian", RuleKind.PRIVATE_MANUAL, 0.0),
            CommissionRule(UUID.randomUUID().toString(), "Fly Baghdad", RuleKind.PRIVATE_MANUAL, 0.0),
            CommissionRule(UUID.randomUUID().toString(), "Middle East Airlines", RuleKind.PRIVATE_MANUAL, 0.0, note = "بيروت → بغداد قد تكون له قاعدة عكسية مختلفة"),
            CommissionRule(UUID.randomUUID().toString(), "SalamAir", RuleKind.PRIVATE_MANUAL, 0.0),
            CommissionRule(UUID.randomUUID().toString(), "Flynas", RuleKind.PRIVATE_MANUAL, 0.0)
        )
        defaults.forEach { insertRule(db, it) }
        db.insertWithOnConflict("airline_prefixes", null, ContentValues().apply {
            put("prefix", "073"); put("airline", "Iraqi Airways"); put("learned", 0); put("updated_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun putSetting(db: SQLiteDatabase, key: String, value: String) {
        db.insert("settings", null, ContentValues().apply {
            put("key", key); put("value", value)
        })
    }

    private fun insertRule(db: SQLiteDatabase, rule: CommissionRule) {
        db.insert("commission_rules", null, ContentValues().apply {
            put("id", rule.id); put("airline", rule.airline); put("kind", rule.kind.name)
            put("value", rule.value); put("round_trip_value", rule.roundTripValue)
            put("reverse_only", if (rule.reverseOnly) 1 else 0); put("direction", rule.direction); put("effective_from", rule.effectiveFrom)
            put("learned", if (rule.learned) 1 else 0); put("active", if (rule.active) 1 else 0)
            put("note", rule.note); put("updated_at", rule.updatedAt)
        })
    }

    fun setting(key: String, default: String = ""): String {
        readableDatabase.rawQuery("SELECT value FROM settings WHERE key=?", arrayOf(key)).use {
            return if (it.moveToFirst()) it.getString(0) ?: default else default
        }
    }

    fun setSetting(key: String, value: String) {
        writableDatabase.insertWithOnConflict("settings", null, ContentValues().apply {
            put("key", key); put("value", value)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun upsertParsed(parsed: ParsedTransaction, source: SourceType = SourceType.PDF): Pair<Transaction, Boolean> {
        val db = writableDatabase
        val normalizedParsed = normalizeBusiness(parsed)
        val hash = semanticHash(normalizedParsed)
        val stableExternalId = normalizedParsed.externalId ?: normalizedParsed.operationNo?.let {
            (normalizedParsed.ledgerId?.takeIf(String::isNotBlank) ?: "BC-" + normalizedParsed.currency.name) + "-" + it
        }
        val parsedWithId = normalizedParsed.copy(externalId = stableExternalId)
        val existing = stableExternalId?.let(::findByExternalId)
            ?: parsedWithId.operationNo?.let { findByOperation(it, parsedWithId.currency) }
        val tx = if (existing != null) {
            val changed = hasMaterialChange(existing, parsedWithId)
            val mergedSource = when {
                existing.source == SourceType.MANUAL && source == SourceType.BRIDGE -> SourceType.BRIDGE_MANUAL
                existing.source == SourceType.MANUAL -> SourceType.PDF_MANUAL
                else -> source
            }
            val updated = existing.copy(
                externalId = parsedWithId.externalId ?: existing.externalId,
                ledgerId = parsedWithId.ledgerId ?: existing.ledgerId,
                documentId = parsedWithId.documentId ?: existing.documentId,
                snapshotId = parsedWithId.snapshotId ?: existing.snapshotId,
                transactionDate = parsedWithId.transactionDate ?: existing.transactionDate,
                currency = parsedWithId.currency,
                type = if (existing.type == TxType.UNKNOWN || parsedWithId.type == TxType.VOID || parsedWithId.type == TxType.CHANGE) parsedWithId.type else existing.type,
                source = mergedSource,
                sourceCode = parsedWithId.sourceCode ?: existing.sourceCode,
                statementSeq = parsedWithId.statementSeq,
                batchId = parsedWithId.batchId ?: existing.batchId,
                pnr = existing.pnr ?: parsedWithId.pnr,
                route = existing.route ?: parsedWithId.route,
                amount = parsedWithId.amount,
                ledgerEffect = parsedWithId.ledgerEffect,
                discount = parsedWithId.discount,
                balanceAfter = parsedWithId.balanceAfter,
                airline = parsedWithId.airline ?: existing.airline,
                visaCountry = parsedWithId.visaCountry ?: existing.visaCountry,
                flags = parsedWithId.flags,
                rawText = parsedWithId.rawText,
                sourceHash = hash,
                warning = if (changed) "تغيّرت بيانات العملية الأساسية" else existing.warning,
                changedAfterReview = existing.changedAfterReview || (changed && existing.reviewState == ReviewState.REVIEWED)
            )
            updateTransaction(db, updated)
            updated
        } else {
            val manualCandidate = findManualCandidate(parsedWithId)
            if (manualCandidate != null) {
                val updated = manualCandidate.copy(
                    externalId = parsedWithId.externalId,
                    ledgerId = parsedWithId.ledgerId,
                    documentId = parsedWithId.documentId,
                    snapshotId = parsedWithId.snapshotId,
                    operationNo = parsedWithId.operationNo,
                    currency = parsedWithId.currency,
                    type = parsedWithId.type,
                    source = SourceType.PDF_MANUAL,
                    route = manualCandidate.route ?: parsedWithId.route,
                    amount = if (manualCandidate.amount == 0.0) parsedWithId.amount else manualCandidate.amount,
                    discount = if (manualCandidate.discount == 0.0) parsedWithId.discount else manualCandidate.discount,
                    airline = parsedWithId.airline ?: manualCandidate.airline,
                    visaCountry = parsedWithId.visaCountry ?: manualCandidate.visaCountry,
                    rawText = parsedWithId.rawText,
                    sourceHash = hash
                )
                updateTransaction(db, updated)
                updated
            } else {
                val created = Transaction(
                    id = UUID.randomUUID().toString(), externalId = parsedWithId.externalId,
                    ledgerId = parsedWithId.ledgerId, documentId = parsedWithId.documentId, snapshotId = parsedWithId.snapshotId,
                    operationNo = parsedWithId.operationNo, transactionDate = parsedWithId.transactionDate,
                    currency = parsedWithId.currency, type = parsedWithId.type, source = source,
                    sourceCode = parsedWithId.sourceCode, statementSeq = parsedWithId.statementSeq, batchId = parsedWithId.batchId,
                    pnr = parsedWithId.pnr, route = parsedWithId.route, amount = parsedWithId.amount,
                    ledgerEffect = parsedWithId.ledgerEffect, discount = parsedWithId.discount, balanceAfter = parsedWithId.balanceAfter,
                    airline = parsedWithId.airline, visaCountry = parsedWithId.visaCountry,
                    rawText = parsedWithId.rawText, sourceHash = hash, flags = parsedWithId.flags,
                    warning = if (parsedWithId.type == TxType.UNKNOWN) "حالة مبهمة تحتاج مراجعة" else null
                )
                insertTransaction(db, created)
                created
            }
        }
        parsedWithId.passengers.forEach { p ->
            val passenger = findOrCreatePassenger(p.name, p.passport)
            linkPassenger(tx.id, passenger.id, p.amount, p.passengerType, p.documentNo, p.product, p.flags)
        }
        return tx to (existing == null)
    }

    private fun findManualCandidate(parsed: ParsedTransaction): Transaction? {
        if (parsed.pnr.isNullOrBlank()) return null
        val candidates = transactions(search = parsed.pnr, limit = 20).filter { it.source == SourceType.MANUAL && it.pnr.equals(parsed.pnr, true) }
        return candidates.minByOrNull { kotlin.math.abs(it.amount - parsed.amount) }
            ?.takeIf { kotlin.math.abs(it.amount - parsed.amount) <= if (parsed.currency == Currency.USD) 1.0 else 1000.0 }
    }

    fun addManual(
        type: TxType, currency: Currency, pnr: String?, route: String?, amount: Double,
        discount: Double, baseFare: Double?, referenceTotal: Double?, airline: String?, passengerNames: List<String>, note: String?
    ): Transaction {
        val tx = Transaction(
            id = UUID.randomUUID().toString(), currency = currency, type = type,
            source = SourceType.MANUAL, pnr = pnr?.trim()?.uppercase()?.ifBlank { null },
            route = route?.trim()?.ifBlank { null }, amount = amount, discount = discount,
            baseFare = baseFare, referenceTotal = referenceTotal, airline = airline?.trim()?.ifBlank { null }, note = note
        )
        insertTransaction(writableDatabase, tx)
        passengerNames.filter { it.isNotBlank() }.forEach { name ->
            val p = findOrCreatePassenger(name, null)
            linkPassenger(tx.id, p.id, null)
        }
        audit("transaction", tx.id, "manual_add", "${tx.type} ${tx.pnr ?: ""}")
        return tx
    }

    private fun insertTransaction(db: SQLiteDatabase, tx: Transaction) {
        db.insertOrThrow("transactions", null, txValues(tx))
    }

    private fun updateTransaction(db: SQLiteDatabase, tx: Transaction) {
        db.update("transactions", txValues(tx), "id=?", arrayOf(tx.id))
    }

    private fun txValues(tx: Transaction) = ContentValues().apply {
        put("id", tx.id); put("external_id", tx.externalId); put("ledger_id", tx.ledgerId); put("document_id", tx.documentId); put("snapshot_id", tx.snapshotId)
        put("operation_no", tx.operationNo); put("transaction_date", tx.transactionDate); put("currency", tx.currency.name)
        put("type", tx.type.name); put("source", tx.source.name); put("source_code", tx.sourceCode); put("statement_seq", tx.statementSeq); put("batch_id", tx.batchId)
        put("pnr", tx.pnr); put("route", tx.route)
        put("amount", tx.amount); put("ledger_effect", tx.ledgerEffect); put("discount", tx.discount); put("balance_after", tx.balanceAfter)
        put("base_fare", tx.baseFare); put("reference_total", tx.referenceTotal); put("airline", tx.airline); put("visa_country", tx.visaCountry)
        put("review_state", tx.reviewState.name); put("warning", tx.warning); put("note", tx.note); put("flags", tx.flags); put("raw_text", tx.rawText)
        put("source_hash", tx.sourceHash); put("imported_at", tx.importedAt); put("reviewed_at", tx.reviewedAt)
        put("changed_after_review", if (tx.changedAfterReview) 1 else 0)
    }

    fun findByExternalId(externalId: String): Transaction? {
        readableDatabase.rawQuery("SELECT * FROM transactions WHERE external_id=? LIMIT 1", arrayOf(externalId)).use {
            return if (it.moveToFirst()) it.toTransaction() else null
        }
    }

    fun findByOperation(operationNo: String, currency: Currency): Transaction? {
        readableDatabase.rawQuery("SELECT * FROM transactions WHERE operation_no=? AND currency=? LIMIT 1", arrayOf(operationNo, currency.name)).use {
            return if (it.moveToFirst()) it.toTransaction() else null
        }
    }

    fun transaction(id: String): Transaction? {
        readableDatabase.rawQuery("SELECT * FROM transactions WHERE id=? LIMIT 1", arrayOf(id)).use {
            return if (it.moveToFirst()) it.toTransaction() else null
        }
    }

    fun transactions(
        search: String = "", types: Set<TxType> = emptySet(), reviewStates: Set<ReviewState> = emptySet(),
        limit: Int = 100, offset: Int = 0
    ): List<Transaction> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (search.isNotBlank()) {
            where += "(pnr LIKE ? OR operation_no LIKE ? OR airline LIKE ? OR visa_country LIKE ? OR note LIKE ? OR id IN (SELECT tp.tx_id FROM tx_passengers tp JOIN passengers p ON p.id=tp.passenger_id WHERE p.normalized_name LIKE ? OR p.passport LIKE ? OR tp.document_no LIKE ?))"
            val q = "%${normalize(search)}%"
            val raw = "%${search.trim()}%"
            args += listOf(raw, raw, raw, raw, raw, q, raw, raw)
        }
        if (types.isNotEmpty()) {
            where += "type IN (${types.joinToString(",") { "?" }})"
            args += types.map { it.name }
        }
        if (reviewStates.isNotEmpty()) {
            where += "review_state IN (${reviewStates.joinToString(",") { "?" }})"
            args += reviewStates.map { it.name }
        }
        val sql = buildString {
            append("SELECT * FROM transactions")
            if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
            append(" ORDER BY CASE review_state WHEN 'UNREVIEWED' THEN 0 WHEN 'FOLLOW_UP' THEN 1 ELSE 2 END, changed_after_review DESC, imported_at DESC LIMIT ? OFFSET ?")
        }
        args += limit.toString(); args += offset.toString()
        val out = mutableListOf<Transaction>()
        readableDatabase.rawQuery(sql, args.toTypedArray()).use { c -> while (c.moveToNext()) out += c.toTransaction() }
        return out
    }

    fun passengersFor(txId: String): List<Passenger> {
        val out = mutableListOf<Passenger>()
        readableDatabase.rawQuery("""
            SELECT p.* FROM passengers p JOIN tx_passengers tp ON tp.passenger_id=p.id WHERE tp.tx_id=? ORDER BY p.name
        """.trimIndent(), arrayOf(txId)).use { c ->
            while (c.moveToNext()) out += c.toPassenger()
        }
        return out
    }

    fun passengerSuggestions(query: String, limit: Int = 8): List<Passenger> {
        if (query.isBlank()) return emptyList()
        val out = mutableListOf<Passenger>()
        readableDatabase.rawQuery(
            "SELECT * FROM passengers WHERE normalized_name LIKE ? OR passport LIKE ? ORDER BY name LIMIT ?",
            arrayOf("%${normalize(query)}%", "%${query.trim()}%", limit.toString())
        ).use { c -> while (c.moveToNext()) out += c.toPassenger() }
        return out
    }

    fun allPassengers(limit: Int = 200): List<Passenger> {
        val out = mutableListOf<Passenger>()
        readableDatabase.rawQuery("SELECT * FROM passengers ORDER BY name LIMIT ?", arrayOf(limit.toString())).use { c ->
            while (c.moveToNext()) out += c.toPassenger()
        }
        return out
    }

    fun findOrCreatePassenger(name: String, passport: String?): Passenger {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        val norm = normalize(clean)
        val sql: String
        val args: Array<String>
        if (passport.isNullOrBlank()) {
            sql = "SELECT * FROM passengers WHERE normalized_name=? LIMIT 1"
            args = arrayOf(norm)
        } else {
            sql = "SELECT * FROM passengers WHERE normalized_name=? OR passport=? LIMIT 1"
            args = arrayOf(norm, passport)
        }
        readableDatabase.rawQuery(sql, args).use { c ->
            if (c.moveToFirst()) return c.toPassenger()
        }
        val p = Passenger(UUID.randomUUID().toString(), clean, passport)
        writableDatabase.insert("passengers", null, ContentValues().apply {
            put("id", p.id); put("name", p.name); put("normalized_name", norm); put("passport", passport)
            putNull("phone"); putNull("responsible_id"); putNull("responsible_relation"); put("is_responsible", 0)
        })
        return p
    }

    private fun linkPassenger(
        txId: String,
        passengerId: String,
        amount: Double?,
        passengerType: String? = null,
        documentNo: String? = null,
        product: String? = null,
        flags: String? = null
    ) {
        val existingBase = readableDatabase.rawQuery(
            "SELECT base_fare FROM tx_passengers WHERE tx_id=? AND passenger_id=?",
            arrayOf(txId, passengerId)
        ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getDouble(0) else null }
        writableDatabase.insertWithOnConflict("tx_passengers", null, ContentValues().apply {
            put("tx_id", txId); put("passenger_id", passengerId); put("amount", amount); put("base_fare", existingBase)
            put("passenger_type", passengerType); put("document_no", documentNo); put("product", product); put("flags", flags)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun markReview(id: String, state: ReviewState) {
        writableDatabase.update("transactions", ContentValues().apply {
            put("review_state", state.name)
            if (state == ReviewState.REVIEWED) put("reviewed_at", System.currentTimeMillis()) else putNull("reviewed_at")
        }, "id=?", arrayOf(id))
        audit("transaction", id, "review", state.name)
    }

    fun updateTransactionFields(tx: Transaction) {
        updateTransaction(writableDatabase, tx)
        audit("transaction", tx.id, "edit", "manual edit")
    }

    fun deleteTransaction(id: String) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("tx_passengers", "tx_id=?", arrayOf(id))
            writableDatabase.delete("transactions", "id=?", arrayOf(id))
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        audit("transaction", id, "delete", "deleted")
    }

    fun dashboardStats(): DashboardStats {
        fun count(where: String = "1=1"): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM transactions WHERE $where", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        return DashboardStats(
            total = count(),
            newCount = count("imported_at > ${System.currentTimeMillis() - 7L*24*60*60*1000}"),
            unreviewed = count("review_state='UNREVIEWED'"),
            ambiguous = count("type='UNKNOWN' OR (type='TICKET' AND currency='USD' AND (airline IS NULL OR airline=''))"),
            changed = count("changed_after_review=1"),
            payments = count("type='PAYMENT'")
        )
    }

    fun rules(): List<CommissionRule> {
        val out = mutableListOf<CommissionRule>()
        readableDatabase.rawQuery("SELECT * FROM commission_rules WHERE active=1 ORDER BY airline", null).use { c ->
            while (c.moveToNext()) out += CommissionRule(
                id = c.s("id"), airline = c.s("airline"), kind = RuleKind.valueOf(c.s("kind")), value = c.d("value"),
                roundTripValue = c.dn("round_trip_value"), reverseOnly = c.i("reverse_only") == 1,
                direction = c.sn("direction") ?: "ANY", effectiveFrom = c.sn("effective_from"),
                learned = c.i("learned") == 1, active = c.i("active") == 1, note = c.sn("note"), updatedAt = c.l("updated_at")
            )
        }
        return out
    }

    fun saveRule(rule: CommissionRule) {
        writableDatabase.insertWithOnConflict("commission_rules", null, ContentValues().apply {
            put("id", rule.id); put("airline", rule.airline); put("kind", rule.kind.name); put("value", rule.value)
            put("round_trip_value", rule.roundTripValue); put("reverse_only", if (rule.reverseOnly) 1 else 0)
            put("direction", rule.direction); put("effective_from", rule.effectiveFrom); put("learned", if (rule.learned) 1 else 0)
            put("active", if (rule.active) 1 else 0); put("note", rule.note); put("updated_at", rule.updatedAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun applyLearnedType(rawText: String, fallback: TxType): TxType {
        readableDatabase.rawQuery("SELECT contains_text,target_type FROM classification_rules WHERE active=1 ORDER BY created_at DESC", null).use { c ->
            while (c.moveToNext()) {
                val pattern = c.getString(0)
                if (rawText.contains(pattern, ignoreCase = true)) return TxType.valueOf(c.getString(1))
            }
        }
        return fallback
    }

    fun learnClassification(rawText: String?, target: TxType) {
        if (rawText.isNullOrBlank()) return
        val candidates = listOf("تغيير", "الغاء", "إلغاء", "New Change", "New Refund", "TicketOperation", "Visa Sale", "Sale Tickets")
        val pattern = candidates.firstOrNull { rawText.contains(it, ignoreCase = true) } ?: return
        val existing = readableDatabase.rawQuery("SELECT id FROM classification_rules WHERE contains_text=? AND target_type=? LIMIT 1", arrayOf(pattern, target.name)).use { c -> if (c.moveToFirst()) c.getString(0) else null }
        if (existing != null) return
        writableDatabase.insert("classification_rules", null, ContentValues().apply {
            put("id", UUID.randomUUID().toString()); put("contains_text", pattern); put("target_type", target.name); put("active", 1); put("created_at", System.currentTimeMillis())
        })
        audit("classification_rule", pattern, "learn", target.name)
    }

    fun audit(entityType: String, entityId: String, action: String, details: String?) {
        writableDatabase.insert("audit_log", null, ContentValues().apply {
            put("id", UUID.randomUUID().toString()); put("entity_type", entityType); put("entity_id", entityId)
            put("action", action); put("details", details); put("created_at", System.currentTimeMillis())
        })
    }

    private fun Cursor.toTransaction() = Transaction(
        id = s("id"), externalId = sn("external_id"), ledgerId = sn("ledger_id"), documentId = sn("document_id"), snapshotId = sn("snapshot_id"),
        operationNo = sn("operation_no"), transactionDate = sn("transaction_date"), currency = Currency.valueOf(s("currency")),
        type = TxType.valueOf(s("type")), source = SourceType.valueOf(s("source")), sourceCode = sn("source_code"),
        statementSeq = inn("statement_seq"), batchId = sn("batch_id"), pnr = sn("pnr"), route = sn("route"),
        amount = d("amount"), ledgerEffect = dn("ledger_effect"), discount = d("discount"), balanceAfter = dn("balance_after"),
        baseFare = dn("base_fare"), referenceTotal = dn("reference_total"), airline = sn("airline"), visaCountry = sn("visa_country"),
        reviewState = ReviewState.valueOf(s("review_state")), warning = sn("warning"), note = sn("note"), flags = sn("flags"), rawText = sn("raw_text"),
        sourceHash = sn("source_hash"), importedAt = l("imported_at"), reviewedAt = ln("reviewed_at"), changedAfterReview = i("changed_after_review") == 1
    )

    private fun Cursor.toPassenger() = Passenger(
        id = s("id"), name = s("name"), passport = sn("passport"), phone = sn("phone"),
        responsibleId = sn("responsible_id"), responsibleRelation = sn("responsible_relation"),
        isResponsible = i("is_responsible") == 1
    )

    fun txPassengerDetails(txId: String): List<TxPassengerDetail> {
        val out = mutableListOf<TxPassengerDetail>()
        readableDatabase.rawQuery("""
            SELECT p.*, tp.amount AS tp_amount, tp.base_fare AS tp_base_fare, tp.passenger_type AS tp_type,
                   tp.document_no AS tp_document, tp.product AS tp_product, tp.flags AS tp_flags
            FROM passengers p JOIN tx_passengers tp ON tp.passenger_id=p.id
            WHERE tp.tx_id=? ORDER BY p.name
        """.trimIndent(), arrayOf(txId)).use { c ->
            while (c.moveToNext()) {
                out += TxPassengerDetail(
                    passenger = c.toPassenger(),
                    amount = c.dn("tp_amount"),
                    baseFare = c.dn("tp_base_fare"),
                    passengerType = c.sn("tp_type"),
                    documentNo = c.sn("tp_document"),
                    product = c.sn("tp_product"),
                    flags = c.sn("tp_flags")
                )
            }
        }
        return out
    }

    fun setPassengerBaseFare(txId: String, passengerId: String, baseFare: Double?) {
        writableDatabase.update("tx_passengers", ContentValues().apply {
            if (baseFare == null) putNull("base_fare") else put("base_fare", baseFare)
        }, "tx_id=? AND passenger_id=?", arrayOf(txId, passengerId))
        audit("tx_passenger", "$txId/$passengerId", "base_fare", baseFare?.toString())
    }

    fun updatePassenger(person: Passenger) {
        writableDatabase.update("passengers", ContentValues().apply {
            put("name", person.name.trim()); put("normalized_name", normalize(person.name)); put("passport", person.passport)
            put("phone", person.phone); put("responsible_id", person.responsibleId); put("responsible_relation", person.responsibleRelation)
            put("is_responsible", if (person.isResponsible) 1 else 0)
        }, "id=?", arrayOf(person.id))
        audit("passenger", person.id, "edit", person.name)
    }

    fun assignResponsible(passengerId: String, responsibleId: String?, relation: String? = null) {
        writableDatabase.update("passengers", ContentValues().apply {
            put("responsible_id", responsibleId); put("responsible_relation", relation)
        }, "id=?", arrayOf(passengerId))
        if (responsibleId != null) {
            writableDatabase.update("passengers", ContentValues().apply { put("is_responsible", 1) }, "id=?", arrayOf(responsibleId))
        }
        audit("passenger", passengerId, "assign_responsible", responsibleId)
    }

    fun dependentsOf(responsibleId: String): List<Passenger> {
        val out = mutableListOf<Passenger>()
        readableDatabase.rawQuery("SELECT * FROM passengers WHERE responsible_id=? ORDER BY name", arrayOf(responsibleId)).use { c ->
            while (c.moveToNext()) out += c.toPassenger()
        }
        return out
    }

    fun passengerById(id: String): Passenger? {
        readableDatabase.rawQuery("SELECT * FROM passengers WHERE id=? LIMIT 1", arrayOf(id)).use { c ->
            return if (c.moveToFirst()) c.toPassenger() else null
        }
    }

    fun setAirlineForTransaction(txId: String, airline: String?, learnPrefix: Boolean = true) {
        val clean = airline?.trim()?.takeIf { it.isNotBlank() }
        writableDatabase.update("transactions", ContentValues().apply { put("airline", clean) }, "id=?", arrayOf(txId))
        if (learnPrefix && clean != null) {
            txPassengerDetails(txId).mapNotNull { ticketPrefix(it.documentNo) }.distinct().forEach { prefix ->
                writableDatabase.insertWithOnConflict("airline_prefixes", null, ContentValues().apply {
                    put("prefix", prefix); put("airline", clean); put("learned", 1); put("updated_at", System.currentTimeMillis())
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
        audit("transaction", txId, "airline", clean)
    }

    fun customerPhoneForTransaction(txId: String): String? {
        val pax = passengersFor(txId)
        for (p in pax) {
            val responsiblePhone = p.responsibleId?.let(::passengerById)?.phone
            if (!responsiblePhone.isNullOrBlank()) return responsiblePhone
        }
        return pax.firstOrNull { !it.phone.isNullOrBlank() }?.phone
    }

    fun airlineNames(): List<String> = rules().map { it.airline }.distinct().sorted()

    private fun normalizeBusiness(parsed: ParsedTransaction): ParsedTransaction {
        var type = parsed.type
        val note = parsed.note.orEmpty()
        if (type == TxType.TICKET && note.contains("تغيير", true)) type = TxType.CHANGE
        if (type == TxType.TICKET && parsed.amount == 0.0 && parsed.flags.orEmpty().contains("VOID", true)) type = TxType.VOID
        if (type == TxType.VISA && parsed.amount == 0.0) type = TxType.VOID

        val visa = parsed.visaCountry ?: parsed.passengers.asSequence().mapNotNull { visaCountryFromProduct(it.product) }.firstOrNull()
        val airline = when {
            !parsed.airline.isNullOrBlank() -> parsed.airline
            type == TxType.TICKET && parsed.currency == Currency.IQD -> "Iraqi Airways"
            type == TxType.TICKET -> parsed.passengers.asSequence()
                .mapNotNull { ticketPrefix(it.documentNo) }
                .mapNotNull(::airlineByPrefix)
                .firstOrNull()
            else -> null
        }
        return parsed.copy(type = type, airline = airline, visaCountry = visa)
    }

    private fun ticketPrefix(documentNo: String?): String? =
        documentNo?.filter(Char::isDigit)?.takeIf { it.length >= 3 }?.take(3)

    private fun airlineByPrefix(prefix: String): String? =
        readableDatabase.rawQuery("SELECT airline FROM airline_prefixes WHERE prefix=? LIMIT 1", arrayOf(prefix)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun visaCountryFromProduct(product: String?): String? {
        val p = product.orEmpty().uppercase()
        return when {
            "UAE" in p || "الامارات" in p || "الإمارات" in p -> "UAE"
            "JORDAN" in p || "الاردن" in p || "الأردن" in p -> "JORDAN"
            "EGYPT" in p || "مصر" in p -> "EGYPT"
            "SAUDI" in p || "السعود" in p -> "SAUDI"
            else -> null
        }
    }

    private fun Cursor.s(col: String) = getString(getColumnIndexOrThrow(col))
    private fun Cursor.sn(col: String): String? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.d(col: String) = getDouble(getColumnIndexOrThrow(col))
    private fun Cursor.dn(col: String): Double? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getDouble(it) }
    private fun Cursor.i(col: String) = getInt(getColumnIndexOrThrow(col))
    private fun Cursor.inn(col: String): Int? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getInt(it) }
    private fun Cursor.l(col: String) = getLong(getColumnIndexOrThrow(col))
    private fun Cursor.ln(col: String): Long? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getLong(it) }

    private fun hasMaterialChange(existing: Transaction, parsed: ParsedTransaction): Boolean {
        fun norm(v: String?) = v.orEmpty().trim().uppercase()
        if (existing.currency != parsed.currency) return true
        if (existing.transactionDate != null && parsed.transactionDate != null && existing.transactionDate != parsed.transactionDate) return true
        if (existing.type != TxType.UNKNOWN && parsed.type != TxType.UNKNOWN && existing.type != parsed.type) return true
        if (kotlin.math.abs(existing.amount - parsed.amount) > 0.0001) return true
        if (kotlin.math.abs(existing.discount - parsed.discount) > 0.0001) return true
        if (norm(existing.pnr) != norm(parsed.pnr) && existing.pnr != null && parsed.pnr != null) return true
        if (norm(existing.route) != norm(parsed.route) && existing.route != null && parsed.route != null) return true
        if (parsed.passengers.isNotEmpty()) {
            val existingNames = passengersFor(existing.id).map { normalize(it.name) }.sorted()
            val parsedNames = parsed.passengers.map { normalize(it.name) }.sorted()
            if (existingNames.isNotEmpty() && existingNames != parsedNames) return true
        }
        return false
    }

    private fun semanticHash(parsed: ParsedTransaction): String {
        val pax = parsed.passengers
            .map { listOf(normalize(it.name), it.passengerType.orEmpty(), it.documentNo.orEmpty(), it.amount?.toString().orEmpty(), it.passport.orEmpty()).joinToString("~") }
            .sorted()
            .joinToString(";")
        val core = listOf(
            parsed.currency.name,
            parsed.operationNo.orEmpty(),
            parsed.transactionDate.orEmpty(),
            parsed.sourceCode.orEmpty(),
            parsed.type.name,
            parsed.pnr.orEmpty().uppercase(),
            parsed.route.orEmpty().uppercase(),
            parsed.amount.toString(),
            parsed.discount.toString(),
            pax,
            parsed.flags.orEmpty()
        ).joinToString("|")
        return sha256(core)
    }

    companion object {
        fun normalize(value: String): String = value.lowercase()
            .replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ى','ي').replace('ة','ه')
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
