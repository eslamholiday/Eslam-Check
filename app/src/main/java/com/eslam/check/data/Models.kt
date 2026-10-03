package com.eslam.check.data

enum class Currency { IQD, USD }

enum class TxType {
    TICKET, VISA, CHANGE, REFUND, PAYMENT, VOID, REISSUE, FEE, UNKNOWN
}

enum class ReviewState { UNREVIEWED, REVIEWED, FOLLOW_UP }

enum class SourceType { PDF, MANUAL, PDF_MANUAL, BRIDGE, BRIDGE_MANUAL }

enum class RuleKind { PERCENT_BASE, FIXED_PER_PASSENGER, PRIVATE_MANUAL, NONE }

enum class PassengerCategory { ALL, RESPONSIBLE, DEPENDENT, INDEPENDENT }

data class Transaction(
    val id: String,
    val externalId: String? = null,
    val ledgerId: String? = null,
    val documentId: String? = null,
    val snapshotId: String? = null,
    val operationNo: String? = null,
    val transactionDate: String? = null,
    val currency: Currency = Currency.USD,
    val type: TxType = TxType.UNKNOWN,
    val source: SourceType = SourceType.MANUAL,
    val sourceCode: String? = null,
    val statementSeq: Int? = null,
    val batchId: String? = null,
    val pnr: String? = null,
    val route: String? = null,
    val amount: Double = 0.0,
    val ledgerEffect: Double? = null,
    val discount: Double = 0.0,
    val balanceAfter: Double? = null,
    val baseFare: Double? = null,
    val referenceTotal: Double? = null,
    val airline: String? = null,
    val visaCountry: String? = null,
    val externalLink: String? = null,
    val commissionRuleSnapshot: String? = null,
    val reviewState: ReviewState = ReviewState.UNREVIEWED,
    val warning: String? = null,
    val note: String? = null,
    val flags: String? = null,
    val rawText: String? = null,
    val sourceHash: String? = null,
    val importedAt: Long = System.currentTimeMillis(),
    val reviewedAt: Long? = null,
    val changedAfterReview: Boolean = false
)

data class Passenger(
    val id: String,
    val name: String,
    val passport: String? = null,
    val phone: String? = null,
    val responsibleId: String? = null,
    val responsibleRelation: String? = null,
    val isResponsible: Boolean = false,
    val mergedIntoId: String? = null
)

data class PassengerAlias(
    val id: String,
    val passengerId: String,
    val kind: String,
    val value: String,
    val normalizedValue: String,
    val sourcePassengerId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class PassengerFile(
    val id: String,
    val passengerId: String,
    val uri: String,
    val mimeType: String? = null,
    val displayName: String? = null,
    val isPrimary: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class TransactionAttachment(
    val id: String,
    val txId: String,
    val uri: String,
    val mimeType: String? = null,
    val displayName: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class DeletedPassengerLink(
    val id: String,
    val txId: String,
    val currency: Currency,
    val operationNo: String?,
    val passengerId: String,
    val passengerName: String,
    val normalizedName: String,
    val normalizedPassport: String? = null,
    val amount: Double? = null,
    val baseFare: Double? = null,
    val passengerType: String? = null,
    val documentNo: String? = null,
    val product: String? = null,
    val flags: String? = null,
    val deletedAt: Long = System.currentTimeMillis()
)

data class ResponsibleContact(
    val id: String,
    val name: String,
    val phone: String? = null
)

data class TxPassenger(
    val txId: String,
    val passengerId: String,
    val amount: Double? = null,
    val baseFare: Double? = null,
    val passengerType: String? = null,
    val documentNo: String? = null,
    val product: String? = null,
    val flags: String? = null
)

data class TxPassengerDetail(
    val passenger: Passenger,
    val amount: Double? = null,
    val baseFare: Double? = null,
    val passengerType: String? = null,
    val documentNo: String? = null,
    val product: String? = null,
    val flags: String? = null
)

data class AirlineInfo(
    val id: String,
    val code: String,
    val name: String,
    val active: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

data class CommissionRule(
    val id: String,
    val airline: String,
    val kind: RuleKind,
    val value: Double,
    val roundTripValue: Double? = null,
    val reverseOnly: Boolean = false,
    val direction: String = "ANY",
    val effectiveFrom: String? = null,
    val learned: Boolean = false,
    val active: Boolean = true,
    val note: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

data class AuditEvent(
    val id: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val details: String? = null,
    val createdAt: Long
)

data class ParsedPassenger(
    val name: String,
    val passengerType: String? = null,
    val documentNo: String? = null,
    val amount: Double? = null,
    val passport: String? = null,
    val product: String? = null,
    val flags: String? = null
)

data class ParsedTransaction(
    val externalId: String? = null,
    val ledgerId: String? = null,
    val documentId: String? = null,
    val snapshotId: String? = null,
    val operationNo: String?,
    val transactionDate: String? = null,
    val currency: Currency,
    val type: TxType,
    val sourceCode: String? = null,
    val statementSeq: Int? = null,
    val batchId: String? = null,
    val pnr: String?,
    val route: String?,
    val amount: Double,
    val ledgerEffect: Double? = null,
    val discount: Double = 0.0,
    val balanceAfter: Double? = null,
    val passengers: List<ParsedPassenger>,
    val airline: String? = null,
    val visaCountry: String? = null,
    val rawText: String,
    val note: String? = null,
    val flags: String? = null
)

data class DashboardStats(
    val total: Int = 0,
    val newCount: Int = 0,
    val unreviewed: Int = 0,
    val ambiguous: Int = 0,
    val changed: Int = 0,
    val payments: Int = 0
)

data class BridgeLedgerMeta(
    val ledgerId: String = "",
    val currency: Currency,
    val rangeFrom: String,
    val rangeTo: String,
    val openingDate: String,
    val openingBalance: Double,
    val closingBalance: Double,
    val expectedCount: Int
)

data class BridgeParseResult(
    val version: Int = 1,
    val dictionaryVersion: String = "",
    val batchId: String = "",
    val documentId: String = "",
    val snapshotId: String = "",
    val mode: String = "CUMULATIVE",
    val ledgers: List<BridgeLedgerMeta> = emptyList(),
    val transactions: List<ParsedTransaction> = emptyList(),
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val checksumVerified: Boolean? = null
) {
    val canImport: Boolean get() = errors.isEmpty() && transactions.isNotEmpty()
    val usdCount: Int get() = transactions.count { it.currency == Currency.USD }
    val iqdCount: Int get() = transactions.count { it.currency == Currency.IQD }
    val passengerCount: Int get() = transactions.sumOf { it.passengers.size }
    val pnrCount: Int get() = transactions.mapNotNull { it.pnr }.distinct().size
    val ambiguousAirlines: Int get() = transactions.count {
        it.type == TxType.TICKET && it.currency == Currency.USD && it.airline.isNullOrBlank()
    }
}
