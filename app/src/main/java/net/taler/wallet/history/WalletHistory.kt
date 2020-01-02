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
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_show_history.*
import net.taler.wallet.R
import net.taler.wallet.WalletViewModel

/**
 * Wallet history.
 *
 */
class WalletHistory : Fragment() {

    lateinit var model: WalletViewModel
    private lateinit var showAllItem: MenuItem
    private val historyAdapter = WalletHistoryAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        model = activity?.run {
            ViewModelProvider(this)[WalletViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.history, menu)
        showAllItem = menu.findItem(R.id.show_all_history)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.show_all_history -> {
                item.isChecked = !item.isChecked
                model.historyShowAll.value = item.isChecked
                true
            }
            R.id.reload_history -> {
                model.historyShowAll.value = showAllItem.isChecked
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_show_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        historyList.apply {
            val myLayoutManager = LinearLayoutManager(context)
            val myItemDecoration = DividerItemDecoration(context, myLayoutManager.orientation)
            layoutManager = myLayoutManager
            adapter = historyAdapter
            addItemDecoration(myItemDecoration)
        }

        model.historyProgress.observe(this, Observer { show ->
            historyProgressBar.visibility = if (show) VISIBLE else INVISIBLE
        })
        model.history.observe(this, Observer { history ->
            historyEmptyState.visibility = if (history.isEmpty()) VISIBLE else INVISIBLE
            historyAdapter.update(history)
        })

        // kicks off initial load, needs to be adapted if showAll state is ever saved
        if (savedInstanceState == null) model.historyShowAll.value = false
    }

    companion object {
        const val TAG = "taler-wallet"
    }
}
