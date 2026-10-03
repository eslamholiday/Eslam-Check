package com.eslam.check.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.util.UUID

class AppDatabase(context: Context) : SQLiteOpenHelper(context, "eslam_check.db", null, 9) {
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
                external_link TEXT,
                commission_rule_snapshot TEXT,
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
                is_responsible INTEGER NOT NULL DEFAULT 0,
                merged_into_id TEXT,
                rating INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_passenger_name ON passengers(normalized_name)")
        db.execSQL("CREATE INDEX idx_passenger_passport ON passengers(passport)")
        db.execSQL("CREATE INDEX idx_passenger_merged_into ON passengers(merged_into_id)")

        db.execSQL("""
            CREATE TABLE passenger_aliases(
                id TEXT PRIMARY KEY,
                passenger_id TEXT NOT NULL,
                kind TEXT NOT NULL,
                value TEXT NOT NULL,
                normalized_value TEXT NOT NULL,
                source_passenger_id TEXT,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_passenger_alias_lookup ON passenger_aliases(kind, normalized_value)")
        db.execSQL("CREATE INDEX idx_passenger_alias_owner ON passenger_aliases(passenger_id)")

        db.execSQL("""
            CREATE TABLE deleted_passenger_aliases(
                id TEXT PRIMARY KEY,
                passenger_id TEXT NOT NULL,
                kind TEXT NOT NULL,
                normalized_value TEXT NOT NULL,
                deleted_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX idx_deleted_alias_unique ON deleted_passenger_aliases(passenger_id, kind, normalized_value)")

        db.execSQL("""
            CREATE TABLE passenger_files(
                id TEXT PRIMARY KEY,
                passenger_id TEXT NOT NULL,
                uri TEXT NOT NULL,
                mime_type TEXT,
                display_name TEXT,
                is_primary INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_passenger_files_owner ON passenger_files(passenger_id)")

        db.execSQL("""
            CREATE TABLE transaction_attachments(
                id TEXT PRIMARY KEY,
                tx_id TEXT NOT NULL,
                uri TEXT NOT NULL,
                mime_type TEXT,
                display_name TEXT,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_tx_attachments_owner ON transaction_attachments(tx_id)")

        db.execSQL("""
            CREATE TABLE deleted_tx_passengers(
                id TEXT PRIMARY KEY,
                tx_id TEXT NOT NULL,
                currency TEXT NOT NULL,
                operation_no TEXT,
                passenger_id TEXT NOT NULL,
                passenger_name TEXT NOT NULL,
                normalized_name TEXT NOT NULL,
                normalized_passport TEXT,
                amount REAL,
                base_fare REAL,
                passenger_type TEXT,
                document_no TEXT,
                product TEXT,
                flags TEXT,
                deleted_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_deleted_tx_pax_tx ON deleted_tx_passengers(tx_id)")
        db.execSQL("CREATE INDEX idx_deleted_tx_pax_key ON deleted_tx_passengers(currency, operation_no)")

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
                source_name TEXT,
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
            CREATE TABLE airlines(
                id TEXT PRIMARY KEY,
                code TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL UNIQUE,
                active INTEGER NOT NULL DEFAULT 1,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE visa_price_rules(
                id TEXT PRIMARY KEY,
                country TEXT NOT NULL,
                visa_type TEXT,
                price REAL,
                currency TEXT NOT NULL,
                active INTEGER NOT NULL DEFAULT 1,
                note TEXT,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_visa_price_country ON visa_price_rules(country, currency, active)")

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
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN external_link TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN commission_rule_snapshot TEXT")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS passenger_files(
                    id TEXT PRIMARY KEY,
                    passenger_id TEXT NOT NULL,
                    uri TEXT NOT NULL,
                    mime_type TEXT,
                    display_name TEXT,
                    is_primary INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_passenger_files_owner ON passenger_files(passenger_id)")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS airlines(
                    id TEXT PRIMARY KEY,
                    code TEXT NOT NULL UNIQUE,
                    name TEXT NOT NULL UNIQUE,
                    active INTEGER NOT NULL DEFAULT 1,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())
            seedAirlines(db)
            putSettingIfMissing(db, "visa_link", "https://docs.google.com/spreadsheets/d/1NwV7H_9AGEWunvE6rY5d-U4qi6lOkME23oawFA2jx_Q/edit?usp=drivesdk")
            putSettingIfMissing(db, "accountant_name", "المحاسب")
            putSettingIfMissing(db, "accountant_whatsapp", "")
            putSettingIfMissing(db, "theme_preset", "NAVY")
            putSettingIfMissing(db, "theme_primary", "")
            putSettingIfMissing(db, "theme_background", "")
            putSettingIfMissing(db, "theme_surface", "")
            putSettingIfMissing(db, "theme_text", "")
            putSettingIfMissing(db, "font_scale", "1.0")
            putSettingIfMissing(db, "color_ticket", "#2F80ED")
            putSettingIfMissing(db, "color_visa", "#7B61FF")
            putSettingIfMissing(db, "color_change", "#F2994A")
            putSettingIfMissing(db, "color_refund", "#27AE60")
            putSettingIfMissing(db, "color_void", "#EB5757")
            putSettingIfMissing(db, "color_payment", "#56CCF2")
            putSettingIfMissing(db, "color_unknown", "#6A4C93")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE passengers ADD COLUMN merged_into_id TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_passenger_merged_into ON passengers(merged_into_id)")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS passenger_aliases(
                    id TEXT PRIMARY KEY,
                    passenger_id TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    value TEXT NOT NULL,
                    normalized_value TEXT NOT NULL,
                    source_passenger_id TEXT,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_passenger_alias_lookup ON passenger_aliases(kind, normalized_value)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_passenger_alias_owner ON passenger_aliases(passenger_id)")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS deleted_passenger_aliases(
                    id TEXT PRIMARY KEY,
                    passenger_id TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    normalized_value TEXT NOT NULL,
                    deleted_at INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_deleted_alias_unique ON deleted_passenger_aliases(passenger_id, kind, normalized_value)")
            db.rawQuery("SELECT id,name,passport,phone FROM passengers", null).use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    addAlias(db, id, "NAME", c.getString(1), id)
                    if (!c.isNull(2)) addAlias(db, id, "PASSPORT", c.getString(2), id)
                    if (!c.isNull(3)) addAlias(db, id, "PHONE", c.getString(3), id)
                }
            }
        }
        if (oldVersion < 6) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS transaction_attachments(
                    id TEXT PRIMARY KEY,
                    tx_id TEXT NOT NULL,
                    uri TEXT NOT NULL,
                    mime_type TEXT,
                    display_name TEXT,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_attachments_owner ON transaction_attachments(tx_id)")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS deleted_tx_passengers(
                    id TEXT PRIMARY KEY,
                    tx_id TEXT NOT NULL,
                    currency TEXT NOT NULL,
                    operation_no TEXT,
                    passenger_id TEXT NOT NULL,
                    passenger_name TEXT NOT NULL,
                    normalized_name TEXT NOT NULL,
                    normalized_passport TEXT,
                    amount REAL,
                    base_fare REAL,
                    passenger_type TEXT,
                    document_no TEXT,
                    product TEXT,
                    flags TEXT,
                    deleted_at INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_deleted_tx_pax_tx ON deleted_tx_passengers(tx_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_deleted_tx_pax_key ON deleted_tx_passengers(currency, operation_no)")
        }
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE tx_passengers ADD COLUMN source_name TEXT")
            db.execSQL("""
                UPDATE tx_passengers
                SET source_name = (
                    SELECT p.name FROM passengers p WHERE p.id = tx_passengers.passenger_id
                )
                WHERE source_name IS NULL OR source_name = ''
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS deleted_passenger_aliases(
                    id TEXT PRIMARY KEY,
                    passenger_id TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    normalized_value TEXT NOT NULL,
                    deleted_at INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_deleted_alias_unique ON deleted_passenger_aliases(passenger_id, kind, normalized_value)")
        }
        if (oldVersion < 8) {
            db.execSQL("ALTER TABLE passengers ADD COLUMN rating INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 9) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS visa_price_rules(
                    id TEXT PRIMARY KEY,
                    country TEXT NOT NULL,
                    visa_type TEXT,
                    price REAL,
                    currency TEXT NOT NULL,
                    active INTEGER NOT NULL DEFAULT 1,
                    note TEXT,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_visa_price_country ON visa_price_rules(country, currency, active)")
            seedVisaCountries(db)

            db.rawQuery("SELECT id,phone FROM passengers WHERE phone IS NOT NULL AND phone<>''", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val oldPhone = cursor.getString(1)
                    val normalized = normalizeIraqPhoneOrNull(oldPhone)
                    if (normalized != null && normalized != oldPhone) {
                        db.update("passengers", ContentValues().apply { put("phone", normalized) }, "id=?", arrayOf(id))
                    }
                }
            }
            db.rawQuery("SELECT id,phone FROM responsible_contacts WHERE phone IS NOT NULL AND phone<>''", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val oldPhone = cursor.getString(1)
                    val normalized = normalizeIraqPhoneOrNull(oldPhone)
                    if (normalized != null && normalized != oldPhone) {
                        db.update("responsible_contacts", ContentValues().apply { put("phone", normalized) }, "id=?", arrayOf(id))
                    }
                }
            }
            listOf("issuer_whatsapp", "accountant_whatsapp").forEach { key ->
                val oldValue = db.rawQuery("SELECT value FROM settings WHERE key=?", arrayOf(key)).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
                normalizeIraqPhoneOrNull(oldValue)?.let { normalized ->
                    db.insertWithOnConflict("settings", null, ContentValues().apply {
                        put("key", key)
                        put("value", normalized)
                    }, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
            db.rawQuery("SELECT id,value FROM passenger_aliases WHERE kind='PHONE'", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val oldValue = cursor.getString(1)
                    normalizeIraqPhoneOrNull(oldValue)?.let { normalized ->
                        db.update("passenger_aliases", ContentValues().apply {
                            put("value", normalized)
                            put("normalized_value", normalized)
                        }, "id=?", arrayOf(id))
                    }
                }
            }
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
        putSetting(db, "visa_link", "https://docs.google.com/spreadsheets/d/1NwV7H_9AGEWunvE6rY5d-U4qi6lOkME23oawFA2jx_Q/edit?usp=drivesdk")
        putSetting(db, "accountant_name", "المحاسب")
        putSetting(db, "accountant_whatsapp", "")
        putSetting(db, "theme_preset", "NAVY")
        putSetting(db, "theme_primary", "")
        putSetting(db, "theme_background", "")
        putSetting(db, "theme_surface", "")
        putSetting(db, "theme_text", "")
        putSetting(db, "font_scale", "1.0")
        putSetting(db, "color_ticket", "#2F80ED")
        putSetting(db, "color_visa", "#7B61FF")
        putSetting(db, "color_change", "#F2994A")
        putSetting(db, "color_refund", "#27AE60")
        putSetting(db, "color_void", "#EB5757")
        putSetting(db, "color_payment", "#56CCF2")
        putSetting(db, "color_unknown", "#6A4C93")
        seedAirlines(db)
        seedVisaCountries(db)

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

    private fun putSettingIfMissing(db: SQLiteDatabase, key: String, value: String) {
        db.insertWithOnConflict("settings", null, ContentValues().apply {
            put("key", key); put("value", value)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun seedAirlines(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        val items = listOf(
            "IA" to "Iraqi Airways",
            "G9" to "Air Arabia",
            "FZ" to "Flydubai",
            "PC" to "Pegasus",
            "TK" to "Turkish Airlines",
            "QR" to "Qatar Airways",
            "EK" to "Emirates",
            "RJ" to "Royal Jordanian",
            "IF" to "Fly Baghdad",
            "ME" to "Middle East Airlines",
            "OV" to "SalamAir",
            "XY" to "Flynas",
            "GF" to "Gulf Air",
            "MS" to "EgyptAir",
            "NP" to "Nile Air",
            "RB" to "Fly Cham"
        )
        items.forEach { (code, name) ->
            db.insertWithOnConflict("airlines", null, ContentValues().apply {
                put("id", "AIR-" + code)
                put("code", code)
                put("name", name)
                put("active", 1)
                put("updated_at", now)
            }, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    private fun seedVisaCountries(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        listOf(
            "UAE" to "الإمارات",
            "JORDAN" to "الأردن",
            "EGYPT" to "مصر"
        ).forEach { (country, label) ->
            db.insertWithOnConflict("visa_price_rules", null, ContentValues().apply {
                put("id", "VISA-" + country + "-DEFAULT-USD")
                put("country", country)
                putNull("visa_type")
                putNull("price")
                put("currency", Currency.USD.name)
                put("active", 1)
                put("note", label + " • أدخل السعر الحالي يدويًا")
                put("updated_at", now)
            }, SQLiteDatabase.CONFLICT_IGNORE)
        }
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

    fun visaPriceRules(): List<VisaPriceRule> {
        val out = mutableListOf<VisaPriceRule>()
        readableDatabase.rawQuery(
            "SELECT * FROM visa_price_rules ORDER BY active DESC, country, COALESCE(visa_type,''), currency",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out += VisaPriceRule(
                    id = cursor.s("id"),
                    country = cursor.s("country"),
                    visaType = cursor.sn("visa_type"),
                    price = cursor.dn("price"),
                    currency = Currency.valueOf(cursor.s("currency")),
                    active = cursor.i("active") == 1,
                    note = cursor.sn("note"),
                    updatedAt = cursor.l("updated_at")
                )
            }
        }
        return out
    }

    fun saveVisaPriceRule(rule: VisaPriceRule): VisaPriceRule {
        val country = canonicalVisaCountry(rule.country)
        val cleanType = rule.visaType?.trim()?.takeIf { it.isNotBlank() }
        val saved = rule.copy(
            id = rule.id.ifBlank { UUID.randomUUID().toString() },
            country = country,
            visaType = cleanType,
            price = rule.price?.takeIf { it >= 0.0 },
            updatedAt = System.currentTimeMillis()
        )
        writableDatabase.insertWithOnConflict("visa_price_rules", null, ContentValues().apply {
            put("id", saved.id)
            put("country", saved.country)
            put("visa_type", saved.visaType)
            put("price", saved.price)
            put("currency", saved.currency.name)
            put("active", if (saved.active) 1 else 0)
            put("note", saved.note)
            put("updated_at", saved.updatedAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        audit("visa_price", saved.id, "save", saved.country + "|" + (saved.visaType ?: "DEFAULT") + "|" + (saved.price?.toString() ?: "EMPTY"))
        return saved
    }

    fun visaPriceFor(country: String?, visaType: String?, currency: Currency): VisaPriceRule? {
        val canonical = country?.takeIf { it.isNotBlank() }?.let(::canonicalVisaCountry) ?: return null
        val type = visaType?.trim().orEmpty()
        val rules = visaPriceRules().filter { it.active && it.country.equals(canonical, true) && it.currency == currency && it.price != null }
        return rules.firstOrNull { !it.visaType.isNullOrBlank() && type.contains(it.visaType!!, true) }
            ?: rules.firstOrNull { it.visaType.isNullOrBlank() }
    }

    fun canonicalVisaCountry(value: String): String {
        val p = value.trim().uppercase()
        return when {
            "UAE" in p || "EMIRAT" in p || "الامارات" in value || "الإمارات" in value -> "UAE"
            "JORDAN" in p || "الاردن" in value || "الأردن" in value -> "JORDAN"
            "EGYPT" in p || "مصر" in value -> "EGYPT"
            "SAUDI" in p || "السعود" in value -> "SAUDI"
            else -> value.trim().uppercase()
        }
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
            if (!isPassengerLinkDeleted(tx, passenger.id, p.name, p.passport)) {
                linkPassenger(tx.id, passenger.id, p.name, p.amount, p.passengerType, p.documentNo, p.product, p.flags)
            }
        }
        propagateResponsibilityAfterImport(tx.id)
        audit(
            "transaction",
            tx.id,
            "source_snapshot",
            listOf(parsedWithId.documentId.orEmpty(), parsedWithId.snapshotId.orEmpty(), hash.take(12)).joinToString("|")
        )
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
            linkPassenger(tx.id, p.id, name, null)
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
        put("external_link", tx.externalLink); put("commission_rule_snapshot", tx.commissionRuleSnapshot)
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
            where += "(pnr LIKE ? OR operation_no LIKE ? OR airline LIKE ? OR visa_country LIKE ? OR note LIKE ? OR id IN (SELECT tp.tx_id FROM tx_passengers tp JOIN passengers p ON p.id=tp.passenger_id WHERE p.normalized_name LIKE ? OR p.passport LIKE ? OR p.phone LIKE ? OR tp.document_no LIKE ? OR tp.source_name LIKE ? OR p.id IN (SELECT passenger_id FROM passenger_aliases WHERE normalized_value LIKE ? OR normalized_value LIKE ?)))"
            val q = "%${normalize(search)}%"
            val raw = "%${search.trim()}%"
            val aliasToken = normalizeAliasValue("PASSPORT", search)
            val aliasRaw = if (aliasToken.isBlank()) "__NO_ALIAS_MATCH__" else "%$aliasToken%"
            args += listOf(raw, raw, raw, raw, raw, q, raw, raw, raw, raw, q, aliasRaw)
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
        val out = linkedMapOf<String, Passenger>()
        readableDatabase.rawQuery("""
            SELECT p.* FROM passengers p
            JOIN tx_passengers tp ON tp.passenger_id=p.id
            WHERE tp.tx_id=?
            ORDER BY p.name
        """.trimIndent(), arrayOf(txId)).use { c ->
            while (c.moveToNext()) {
                val raw = c.toPassenger()
                val canonical = passengerById(raw.id) ?: raw
                out.putIfAbsent(canonical.id, canonical)
            }
        }
        return out.values.toList()
    }

    fun passengerSuggestions(query: String, limit: Int = 100): List<Passenger> {
        if (query.isBlank()) return emptyList()
        val nameQuery = "%" + normalize(query) + "%"
        val rawQuery = "%" + query.trim() + "%"
        val aliasName = "%" + normalizeAliasValue("NAME", query) + "%"
        val aliasToken = normalizeAliasValue("PASSPORT", query)
        val aliasRaw = if (aliasToken.isBlank()) "__NO_ALIAS_MATCH__" else "%" + aliasToken + "%"
        val ids = linkedSetOf<String>()
        val out = mutableListOf<Passenger>()
        readableDatabase.rawQuery("""
            SELECT DISTINCT p.* FROM passengers p
            LEFT JOIN passenger_aliases a ON a.passenger_id=p.id
            WHERE p.merged_into_id IS NULL
              AND (
                p.normalized_name LIKE ? OR p.passport LIKE ? OR p.phone LIKE ?
                OR (a.kind='NAME' AND a.normalized_value LIKE ?)
                OR (a.kind IN ('PASSPORT','PHONE') AND a.normalized_value LIKE ?)
              )
            ORDER BY p.name
            LIMIT ?
        """.trimIndent(), arrayOf(nameQuery, rawQuery, rawQuery, aliasName, aliasRaw, limit.toString())).use { c ->
            while (c.moveToNext()) {
                val p = c.toPassenger()
                if (ids.add(p.id)) out += p
            }
        }
        return out
    }

    fun allPassengers(limit: Int = 500): List<Passenger> {
        val out = mutableListOf<Passenger>()
        readableDatabase.rawQuery(
            "SELECT * FROM passengers WHERE merged_into_id IS NULL ORDER BY name LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) out += c.toPassenger()
        }
        return out
    }

    fun findOrCreatePassenger(name: String, passport: String?): Passenger {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        val normName = normalizeAliasValue("NAME", clean)
        val normPassport = passport?.takeIf { it.isNotBlank() }?.let { normalizeAliasValue("PASSPORT", it) }

        fun aliasOwner(kind: String, normalized: String): String? =
            readableDatabase.rawQuery(
                "SELECT passenger_id FROM passenger_aliases WHERE kind=? AND normalized_value=? ORDER BY created_at DESC LIMIT 1",
                arrayOf(kind, normalized)
            ).use { c -> if (c.moveToFirst()) c.getString(0) else null }

        val candidateId = normPassport?.let { aliasOwner("PASSPORT", it) }
            ?: aliasOwner("NAME", normName)
            ?: readableDatabase.rawQuery(
                if (passport.isNullOrBlank())
                    "SELECT id FROM passengers WHERE normalized_name=? AND merged_into_id IS NULL LIMIT 1"
                else
                    "SELECT id FROM passengers WHERE passport=? OR (normalized_name=? AND merged_into_id IS NULL) ORDER BY CASE WHEN passport=? THEN 0 ELSE 1 END LIMIT 1",
                if (passport.isNullOrBlank()) arrayOf(normName) else arrayOf(passport, normName, passport)
            ).use { c -> if (c.moveToFirst()) c.getString(0) else null }

        if (candidateId != null) {
            val canonicalId = resolveCanonicalPassengerId(candidateId)
            val existing = passengerRawById(canonicalId)
            if (existing != null) {
                addAlias(writableDatabase, canonicalId, "NAME", clean, candidateId)
                if (!passport.isNullOrBlank()) addAlias(writableDatabase, canonicalId, "PASSPORT", passport, candidateId)
                if (existing.passport.isNullOrBlank() && !passport.isNullOrBlank()) {
                    writableDatabase.update("passengers", ContentValues().apply { put("passport", passport) }, "id=?", arrayOf(canonicalId))
                }
                return passengerRawById(canonicalId) ?: existing
            }
        }

        val p = Passenger(UUID.randomUUID().toString(), clean, passport)
        writableDatabase.insert("passengers", null, ContentValues().apply {
            put("id", p.id)
            put("name", p.name)
            put("normalized_name", normalize(clean))
            put("passport", passport)
            putNull("phone")
            putNull("responsible_id")
            putNull("responsible_relation")
            put("is_responsible", 0)
            putNull("merged_into_id")
            put("rating", 0)
        })
        addAlias(writableDatabase, p.id, "NAME", clean, p.id)
        if (!passport.isNullOrBlank()) addAlias(writableDatabase, p.id, "PASSPORT", passport, p.id)
        return p
    }

    private fun linkPassenger(
        txId: String,
        passengerId: String,
        sourceName: String?,
        amount: Double?,
        passengerType: String? = null,
        documentNo: String? = null,
        product: String? = null,
        flags: String? = null
    ) {
        val existing = readableDatabase.rawQuery(
            "SELECT base_fare,source_name FROM tx_passengers WHERE tx_id=? AND passenger_id=?",
            arrayOf(txId, passengerId)
        ).use { cursor ->
            if (!cursor.moveToFirst()) null
            else Pair(
                if (cursor.isNull(0)) null else cursor.getDouble(0),
                if (cursor.isNull(1)) null else cursor.getString(1)
            )
        }
        writableDatabase.insertWithOnConflict("tx_passengers", null, ContentValues().apply {
            put("tx_id", txId)
            put("passenger_id", passengerId)
            put("source_name", existing?.second ?: sourceName?.trim()?.takeIf { it.isNotBlank() })
            put("amount", amount)
            put("base_fare", existing?.first)
            put("passenger_type", passengerType)
            put("document_no", documentNo)
            put("product", product)
            put("flags", flags)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun isPassengerLinkDeleted(tx: Transaction, passengerId: String, sourceName: String, passport: String?): Boolean {
        val groupIds = mergedGroupIds(passengerId)
        val normalizedName = normalizeAliasValue("NAME", sourceName)
        val normalizedPassport = passport?.takeIf { it.isNotBlank() }?.let { normalizeAliasValue("PASSPORT", it) }
        val scopeSql: String
        val scopeArgs = mutableListOf<String>()
        if (!tx.operationNo.isNullOrBlank()) {
            scopeSql = "currency=? AND operation_no=?"
            scopeArgs += tx.currency.name
            scopeArgs += tx.operationNo
        } else {
            scopeSql = "tx_id=?"
            scopeArgs += tx.id
        }

        val identityParts = mutableListOf<String>()
        if (groupIds.isNotEmpty()) {
            identityParts += "passenger_id IN (" + groupIds.joinToString(",") { "?" } + ")"
            scopeArgs += groupIds
        }
        identityParts += "normalized_name=?"
        scopeArgs += normalizedName
        if (!normalizedPassport.isNullOrBlank()) {
            identityParts += "normalized_passport=?"
            scopeArgs += normalizedPassport
        }

        return readableDatabase.rawQuery(
            "SELECT 1 FROM deleted_tx_passengers WHERE $scopeSql AND (" + identityParts.joinToString(" OR ") + ") LIMIT 1",
            scopeArgs.toTypedArray()
        ).use { it.moveToFirst() }
    }

    fun deletePassengerLink(txId: String, passengerId: String): DeletedPassengerLink? {
        val tx = transaction(txId) ?: return null
        val rootId = resolveCanonicalPassengerId(passengerId)
        val groupIds = mergedGroupIds(rootId)
        if (groupIds.isEmpty()) return null
        val placeholders = groupIds.joinToString(",") { "?" }
        val args = mutableListOf<String>()
        args += txId
        args += groupIds

        var row: DeletedPassengerLink? = null
        readableDatabase.rawQuery(
            """
                SELECT tp.amount,tp.base_fare,tp.passenger_type,tp.document_no,tp.product,tp.flags,
                       COALESCE(tp.source_name,p.name),p.passport,tp.passenger_id
                FROM tx_passengers tp
                JOIN passengers p ON p.id=tp.passenger_id
                WHERE tp.tx_id=? AND tp.passenger_id IN ($placeholders)
                LIMIT 1
            """.trimIndent(),
            args.toTypedArray()
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val canonical = passengerById(rootId) ?: passengerRawById(rootId)
                val displayName = canonical?.name ?: cursor.getString(6)
                val passportValue = canonical?.passport ?: if (cursor.isNull(7)) null else cursor.getString(7)
                row = DeletedPassengerLink(
                    id = UUID.randomUUID().toString(),
                    txId = tx.id,
                    currency = tx.currency,
                    operationNo = tx.operationNo,
                    passengerId = rootId,
                    passengerName = displayName,
                    normalizedName = normalizeAliasValue("NAME", displayName),
                    normalizedPassport = passportValue?.takeIf { it.isNotBlank() }?.let { normalizeAliasValue("PASSPORT", it) },
                    amount = if (cursor.isNull(0)) null else cursor.getDouble(0),
                    baseFare = if (cursor.isNull(1)) null else cursor.getDouble(1),
                    passengerType = if (cursor.isNull(2)) null else cursor.getString(2),
                    documentNo = if (cursor.isNull(3)) null else cursor.getString(3),
                    product = if (cursor.isNull(4)) null else cursor.getString(4),
                    flags = if (cursor.isNull(5)) null else cursor.getString(5)
                )
            }
        }
        val deleted = row ?: return null

        writableDatabase.beginTransaction()
        try {
            val duplicateWhere = if (!tx.operationNo.isNullOrBlank())
                "currency=? AND operation_no=? AND (passenger_id=? OR normalized_name=?)"
            else
                "tx_id=? AND (passenger_id=? OR normalized_name=?)"
            val duplicateArgs = if (!tx.operationNo.isNullOrBlank())
                arrayOf(tx.currency.name, tx.operationNo, rootId, deleted.normalizedName)
            else
                arrayOf(tx.id, rootId, deleted.normalizedName)
            writableDatabase.delete("deleted_tx_passengers", duplicateWhere, duplicateArgs)
            writableDatabase.insertOrThrow("deleted_tx_passengers", null, ContentValues().apply {
                put("id", deleted.id)
                put("tx_id", deleted.txId)
                put("currency", deleted.currency.name)
                put("operation_no", deleted.operationNo)
                put("passenger_id", deleted.passengerId)
                put("passenger_name", deleted.passengerName)
                put("normalized_name", deleted.normalizedName)
                put("normalized_passport", deleted.normalizedPassport)
                put("amount", deleted.amount)
                put("base_fare", deleted.baseFare)
                put("passenger_type", deleted.passengerType)
                put("document_no", deleted.documentNo)
                put("product", deleted.product)
                put("flags", deleted.flags)
                put("deleted_at", deleted.deletedAt)
            })
            writableDatabase.delete(
                "tx_passengers",
                "tx_id=? AND passenger_id IN ($placeholders)",
                args.toTypedArray()
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        audit("transaction", tx.id, "delete_passenger_link", deleted.passengerName + "|" + (tx.operationNo ?: tx.id))
        return deleted
    }

    fun deletedPassengerLinks(limit: Int = 200): List<DeletedPassengerLink> {
        val out = mutableListOf<DeletedPassengerLink>()
        readableDatabase.rawQuery(
            "SELECT * FROM deleted_tx_passengers ORDER BY deleted_at DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += DeletedPassengerLink(
                    id = c.s("id"),
                    txId = c.s("tx_id"),
                    currency = Currency.valueOf(c.s("currency")),
                    operationNo = c.sn("operation_no"),
                    passengerId = c.s("passenger_id"),
                    passengerName = c.s("passenger_name"),
                    normalizedName = c.s("normalized_name"),
                    normalizedPassport = c.sn("normalized_passport"),
                    amount = c.dn("amount"),
                    baseFare = c.dn("base_fare"),
                    passengerType = c.sn("passenger_type"),
                    documentNo = c.sn("document_no"),
                    product = c.sn("product"),
                    flags = c.sn("flags"),
                    deletedAt = c.l("deleted_at")
                )
            }
        }
        return out
    }

    fun restoreDeletedPassengerLink(id: String): Boolean {
        val item = readableDatabase.rawQuery(
            "SELECT * FROM deleted_tx_passengers WHERE id=? LIMIT 1",
            arrayOf(id)
        ).use { c ->
            if (!c.moveToFirst()) null else DeletedPassengerLink(
                id = c.s("id"),
                txId = c.s("tx_id"),
                currency = Currency.valueOf(c.s("currency")),
                operationNo = c.sn("operation_no"),
                passengerId = c.s("passenger_id"),
                passengerName = c.s("passenger_name"),
                normalizedName = c.s("normalized_name"),
                normalizedPassport = c.sn("normalized_passport"),
                amount = c.dn("amount"),
                baseFare = c.dn("base_fare"),
                passengerType = c.sn("passenger_type"),
                documentNo = c.sn("document_no"),
                product = c.sn("product"),
                flags = c.sn("flags"),
                deletedAt = c.l("deleted_at")
            )
        } ?: return false
        val tx = transaction(item.txId) ?: return false
        val passenger = passengerById(item.passengerId) ?: passengerRawById(item.passengerId) ?: return false

        writableDatabase.beginTransaction()
        try {
            linkPassenger(
                tx.id,
                passenger.id,
                item.passengerName,
                item.amount,
                item.passengerType,
                item.documentNo,
                item.product,
                item.flags
            )
            if (item.baseFare != null) {
                setPassengerBaseFare(tx.id, passenger.id, item.baseFare)
            }
            writableDatabase.delete("deleted_tx_passengers", "id=?", arrayOf(id))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        audit("transaction", tx.id, "restore_passenger_link", passenger.name + "|" + (tx.operationNo ?: tx.id))
        return true
    }

    fun markReview(id: String, state: ReviewState) {
        val tx = transaction(id)
        val values = ContentValues().apply {
            put("review_state", state.name)
            if (state == ReviewState.REVIEWED) {
                put("reviewed_at", System.currentTimeMillis())
                put("changed_after_review", 0)
                if (tx?.commissionRuleSnapshot.isNullOrBlank() && tx?.type == TxType.TICKET) {
                    ruleForTransaction(tx)?.let { put("commission_rule_snapshot", ruleSnapshot(it)) }
                }
            } else putNull("reviewed_at")
        }
        writableDatabase.update("transactions", values, "id=?", arrayOf(id))
        audit("transaction", id, "review", state.name)
    }

    fun saveAndMarkReviewed(tx: Transaction): Transaction {
        val now = System.currentTimeMillis()
        val snapshot = tx.commissionRuleSnapshot ?: if (tx.type == TxType.TICKET) {
            ruleForTransaction(tx)?.let(::ruleSnapshot)
        } else null
        val reviewed = tx.copy(
            reviewState = ReviewState.REVIEWED,
            reviewedAt = now,
            changedAfterReview = false,
            commissionRuleSnapshot = snapshot
        )
        writableDatabase.beginTransaction()
        try {
            updateTransaction(writableDatabase, reviewed)
            audit("transaction", reviewed.id, "edit", "manual edit + review")
            audit("transaction", reviewed.id, "review", ReviewState.REVIEWED.name)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return transaction(reviewed.id) ?: reviewed
    }

    fun updateTransactionFields(tx: Transaction) {
        updateTransaction(writableDatabase, tx)
        audit("transaction", tx.id, "edit", "manual edit")
    }

    fun deleteTransaction(id: String) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("tx_passengers", "tx_id=?", arrayOf(id))
            writableDatabase.delete("transaction_attachments", "tx_id=?", arrayOf(id))
            writableDatabase.delete("deleted_tx_passengers", "tx_id=?", arrayOf(id))
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
        externalLink = sn("external_link"), commissionRuleSnapshot = sn("commission_rule_snapshot"),
        reviewState = ReviewState.valueOf(s("review_state")), warning = sn("warning"), note = sn("note"), flags = sn("flags"), rawText = sn("raw_text"),
        sourceHash = sn("source_hash"), importedAt = l("imported_at"), reviewedAt = ln("reviewed_at"), changedAfterReview = i("changed_after_review") == 1
    )

    private fun Cursor.toPassenger() = Passenger(
        id = s("id"), name = s("name"), passport = sn("passport"), phone = sn("phone"),
        responsibleId = sn("responsible_id"), responsibleRelation = sn("responsible_relation"),
        isResponsible = i("is_responsible") == 1, mergedIntoId = sn("merged_into_id"),
        rating = i("rating").coerceIn(0, 5)
    )

    private fun normalizeAliasValue(kind: String, value: String): String = when (kind.uppercase()) {
        "PHONE" -> normalizeIraqPhoneOrNull(value) ?: value.filter(Char::isDigit)
        "PASSPORT" -> value.uppercase().replace(Regex("[^A-Z0-9]"), "")
        else -> normalize(value)
    }

    private fun addAlias(
        db: SQLiteDatabase,
        passengerId: String,
        kind: String,
        value: String?,
        sourcePassengerId: String? = null
    ) {
        val clean = value?.trim()?.takeIf { it.isNotBlank() } ?: return
        val normalized = normalizeAliasValue(kind, clean)
        if (normalized.isBlank()) return
        val rootPassengerId = try { resolveCanonicalPassengerId(passengerId) } catch (_: Exception) { passengerId }
        val deleted = db.rawQuery(
            "SELECT 1 FROM deleted_passenger_aliases WHERE passenger_id=? AND kind=? AND normalized_value=? LIMIT 1",
            arrayOf(rootPassengerId, kind.uppercase(), normalized)
        ).use { it.moveToFirst() }
        if (deleted) return
        val exists = db.rawQuery(
            "SELECT 1 FROM passenger_aliases WHERE passenger_id=? AND kind=? AND normalized_value=? LIMIT 1",
            arrayOf(passengerId, kind.uppercase(), normalized)
        ).use { it.moveToFirst() }
        if (exists) return
        db.insert("passenger_aliases", null, ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("passenger_id", passengerId)
            put("kind", kind.uppercase())
            put("value", clean)
            put("normalized_value", normalized)
            put("source_passenger_id", sourcePassengerId)
            put("created_at", System.currentTimeMillis())
        })
    }

    private fun passengerRawById(id: String): Passenger? {
        readableDatabase.rawQuery("SELECT * FROM passengers WHERE id=? LIMIT 1", arrayOf(id)).use { c ->
            return if (c.moveToFirst()) c.toPassenger() else null
        }
    }

    private fun resolveCanonicalPassengerId(id: String): String {
        var current = id
        val visited = mutableSetOf<String>()
        repeat(32) {
            if (!visited.add(current)) return current
            val next = readableDatabase.rawQuery(
                "SELECT merged_into_id FROM passengers WHERE id=? LIMIT 1",
                arrayOf(current)
            ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null }
            if (next.isNullOrBlank()) return current
            current = next
        }
        return current
    }

    private fun mergedGroupIds(primaryId: String): List<String> {
        val root = resolveCanonicalPassengerId(primaryId)
        val out = linkedSetOf(root)
        val queue = ArrayDeque<String>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val parent = queue.removeFirst()
            readableDatabase.rawQuery(
                "SELECT id FROM passengers WHERE merged_into_id=?",
                arrayOf(parent)
            ).use { c ->
                while (c.moveToNext()) {
                    val child = c.getString(0)
                    if (out.add(child)) queue.add(child)
                }
            }
        }
        return out.toList()
    }

    fun passengerById(id: String): Passenger? {
        val rootId = resolveCanonicalPassengerId(id)
        val raw = passengerRawById(rootId) ?: return null
        val group = mergedGroupIds(rootId)
        val responsible = group.any { passengerRawById(it)?.isResponsible == true }
        val responsibleId = raw.responsibleId?.let { resolveCanonicalPassengerId(it) }
        return raw.copy(
            isResponsible = responsible,
            responsibleId = responsibleId,
            mergedIntoId = null
        )
    }

    fun passengerAliases(passengerId: String): List<PassengerAlias> {
        val ids = mergedGroupIds(passengerId)
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        val out = mutableListOf<PassengerAlias>()
        readableDatabase.rawQuery(
            "SELECT * FROM passenger_aliases WHERE passenger_id IN ($placeholders) ORDER BY kind,value",
            ids.toTypedArray()
        ).use { c ->
            while (c.moveToNext()) {
                out += PassengerAlias(
                    id = c.s("id"),
                    passengerId = c.s("passenger_id"),
                    kind = c.s("kind"),
                    value = c.s("value"),
                    normalizedValue = c.s("normalized_value"),
                    sourcePassengerId = c.sn("source_passenger_id"),
                    createdAt = c.l("created_at")
                )
            }
        }
        return out.distinctBy { it.kind + "|" + it.normalizedValue }
    }

    fun deletePassengerAlias(aliasId: String): Boolean {
        val alias = readableDatabase.rawQuery(
            "SELECT * FROM passenger_aliases WHERE id=? LIMIT 1",
            arrayOf(aliasId)
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else PassengerAlias(
                id = cursor.s("id"),
                passengerId = cursor.s("passenger_id"),
                kind = cursor.s("kind"),
                value = cursor.s("value"),
                normalizedValue = cursor.s("normalized_value"),
                sourcePassengerId = cursor.sn("source_passenger_id"),
                createdAt = cursor.l("created_at")
            )
        } ?: return false

        val rootId = resolveCanonicalPassengerId(alias.passengerId)
        val root = passengerRawById(rootId) ?: return false
        if (alias.kind == "NAME" && alias.normalizedValue == normalizeAliasValue("NAME", root.name)) {
            return false
        }

        val groupIds = mergedGroupIds(rootId)
        val placeholders = groupIds.joinToString(",") { "?" }
        val deleteArgs = groupIds.toMutableList().apply {
            add(alias.kind)
            add(alias.normalizedValue)
        }.toTypedArray()

        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(
                "passenger_aliases",
                "passenger_id IN ($placeholders) AND kind=? AND normalized_value=?",
                deleteArgs
            )
            writableDatabase.insertWithOnConflict(
                "deleted_passenger_aliases",
                null,
                ContentValues().apply {
                    put("id", UUID.randomUUID().toString())
                    put("passenger_id", rootId)
                    put("kind", alias.kind)
                    put("normalized_value", alias.normalizedValue)
                    put("deleted_at", System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_IGNORE
            )
            audit("passenger", rootId, "delete_alias", alias.kind + "|" + alias.value)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return true
    }

    fun mergedPassengers(passengerId: String): List<Passenger> =
        mergedGroupIds(passengerId).drop(1).mapNotNull(::passengerRawById).sortedBy { it.name }

    fun mergePassengers(primaryId: String, secondaryIds: Collection<String>): Passenger? {
        val primary = passengerById(primaryId) ?: return null
        val targets = secondaryIds
            .map(::resolveCanonicalPassengerId)
            .filter { it != primary.id }
            .distinct()
        if (targets.isEmpty()) return primary

        writableDatabase.beginTransaction()
        try {
            targets.forEach { sourceId ->
                val source = passengerRawById(sourceId) ?: return@forEach

                addAlias(writableDatabase, primary.id, "NAME", source.name, source.id)
                addAlias(writableDatabase, primary.id, "PASSPORT", source.passport, source.id)
                addAlias(writableDatabase, primary.id, "PHONE", source.phone, source.id)
                addAlias(writableDatabase, source.id, "NAME", primary.name, primary.id)

                readableDatabase.rawQuery(
                    "SELECT kind,value FROM passenger_aliases WHERE passenger_id=?",
                    arrayOf(source.id)
                ).use { c ->
                    while (c.moveToNext()) addAlias(writableDatabase, primary.id, c.getString(0), c.getString(1), source.id)
                }

                val currentPrimary = passengerRawById(primary.id) ?: primary
                writableDatabase.update("passengers", ContentValues().apply {
                    if (currentPrimary.passport.isNullOrBlank() && !source.passport.isNullOrBlank()) put("passport", source.passport)
                    if (currentPrimary.phone.isNullOrBlank() && !source.phone.isNullOrBlank()) put("phone", source.phone)
                    if (currentPrimary.responsibleId.isNullOrBlank() && !source.responsibleId.isNullOrBlank()) {
                        put("responsible_id", resolveCanonicalPassengerId(source.responsibleId))
                        put("responsible_relation", source.responsibleRelation)
                    }
                    put("is_responsible", if (currentPrimary.isResponsible || source.isResponsible) 1 else 0)
                    put("rating", maxOf(currentPrimary.rating, source.rating).coerceIn(0, 5))
                }, "id=?", arrayOf(primary.id))

                writableDatabase.update("passengers", ContentValues().apply {
                    put("merged_into_id", primary.id)
                }, "id=?", arrayOf(source.id))

                audit("passenger", primary.id, "merge_passenger", source.id + "|" + source.name)
                audit("passenger", source.id, "merged_into", primary.id + "|" + primary.name)
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return passengerById(primary.id)
    }

    fun unmergePassenger(sourceId: String): Passenger? {
        val source = passengerRawById(sourceId) ?: return null
        val primaryId = source.mergedIntoId ?: return source
        writableDatabase.beginTransaction()
        try {
            writableDatabase.update("passengers", ContentValues().apply { putNull("merged_into_id") }, "id=?", arrayOf(sourceId))
            val rootPrimaryId = resolveCanonicalPassengerId(primaryId)
            writableDatabase.delete(
                "passenger_aliases",
                "passenger_id=? AND source_passenger_id=?",
                arrayOf(rootPrimaryId, sourceId)
            )
            writableDatabase.delete(
                "passenger_aliases",
                "passenger_id=? AND source_passenger_id=?",
                arrayOf(sourceId, rootPrimaryId)
            )
            audit("passenger", rootPrimaryId, "unmerge_passenger", sourceId + "|" + source.name)
            audit("passenger", sourceId, "unmerged_from", primaryId)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return passengerRawById(sourceId)
    }

    fun txPassengerDetails(txId: String): List<TxPassengerDetail> {
        val byCanonical = linkedMapOf<String, TxPassengerDetail>()
        readableDatabase.rawQuery("""
            SELECT p.*, tp.source_name AS tp_source_name, tp.amount AS tp_amount, tp.base_fare AS tp_base_fare, tp.passenger_type AS tp_type,
                   tp.document_no AS tp_document, tp.product AS tp_product, tp.flags AS tp_flags
            FROM passengers p JOIN tx_passengers tp ON tp.passenger_id=p.id
            WHERE tp.tx_id=? ORDER BY COALESCE(tp.source_name,p.name)
        """.trimIndent(), arrayOf(txId)).use { c ->
            while (c.moveToNext()) {
                val raw = c.toPassenger()
                val canonical = passengerById(raw.id) ?: raw
                val next = TxPassengerDetail(
                    passenger = canonical,
                    sourceName = c.sn("tp_source_name") ?: raw.name,
                    amount = c.dn("tp_amount"),
                    baseFare = c.dn("tp_base_fare"),
                    passengerType = c.sn("tp_type"),
                    documentNo = c.sn("tp_document"),
                    product = c.sn("tp_product"),
                    flags = c.sn("tp_flags")
                )
                val previous = byCanonical[canonical.id]
                byCanonical[canonical.id] = if (previous == null) next else previous.copy(
                    sourceName = previous.sourceName ?: next.sourceName,
                    amount = previous.amount ?: next.amount,
                    baseFare = previous.baseFare ?: next.baseFare,
                    passengerType = previous.passengerType ?: next.passengerType,
                    documentNo = previous.documentNo ?: next.documentNo,
                    product = previous.product ?: next.product,
                    flags = previous.flags ?: next.flags
                )
            }
        }
        return byCanonical.values.sortedBy { it.passenger.name }
    }

    fun setPassengerBaseFare(txId: String, passengerId: String, baseFare: Double?) {
        val ids = mergedGroupIds(passengerId)
        val placeholders = ids.joinToString(",") { "?" }
        val args = mutableListOf<String>()
        args += txId
        args += ids
        writableDatabase.update("tx_passengers", ContentValues().apply {
            if (baseFare == null) putNull("base_fare") else put("base_fare", baseFare)
        }, "tx_id=? AND passenger_id IN ($placeholders)", args.toTypedArray())
        audit("tx_passenger", txId + "/" + resolveCanonicalPassengerId(passengerId), "base_fare", baseFare?.toString())
    }

    fun setPassengerBaseFareForTransaction(txId: String, baseFare: Double?) {
        writableDatabase.update("tx_passengers", ContentValues().apply {
            if (baseFare == null) putNull("base_fare") else put("base_fare", baseFare)
        }, "tx_id=?", arrayOf(txId))
        audit("transaction", txId, "base_fare_all", baseFare?.toString())
    }

    fun updatePassenger(person: Passenger) {
        val canonicalId = resolveCanonicalPassengerId(person.id)
        val old = passengerRawById(canonicalId) ?: return
        addAlias(writableDatabase, canonicalId, "NAME", old.name, canonicalId)
        addAlias(writableDatabase, canonicalId, "PASSPORT", old.passport, canonicalId)
        addAlias(writableDatabase, canonicalId, "PHONE", old.phone, canonicalId)

        val responsibleId = person.responsibleId?.let(::resolveCanonicalPassengerId)
        writableDatabase.update("passengers", ContentValues().apply {
            put("name", person.name.trim())
            put("normalized_name", normalize(person.name))
            put("passport", person.passport)
            put("phone", normalizeIraqPhoneForStorage(person.phone))
            put("responsible_id", responsibleId)
            put("responsible_relation", person.responsibleRelation)
            put("is_responsible", if (person.isResponsible) 1 else 0)
            put("rating", person.rating.coerceIn(0, 5))
        }, "id=?", arrayOf(canonicalId))

        addAlias(writableDatabase, canonicalId, "NAME", person.name, canonicalId)
        addAlias(writableDatabase, canonicalId, "PASSPORT", person.passport, canonicalId)
        addAlias(writableDatabase, canonicalId, "PHONE", normalizeIraqPhoneForStorage(person.phone), canonicalId)
        audit("passenger", canonicalId, "edit", person.name)
    }

    fun setPassengerRating(passengerId: String, rating: Int) {
        val canonicalId = resolveCanonicalPassengerId(passengerId)
        val safe = rating.coerceIn(0, 5)
        writableDatabase.update(
            "passengers",
            ContentValues().apply { put("rating", safe) },
            "id=?",
            arrayOf(canonicalId)
        )
        audit("passenger", canonicalId, "rating", safe.toString())
    }

    fun assignResponsible(
        passengerId: String,
        responsibleId: String?,
        relation: String? = null,
        forceConflicts: Boolean = false
    ): List<Passenger> {
        val canonicalPassenger = resolveCanonicalPassengerId(passengerId)
        val canonicalResponsible = responsibleId?.let(::resolveCanonicalPassengerId)
        if (canonicalResponsible == null) {
            writableDatabase.update("passengers", ContentValues().apply {
                putNull("responsible_id")
                put("responsible_relation", relation)
            }, "id=?", arrayOf(canonicalPassenger))
            audit("passenger", canonicalPassenger, "assign_responsible", null)
            return emptyList()
        }

        val conflicts = assignResponsibleTransitively(
            seedIds = listOf(canonicalPassenger),
            responsibleId = canonicalResponsible,
            forceConflicts = forceConflicts
        )
        if (conflicts.isEmpty()) {
            writableDatabase.update("passengers", ContentValues().apply {
                put("responsible_relation", relation)
            }, "id=?", arrayOf(canonicalPassenger))
            audit("passenger", canonicalPassenger, "assign_responsible", canonicalResponsible)
        }
        return conflicts
    }

    fun dependentsOf(responsibleId: String): List<Passenger> {
        val responsibleGroup = mergedGroupIds(responsibleId)
        if (responsibleGroup.isEmpty()) return emptyList()
        val placeholders = responsibleGroup.joinToString(",") { "?" }
        val out = linkedMapOf<String, Passenger>()
        readableDatabase.rawQuery(
            "SELECT * FROM passengers WHERE responsible_id IN ($placeholders) AND merged_into_id IS NULL ORDER BY name",
            responsibleGroup.toTypedArray()
        ).use { c ->
            while (c.moveToNext()) {
                val p = c.toPassenger()
                val canonical = passengerById(p.id) ?: p
                out.putIfAbsent(canonical.id, canonical)
            }
        }
        return out.values.toList()
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

    fun airlineNames(): List<String> = airlines().map { it.name }

    fun airlines(): List<AirlineInfo> {
        val out = mutableListOf<AirlineInfo>()
        readableDatabase.rawQuery("SELECT * FROM airlines WHERE active=1 ORDER BY name", null).use { c ->
            while (c.moveToNext()) out += AirlineInfo(
                id = c.s("id"), code = c.s("code"), name = c.s("name"),
                active = c.i("active") == 1, updatedAt = c.l("updated_at")
            )
        }
        return out
    }

    fun saveAirline(code: String, name: String): AirlineInfo {
        val cleanCode = code.trim().uppercase().ifBlank {
            "A" + (airlines().size + 1).toString().padStart(2, '0')
        }
        val cleanName = name.trim()
        val existing = readableDatabase.rawQuery(
            "SELECT * FROM airlines WHERE LOWER(name)=LOWER(?) OR code=? LIMIT 1",
            arrayOf(cleanName, cleanCode)
        ).use { c -> if (c.moveToFirst()) AirlineInfo(c.s("id"), c.s("code"), c.s("name"), c.i("active")==1, c.l("updated_at")) else null }
        if (existing != null) {
            if (!existing.active) {
                writableDatabase.update("airlines", ContentValues().apply {
                    put("active", 1)
                    put("updated_at", System.currentTimeMillis())
                }, "id=?", arrayOf(existing.id))
                audit("airline", existing.id, "restore", existing.name)
                return existing.copy(active = true, updatedAt = System.currentTimeMillis())
            }
            return existing
        }
        val item = AirlineInfo("AIR-" + UUID.randomUUID().toString(), cleanCode, cleanName)
        writableDatabase.insert("airlines", null, ContentValues().apply {
            put("id", item.id); put("code", item.code); put("name", item.name); put("active", 1); put("updated_at", item.updatedAt)
        })
        audit("airline", item.id, "create", "${item.code}|${item.name}")
        return item
    }

    fun deleteAirline(id: String): Boolean {
        val airline = readableDatabase.rawQuery(
            "SELECT * FROM airlines WHERE id=? LIMIT 1",
            arrayOf(id)
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else AirlineInfo(
                id = cursor.s("id"),
                code = cursor.s("code"),
                name = cursor.s("name"),
                active = cursor.i("active") == 1,
                updatedAt = cursor.l("updated_at")
            )
        } ?: return false

        writableDatabase.beginTransaction()
        try {
            writableDatabase.update(
                "airlines",
                ContentValues().apply {
                    put("active", 0)
                    put("updated_at", System.currentTimeMillis())
                },
                "id=?",
                arrayOf(id)
            )
            writableDatabase.delete("airline_prefixes", "LOWER(airline)=LOWER(?)", arrayOf(airline.name))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        audit("airline", id, "delete", airline.name)
        return true
    }

    private fun resolveAirlineToken(value: String?): String? {
        val token = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        readableDatabase.rawQuery(
            "SELECT name FROM airlines WHERE UPPER(code)=UPPER(?) OR LOWER(name)=LOWER(?) LIMIT 1",
            arrayOf(token, token)
        ).use { c -> if (c.moveToFirst()) return c.getString(0) }
        return token
    }

    fun ruleForTransaction(tx: Transaction): CommissionRule? {
        val airline = tx.airline ?: return null
        val candidates = rules().filter { it.airline.equals(airline, true) }
            .filter { r -> r.effectiveFrom.isNullOrBlank() || tx.transactionDate.isNullOrBlank() || r.effectiveFrom <= tx.transactionDate }
        val reverse = isReverseRoute(tx.route)
        return candidates
            .filter { r -> r.direction == "ANY" || (r.direction == "REVERSE" && reverse) || (r.direction == "NORMAL" && !reverse) }
            .maxByOrNull { it.effectiveFrom.orEmpty() }
            ?: candidates.maxByOrNull { it.effectiveFrom.orEmpty() }
    }

    private fun ruleSnapshot(rule: CommissionRule): String = listOf(
        rule.airline, rule.kind.name, rule.value.toString(), rule.roundTripValue?.toString().orEmpty(),
        rule.direction, rule.effectiveFrom.orEmpty(), rule.note.orEmpty()
    ).joinToString("~")

    private fun isReverseRoute(route: String?): Boolean {
        val airports = route.orEmpty().replace(" ", "-").split("-").filter { it.isNotBlank() }
        if (airports.size < 2) return false
        val iraq = setOf("BGW", "BSR", "NJF", "EBL", "ISU", "KIK")
        return airports.first().uppercase() !in iraq && airports.drop(1).any { it.uppercase() in iraq }
    }

    fun responsibleForTransaction(txId: String): Passenger? {
        val pax = passengersFor(txId)
        val responsibleIds = pax.mapNotNull { it.responsibleId }.distinct()
        if (responsibleIds.size == 1) return passengerById(responsibleIds.first())
        val direct = pax.filter { it.isResponsible }
        return direct.singleOrNull()
    }

    private fun connectedPassengerIds(seedIds: Collection<String>): LinkedHashSet<String> {
        val connected = linkedSetOf<String>()
        val queue = ArrayDeque<String>()
        seedIds.map(::resolveCanonicalPassengerId).distinct().forEach {
            if (connected.add(it)) queue.add(it)
        }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val groupIds = mergedGroupIds(current)
            if (groupIds.isEmpty()) continue
            val groupPlaceholders = groupIds.joinToString(",") { "?" }
            val txIds = mutableListOf<String>()
            readableDatabase.rawQuery(
                "SELECT DISTINCT tx_id FROM tx_passengers WHERE passenger_id IN ($groupPlaceholders)",
                groupIds.toTypedArray()
            ).use { cursor ->
                while (cursor.moveToNext()) txIds += cursor.getString(0)
            }
            if (txIds.isEmpty()) continue

            val txPlaceholders = txIds.joinToString(",") { "?" }
            readableDatabase.rawQuery(
                "SELECT DISTINCT passenger_id FROM tx_passengers WHERE tx_id IN ($txPlaceholders)",
                txIds.toTypedArray()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val next = resolveCanonicalPassengerId(cursor.getString(0))
                    if (connected.add(next)) queue.add(next)
                }
            }
        }
        return connected
    }

    private fun responsibleRootForPassenger(passenger: Passenger): String? = when {
        passenger.responsibleId != null -> resolveCanonicalPassengerId(passenger.responsibleId)
        passenger.isResponsible -> passenger.id
        else -> null
    }

    fun responsibilityConflictsForTransaction(txId: String, responsibleId: String): List<Passenger> =
        responsibilityConflicts(passengersFor(txId).map { it.id }, responsibleId)

    fun responsibilityConflictsForPassenger(passengerId: String, responsibleId: String): List<Passenger> =
        responsibilityConflicts(listOf(passengerId), responsibleId)

    fun responsibilityConflictsForPnr(pnr: String, responsibleId: String): List<Passenger> {
        val txIds = transactions(search = pnr, limit = 500)
            .filter { it.pnr.equals(pnr, true) }
            .map { it.id }
        val seeds = linkedSetOf<String>()
        txIds.forEach { txId -> passengersFor(txId).forEach { seeds += it.id } }
        return responsibilityConflicts(seeds, responsibleId)
    }

    private fun responsibilityConflicts(seedIds: Collection<String>, responsibleId: String): List<Passenger> {
        val chosen = resolveCanonicalPassengerId(responsibleId)
        val graphSeeds = seedIds.map(::resolveCanonicalPassengerId) + chosen
        return connectedPassengerIds(graphSeeds)
            .mapNotNull(::passengerById)
            .filter { passenger ->
                passenger.id != chosen &&
                    responsibleRootForPassenger(passenger)?.let { it != chosen } == true
            }
            .distinctBy { it.id }
            .sortedBy { it.name }
    }

    private fun assignResponsibleTransitively(
        seedIds: Collection<String>,
        responsibleId: String,
        forceConflicts: Boolean
    ): List<Passenger> {
        val chosen = resolveCanonicalPassengerId(responsibleId)
        val conflicts = responsibilityConflicts(seedIds, chosen)
        if (conflicts.isNotEmpty() && !forceConflicts) return conflicts

        val connected = connectedPassengerIds(seedIds.map(::resolveCanonicalPassengerId) + chosen)
        writableDatabase.beginTransaction()
        try {
            writableDatabase.update(
                "passengers",
                ContentValues().apply {
                    put("is_responsible", 1)
                    putNull("responsible_id")
                    putNull("responsible_relation")
                },
                "id=?",
                arrayOf(chosen)
            )
            connected.forEach { pid ->
                if (pid == chosen) return@forEach
                writableDatabase.update(
                    "passengers",
                    ContentValues().apply {
                        put("responsible_id", chosen)
                    },
                    "id=?",
                    arrayOf(pid)
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return emptyList()
    }

    fun assignResponsibleForTransaction(txId: String, responsibleId: String, forceConflicts: Boolean = false): List<Passenger> {
        val seeds = passengersFor(txId).map { it.id }
        val conflicts = assignResponsibleTransitively(seeds, responsibleId, forceConflicts)
        if (conflicts.isEmpty()) audit("transaction", txId, "assign_responsible_group", resolveCanonicalPassengerId(responsibleId))
        return conflicts
    }

    fun assignResponsibleForPnr(pnr: String, responsibleId: String, forceConflicts: Boolean = false): List<Passenger> {
        val txIds = transactions(search = pnr, limit = 500).filter { it.pnr.equals(pnr, true) }.map { it.id }
        val seeds = linkedSetOf<String>()
        txIds.forEach { id -> passengersFor(id).forEach { seeds += it.id } }
        val conflicts = assignResponsibleTransitively(seeds, responsibleId, forceConflicts)
        if (conflicts.isEmpty()) audit("pnr", pnr, "assign_responsible_group", resolveCanonicalPassengerId(responsibleId))
        return conflicts
    }

    private fun propagateResponsibilityAfterImport(txId: String) {
        val pax = passengersFor(txId)
        if (pax.isEmpty()) return
        val candidates = pax.mapNotNull(::responsibleRootForPassenger).distinct()
        when (candidates.size) {
            1 -> assignResponsibleTransitively(pax.map { it.id }, candidates.first(), forceConflicts = false)
            in 2..Int.MAX_VALUE -> audit(
                "transaction",
                txId,
                "responsible_conflict",
                candidates.joinToString("|")
            )
        }
    }

    fun transactionsForPassenger(passengerId: String): List<Transaction> {
        val ids = mergedGroupIds(passengerId)
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        val out = mutableListOf<Transaction>()
        readableDatabase.rawQuery("""
            SELECT DISTINCT t.* FROM transactions t
            JOIN tx_passengers tp ON tp.tx_id=t.id
            WHERE tp.passenger_id IN ($placeholders)
            ORDER BY COALESCE(t.transaction_date,'') DESC, t.imported_at DESC
        """.trimIndent(), ids.toTypedArray()).use { c -> while (c.moveToNext()) out += c.toTransaction() }
        return out
    }

    fun transactionsForResponsible(responsibleId: String): List<Transaction> {
        val dependentIds = dependentsOf(responsibleId).flatMap { mergedGroupIds(it.id) }.distinct()
        if (dependentIds.isEmpty()) return emptyList()
        val placeholders = dependentIds.joinToString(",") { "?" }
        val out = mutableListOf<Transaction>()
        readableDatabase.rawQuery("""
            SELECT DISTINCT t.* FROM transactions t
            JOIN tx_passengers tp ON tp.tx_id=t.id
            WHERE tp.passenger_id IN ($placeholders)
            ORDER BY COALESCE(t.transaction_date,'') DESC, t.imported_at DESC
        """.trimIndent(), dependentIds.toTypedArray()).use { c -> while (c.moveToNext()) out += c.toTransaction() }
        return out
    }

    fun relatedOperations(txId: String): List<Transaction> {
        val tx = transaction(txId) ?: return emptyList()
        val docs = txPassengerDetails(txId).mapNotNull { it.documentNo?.trim()?.takeIf(String::isNotBlank) }.toSet()
        val pnr = tx.pnr
        val candidates = if (!pnr.isNullOrBlank()) transactions(search = pnr, limit = 500).filter { it.id != txId && it.pnr.equals(pnr, true) } else emptyList()
        if (docs.isEmpty()) return candidates
        return candidates.sortedWith(compareByDescending<Transaction> { other ->
            txPassengerDetails(other.id).any { it.documentNo in docs }
        }.thenBy { it.transactionDate.orEmpty() })
    }

    fun passengerFiles(passengerId: String): List<PassengerFile> {
        val ids = mergedGroupIds(passengerId)
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        val out = mutableListOf<PassengerFile>()
        readableDatabase.rawQuery(
            "SELECT * FROM passenger_files WHERE passenger_id IN ($placeholders) ORDER BY is_primary DESC, created_at DESC",
            ids.toTypedArray()
        ).use { c ->
            while (c.moveToNext()) out += PassengerFile(
                id = c.s("id"), passengerId = c.s("passenger_id"), uri = c.s("uri"),
                mimeType = c.sn("mime_type"), displayName = c.sn("display_name"),
                isPrimary = c.i("is_primary") == 1, createdAt = c.l("created_at")
            )
        }
        return out
    }

    fun addPassengerFile(passengerId: String, uri: String, mimeType: String?, displayName: String?): PassengerFile {
        val ownerId = resolveCanonicalPassengerId(passengerId)
        val first = passengerFiles(ownerId).isEmpty()
        val item = PassengerFile(UUID.randomUUID().toString(), ownerId, uri, mimeType, displayName, first)
        writableDatabase.insert("passenger_files", null, ContentValues().apply {
            put("id", item.id); put("passenger_id", ownerId); put("uri", uri); put("mime_type", mimeType)
            put("display_name", displayName); put("is_primary", if (item.isPrimary) 1 else 0); put("created_at", item.createdAt)
        })
        audit("passenger", ownerId, "add_passport_file", displayName ?: uri)
        return item
    }

    fun deletePassengerFile(id: String) {
        val owner = readableDatabase.rawQuery("SELECT passenger_id FROM passenger_files WHERE id=?", arrayOf(id))
            .use { c -> if (c.moveToFirst()) c.getString(0) else "" }
        writableDatabase.delete("passenger_files", "id=?", arrayOf(id))
        if (owner.isNotBlank()) audit("passenger", owner, "delete_passport_file", id)
    }

    fun setPrimaryPassengerFile(passengerId: String, id: String) {
        val ownerId = resolveCanonicalPassengerId(passengerId)
        val ids = mergedGroupIds(ownerId)
        val placeholders = ids.joinToString(",") { "?" }
        writableDatabase.beginTransaction()
        try {
            writableDatabase.update("passenger_files", ContentValues().apply { put("is_primary", 0) }, "passenger_id IN ($placeholders)", ids.toTypedArray())
            writableDatabase.update("passenger_files", ContentValues().apply { put("is_primary", 1) }, "id=?", arrayOf(id))
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        audit("passenger", ownerId, "primary_passport_file", id)
    }

    fun transactionAttachments(txId: String): List<TransactionAttachment> {
        val out = mutableListOf<TransactionAttachment>()
        readableDatabase.rawQuery(
            "SELECT * FROM transaction_attachments WHERE tx_id=? ORDER BY created_at DESC",
            arrayOf(txId)
        ).use { c ->
            while (c.moveToNext()) {
                out += TransactionAttachment(
                    id = c.s("id"),
                    txId = c.s("tx_id"),
                    uri = c.s("uri"),
                    mimeType = c.sn("mime_type"),
                    displayName = c.sn("display_name"),
                    createdAt = c.l("created_at")
                )
            }
        }
        return out
    }

    fun addTransactionAttachment(txId: String, uri: String, mimeType: String?, displayName: String?): TransactionAttachment {
        val tx = transaction(txId) ?: throw IllegalArgumentException("Unknown transaction")
        val item = TransactionAttachment(
            id = UUID.randomUUID().toString(),
            txId = txId,
            uri = uri,
            mimeType = mimeType,
            displayName = displayName
        )
        writableDatabase.insertOrThrow("transaction_attachments", null, ContentValues().apply {
            put("id", item.id)
            put("tx_id", item.txId)
            put("uri", item.uri)
            put("mime_type", item.mimeType)
            put("display_name", item.displayName)
            put("created_at", item.createdAt)
        })
        audit("transaction", tx.id, "add_attachment", item.displayName ?: item.uri)
        return item
    }

    fun deleteTransactionAttachment(id: String) {
        val owner = readableDatabase.rawQuery(
            "SELECT tx_id FROM transaction_attachments WHERE id=? LIMIT 1",
            arrayOf(id)
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
        writableDatabase.delete("transaction_attachments", "id=?", arrayOf(id))
        owner?.let { audit("transaction", it, "delete_attachment", id) }
    }

    fun auditEvents(entityType: String, entityId: String, limit: Int = 100): List<AuditEvent> {
        val out = mutableListOf<AuditEvent>()
        readableDatabase.rawQuery(
            "SELECT * FROM audit_log WHERE entity_type=? AND entity_id=? ORDER BY created_at DESC LIMIT ?",
            arrayOf(entityType, entityId, limit.toString())
        ).use { c ->
            while (c.moveToNext()) out += AuditEvent(
                id = c.s("id"), entityType = c.s("entity_type"), entityId = c.s("entity_id"),
                action = c.s("action"), details = c.sn("details"), createdAt = c.l("created_at")
            )
        }
        return out
    }

    private fun normalizeBusiness(parsed: ParsedTransaction): ParsedTransaction {
        var type = parsed.type
        val note = parsed.note.orEmpty()
        if (type == TxType.TICKET && note.contains("تغيير", true)) type = TxType.CHANGE
        if (type == TxType.TICKET && parsed.amount == 0.0 && parsed.flags.orEmpty().contains("VOID", true)) type = TxType.VOID
        if (type == TxType.VISA && parsed.amount == 0.0) type = TxType.VOID

        val visa = parsed.visaCountry ?: parsed.passengers.asSequence().mapNotNull { visaCountryFromProduct(it.product) }.firstOrNull()
        val airline = when {
            !parsed.airline.isNullOrBlank() -> resolveAirlineToken(parsed.airline)
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
        val raw = product.orEmpty()
        val canonical = canonicalVisaCountry(raw)
        return canonical.takeIf { it in setOf("UAE", "JORDAN", "EGYPT", "SAUDI") }
    }

    private fun Cursor.s(col: String) = getString(getColumnIndexOrThrow(col))
    private fun Cursor.sn(col: String): String? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.d(col: String) = getDouble(getColumnIndexOrThrow(col))
    private fun Cursor.dn(col: String): Double? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getDouble(it) }
    private fun Cursor.i(col: String) = getInt(getColumnIndexOrThrow(col))
    private fun Cursor.inn(col: String): Int? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getInt(it) }
    private fun Cursor.l(col: String) = getLong(getColumnIndexOrThrow(col))
    private fun Cursor.ln(col: String): Long? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getLong(it) }

    private fun sourcePassengerNamesFor(txId: String): List<String> {
        val out = mutableListOf<String>()
        readableDatabase.rawQuery("""
            SELECT COALESCE(tp.source_name,p.name) FROM passengers p
            JOIN tx_passengers tp ON tp.passenger_id=p.id
            WHERE tp.tx_id=?
            ORDER BY COALESCE(tp.source_name,p.name)
        """.trimIndent(), arrayOf(txId)).use { c ->
            while (c.moveToNext()) out += normalize(c.getString(0))
        }
        readableDatabase.rawQuery(
            "SELECT normalized_name FROM deleted_tx_passengers WHERE tx_id=?",
            arrayOf(txId)
        ).use { c ->
            while (c.moveToNext()) out += c.getString(0)
        }
        return out.distinct().sorted()
    }

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
            val existingNames = sourcePassengerNamesFor(existing.id)
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
