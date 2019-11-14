package net.taler.wallet

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import net.taler.wallet.backend.WalletBackendApi
import org.json.JSONObject

val TAG = "taler-wallet"


data class Amount(val currency: String, val amount: String) {
    fun isZero(): Boolean {
        return amount.toDouble() == 0.0
    }

    companion object {
        const val FRACTIONAL_BASE = 1e8
        fun fromJson(jsonAmount: JSONObject): Amount {
            val amountCurrency = jsonAmount.getString("currency")
            val amountValue = jsonAmount.getString("value")
            val amountFraction = jsonAmount.getString("fraction")
            val amountIntValue = Integer.parseInt(amountValue)
            val amountIntFraction = Integer.parseInt(amountFraction)
            return Amount(
                amountCurrency,
                (amountIntValue + amountIntFraction / FRACTIONAL_BASE).toString()
            )
        }

        fun fromString(strAmount: String): Amount {
            val components = strAmount.split(":")
            return Amount(components[0], components[1])
        }
    }
}

data class BalanceEntry(val available: Amount, val pendingIncoming: Amount)


data class WalletBalances(val initialized: Boolean, val byCurrency: List<BalanceEntry>)

data class ContractTerms(val summary: String, val amount: Amount)

open class PayStatus {
    class None : PayStatus()
    class Loading : PayStatus()
    data class Prepared(
        val contractTerms: ContractTerms,
        val proposalId: Int,
        val totalFees: Amount
    ) : PayStatus()

    data class InsufficientBalance(val contractTerms: ContractTerms) : PayStatus()
    data class AlreadyPaid(val contractTerms: ContractTerms) : PayStatus()
    data class Error(val error: String) : PayStatus()
    class Success : PayStatus()
}

open class WithdrawStatus {
    class None : WithdrawStatus()
    data class Loading(val talerWithdrawUri: String) : WithdrawStatus()
    class Success : WithdrawStatus()
    data class ReceivedDetails(
        val talerWithdrawUri: String,
        val amount: Amount,
        val suggestedExchange: String
    ) : WithdrawStatus()

    data class Withdrawing(val talerWithdrawUri: String) : WithdrawStatus()
}


class WalletViewModel(val app: Application) : AndroidViewModel(app) {
    private var initialized = false

    val testWithdrawalInProgress = MutableLiveData<Boolean>().apply {
        value = false
    }

    val balances = MutableLiveData<WalletBalances>().apply {
        value = WalletBalances(false, listOf())
    }

    val payStatus = MutableLiveData<PayStatus>().apply {
        value = PayStatus.None()
    }

    val withdrawStatus = MutableLiveData<WithdrawStatus>().apply {
        value = WithdrawStatus.None()
    }

    private val walletBackendApi = WalletBackendApi(app)

    fun init() {
        if (initialized) {
            Log.e(TAG, "WalletViewModel already initialized")
            return
        }

        this.initialized = true

        walletBackendApi.notificationHandler = {
            Log.i(TAG, "got notification from wallet")
            getBalances()
        }
    }


    fun getBalances() {
        walletBackendApi.sendRequest("getBalances", null) { result ->
            val balanceList = mutableListOf<BalanceEntry>()
            val byCurrency = result.getJSONObject("byCurrency")
            val currencyList = byCurrency.keys().asSequence().toList().sorted()
            for (currency in currencyList) {
                val jsonAmount = byCurrency.getJSONObject(currency)
                    .getJSONObject("available")
                val amount = Amount.fromJson(jsonAmount)
                val jsonAmountIncoming = byCurrency.getJSONObject(currency)
                    .getJSONObject("pendingIncoming")
                val amountIncoming = Amount.fromJson(jsonAmountIncoming)
                balanceList.add(BalanceEntry(amount, amountIncoming))
            }
            balances.postValue(WalletBalances(true, balanceList))
        }
    }

    fun withdrawTestkudos() {
        testWithdrawalInProgress.value = true

        walletBackendApi.sendRequest("withdrawTestkudos", null) {
            testWithdrawalInProgress.postValue(false)
        }
    }

