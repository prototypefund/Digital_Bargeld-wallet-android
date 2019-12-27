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


import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.taler.wallet.R
import net.taler.wallet.WalletViewModel

/**
 * Wallet history.
 *
 */
class WalletHistory : Fragment() {

    lateinit var model: WalletViewModel
    private val historyAdapter = WalletHistoryAdapter()
    lateinit var historyPlaceholder: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

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
            if (it.isEmpty()) {
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
        val myLayoutManager = LinearLayoutManager(context).apply {
            reverseLayout = true  // show latest events first
        }
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
