package net.taler.wallet


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController

/**
 * A simple [Fragment] subclass.
 */
class ReviewExchangeTOS : Fragment() {

    private lateinit var acceptButton: Button
    private lateinit var model: WalletViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = activity?.run {
            ViewModelProviders.of(this)[WalletViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
    }

    private fun onAcceptCheck(checked: Boolean) {
        acceptButton.isEnabled = checked
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_review_exchange_tos, container, false)
        val navController = requireActivity().findNavController(R.id.nav_host_fragment)
        view.findViewById<Button>(R.id.button_tos_abort).setOnClickListener {
            model.cancelCurrentWithdraw()
            navController.navigateUp()
        }
        acceptButton = view.findViewById<Button>(R.id.button_tos_accept)
        acceptButton.setOnClickListener {
            model.acceptCurrentTermsOfService()
        }
        val checkbox = view.findViewById<CheckBox>(R.id.checkBox_accept_tos)
        checkbox.isChecked = false
        checkbox.setOnCheckedChangeListener { buttonView, isChecked ->
            onAcceptCheck(isChecked)
        }
        onAcceptCheck(false)
        val tosTextField = view.findViewById<TextView>(R.id.text_tos)
        model.withdrawStatus.observe(this, Observer {
            when (it) {
                is WithdrawStatus.TermsOfServiceReviewRequired -> {
                    tosTextField.text = it.tosText
                }
                is WithdrawStatus.Loading -> {
                    navController.navigate(R.id.action_reviewExchangeTOS_to_promptWithdraw)
                }
                is WithdrawStatus.ReceivedDetails -> {
                    navController.navigate(R.id.action_reviewExchangeTOS_to_promptWithdraw)
                }
                else -> {
                }
            }
        })
        return view
    }
}
