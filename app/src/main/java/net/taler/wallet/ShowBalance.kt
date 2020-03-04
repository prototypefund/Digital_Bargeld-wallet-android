/*
 * This file is part of GNU Taler
 * (C) 2020 Taler Systems S.A.
 *
 * GNU Taler is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3, or (at your option) any later version.
 *
 * GNU Taler is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * GNU Taler; see the file COPYING.  If not, see <http://www.gnu.org/licenses/>
 */

package net.taler.wallet

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentIntegrator.QR_CODE_TYPES

class ShowBalance : Fragment() {

    private val model: WalletViewModel by activityViewModels()
    private val withdrawManager by lazy { model.withdrawManager }

    private lateinit var balancesView: RecyclerView
    private lateinit var balancesPlaceholderView: TextView
    private lateinit var balancesAdapter: BalanceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_show_balance, container, false)
        val payQrButton = view.findViewById<Button>(R.id.button_pay_qr)
        payQrButton.setOnClickListener {
            IntentIntegrator(activity).apply {
                setBeepEnabled(true)
                setOrientationLocked(false)
            }.initiateScan(QR_CODE_TYPES)
        }

        this.balancesView = view.findViewById(R.id.list_balances)
        this.balancesPlaceholderView = view.findViewById(R.id.list_balances_placeholder)

        val balances = model.balances.value!!

        balancesAdapter = BalanceAdapter(balances)

        view.findViewById<RecyclerView>(R.id.list_balances).apply {
            val myLayoutManager = LinearLayoutManager(context)
            val myItemDecoration = DividerItemDecoration(context, myLayoutManager.orientation)
            layoutManager = myLayoutManager
            adapter = balancesAdapter
            addItemDecoration(myItemDecoration)
        }

        updateBalances(balances)

        model.balances.observe(viewLifecycleOwner, Observer {
            triggerLoading()
            updateBalances(it)
        })


        val withdrawTestkudosButton = view.findViewById<Button>(R.id.button_withdraw_testkudos)
        withdrawTestkudosButton.setOnClickListener {
            withdrawManager.withdrawTestkudos()
        }

        withdrawManager.testWithdrawalInProgress.observe(viewLifecycleOwner, Observer { loading ->
            Log.v("taler-wallet", "observing balance loading $loading in show balance")
            withdrawTestkudosButton.isEnabled = !loading
            triggerLoading()
        })

        return view
    }

    override fun onResume() {
        super.onResume()
        triggerLoading()
        Log.v("taler-wallet", "called onResume on ShowBalance")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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
        inflater.inflate(R.menu.balance, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    private fun triggerLoading() {
        val withdrawInProgress = withdrawManager.testWithdrawalInProgress.value == true
        val balances = model.balances.value
        val loading: Boolean = (withdrawInProgress) || (balances == null) || !balances.initialized
        model.showProgressBar.value = loading
    }

    private fun updateBalances(balances: WalletBalances) {
        if (!balances.initialized) {
            balancesPlaceholderView.visibility = GONE
            balancesView.visibility = GONE
        } else if (balances.byCurrency.isEmpty()) {
            balancesPlaceholderView.visibility = VISIBLE
            balancesView.visibility = GONE
        } else {
            balancesPlaceholderView.visibility = GONE
            balancesView.visibility = VISIBLE
        }
        Log.v(TAG, "updating balances $balances")
        balancesAdapter.update(balances)
    }

}

class BalanceAdapter(private var myDataset: WalletBalances) :
    RecyclerView.Adapter<BalanceAdapter.BalanceViewHolder>() {

    init {
        setHasStableIds(false)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BalanceViewHolder {
        val rowView =
            LayoutInflater.from(parent.context).inflate(R.layout.balance_row, parent, false)
        return BalanceViewHolder(rowView)
    }

    override fun getItemCount(): Int {
        return myDataset.byCurrency.size
    }

    override fun onBindViewHolder(holder: BalanceViewHolder, position: Int) {
        val amount = myDataset.byCurrency[position].available
        val amountIncoming = myDataset.byCurrency[position].pendingIncoming
        val currencyView = holder.rowView.findViewById<TextView>(R.id.balance_currency)
        currencyView.text = amount.currency
        val amountView = holder.rowView.findViewById<TextView>(R.id.balance_amount)
        amountView.text = amount.amount

        val amountIncomingRow = holder.rowView.findViewById<View>(R.id.balance_row_pending)

        val amountIncomingView = holder.rowView.findViewById<TextView>(R.id.balance_pending)
        if (amountIncoming.isZero()) {
            amountIncomingRow.visibility = GONE
        } else {
            amountIncomingRow.visibility = VISIBLE
            @SuppressLint("SetTextI18n")
            amountIncomingView.text = "${amountIncoming.amount} ${amountIncoming.currency}"
        }
    }

    fun update(updatedBalances: WalletBalances) {
        this.myDataset = updatedBalances
        this.notifyDataSetChanged()
    }

    class BalanceViewHolder(val rowView: View) : RecyclerView.ViewHolder(rowView)

}
