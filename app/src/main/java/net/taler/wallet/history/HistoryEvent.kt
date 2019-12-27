package net.taler.wallet.history

import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME
import org.json.JSONObject

open class HistoryEntry(
    val detail: JSONObject,
    val type: String,
    val timestamp: JSONObject
)

enum class ReserveType {
    /**
     * Manually created.
     */
    @JsonProperty("manual")
    MANUAL,
    /**
     * Withdrawn from a bank that has "tight" Taler integration
     */
    @JsonProperty("taler-bank-withdraw")
    TALER_BANK_WITHDRAW,
}

@JsonInclude(NON_EMPTY)
class ReserveCreationDetail(val type: ReserveType)


@JsonInclude(NON_EMPTY)
class Timestamp(
    @JsonProperty("t_ms")
    val ms: Long
)

@JsonInclude(NON_EMPTY)
class ReserveShortInfo(
    /**
     * The exchange that the reserve will be at.
     */
    val exchangeBaseUrl: String,
    /**
     * Key to query more details
     */
    val reservePub: String,
    /**
     * Detail about how the reserve has been created.
     */
    val reserveCreationDetail: ReserveCreationDetail
)

class History: ArrayList<HistoryEvent>()

@JsonTypeInfo(
    use = NAME,
    include = PROPERTY,
    property = "type"
)
@JsonSubTypes(
    Type(value = ExchangeAddedEvent::class, name = "exchange-added"),
    Type(value = ExchangeUpdatedEvent::class, name = "exchange-updated"),
    Type(value = ReserveBalanceUpdatedEvent::class, name = "reserve-balance-updated"),
    Type(value = HistoryWithdrawnEvent::class, name = "withdrawn")
)
@JsonIgnoreProperties(
    value = [
        "eventId"
    ]
)
abstract class HistoryEvent(
    val timestamp: Timestamp
)


@JsonTypeName("exchange-added")
class ExchangeAddedEvent(
    timestamp: Timestamp,
    val exchangeBaseUrl: String,
    val builtIn: Boolean
) : HistoryEvent(timestamp)

@JsonTypeName("exchange-updated")
class ExchangeUpdatedEvent(
    timestamp: Timestamp,
    val exchangeBaseUrl: String
) : HistoryEvent(timestamp)


@JsonTypeName("reserve-balance-updated")
class ReserveBalanceUpdatedEvent(
    timestamp: Timestamp,
    val newHistoryTransactions: List<ReserveTransaction>,
    /**
     * Condensed information about the reserve.
     */
    val reserveShortInfo: ReserveShortInfo,
    /**
     * Amount currently left in the reserve.
     */
    val amountReserveBalance: String,
    /**
     * Amount we expected to be in the reserve at that time,
     * considering ongoing withdrawals from that reserve.
     */
    val amountExpected: String
) : HistoryEvent(timestamp)

@JsonTypeName("withdrawn")
class HistoryWithdrawnEvent(
    timestamp: Timestamp,
    /**
     * Exchange that was withdrawn from.
     */
    val exchangeBaseUrl: String,
    /**
     * Unique identifier for the withdrawal session, can be used to
     * query more detailed information from the wallet.
     */
    val withdrawSessionId: String,
    val withdrawalSource: WithdrawalSource,
    /**
     * Amount that has been subtracted from the reserve's balance
     * for this withdrawal.
     */
    val amountWithdrawnRaw: String,
    /**
     * Amount that actually was added to the wallet's balance.
     */
    val amountWithdrawnEffective: String
) : HistoryEvent(timestamp)


@JsonTypeInfo(
    use = NAME,
    include = PROPERTY,
    property = "type"
)
@JsonSubTypes(
    Type(value = WithdrawalSourceReserve::class, name = "reserve")
)
abstract class WithdrawalSource

@JsonTypeName("tip")
class WithdrawalSourceTip(
    val tipId: String
) : WithdrawalSource()

@JsonTypeName("reserve")
class WithdrawalSourceReserve(
    val reservePub: String
) : WithdrawalSource()