    fun preparePay(url: String) {
        val args = JSONObject()
        args.put("url", url)

        this.payStatus.value = PayStatus.Loading()

        walletBackendApi.sendRequest("preparePay", args) { result ->
            Log.v(TAG, "got preparePay result")
            val status = result.getString("status")
            var contractTerms: ContractTerms? = null
            var proposalId: Int? = null
            var totalFees: Amount? = null
            if (result.has("proposalId")) {
                proposalId = result.getInt("proposalId")
            }
            if (result.has("contractTerms")) {
                val ctJson = result.getJSONObject("contractTerms")
                val amount = Amount.fromString(ctJson.getString("amount"))
                val summary = ctJson.getString("summary")
                contractTerms = ContractTerms(summary, amount)
            }
            if (result.has("totalFees")) {
                totalFees = Amount.fromJson(result.getJSONObject("totalFees"))
            }
            val res = when (status) {
                "payment-possible" -> PayStatus.Prepared(
                    contractTerms!!,
                    proposalId!!,
                    totalFees!!
                )
                "paid" -> PayStatus.AlreadyPaid(contractTerms!!)
                "insufficient-balance" -> PayStatus.InsufficientBalance(
                    contractTerms!!
                )
                "error" -> PayStatus.Error("got some error")
                else -> PayStatus.Error("unkown status")
            }
            payStatus.postValue(res)
        }
    }

    fun confirmPay(proposalId: Int) {
        val msg = JSONObject()
        msg.put("operation", "confirmPay")

        val args = JSONObject()
        args.put("proposalId", proposalId)

        walletBackendApi.sendRequest("confirmPay", args) {
            payStatus.postValue(PayStatus.Success())
        }
    }

    fun dangerouslyReset() {
        walletBackendApi.sendRequest("reset", null)
        testWithdrawalInProgress.value = false
        balances.value = WalletBalances(false, listOf())
        getBalances()
    }

    fun startTunnel() {
        walletBackendApi.sendRequest("startTunnel", null)
    }

    fun stopTunnel() {
        walletBackendApi.sendRequest("stopTunnel", null)
    }

    fun tunnelResponse(resp: String) {
        val respJson = JSONObject(resp)
        walletBackendApi.sendRequest("tunnelResponse", respJson)
    }

    fun getWithdrawalInfo(talerWithdrawUri: String) {
        val args = JSONObject()
        args.put("talerWithdrawUri", talerWithdrawUri)

        withdrawStatus.value = WithdrawStatus.Loading(talerWithdrawUri)

        walletBackendApi.sendRequest("getWithdrawalInfo", args) { result ->
            Log.v(TAG, "got getWithdrawalInfo result")
            val status = withdrawStatus.value
            if (status !is WithdrawStatus.Loading) {
                Log.v(TAG, "ignoring withdrawal info result, not loading.")
                return@sendRequest
            }
            val suggestedExchange = result.getString("suggestedExchange")
            val amount = Amount.fromJson(result.getJSONObject("amount"))
            withdrawStatus.postValue(
                WithdrawStatus.ReceivedDetails(
                    status.talerWithdrawUri,
                    amount,
                    suggestedExchange
                )
            )
        }
    }

    fun acceptWithdrawal(talerWithdrawUri: String, selectedExchange: String) {
        val args = JSONObject()
        args.put("talerWithdrawUri", talerWithdrawUri)
        args.put("selectedExchange", selectedExchange)

        withdrawStatus.value = WithdrawStatus.Withdrawing(talerWithdrawUri)

        walletBackendApi.sendRequest("acceptWithdrawal", args) {
            Log.v(TAG, "got acceptWithdrawal result")
            val status = withdrawStatus.value
            if (status !is WithdrawStatus.Withdrawing) {
                Log.v(TAG, "ignoring acceptWithdrawal result, invalid state")
            }
            withdrawStatus.postValue(WithdrawStatus.Success())
        }
    }

    override fun onCleared() {
        walletBackendApi.destroy()
        super.onCleared()
    }
}
