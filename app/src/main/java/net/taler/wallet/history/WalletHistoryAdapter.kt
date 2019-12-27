package net.taler.wallet.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import net.taler.wallet.HistoryResult
import net.taler.wallet.R


class WalletHistoryAdapter(private var myDataset: HistoryResult) : RecyclerView.Adapter<WalletHistoryAdapter.MyViewHolder>() {

    init {
        setHasStableIds(false)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val rowView = LayoutInflater.from(parent.context).inflate(R.layout.history_row, parent, false)
        return MyViewHolder(rowView)
    }

    override fun getItemCount(): Int {
        return myDataset.history.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val h = myDataset.history[myDataset.history.size - position - 1]
        val text = holder.rowView.findViewById<TextView>(R.id.history_text)
        val subText = holder.rowView.findViewById<TextView>(R.id.history_subtext)
        text.text = h.type
        subText.text = h.timestamp.toString() + "\n" + h.detail.toString(1)
    }

    fun update(updatedHistory: HistoryResult) {
        this.myDataset = updatedHistory
        this.notifyDataSetChanged()
    }

    class MyViewHolder(val rowView: View) : RecyclerView.ViewHolder(rowView)
}
