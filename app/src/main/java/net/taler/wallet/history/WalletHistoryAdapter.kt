package net.taler.wallet.history

import android.text.format.DateUtils.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import net.taler.wallet.R


internal class WalletHistoryAdapter(private var history: History = History()) :
    Adapter<HistoryEventViewHolder>() {

    init {
        setHasStableIds(false)
    }

    override fun getItemViewType(position: Int): Int = when (history[position]) {
        is HistoryWithdrawnEvent -> R.layout.history_withdrawn
        else -> R.layout.history_row
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryEventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return when (viewType) {
            R.layout.history_withdrawn -> HistoryWithdrawnEventViewHolder(view)
            else -> HistoryEventViewHolder(view)
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

internal open class HistoryEventViewHolder(protected val v: View) : ViewHolder(v) {

    protected val title: TextView = v.findViewById(R.id.title)
    private val time: TextView = v.findViewById(R.id.time)

    @CallSuper
    open fun bind(event: HistoryEvent) {
        title.text = event::class.java.simpleName
        time.text = getRelativeTime(event.timestamp.ms)
    }

    private fun getRelativeTime(timestamp: Long): CharSequence {
        val now = System.currentTimeMillis()
        return getRelativeTimeSpanString(timestamp, now, MINUTE_IN_MILLIS, FORMAT_ABBREV_RELATIVE)
    }

    protected fun getString(resId: Int) = v.context.getString(resId)

}

internal class HistoryWithdrawnEventViewHolder(v: View) : HistoryEventViewHolder(v) {

    private val amountWithdrawnRaw: TextView = v.findViewById(R.id.amountWithdrawnRaw)
    private val amountWithdrawnEffective: TextView = v.findViewById(R.id.amountWithdrawnEffective)

    override fun bind(event: HistoryEvent) {
        super.bind(event)
        event as HistoryWithdrawnEvent

        title.text = getString(R.string.history_event_withdrawn)
        amountWithdrawnRaw.text = event.amountWithdrawnRaw
        amountWithdrawnEffective.text = event.amountWithdrawnEffective
    }

}
