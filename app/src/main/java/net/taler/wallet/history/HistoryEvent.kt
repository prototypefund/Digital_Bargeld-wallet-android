/*
 This file is part of GNU Taler
 (C) 2019 Taler Systems S.A.

 GNU Taler is free software; you can redistribute it and/or modify it under the
 terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3, or (at your option) any later version.

 GNU Taler is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 A PARTICULAR PURPOSE.  See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 GNU Taler; see the file COPYING.  If not, see <http://www.gnu.org/licenses/>
 */

package net.taler.wallet.history

import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME
import net.taler.wallet.R

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
class ReserveCreationDetail(val type: ReserveType, val bankUrl: String?)

enum class RefreshReason {
    @JsonProperty("manual")
    MANUAL,
    @JsonProperty("pay")
    PAY,
    @JsonProperty("refund")
    REFUND,
    @JsonProperty("abort-pay")
    ABORT_PAY,
    @JsonProperty("recoup")
    RECOUP,
    @JsonProperty("backup-restored")
    BACKUP_RESTORED
}


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

class History : ArrayList<HistoryEvent>()

@JsonTypeInfo(
    use = NAME,
    include = PROPERTY,
    property = "type"
)
@JsonSubTypes(
    Type(value = ExchangeAddedEvent::class, name = "exchange-added"),
    Type(value = ExchangeUpdatedEvent::class, name = "exchange-updated"),
    Type(value = ReserveBalanceUpdatedEvent::class, name = "reserve-balance-updated"),
    Type(value = HistoryWithdrawnEvent::class, name = "withdrawn"),
    Type(value = HistoryOrderAcceptedEvent::class, name = "order-accepted"),
    Type(value = HistoryOrderRefusedEvent::class, name = "order-refused"),
    Type(value = HistoryPaymentSentEvent::class, name = "payment-sent"),
    Type(value = HistoryRefreshedEvent::class, name = "refreshed")
)
@JsonIgnoreProperties(
    value = [
        "eventId"
    ]
)
abstract class HistoryEvent(
    val timestamp: Timestamp,
    @get:LayoutRes
    open val layout: Int = R.layout.history_row,
    @get:StringRes
    open val title: Int = 0,
    @get:DrawableRes
    open val icon: Int = R.drawable.ic_account_balance
)


@JsonTypeName("exchange-added")
class ExchangeAddedEvent(
    timestamp: Timestamp,
    val exchangeBaseUrl: String,
    val builtIn: Boolean
) : HistoryEvent(timestamp) {
    override val title = R.string.history_event_exchange_added
}

@JsonTypeName("exchange-updated")
class ExchangeUpdatedEvent(
    timestamp: Timestamp,
    val exchangeBaseUrl: String
) : HistoryEvent(timestamp) {
    override val title = R.string.history_event_exchange_updated
}


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
) : HistoryEvent(timestamp) {
    override val title = R.string.history_event_reserve_balance_updated
}

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
) : HistoryEvent(timestamp) {
    override val layout = R.layout.history_withdrawn
    override val title = R.string.history_event_withdrawn
    override val icon = R.drawable.history_withdrawn
}

@JsonTypeName("order-accepted")
class HistoryOrderAcceptedEvent(
    timestamp: Timestamp,
    /**
     * Condensed info about the order.
     */
    val orderShortInfo: OrderShortInfo
) : HistoryEvent(timestamp) {
    override val icon = R.drawable.ic_add_circle
    override val title = R.string.history_event_order_accepted
}

@JsonTypeName("order-refused")
class HistoryOrderRefusedEvent(
    timestamp: Timestamp,
    /**
     * Condensed info about the order.
     */
    val orderShortInfo: OrderShortInfo
) : HistoryEvent(timestamp) {
    override val icon = R.drawable.ic_cancel
    override val title = R.string.history_event_order_refused
}

@JsonTypeName("payment-sent")
class HistoryPaymentSentEvent(
    timestamp: Timestamp,
    /**
     * Condensed info about the order that we already paid for.
     */
    val orderShortInfo: OrderShortInfo,
    /**
     * Set to true if the payment has been previously sent
     * to the merchant successfully, possibly with a different session ID.
     */
    val replay: Boolean,
    /**
     * Number of coins that were involved in the payment.
     */
    val numCoins: Int,
    /**
     * Amount that was paid, including deposit and wire fees.
     */
    val amountPaidWithFees: String,
    /**
     * Session ID that the payment was (re-)submitted under.
     */
    val sessionId: String?
) : HistoryEvent(timestamp) {
    override val layout = R.layout.history_payment_sent
    override val title = R.string.history_event_payment_sent
    override val icon = R.drawable.ic_cash_usd_outline
}

@JsonTypeName("refreshed")
class HistoryRefreshedEvent(
    timestamp: Timestamp,
    /**
     * Amount that is now available again because it has
     * been refreshed.
     */
    val amountRefreshedEffective: String,
    /**
     * Amount that we spent for refreshing.
     */
    val amountRefreshedRaw: String,
    /**
     * Why was the refreshing done?
     */
    val refreshReason: RefreshReason,
    val numInputCoins: Int,
    val numRefreshedInputCoins: Int,
    val numOutputCoins: Int,
    /**
     * Identifier for a refresh group, contains one or
     * more refresh session IDs.
     */
    val refreshGroupId: String
) : HistoryEvent(timestamp) {
    override val icon = R.drawable.ic_history_black_24dp
    override val title = R.string.history_event_refreshed
}

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

data class OrderShortInfo(
    /**
     * Wallet-internal identifier of the proposal.
     */
    val proposalId: String,
    /**
     * Order ID, uniquely identifies the order within a merchant instance.
     */
    val orderId: String,
    /**
     * Base URL of the merchant.
     */
    val merchantBaseUrl: String,
    /**
     * Amount that must be paid for the contract.
     */
    val amount: String,
    /**
     * Summary of the proposal, given by the merchant.
     */
    val summary: String
)
