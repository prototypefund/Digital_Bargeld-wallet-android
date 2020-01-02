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

import android.text.format.DateUtils.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import net.taler.wallet.ParsedAmount.Companion.parseAmount
import net.taler.wallet.R


internal class WalletHistoryAdapter(private var history: History = History()) :
    Adapter<HistoryEventViewHolder>() {

    init {
        setHasStableIds(false)
    }

    override fun getItemViewType(position: Int): Int = history[position].layout

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryEventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return when (viewType) {
            R.layout.history_withdrawn -> HistoryWithdrawnViewHolder(view)
            R.layout.history_payment_sent -> HistoryPaymentSentViewHolder(view)
            else -> GenericHistoryEventViewHolder(view)
        }
    }

    override fun getItemCount(): Int = history.size

    override fun onBindViewHolder(holder: HistoryEventViewHolder, position: Int) {
        val event = history[position]
        holder.bind(event)
    }

    fun update(updatedHistory: History) {
        this.history = updatedHistory
        this.notifyDataSetChanged()
    }

}

internal abstract class HistoryEventViewHolder(protected val v: View) : ViewHolder(v) {

    private val icon: ImageView = v.findViewById(R.id.icon)
    protected val title: TextView = v.findViewById(R.id.title)
    private val time: TextView = v.findViewById(R.id.time)

    @CallSuper
    open fun bind(event: HistoryEvent) {
        icon.setImageResource(event.icon)
        if (event.title == 0) title.text = event::class.java.simpleName
        else title.setText(event.title)
        time.text = getRelativeTime(event.timestamp.ms)
    }

    private fun getRelativeTime(timestamp: Long): CharSequence {
        val now = System.currentTimeMillis()
        return if (now - timestamp > DAY_IN_MILLIS * 2) {
            formatDateTime(
                v.context,
                timestamp,
                FORMAT_SHOW_TIME or FORMAT_SHOW_DATE or FORMAT_ABBREV_MONTH or FORMAT_NO_YEAR
            )
        } else {
            getRelativeTimeSpanString(timestamp, now, MINUTE_IN_MILLIS, FORMAT_ABBREV_RELATIVE)
        }
    }

}

internal class GenericHistoryEventViewHolder(v: View) : HistoryEventViewHolder(v) {

    private val info: TextView = v.findViewById(R.id.info)

    override fun bind(event: HistoryEvent) {
        super.bind(event)
        info.text = when (event) {
            is ExchangeAddedEvent -> event.exchangeBaseUrl
            is ExchangeUpdatedEvent -> event.exchangeBaseUrl
            is ReserveBalanceUpdatedEvent -> parseAmount(event.amountReserveBalance).toString()
            is HistoryPaymentSentEvent -> event.orderShortInfo.summary
            is HistoryOrderAcceptedEvent -> event.orderShortInfo.summary
            is HistoryOrderRefusedEvent -> event.orderShortInfo.summary
            is HistoryRefreshedEvent -> {
                "${parseAmount(event.amountRefreshedRaw)} - ${parseAmount(event.amountRefreshedEffective)}"
            }
            else -> ""
        }
    }

}

internal class HistoryWithdrawnViewHolder(v: View) : HistoryEventViewHolder(v) {

    private val exchangeUrl: TextView = v.findViewById(R.id.exchangeUrl)
    private val amountWithdrawn: TextView = v.findViewById(R.id.amountWithdrawn)
    private val fee: TextView = v.findViewById(R.id.fee)

    override fun bind(event: HistoryEvent) {
        super.bind(event)
        event as HistoryWithdrawnEvent

        exchangeUrl.text = event.exchangeBaseUrl
        val parsedEffective = parseAmount(event.amountWithdrawnEffective)
        val parsedRaw = parseAmount(event.amountWithdrawnRaw)
        amountWithdrawn.text = parsedRaw.toString()
        fee.text = (parsedRaw - parsedEffective).toString()
    }

}

internal class HistoryPaymentSentViewHolder(v: View) : HistoryEventViewHolder(v) {

    private val summary: TextView = v.findViewById(R.id.summary)
    private val amountPaidWithFees: TextView = v.findViewById(R.id.amountPaidWithFees)

    override fun bind(event: HistoryEvent) {
        super.bind(event)
        event as HistoryPaymentSentEvent

        title.text = event.orderShortInfo.summary
        summary.setText(event.title)
        amountPaidWithFees.text = parseAmount(event.amountPaidWithFees).toString()
    }

}
