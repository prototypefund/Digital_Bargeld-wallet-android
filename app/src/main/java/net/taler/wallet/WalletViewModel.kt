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
import androidx.annotation.UiThread
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
import net.taler.wallet.pending.PendingOperationsManager
import net.taler.wallet.withdraw.WithdrawManager
import org.json.JSONObject

const val TAG = "taler-wallet"


data class BalanceEntry(val available: Amount, val pendingIncoming: Amount)


data class WalletBalances(val initialized: Boolean, val byCurrency: List<BalanceEntry>)


@Suppress("EXPERIMENTAL_API_USAGE")
class WalletViewModel(val app: Application) : AndroidViewModel(app) {

    val balances = MutableLiveData<WalletBalances>().apply {
        value = WalletBalances(false, listOf())
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

    private val walletBackendApi = WalletBackendApi(app, {
        activeGetBalance = 0
        getBalances()
        pendingOperationsManager.getPending()
    }) {
        Log.i(TAG, "Received notification from wallet-core")
        getBalances()
        pendingOperationsManager.getPending()
    }

    private val mapper = ObjectMapper()
        .registerModule(KotlinModule())
        .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)

    val withdrawManager = WithdrawManager(walletBackendApi)
    val paymentManager = PaymentManager(walletBackendApi, mapper)
    val pendingOperationsManager: PendingOperationsManager =
        PendingOperationsManager(walletBackendApi)

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

    @UiThread
    fun dangerouslyReset() {
        walletBackendApi.sendRequest("reset", null)
        withdrawManager.testWithdrawalInProgress.value = false
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

    fun retryPendingNow() {
        walletBackendApi.sendRequest("retryPendingNow", null)
    }

    override fun onCleared() {
        walletBackendApi.destroy()
        super.onCleared()
    }
}
