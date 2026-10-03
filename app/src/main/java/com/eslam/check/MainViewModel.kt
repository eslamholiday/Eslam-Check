package com.eslam.check

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eslam.check.data.*
import com.eslam.check.parser.BestChoiceParser
import com.eslam.check.parser.BridgeParser
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase(app)

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _passengers = MutableStateFlow<List<Passenger>>(emptyList())
    val passengers: StateFlow<List<Passenger>> = _passengers.asStateFlow()

    private val _stats = MutableStateFlow(DashboardStats())
    val stats: StateFlow<DashboardStats> = _stats.asStateFlow()

    private val _rules = MutableStateFlow<List<CommissionRule>>(emptyList())
    val rules: StateFlow<List<CommissionRule>> = _rules.asStateFlow()

    private val _airlines = MutableStateFlow<List<AirlineInfo>>(emptyList())
    val airlines: StateFlow<List<AirlineInfo>> = _airlines.asStateFlow()

    private val _settingsRevision = MutableStateFlow(0)
    val settingsRevision: StateFlow<Int> = _settingsRevision.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        PDFBoxResourceLoader.init(app)
        refresh()
    }

    fun refresh(search: String = "", types: Set<TxType> = emptySet(), states: Set<ReviewState> = emptySet()) {
        viewModelScope.launch(Dispatchers.IO) {
            _transactions.value = db.transactions(search = search, types = types, reviewStates = states, limit = 1000)
            _passengers.value = db.allPassengers(500)
            _stats.value = db.dashboardStats()
            _rules.value = db.rules()
            _airlines.value = db.airlines()
        }
    }

    fun clearMessage() { _message.value = null }

    fun previewBridge(text: String): BridgeParseResult = BridgeParser.parse(text)

    fun importBridge(result: BridgeParseResult) {
        if (!result.canImport) {
            _message.value = "تعذر الاستيراد: " + result.errors.firstOrNull().orEmpty()
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                val counts = withContext(Dispatchers.IO) {
                    var created = 0
                    var updated = 0
                    result.transactions.forEach { parsed ->
                        val (_, isNew) = db.upsertParsed(parsed, SourceType.BRIDGE)
                        if (isNew) created++ else updated++
                    }
                    db.setSetting("last_bridge_batch", result.batchId)
                    db.setSetting("last_bridge_snapshot", result.snapshotId)
                    db.setSetting("last_bridge_document", result.documentId)
                    db.setSetting("last_bridge_mode", result.mode)
                    created to updated
                }
                _message.value = "ECX: " + counts.first + " جديدة، " + counts.second + " موجودة/محدثة"
                refresh()
            } catch (e: Exception) {
                _message.value = "فشل استيراد ECX: " + (e.message ?: "خطأ غير معروف")
            } finally {
                _busy.value = false
            }
        }
    }

    fun importPdf(uri: Uri, forcedCurrency: Currency?) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val text = resolver.openInputStream(uri)?.use { input ->
                        PDDocument.load(input).use { doc -> PDFTextStripper().getText(doc) }
                    } ?: error("تعذر فتح الملف")
                    val parsed = BestChoiceParser.parse(text, forcedCurrency)
                    var created = 0
                    var updated = 0
                    parsed.forEach { parsedTx ->
                        val learnedType = db.applyLearnedType(parsedTx.rawText, parsedTx.type)
                        val item = if (learnedType != parsedTx.type) parsedTx.copy(type = learnedType) else parsedTx
                        val (_, isNew) = db.upsertParsed(item)
                        if (isNew) created++ else updated++
                    }
                    Triple(parsed.size, created, updated)
                }
                _message.value = "تمت قراءة ${result.first} عملية: ${result.second} جديدة، ${result.third} موجودة/محدثة"
                refresh()
            } catch (e: Exception) {
                _message.value = "فشل استيراد PDF: ${e.message ?: "خطأ غير معروف"}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun addManual(
        type: TxType,
        currency: Currency,
        pnr: String?,
        route: String?,
        amount: Double,
        discount: Double,
        baseFare: Double?,
        referenceTotal: Double?,
        airline: String?,
        passengerNames: List<String>,
        note: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            db.addManual(type, currency, pnr, route, amount, discount, baseFare, referenceTotal, airline, passengerNames, note)
            _message.value = "تمت إضافة العملية"
            refresh()
        }
    }

    fun setReview(id: String, state: ReviewState) {
        viewModelScope.launch(Dispatchers.IO) {
            db.markReview(id, state)
            refresh()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteTransaction(id)
            _message.value = "تم حذف العملية"
            refresh()
        }
    }

    fun transaction(id: String): Transaction? = db.transaction(id)
    fun passengersFor(id: String): List<Passenger> = db.passengersFor(id)
    fun txPassengerDetails(id: String): List<TxPassengerDetail> = db.txPassengerDetails(id)
    fun passengerSuggestions(query: String): List<Passenger> = db.passengerSuggestions(query)
    fun passengerById(id: String): Passenger? = db.passengerById(id)
    fun dependentsOf(id: String): List<Passenger> = db.dependentsOf(id)
    fun customerPhoneForTransaction(id: String): String? = db.customerPhoneForTransaction(id)
    fun responsibleForTransaction(id: String): Passenger? = db.responsibleForTransaction(id)
    fun relatedOperations(id: String): List<Transaction> = db.relatedOperations(id)
    fun transactionsForPassenger(id: String): List<Transaction> = db.transactionsForPassenger(id)
    fun transactionsForResponsible(id: String): List<Transaction> = db.transactionsForResponsible(id)
    fun passengerFiles(id: String): List<PassengerFile> = db.passengerFiles(id)
    fun passengerAliases(id: String): List<PassengerAlias> = db.passengerAliases(id)
    fun mergedPassengers(id: String): List<Passenger> = db.mergedPassengers(id)
    fun auditEvents(entityType: String, entityId: String): List<AuditEvent> = db.auditEvents(entityType, entityId)
    fun airlineNames(): List<String> = db.airlineNames()
    fun ruleForTransaction(tx: Transaction): CommissionRule? = db.ruleForTransaction(tx)

    fun updateTransaction(tx: Transaction) {
        viewModelScope.launch(Dispatchers.IO) {
            val old = db.transaction(tx.id)
            db.updateTransactionFields(tx)
            if (old?.type == TxType.UNKNOWN && tx.type != TxType.UNKNOWN) {
                db.learnClassification(old.rawText, tx.type)
            }
            refresh()
        }
    }

    fun saveRule(
        airline: String,
        kind: RuleKind,
        value: Double,
        roundTripValue: Double? = null,
        note: String? = null,
        effectiveFrom: String? = null,
        direction: String = "ANY",
        ruleId: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = ruleId?.let { id -> db.rules().firstOrNull { it.id == id } }
            val rule = CommissionRule(
                id = existing?.id ?: UUID.randomUUID().toString(),
                airline = airline.trim(),
                kind = kind,
                value = value,
                roundTripValue = roundTripValue,
                reverseOnly = existing?.reverseOnly ?: false,
                direction = direction,
                effectiveFrom = effectiveFrom,
                learned = existing?.learned ?: false,
                active = true,
                note = note
            )
            db.saveRule(rule)
            _rules.value = db.rules()
            _message.value = if (existing == null) "تمت إضافة قاعدة عمولة جديدة" else "تم تحديث قاعدة العمولة"
        }
    }

    fun addAirline(code: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.saveAirline(code, name)
            _airlines.value = db.airlines()
            _message.value = "تمت إضافة شركة الطيران"
        }
    }

    fun setPassengerBaseFare(txId: String, passengerId: String, baseFare: Double?) {
        viewModelScope.launch(Dispatchers.IO) {
            db.setPassengerBaseFare(txId, passengerId, baseFare)
            refresh()
        }
    }

    fun setPassengerBaseFareForAll(txId: String, baseFare: Double?) {
        viewModelScope.launch(Dispatchers.IO) {
            db.setPassengerBaseFareForTransaction(txId, baseFare)
            refresh()
        }
    }

    fun setAirline(txId: String, airline: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            db.setAirlineForTransaction(txId, airline, learnPrefix = true)
            refresh()
        }
    }

    fun updatePassenger(person: Passenger) {
        viewModelScope.launch(Dispatchers.IO) {
            db.updatePassenger(person)
            refresh()
        }
    }

    fun assignResponsible(passengerId: String, responsibleId: String?, relation: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            db.assignResponsible(passengerId, responsibleId, relation)
            refresh()
        }
    }

    fun assignResponsibleForTransaction(txId: String, responsibleId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.assignResponsibleForTransaction(txId, responsibleId)
            refresh()
        }
    }

    fun assignResponsibleForPnr(pnr: String, responsibleId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.assignResponsibleForPnr(pnr, responsibleId)
            refresh()
        }
    }

    fun mergePassengers(primaryId: String, secondaryIds: Collection<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = db.mergePassengers(primaryId, secondaryIds)
            _message.value = if (result != null) "تم دمج المسافرين في ملف واحد" else "تعذر دمج المسافرين"
            refresh()
        }
    }

    fun unmergePassenger(sourceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = db.unmergePassenger(sourceId)
            _message.value = if (result != null) "تم فك الدمج" else "تعذر فك الدمج"
            refresh()
        }
    }

    fun addPassengerFileImmediate(passengerId: String, uri: String, mimeType: String?, displayName: String?): PassengerFile =
        db.addPassengerFile(passengerId, uri, mimeType, displayName)

    fun deletePassengerFileImmediate(id: String) = db.deletePassengerFile(id)

    fun setPrimaryPassengerFileImmediate(passengerId: String, id: String) = db.setPrimaryPassengerFile(passengerId, id)

    fun addPassengerFile(passengerId: String, uri: String, mimeType: String?, displayName: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            db.addPassengerFile(passengerId, uri, mimeType, displayName)
            _message.value = "تمت إضافة ملف الجواز"
            refresh()
        }
    }

    fun deletePassengerFile(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deletePassengerFile(id)
            _message.value = "تم حذف الملف"
            refresh()
        }
    }

    fun setPrimaryPassengerFile(passengerId: String, id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.setPrimaryPassengerFile(passengerId, id)
            _message.value = "تم تعيين ملف الجواز الأساسي"
            refresh()
        }
    }

    fun setting(key: String, default: String = "") = db.setting(key, default)
    fun setSetting(key: String, value: String) {
        db.setSetting(key, value)
        _settingsRevision.value = _settingsRevision.value + 1
    }
}
