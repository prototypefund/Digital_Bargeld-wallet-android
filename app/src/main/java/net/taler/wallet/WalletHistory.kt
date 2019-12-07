package net.taler.wallet


import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import android.widget.TextView
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

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
        val h = myDataset.history[position]
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


/**
 * Wallet history.
 *
 */
class WalletHistory : Fragment() {

    lateinit var model: WalletViewModel
    lateinit var historyAdapter: WalletHistoryAdapter
    lateinit var historyPlaceholder: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        historyAdapter = WalletHistoryAdapter(HistoryResult(listOf()))

        model = activity?.run {
            ViewModelProviders.of(this)[WalletViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        activity?.menuInflater?.inflate(R.menu.history, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.reload_history -> {
                updateHistory()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateHistory() {
        model.getHistory {
            if (it.history.size == 0) {
                historyPlaceholder.visibility = View.VISIBLE
            }
            historyAdapter.update(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_show_history, container, false)
        val myLayoutManager = LinearLayoutManager(context)
        val myItemDecoration = DividerItemDecoration(context, myLayoutManager.orientation)
        view.findViewById<RecyclerView>(R.id.list_history).apply {
            layoutManager = myLayoutManager
            adapter = historyAdapter
            addItemDecoration(myItemDecoration)
        }

        historyPlaceholder = view.findViewById<View>(R.id.list_history_placeholder)
        historyPlaceholder.visibility = View.GONE

        updateHistory()

        return view
    }

    companion object {
        const val TAG = "taler-wallet"
    }
}
