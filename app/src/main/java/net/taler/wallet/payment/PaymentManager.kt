package net.taler.wallet.payment

import android.util.Log
import androidx.annotation.UiThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.taler.wallet.Amount
import net.taler.wallet.TAG
import net.taler.wallet.backend.WalletBackendApi
import org.json.JSONObject

class PaymentManager(
    private val walletBackendApi: WalletBackendApi,
    private val mapper: ObjectMapper
) {

    private val mPayStatus = MutableLiveData<PayStatus>(PayStatus.None)
    internal val payStatus: LiveData<PayStatus> = mPayStatus

    private val mDetailsShown = MutableLiveData<Boolean>()
    internal val detailsShown: LiveData<Boolean> = mDetailsShown

    private var currentPayRequestId = 0

    @UiThread
    fun preparePay(url: String) {
        mPayStatus.value = PayStatus.Loading
        mDetailsShown.value = false

        val args = JSONObject(mapOf("url" to url))

        currentPayRequestId += 1
        val payRequestId = currentPayRequestId

        walletBackendApi.sendRequest("preparePay", args) { isError, result ->
            when {
                isError -> {
                    Log.v(TAG, "got preparePay error result")
                    mPayStatus.value = PayStatus.Error(result.toString())
                }
                payRequestId != this.currentPayRequestId -> {
                    Log.v(TAG, "preparePay result was for old request")
                }
                else -> {
                    val status = result.getString("status")
                    mPayStatus.postValue(getPayStatusUpdate(status, result))
                }
            }
        }
    }

    private fun getPayStatusUpdate(status: String, json: JSONObject) = when (status) {
        "payment-possible" -> PayStatus.Prepared(
            contractTerms = getContractTerms(json),
            proposalId = json.getString("proposalId"),
            totalFees = Amount.fromJson(json.getJSONObject("totalFees"))
        )
        "paid" -> PayStatus.AlreadyPaid(getContractTerms(json))
        "insufficient-balance" -> PayStatus.InsufficientBalance(getContractTerms(json))
        "error" -> PayStatus.Error("got some error")
        else -> PayStatus.Error("unknown status")
    }

    private fun getContractTerms(json: JSONObject): ContractTerms {
        return mapper.readValue(json.getString("contractTermsRaw"))
    }

    @UiThread
    fun toggleDetailsShown() {
        val oldValue = mDetailsShown.value ?: false
        mDetailsShown.value = !oldValue
    }

    fun confirmPay(proposalId: String) {
        val args = JSONObject(mapOf("proposalId" to proposalId))

        walletBackendApi.sendRequest("confirmPay", args) { _, _ ->
            mPayStatus.postValue(PayStatus.Success)
        }
    }

    fun abortProposal(proposalId: String) {
        val args = JSONObject(mapOf("proposalId" to proposalId))

        Log.i(TAG, "aborting proposal")

        walletBackendApi.sendRequest("abortProposal", args) { isError, _ ->
            if (isError) {
                Log.e(TAG, "received error response to abortProposal")
                return@sendRequest
            }
            mPayStatus.postValue(PayStatus.None)
        }
    }

    @UiThread
    fun resetPayStatus() {
        mPayStatus.value = PayStatus.None
    }

}

sealed class PayStatus {
    object None : PayStatus()
    object Loading : PayStatus()
    data class Prepared(
        val contractTerms: ContractTerms,
        val proposalId: String,
        val totalFees: Amount
    ) : PayStatus()

    data class InsufficientBalance(val contractTerms: ContractTerms) : PayStatus()
    data class AlreadyPaid(val contractTerms: ContractTerms) : PayStatus()
    data class Error(val error: String) : PayStatus()
    object Success : PayStatus()
}
