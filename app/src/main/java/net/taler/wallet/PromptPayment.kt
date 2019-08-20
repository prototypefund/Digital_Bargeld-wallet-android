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


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 *
 */
class PromptPayment : Fragment() {

    lateinit var model: WalletViewModel

    var fragmentView: View? = null

    fun triggerLoading(loading: Boolean) {
        val myActivity = activity!!
        val progressBar = myActivity.findViewById<MaterialProgressBar>(R.id.progress_bar)
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

        triggerLoading(true)
    }

    override fun onResume() {
        super.onResume()
        Log.v("taler-wallet", "called onResume on PromptPayment")
        triggerLoading(model.payStatus.value == null || model.payStatus.value is PayStatus.Loading)
    }

    fun fillOrderInfo(view: View, contractTerms: ContractTerms, totalFees: Amount?) {
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

    fun showPayStatus(view: View, payStatus: PayStatus) {
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
                    triggerLoading(true)
                    confirmPaymentButton.isEnabled = false
                }
                triggerLoading(false)
            }
            is PayStatus.InsufficientBalance -> {
                fillOrderInfo(view, payStatus.contractTerms, null)
                promptPaymentDetails.visibility = View.VISIBLE
                balanceInsufficientWarning.visibility = View.VISIBLE
                confirmPaymentButton.isEnabled = false
                triggerLoading(false)
            }
            is PayStatus.Success -> {
                triggerLoading(false)
                activity!!.findNavController(R.id.nav_host_fragment).navigate(R.id.action_promptPayment_to_paymentSuccessful)
            }
            else -> {
                val bar = Snackbar.make(view , "Unexpected result", Snackbar.LENGTH_SHORT)
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
            activity!!.findNavController(R.id.nav_host_fragment).navigateUp()
        }

        triggerLoading(true)

        model.payStatus.observe(this, Observer {
            showPayStatus(view, it)
        })
        return view
    }
}
