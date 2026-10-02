package com.eslam.check.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.util.UUID

class AppDatabase(context: Context) : SQLiteOpenHelper(context, "eslam_check.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE transactions(
                id TEXT PRIMARY KEY,
                operation_no TEXT,
                currency TEXT NOT NULL,
                type TEXT NOT NULL,
                source TEXT NOT NULL,
                pnr TEXT,
                route TEXT,
                amount REAL NOT NULL DEFAULT 0,
                discount REAL NOT NULL DEFAULT 0,
                base_fare REAL,
                reference_total REAL,
                airline TEXT,
                review_state TEXT NOT NULL,
                warning TEXT,
                note TEXT,
                raw_text TEXT,
                source_hash TEXT,
                imported_at INTEGER NOT NULL,
                reviewed_at INTEGER,
                changed_after_review INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX idx_tx_operation ON transactions(operation_no) WHERE operation_no IS NOT NULL AND operation_no <> ''")
        db.execSQL("CREATE INDEX idx_tx_pnr ON transactions(pnr)")
        db.execSQL("CREATE INDEX idx_tx_type ON transactions(type)")
        db.execSQL("CREATE INDEX idx_tx_review ON transactions(review_state)")

        db.execSQL("""
            CREATE TABLE passengers(
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                normalized_name TEXT NOT NULL,
                passport TEXT,
                responsible_id TEXT
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
                PRIMARY KEY(tx_id, passenger_id)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE commission_rules(
                id TEXT PRIMARY KEY,
                airline TEXT NOT NULL,
                kind TEXT NOT NULL,
                value REAL NOT NULL,
                reverse_only INTEGER NOT NULL DEFAULT 0,
                learned INTEGER NOT NULL DEFAULT 0,
                active INTEGER NOT NULL DEFAULT 1,
                note TEXT,
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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun seedDefaults(db: SQLiteDatabase) {
        putSetting(db, "page_size", "20")
        putSetting(db, "usd_tolerance", "1.0")
        putSetting(db, "iqd_tolerance", "1000")
        putSetting(db, "issuer_whatsapp", "")
        putSetting(db, "review_lock", "false")

        val defaults = listOf(
            CommissionRule(UUID.randomUUID().toString(), "Iraqi Airways", RuleKind.PERCENT_BASE, 4.0, note = "قابل للتعديل"),
            CommissionRule(UUID.randomUUID().toString(), "Flydubai", RuleKind.FIXED_PER_PASSENGER, 5.0),
            CommissionRule(UUID.randomUUID().toString(), "Air Arabia", RuleKind.FIXED_PER_PASSENGER, 5.0),
            CommissionRule(UUID.randomUUID().toString(), "SalamAir", RuleKind.FIXED_PER_PASSENGER, 5.0),
            CommissionRule(UUID.randomUUID().toString(), "Flynas", RuleKind.FIXED_PER_PASSENGER, 5.0)
        )
        defaults.forEach { insertRule(db, it) }
    }

    private fun putSetting(db: SQLiteDatabase, key: String, value: String) {
        db.insert("settings", null, ContentValues().apply {
            put("key", key); put("value", value)
        })
    }

    private fun insertRule(db: SQLiteDatabase, rule: CommissionRule) {
        db.insert("commission_rules", null, ContentValues().apply {
            put("id", rule.id); put("airline", rule.airline); put("kind", rule.kind.name)
            put("value", rule.value); put("reverse_only", if (rule.reverseOnly) 1 else 0)
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
        val hash = sha256(parsed.rawText.trim())
        val existing = parsed.operationNo?.let { findByOperation(it) }
        val tx = if (existing != null) {
            val changed = existing.sourceHash != null && existing.sourceHash != hash
            val mergedSource = if (existing.source == SourceType.MANUAL) SourceType.PDF_MANUAL else existing.source
            val updated = existing.copy(
                currency = parsed.currency,
                type = if (existing.type == TxType.UNKNOWN) parsed.type else existing.type,
                source = mergedSource,
                pnr = existing.pnr ?: parsed.pnr,
                route = existing.route ?: parsed.route,
                amount = parsed.amount,
                discount = parsed.discount,
                rawText = parsed.rawText,
                sourceHash = hash,
                warning = if (changed) "تغيّرت بيانات عملية قديمة" else existing.warning,
                changedAfterReview = existing.changedAfterReview || (changed && existing.reviewState == ReviewState.REVIEWED)
            )
            updateTransaction(db, updated)
            updated
        } else {
            val manualCandidate = findManualCandidate(parsed)
            if (manualCandidate != null) {
                val updated = manualCandidate.copy(
                    operationNo = parsed.operationNo,
                    currency = parsed.currency,
                    source = SourceType.PDF_MANUAL,
                    route = manualCandidate.route ?: parsed.route,
                    amount = if (manualCandidate.amount == 0.0) parsed.amount else manualCandidate.amount,
                    discount = if (manualCandidate.discount == 0.0) parsed.discount else manualCandidate.discount,
                    rawText = parsed.rawText,
                    sourceHash = hash
                )
                updateTransaction(db, updated)
                updated
            } else {
                val created = Transaction(
                    id = UUID.randomUUID().toString(), operationNo = parsed.operationNo,
                    currency = parsed.currency, type = parsed.type, source = source,
                    pnr = parsed.pnr, route = parsed.route, amount = parsed.amount,
                    discount = parsed.discount, rawText = parsed.rawText, sourceHash = hash,
                    warning = if (parsed.type == TxType.UNKNOWN) "حالة مبهمة تحتاج مراجعة" else null
                )
                insertTransaction(db, created)
                created
            }
        }
        parsed.passengers.forEach { p ->
            val passenger = findOrCreatePassenger(p.name, p.passport)
            linkPassenger(tx.id, passenger.id, p.amount)
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
        put("id", tx.id); put("operation_no", tx.operationNo); put("currency", tx.currency.name)
        put("type", tx.type.name); put("source", tx.source.name); put("pnr", tx.pnr); put("route", tx.route)
        put("amount", tx.amount); put("discount", tx.discount); put("base_fare", tx.baseFare); put("reference_total", tx.referenceTotal); put("airline", tx.airline)
        put("review_state", tx.reviewState.name); put("warning", tx.warning); put("note", tx.note); put("raw_text", tx.rawText)
        put("source_hash", tx.sourceHash); put("imported_at", tx.importedAt); put("reviewed_at", tx.reviewedAt)
        put("changed_after_review", if (tx.changedAfterReview) 1 else 0)
    }

    fun findByOperation(operationNo: String): Transaction? {
        readableDatabase.rawQuery("SELECT * FROM transactions WHERE operation_no=? LIMIT 1", arrayOf(operationNo)).use {
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
            where += "(pnr LIKE ? OR operation_no LIKE ? OR note LIKE ? OR id IN (SELECT tp.tx_id FROM tx_passengers tp JOIN passengers p ON p.id=tp.passenger_id WHERE p.normalized_name LIKE ?))"
            val q = "%${normalize(search)}%"
            args += listOf("%${search.trim()}%", "%${search.trim()}%", "%${search.trim()}%", q)
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
            while (c.moveToNext()) out += Passenger(c.s("id"), c.s("name"), c.sn("passport"), c.sn("responsible_id"))
        }
        return out
    }

    fun passengerSuggestions(query: String, limit: Int = 8): List<Passenger> {
        if (query.isBlank()) return emptyList()
        val out = mutableListOf<Passenger>()
        readableDatabase.rawQuery(
            "SELECT * FROM passengers WHERE normalized_name LIKE ? OR passport LIKE ? ORDER BY name LIMIT ?",
            arrayOf("%${normalize(query)}%", "%${query.trim()}%", limit.toString())
        ).use { c -> while (c.moveToNext()) out += Passenger(c.s("id"), c.s("name"), c.sn("passport"), c.sn("responsible_id")) }
        return out
    }

    fun allPassengers(limit: Int = 200): List<Passenger> {
        val out = mutableListOf<Passenger>()
        readableDatabase.rawQuery("SELECT * FROM passengers ORDER BY name LIMIT ?", arrayOf(limit.toString())).use { c ->
            while (c.moveToNext()) out += Passenger(c.s("id"), c.s("name"), c.sn("passport"), c.sn("responsible_id"))
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
            if (c.moveToFirst()) return Passenger(c.s("id"), c.s("name"), c.sn("passport"), c.sn("responsible_id"))
        }
        val p = Passenger(UUID.randomUUID().toString(), clean, passport)
        writableDatabase.insert("passengers", null, ContentValues().apply {
            put("id", p.id); put("name", p.name); put("normalized_name", norm); put("passport", passport); putNull("responsible_id")
        })
        return p
    }

    private fun linkPassenger(txId: String, passengerId: String, amount: Double?) {
        writableDatabase.insertWithOnConflict("tx_passengers", null, ContentValues().apply {
            put("tx_id", txId); put("passenger_id", passengerId); put("amount", amount)
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
            ambiguous = count("type='UNKNOWN'"),
            changed = count("changed_after_review=1"),
            payments = count("type='PAYMENT'")
        )
    }

    fun rules(): List<CommissionRule> {
        val out = mutableListOf<CommissionRule>()
        readableDatabase.rawQuery("SELECT * FROM commission_rules WHERE active=1 ORDER BY airline", null).use { c ->
            while (c.moveToNext()) out += CommissionRule(
                c.s("id"), c.s("airline"), RuleKind.valueOf(c.s("kind")), c.d("value"),
                c.i("reverse_only") == 1, c.i("learned") == 1, c.i("active") == 1, c.sn("note"), c.l("updated_at")
            )
        }
        return out
    }

    fun saveRule(rule: CommissionRule) {
        writableDatabase.insertWithOnConflict("commission_rules", null, ContentValues().apply {
            put("id", rule.id); put("airline", rule.airline); put("kind", rule.kind.name); put("value", rule.value)
            put("reverse_only", if (rule.reverseOnly) 1 else 0); put("learned", if (rule.learned) 1 else 0)
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
        id = s("id"), operationNo = sn("operation_no"), currency = Currency.valueOf(s("currency")),
        type = TxType.valueOf(s("type")), source = SourceType.valueOf(s("source")), pnr = sn("pnr"), route = sn("route"),
        amount = d("amount"), discount = d("discount"), baseFare = dn("base_fare"), referenceTotal = dn("reference_total"), airline = sn("airline"),
        reviewState = ReviewState.valueOf(s("review_state")), warning = sn("warning"), note = sn("note"), rawText = sn("raw_text"),
        sourceHash = sn("source_hash"), importedAt = l("imported_at"), reviewedAt = ln("reviewed_at"), changedAfterReview = i("changed_after_review") == 1
    )

    private fun Cursor.s(col: String) = getString(getColumnIndexOrThrow(col))
    private fun Cursor.sn(col: String): String? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.d(col: String) = getDouble(getColumnIndexOrThrow(col))
    private fun Cursor.dn(col: String): Double? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getDouble(it) }
    private fun Cursor.i(col: String) = getInt(getColumnIndexOrThrow(col))
    private fun Cursor.l(col: String) = getLong(getColumnIndexOrThrow(col))
    private fun Cursor.ln(col: String): Long? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getLong(it) }

    companion object {
        fun normalize(value: String): String = value.lowercase()
            .replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ى','ي').replace('ة','ه')
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
