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

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import net.taler.wallet.backend.WalletBackendApi
import net.taler.wallet.history.History
import net.taler.wallet.history.HistoryEvent
import net.taler.wallet.payment.PaymentManager
import org.json.JSONObject

const val TAG = "taler-wallet"


data class BalanceEntry(val available: Amount, val pendingIncoming: Amount)


data class WalletBalances(val initialized: Boolean, val byCurrency: List<BalanceEntry>)

open class WithdrawStatus {
    class None : WithdrawStatus()
    data class Loading(val talerWithdrawUri: String) : WithdrawStatus()
    data class TermsOfServiceReviewRequired(
        val talerWithdrawUri: String,
        val exchangeBaseUrl: String,
        val tosText: String,
        val tosEtag: String
    ) : WithdrawStatus()

    class Success : WithdrawStatus()
    data class ReceivedDetails(
        val talerWithdrawUri: String,
        val amount: Amount,
        val suggestedExchange: String
    ) : WithdrawStatus()

    data class Withdrawing(val talerWithdrawUri: String) : WithdrawStatus()
}

open class PendingOperationInfo(
    val type: String,
    val detail: JSONObject
)

open class PendingOperations(
    val pending: List<PendingOperationInfo>
)


@Suppress("EXPERIMENTAL_API_USAGE")
class WalletViewModel(val app: Application) : AndroidViewModel(app) {
    private var initialized = false

    val testWithdrawalInProgress = MutableLiveData<Boolean>().apply {
        value = false
    }

    val balances = MutableLiveData<WalletBalances>().apply {
        value = WalletBalances(false, listOf())
    }

    val withdrawStatus = MutableLiveData<WithdrawStatus>().apply {
        value = WithdrawStatus.None()
    }

    val pendingOperations = MutableLiveData<PendingOperations>().apply {
        value = PendingOperations(listOf())
    }

    private val mHistoryProgress = MutableLiveData<Boolean>()
    val historyProgress: LiveData<Boolean> = mHistoryProgress

    val historyShowAll = MutableLiveData<Boolean>()

    val history: LiveData<History> = historyShowAll.switchMap { showAll ->
        loadHistory(showAll)
            .onStart { mHistoryProgress.postValue(true) }
            .onCompletion { mHistoryProgress.postValue(false) }
            .asLiveData(Dispatchers.IO)
    }

    val showProgressBar = MutableLiveData<Boolean>()

    private var activeGetBalance = 0
    private var activeGetPending = 0

    private var currentWithdrawRequestId = 0

    private val walletBackendApi = WalletBackendApi(app)

