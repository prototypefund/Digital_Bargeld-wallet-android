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

package net.taler.wallet


import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.fragment.app.Fragment
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.zxing.integration.android.IntentIntegrator
import me.zhanghai.android.materialprogressbar.MaterialProgressBar

class WalletBalanceAdapter(private var myDataset: WalletBalances) : RecyclerView.Adapter<WalletBalanceAdapter.MyViewHolder>() {

    init {
        setHasStableIds(false)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val rowView = LayoutInflater.from(parent.context).inflate(R.layout.balance_row, parent, false)
        return MyViewHolder(rowView)
    }

    override fun getItemCount(): Int {
        return myDataset.byCurrency.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val amount = myDataset.byCurrency[position].available
        val amountIncoming = myDataset.byCurrency[position].pendingIncoming
        val currencyView = holder.rowView.findViewById<TextView>(R.id.balance_currency)
        currencyView.text = amount.currency
        val amountView = holder.rowView.findViewById<TextView>(R.id.balance_amount)
        amountView.text = amount.amount

        val amountIncomingRow = holder.rowView.findViewById<View>(R.id.balance_row_pending)

        val amountIncomingView = holder.rowView.findViewById<TextView>(R.id.balance_pending)
        if (amountIncoming.isZero()) {
            amountIncomingRow.visibility = View.GONE
        } else {
            amountIncomingRow.visibility = View.VISIBLE
            amountIncomingView.text = "${amountIncoming.amount} ${amountIncoming.currency}"
        }
    }

    fun update(updatedBalances: WalletBalances) {
        this.myDataset = updatedBalances
        this.notifyDataSetChanged()
    }

    class MyViewHolder(val rowView: View) : RecyclerView.ViewHolder(rowView)
}

class PendingOperationsAdapter(private var myDataset: PendingOperations) : RecyclerView.Adapter<PendingOperationsAdapter.MyViewHolder>() {

    init {
        setHasStableIds(false)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val rowView = LayoutInflater.from(parent.context).inflate(R.layout.pending_row, parent, false)
        return MyViewHolder(rowView)
    }

    override fun getItemCount(): Int {
        return myDataset.pending.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val p = myDataset.pending[position]
        val textView = holder.rowView.findViewById<TextView>(R.id.pending_text)
        val subTextView = holder.rowView.findViewById<TextView>(R.id.pending_subtext)
        subTextView.text = p.detail.toString(1)
        textView.text = p.type
    }

    fun update(updatedDataset: PendingOperations) {
        this.myDataset = updatedDataset
        this.notifyDataSetChanged()
    }

    class MyViewHolder(val rowView: View) : RecyclerView.ViewHolder(rowView)
}


/**
 * A simple [Fragment] subclass.
 *
 */
class ShowBalance : Fragment() {

    private lateinit var pendingOperationsLabel: View
    lateinit var balancesView: RecyclerView
    lateinit var balancesPlaceholderView: TextView
    lateinit var model: WalletViewModel
    lateinit var balancesAdapter: WalletBalanceAdapter

    lateinit var pendingAdapter: PendingOperationsAdapter

    private fun triggerLoading() {
        val loading: Boolean =
            (model.testWithdrawalInProgress.value == true) || (model.balances.value == null) || !model.balances.value!!.initialized

        val myActivity = activity!!
        val progressBar = myActivity.findViewById<MaterialProgressBar>(R.id.progress_bar)
        if (loading) {
            progressBar.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.INVISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        triggerLoading()
        Log.v("taler-wallet", "called onResume on ShowBalance")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.retry_pending -> {
                model.retryPendingNow()
                true
            }
            R.id.reload_balance -> {
                triggerLoading()
                model.balances.value = WalletBalances(false, listOf())
                model.getBalances()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        activity?.menuInflater?.inflate(R.menu.balance, menu)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        model = activity?.run {
            ViewModelProviders.of(this)[WalletViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

    }


    private fun updateBalances(balances: WalletBalances) {
        if (!balances.initialized) {
            balancesPlaceholderView.visibility = View.GONE
            balancesView.visibility = View.GONE
        } else if (balances.byCurrency.isEmpty()) {
            balancesPlaceholderView.visibility = View.VISIBLE
            balancesView.visibility = View.GONE
        } else {
            balancesPlaceholderView.visibility = View.GONE
            balancesView.visibility = View.VISIBLE
        }
        Log.v(TAG, "updating balances ${balances}")
        balancesAdapter.update(balances)
    }

    private fun updatePending(pendingOperations: PendingOperations) {
        if (pendingOperations.pending.size == 0) {
            pendingOperationsLabel.visibility = View.GONE
        } else {
            pendingOperationsLabel.visibility = View.VISIBLE
        }
        pendingAdapter.update(pendingOperations)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_show_balance, container, false)
        val payQrButton = view.findViewById<Button>(R.id.button_pay_qr)
        payQrButton.setOnClickListener {
            val integrator = IntentIntegrator(activity)
            integrator.setPrompt("Place merchant's QR Code inside the viewfinder rectangle to initiate payment.")
            integrator.initiateScan(listOf("QR_CODE"))
        }

        val withdrawTestkudosButton = view.findViewById<Button>(R.id.button_withdraw_testkudos)
        withdrawTestkudosButton.setOnClickListener {
            model.withdrawTestkudos()
        }

        this.balancesView = view.findViewById(R.id.list_balances)
        this.balancesPlaceholderView = view.findViewById(R.id.list_balances_placeholder)


        val balances = model.balances.value!!

        balancesAdapter = WalletBalanceAdapter(balances)

        view.findViewById<RecyclerView>(R.id.list_balances).apply {
            val myLayoutManager = LinearLayoutManager(context)
            val myItemDecoration = DividerItemDecoration(context, myLayoutManager.orientation)
            layoutManager = myLayoutManager
            adapter = balancesAdapter
            addItemDecoration(myItemDecoration)
        }

        updateBalances(balances)

        model.balances.observe(this, Observer {
            triggerLoading()
            updateBalances(it)
        })

        model.testWithdrawalInProgress.observe(this, Observer { loading ->
            Log.v("taler-wallet", "observing balance loading ${loading} in show balance")
            withdrawTestkudosButton.isEnabled = !loading
            triggerLoading()
        })

        pendingAdapter = PendingOperationsAdapter(PendingOperations(listOf()))

        this.pendingOperationsLabel = view.findViewById<View>(R.id.pending_operations_label)

        view.findViewById<RecyclerView>(R.id.list_pending).apply {
            val myLayoutManager = LinearLayoutManager(context)
            val myItemDecoration = DividerItemDecoration(context, myLayoutManager.orientation)
            layoutManager = myLayoutManager
            adapter = pendingAdapter
            addItemDecoration(myItemDecoration)
        }

        model.pendingOperations.observe(this, Observer {
            updatePending(it)
        })

        return view
    }
}
