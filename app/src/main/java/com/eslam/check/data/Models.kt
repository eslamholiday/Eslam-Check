package com.eslam.check.data

enum class Currency { IQD, USD }

enum class TxType {
    TICKET, VISA, CHANGE, REFUND, PAYMENT, VOID, REISSUE, FEE, UNKNOWN
}

enum class ReviewState { UNREVIEWED, REVIEWED, FOLLOW_UP }

enum class SourceType { PDF, MANUAL, PDF_MANUAL }

enum class RuleKind { PERCENT_BASE, FIXED_PER_PASSENGER, PRIVATE_MANUAL, NONE }

data class Transaction(
    val id: String,
    val operationNo: String? = null,
    val currency: Currency = Currency.USD,
    val type: TxType = TxType.UNKNOWN,
    val source: SourceType = SourceType.MANUAL,
    val pnr: String? = null,
    val route: String? = null,
    val amount: Double = 0.0,
    val discount: Double = 0.0,
    val baseFare: Double? = null,
    val referenceTotal: Double? = null,
    val airline: String? = null,
    val reviewState: ReviewState = ReviewState.UNREVIEWED,
    val warning: String? = null,
    val note: String? = null,
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
    val responsibleId: String? = null
)

data class ResponsibleContact(
    val id: String,
    val name: String,
    val phone: String? = null
)

data class TxPassenger(
    val txId: String,
    val passengerId: String,
    val amount: Double? = null
)

data class CommissionRule(
    val id: String,
    val airline: String,
    val kind: RuleKind,
    val value: Double,
    val reverseOnly: Boolean = false,
    val learned: Boolean = false,
    val active: Boolean = true,
    val note: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

data class ParsedPassenger(
    val name: String,
    val passport: String? = null,
    val amount: Double? = null
)

data class ParsedTransaction(
    val operationNo: String?,
    val currency: Currency,
    val type: TxType,
    val pnr: String?,
    val route: String?,
    val amount: Double,
    val discount: Double,
    val passengers: List<ParsedPassenger>,
    val rawText: String,
    val note: String? = null
)

data class DashboardStats(
    val total: Int = 0,
    val newCount: Int = 0,
    val unreviewed: Int = 0,
    val ambiguous: Int = 0,
    val changed: Int = 0,
    val payments: Int = 0
)
