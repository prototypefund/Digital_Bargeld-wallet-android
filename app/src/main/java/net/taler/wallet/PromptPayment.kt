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
import android.util.Log
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

/**
 * Show a payment and ask the user to accept/decline.
 */
class PromptPayment : Fragment() {

    lateinit var model: WalletViewModel

    var fragmentView: View? = null

    private fun triggerLoading() {
        val loading = model.payStatus.value == null || (model.payStatus.value is PayStatus.Loading)
        val progressBar = requireActivity().findViewById<MaterialProgressBar>(R.id.progress_bar)
        if (loading) {
            progressBar.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.INVISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = activity?.run {
            ViewModelProviders.of(this)[WalletViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
    }

    override fun onResume() {
        super.onResume()
        Log.v("taler-wallet", "called onResume on PromptPayment")
        triggerLoading()
    }

    private fun fillOrderInfo(view: View, contractTerms: ContractTerms, totalFees: Amount?) {
        val feesAmountView = view.findViewById<TextView>(R.id.order_fees_amount)
        val amountView = view.findViewById<TextView>(R.id.order_amount)
        val summaryView = view.findViewById<TextView>(R.id.order_summary)
        summaryView.text = contractTerms.summary
        val amount = contractTerms.amount
        @SuppressLint("SetTextI18n")
        amountView.text = "${amount.amount} ${amount.currency}"
        val feesBox = view.findViewById<View>(R.id.order_fees_box)
        if (totalFees != null) {
            @SuppressLint("SetTextI18n")
            feesAmountView.text = "${totalFees.amount} ${totalFees.currency}"
            feesBox.visibility = View.VISIBLE
        } else {
            feesBox.visibility = View.INVISIBLE
        }

    }


    private fun showPayStatus(view: View, payStatus: PayStatus) {
        val promptPaymentDetails = view.findViewById<View>(R.id.prompt_payment_details)
        val balanceInsufficientWarning = view.findViewById<View>(R.id.balance_insufficient_warning)
        val confirmPaymentButton = view.findViewById<Button>(R.id.button_confirm_payment)
        when (payStatus) {
            is PayStatus.Prepared -> {
                fillOrderInfo(view, payStatus.contractTerms, payStatus.totalFees)
                promptPaymentDetails.visibility = View.VISIBLE
                balanceInsufficientWarning.visibility = View.GONE
                confirmPaymentButton.isEnabled = true

                confirmPaymentButton.setOnClickListener {
                    model.confirmPay(payStatus.proposalId)
                    confirmPaymentButton.isEnabled = false
                }
            }
            is PayStatus.InsufficientBalance -> {
                fillOrderInfo(view, payStatus.contractTerms, null)
                promptPaymentDetails.visibility = View.VISIBLE
                balanceInsufficientWarning.visibility = View.VISIBLE
                confirmPaymentButton.isEnabled = false
            }
            is PayStatus.Success -> {
                model.payStatus.value = PayStatus.None()
                activity!!.findNavController(R.id.nav_host_fragment).navigate(R.id.action_promptPayment_to_paymentSuccessful)
            }
            is PayStatus.AlreadyPaid -> {
                activity!!.findNavController(R.id.nav_host_fragment).navigate(R.id.action_promptPayment_to_alreadyPaid)
                model.payStatus.value = PayStatus.None()
            }
            is PayStatus.None -> {
                // No payment active.
            }
            is PayStatus.Loading -> {
                // Wait until loaded ...
            }
            else -> {
                val bar = Snackbar.make(view , "Bug: Unexpected result", Snackbar.LENGTH_SHORT)
                bar.show()
            }
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment

        val view = inflater.inflate(R.layout.fragment_prompt_payment, container, false)
        fragmentView = view

        val promptPaymentDetails = view.findViewById<View>(R.id.prompt_payment_details)
        // Set invisible until data is loaded.
        promptPaymentDetails.visibility = View.INVISIBLE

        val abortPaymentButton = view.findViewById<Button>(R.id.button_abort_payment)

        abortPaymentButton.setOnClickListener {
            model.payStatus.value = PayStatus.None()
            requireActivity().findNavController(R.id.nav_host_fragment).navigateUp()
        }

        triggerLoading()

        model.payStatus.observe(viewLifecycleOwner, Observer {
            triggerLoading()
            showPayStatus(view, it)
        })
        return view
    }
}
