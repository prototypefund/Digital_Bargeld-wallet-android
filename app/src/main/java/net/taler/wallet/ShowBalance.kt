package net.taler.wallet


import android.app.Activity
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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.integration.android.IntentIntegrator
import me.zhanghai.android.materialprogressbar.MaterialProgressBar

class MyAdapter(private var myDataset: WalletBalances) : RecyclerView.Adapter<MyAdapter.MyViewHolder>() {

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
        val currencyView = holder.rowView.findViewById<TextView>(R.id.balance_currency)
        currencyView.text = myDataset.byCurrency[position].currency
        val amountView = holder.rowView.findViewById<TextView>(R.id.balance_amount)
        amountView.text = myDataset.byCurrency[position].amount
    }

    fun update(updatedBalances: WalletBalances) {
        this.myDataset = updatedBalances
        this.notifyDataSetChanged()
    }

    class MyViewHolder(val rowView: View) : RecyclerView.ViewHolder(rowView)
}


/**
 * A simple [Fragment] subclass.
 *
 */
class ShowBalance : Fragment() {

    lateinit var balancesView: RecyclerView
    lateinit var balancesPlaceholderView: TextView
    lateinit var model: WalletViewModel
    lateinit var balancesAdapter: MyAdapter

    fun triggerLoading() {
        val loading: Boolean =
            (model.isBalanceLoading.value == true) || (model.balances.value == null) || !model.balances.value!!.initialized

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = activity?.run {
            ViewModelProviders.of(this)[WalletViewModel::class.java]
        } ?: throw Exception("Invalid Activity")


        model.isBalanceLoading.observe(this, Observer { loading ->
            Log.v("taler-wallet", "observing balance loading ${loading} in show balance")
            triggerLoading()
        })
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
        //this.balancesView.adapter = balancesAdapter
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

        val payNfcButton = view.findViewById<Button>(R.id.button_pay_nfc)
        payNfcButton.setOnClickListener {
            val bar: Snackbar = Snackbar.make(view, "Sorry, NFC is not implemented yet!", Snackbar.LENGTH_SHORT)
            bar.show()
        }


        this.balancesView = view.findViewById(R.id.list_balances)
        this.balancesPlaceholderView = view.findViewById(R.id.list_balances_placeholder)

        val myLayoutManager = LinearLayoutManager(context)
        val myItemDecoration = DividerItemDecoration(context, myLayoutManager.orientation)

        val balances = model.balances.value!!

        balancesAdapter = MyAdapter(balances)

        view.findViewById<RecyclerView>(R.id.list_balances).apply {
            layoutManager = myLayoutManager
            adapter = balancesAdapter
            addItemDecoration(myItemDecoration)
        }

        updateBalances(balances)

        model.balances.observe(this, Observer {
            triggerLoading()
            updateBalances(it)
        })

        return view
    }
}