    private val mapper = ObjectMapper()
        .registerModule(KotlinModule())
        .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)

    val paymentManager = PaymentManager(walletBackendApi, mapper)

    fun init() {
        if (initialized) {
            Log.e(TAG, "WalletViewModel already initialized")
            return
        }

        this.initialized = true

        getBalances()
        getPending()

        walletBackendApi.notificationHandler = {
            Log.i(TAG, "got notification from wallet")
            getBalances()
            getPending()
        }
        walletBackendApi.connectedHandler = {
            activeGetBalance = 0
            activeGetPending = 0
            getBalances()
            getPending()
        }
    }


    fun getBalances() {
        if (activeGetBalance > 0) {
            return
        }
        activeGetBalance++
        walletBackendApi.sendRequest("getBalances", null) { isError, result ->
            activeGetBalance--
            if (isError) {
                return@sendRequest
            }
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

    private fun getPending() {
        if (activeGetPending > 0) {
            return
        }
        activeGetPending++
        walletBackendApi.sendRequest("getPendingOperations", null) { isError, result ->
            activeGetPending--
            if (isError) {
                Log.i(TAG, "got getPending error result")
                return@sendRequest
            }
            Log.i(TAG, "got getPending result")
            val pendingList = mutableListOf<PendingOperationInfo>()
            val pendingJson = result.getJSONArray("pendingOperations")
            for (i in 0 until pendingJson.length()) {
                val p = pendingJson.getJSONObject(i)
                val type = p.getString("type")
                pendingList.add(PendingOperationInfo(type, p))
            }
            Log.i(TAG, "Got ${pendingList.size} pending operations")
            pendingOperations.postValue(PendingOperations((pendingList)))
        }
    }

    private fun loadHistory(showAll: Boolean) = callbackFlow {
        mHistoryProgress.postValue(true)
        walletBackendApi.sendRequest("getHistory", null) { isError, result ->
            if (isError) {
                // TODO show error message in [WalletHistory] fragment
                close()
                return@sendRequest
            }
            val history = History()
            val json = result.getJSONArray("history")
            for (i in 0 until json.length()) {
                val event: HistoryEvent = mapper.readValue(json.getString(i))
                event.json = json.getJSONObject(i)
                history.add(event)
            }
            history.reverse()  // show latest first
            mHistoryProgress.postValue(false)
            offer(if (showAll) history else history.filter { it.showToUser } as History)
            close()
        }
        awaitClose()
    }

    fun withdrawTestkudos() {
        testWithdrawalInProgress.value = true

        walletBackendApi.sendRequest("withdrawTestkudos", null) { _, _ ->
            testWithdrawalInProgress.postValue(false)
        }
    }

    fun dangerouslyReset() {
        walletBackendApi.sendRequest("reset", null)
        testWithdrawalInProgress.value = false
        balances.value = WalletBalances(false, listOf())
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

        this.currentWithdrawRequestId++
        val myWithdrawRequestId = this.currentWithdrawRequestId

        walletBackendApi.sendRequest("getWithdrawDetailsForUri", args) { isError, result ->
            if (isError) {
                Log.e(TAG, "Error getWithdrawDetailsForUri ${result.toString(4)}")
                return@sendRequest
            }
            if (myWithdrawRequestId != this.currentWithdrawRequestId) {
                val mismatch = "$myWithdrawRequestId != ${this.currentWithdrawRequestId}"
                Log.w(TAG, "Got withdraw result for different request id $mismatch")
                return@sendRequest
            }
            Log.v(TAG, "got getWithdrawDetailsForUri result")
            val status = withdrawStatus.value
            if (status !is WithdrawStatus.Loading) {
                Log.v(TAG, "ignoring withdrawal info result, not loading.")
                return@sendRequest
            }
            val wi = result.getJSONObject("bankWithdrawDetails")
            val suggestedExchange = wi.getString("suggestedExchange")
            // We just use the suggested exchange, in the future there will be
            // a selection dialog.
            getWithdrawalInfoWithExchange(talerWithdrawUri, suggestedExchange)
        }
    }

    private fun getWithdrawalInfoWithExchange(talerWithdrawUri: String, selectedExchange: String) {
        val args = JSONObject()
        args.put("talerWithdrawUri", talerWithdrawUri)
        args.put("selectedExchange", selectedExchange)

        this.currentWithdrawRequestId++
        val myWithdrawRequestId = this.currentWithdrawRequestId

        walletBackendApi.sendRequest("getWithdrawDetailsForUri", args) { isError, result ->
            if (isError) {
                Log.e(TAG, "Error getWithdrawDetailsForUri ${result.toString(4)}")
                return@sendRequest
            }
            if (myWithdrawRequestId != this.currentWithdrawRequestId) {
                val mismatch = "$myWithdrawRequestId != ${this.currentWithdrawRequestId}"
                Log.w(TAG, "Got withdraw result for different request id $mismatch")
                return@sendRequest
            }
            Log.v(TAG, "got getWithdrawDetailsForUri result (with exchange details)")
            val status = withdrawStatus.value
            if (status !is WithdrawStatus.Loading) {
                Log.v(TAG, "ignoring withdrawal info result, not loading.")
                return@sendRequest
            }
            val ei = result.getJSONObject("exchangeWithdrawDetails")
            val termsOfServiceAccepted = ei.getBoolean("termsOfServiceAccepted")
            if (!termsOfServiceAccepted) {
                val exchange = ei.getJSONObject("exchangeInfo")
                val tosText = exchange.getString("termsOfServiceText")
                val tosEtag = exchange.optString("termsOfServiceLastEtag", "undefined")
                withdrawStatus.postValue(
                    WithdrawStatus.TermsOfServiceReviewRequired(
                        status.talerWithdrawUri,
                        selectedExchange,
                        tosText,
                        tosEtag
                    )
                )
            } else {
                val wi = result.getJSONObject("bankWithdrawDetails")
                val suggestedExchange = wi.getString("suggestedExchange")
                val amount = Amount.fromJson(wi.getJSONObject("amount"))
                withdrawStatus.postValue(
                    WithdrawStatus.ReceivedDetails(
                        status.talerWithdrawUri,
                        amount,
                        suggestedExchange
                    )
                )
            }
        }
    }

    fun acceptWithdrawal(talerWithdrawUri: String, selectedExchange: String) {
        val args = JSONObject()
        args.put("talerWithdrawUri", talerWithdrawUri)
        args.put("selectedExchange", selectedExchange)

        withdrawStatus.value = WithdrawStatus.Withdrawing(talerWithdrawUri)

        walletBackendApi.sendRequest("acceptWithdrawal", args) { isError, _ ->
            if (isError) {
                Log.v(TAG, "got acceptWithdrawal error result")
                return@sendRequest
            }
            Log.v(TAG, "got acceptWithdrawal result")
            val status = withdrawStatus.value
            if (status !is WithdrawStatus.Withdrawing) {
                Log.v(TAG, "ignoring acceptWithdrawal result, invalid state")
            }
            withdrawStatus.postValue(WithdrawStatus.Success())
        }
    }

    fun retryPendingNow() {
        walletBackendApi.sendRequest("retryPendingNow", null)
    }

    override fun onCleared() {
        walletBackendApi.destroy()
        super.onCleared()
    }

    /**
     * Accept the currently displayed terms of service.
     */
    fun acceptCurrentTermsOfService() {
        when (val s = withdrawStatus.value) {
            is WithdrawStatus.TermsOfServiceReviewRequired -> {
                val args = JSONObject()
                args.put("exchangeBaseUrl", s.exchangeBaseUrl)
                args.put("etag", s.tosEtag)
                walletBackendApi.sendRequest("acceptExchangeTermsOfService", args) { isError, _ ->
                    if (isError) {
                        return@sendRequest
                    }
                    // Try withdrawing again with accepted ToS
                    getWithdrawalInfo(s.talerWithdrawUri)
                }
            }
        }
    }

    fun cancelCurrentWithdraw() {
        currentWithdrawRequestId++
        withdrawStatus.value = WithdrawStatus.None()
    }
}
