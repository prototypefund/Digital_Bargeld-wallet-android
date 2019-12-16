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

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import com.google.android.material.snackbar.Snackbar
import me.zhanghai.android.materialprogressbar.MaterialProgressBar


class PromptWithdraw : Fragment() {

    private lateinit var model: WalletViewModel

    private fun triggerLoading() {
        val loading =
            model.withdrawStatus.value is WithdrawStatus.Loading || model.withdrawStatus.value is WithdrawStatus.Withdrawing
        val myActivity = activity!!
        val progressBar = myActivity.findViewById<MaterialProgressBar>(R.id.progress_bar)
        if (loading) {
            progressBar.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.INVISIBLE
        }
    }

    private fun showWithdrawStatus(view: View, status: WithdrawStatus) {
        val confirmButton = view.findViewById<Button>(R.id.button_confirm_withdraw)
        val promptWithdraw = view.findViewById<View>(R.id.prompt_withdraw)
        when (status) {
            is WithdrawStatus.ReceivedDetails -> {
                promptWithdraw.visibility = View.VISIBLE
                confirmButton.isEnabled = true
                val promptWithdraw = view.findViewById<View>(R.id.prompt_withdraw)
                promptWithdraw.visibility = View.VISIBLE
                val amountView = view.findViewById<TextView>(R.id.withdraw_amount)
                val exchangeView = view.findViewById<TextView>(R.id.withdraw_exchange)
                exchangeView.text = status.suggestedExchange
                @SuppressLint("SetTextI18n")
                amountView.text = "${status.amount.amount} ${status.amount.currency}"
            }
            is WithdrawStatus.Success -> {
                this.model.withdrawStatus.value = WithdrawStatus.None()
                activity!!.findNavController(R.id.nav_host_fragment)
                    .navigate(R.id.action_promptWithdraw_to_withdrawSuccessful)
            }
            is WithdrawStatus.Loading -> {
                promptWithdraw.visibility = View.INVISIBLE
                // Wait
            }
            is WithdrawStatus.Withdrawing -> {
                confirmButton.isEnabled = false

            }
            is WithdrawStatus.None -> {

            }
            is WithdrawStatus.TermsOfServiceReviewRequired -> {
                val navController = requireActivity().findNavController(R.id.nav_host_fragment)
                navController.navigate(R.id.action_promptWithdraw_to_reviewExchangeTOS)
            }
            else -> {
                val bar = Snackbar.make(view, "Bug: Unexpected result", Snackbar.LENGTH_SHORT)
                bar.show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = activity?.run {
            ViewModelProviders.of(this)[WalletViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_prompt_withdraw, container, false)

        this.model.withdrawStatus.observe(this, Observer {
            triggerLoading()
            showWithdrawStatus(view, it)
        })

        view.findViewById<Button>(R.id.button_cancel_withdraw).setOnClickListener {
            val navController = requireActivity().findNavController(R.id.nav_host_fragment)
            model.cancelCurrentWithdraw()
            navController.navigateUp()
        }

        view.findViewById<Button>(R.id.button_confirm_withdraw).setOnClickListener {
            val status = this.model.withdrawStatus.value
            if (status !is WithdrawStatus.ReceivedDetails) {
                return@setOnClickListener
            }
            model.acceptWithdrawal(status.talerWithdrawUri, status.suggestedExchange)
        }

        return view
    }
}
