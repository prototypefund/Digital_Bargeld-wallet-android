package net.taler.wallet

import akono.AkonoJni
import akono.ModuleResult
import android.app.Application
import android.content.res.AssetManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import org.json.JSONObject
import java.io.InputStream
import java.lang.Exception
import java.nio.file.Paths

val TAG = "taler-wallet"

class AssetModuleLoader(private val assetManager: AssetManager, private val rootPath: String = "node_modules") :
    AkonoJni.LoadModuleHandler {

    private fun makeResult(localPath: String, stream: InputStream): ModuleResult {
        val moduleString = stream.bufferedReader().use {
            it.readText()
        }
        return ModuleResult("/vmodroot/$localPath", moduleString)
    }

    private fun tryPath(rawAssetPath: String): ModuleResult? {
        val assetPath = Paths.get(rawAssetPath).normalize().toString()
        try {
            val moduleStream = assetManager.open(assetPath)
            return makeResult(assetPath, moduleStream)
        } catch (e: Exception) {
        }
        try {
            val jsPath = "$assetPath.js"
            val moduleStream = assetManager.open(jsPath)
            return makeResult(jsPath, moduleStream)
        } catch (e: Exception) {
            // ignore
        }
        val packageJsonPath = "$assetPath/package.json"
        try {
            val packageStream = assetManager.open(packageJsonPath)
            val packageString = packageStream.bufferedReader().use {
                it.readText()
            }
            val packageJson = JSONObject(packageString)
            val mainFile = try {
                packageJson.getString("main")
            } catch (e: Exception) {
                Log.w(TAG, "package.json does not have a 'main' filed")
                throw e
            }
            Log.i(TAG, "main field is $mainFile")
            try {
                val modPath = Paths.get("$assetPath/$mainFile").normalize().toString()
                return makeResult(modPath, assetManager.open(modPath))
            } catch (e: Exception) {
                // ignore
            }
            try {
                val modPath = Paths.get("$assetPath/$mainFile.js").normalize().toString()
                return makeResult(modPath, assetManager.open(modPath))
            } catch (e: Exception) {
            }
        } catch (e: Exception) {
        }
        try {
            val jsPath = "$assetPath/index.js"
            Log.i(TAG, "trying to open $jsPath")
            val moduleStream = assetManager.open(jsPath)
            return makeResult(jsPath, moduleStream)
        } catch (e: Exception) {
        }
        return null
    }

    override fun loadModule(name: String, paths: Array<String>): ModuleResult? {
        Log.i(TAG, "loading module $name from paths [${paths.fold("", { acc, s -> "$acc,$s" })}]")
        for (path in paths) {
            Log.i(TAG, "trying from path $path")
            val prefix = "/vmodroot"
            if (!path.startsWith(prefix)) {
                continue
            }
            if (path == prefix) {
                Log.i(TAG, "path is prefix")
                val res = tryPath("$rootPath/$name")
                if (res != null)
                    return res
            } else {
                Log.i(TAG, "path is not prefix")
                val res = tryPath(path.drop(prefix.length + 1) + "/$name")
                if (res != null)
                    return res
            }
        }
        return null
    }
}


class AssetDataHandler(private val assetManager: AssetManager) : AkonoJni.GetDataHandler {
    override fun handleGetData(what: String): ByteArray? {
        if (what == "taler-emscripten-lib.wasm") {
            Log.i(TAG, "loading emscripten binary from taler-wallet")
            val stream = assetManager.open("node_modules/taler-wallet/emscripten/taler-emscripten-lib.wasm")
            val bytes: ByteArray = stream.readBytes()
            Log.i(TAG, "size of emscripten binary: ${bytes.size}")
            return bytes
        } else {
            Log.w(TAG, "data '$what' requested by akono not found")
            return null
        }
    }
}

data class Amount(val currency: String, val amount: String) {
    companion object {
        val FRACTIONAL_BASE = 1e8;
        fun fromJson(jsonAmount: JSONObject): Amount {
            val amountCurrency = jsonAmount.getString("currency")
            val amountValue = jsonAmount.getString("value")
            val amountFraction = jsonAmount.getString("fraction")
            val amountIntValue = Integer.parseInt(amountValue)
            val amountIntFraction = Integer.parseInt(amountFraction)
            return Amount(amountCurrency, (amountIntValue + amountIntFraction / FRACTIONAL_BASE).toString())
        }
        fun fromString(strAmount: String): Amount {
            val components = strAmount.split(":")
            return Amount(components[0], components[1])
        }
    }
}


data class WalletBalances(val initialized: Boolean, val byCurrency: List<Amount>)

data class ContractTerms(val summary: String, val amount: Amount)

open class PayStatus {
    class Loading : PayStatus()
    data class Prepared(val contractTerms: ContractTerms, val proposalId: Int, val totalFees: Amount) : PayStatus()
    data class InsufficientBalance(val contractTerms: ContractTerms) : PayStatus()
    data class AlreadyPaid(val contractTerms: ContractTerms) : PayStatus()
    data class Error(val error: String) : PayStatus()
    class Success : PayStatus()
}


class WalletViewModel(val app: Application) : AndroidViewModel(app) {
    private lateinit var myAkono: AkonoJni
    private var initialized = false

    private var withdrawInProgress: Int = 0

    val balances: MutableLiveData<WalletBalances> = MutableLiveData()

    val isBalanceLoading: MutableLiveData<Boolean> = MutableLiveData()

    //val isProposalLoading: MutableLiveData<Boolean> = MutableLiveData()

    val payStatus: MutableLiveData<PayStatus> = MutableLiveData()

    init {
        isBalanceLoading.value = false
        balances.value = WalletBalances(false, listOf())
    }

    fun init() {
        if (initialized) {
            Log.e(TAG, "WalletViewModel already initialized")
            return
        }
        val app = this.getApplication<Application>()
        myAkono = AkonoJni()
        myAkono.setLoadModuleHandler(AssetModuleLoader(app.assets))
        myAkono.setGetDataHandler(AssetDataHandler(app.assets))
        myAkono.setMessageHandler(object : AkonoJni.MessageHandler {
            override fun handleMessage(messageStr: String) {
                Log.v(TAG, "got back message: ${messageStr}")
                val message = JSONObject(messageStr)
                val type = message.getString("type")
                when (type) {
                    "notification" -> {
                        getBalances()
                    }
                    "response" -> {
                        val operation = message.getString("operation")
                        Log.v(TAG, "got response for operation $operation")
                        when (operation) {
                            "withdrawTestkudos" -> {
                                withdrawInProgress--
                                if (withdrawInProgress == 0) {
                                    isBalanceLoading.postValue(false)
                                }
                            }
                            "getBalances" -> {
                                val balanceList = mutableListOf<Amount>();
                                val result = message.getJSONObject("result")
                                val byCurrency = result.getJSONObject("byCurrency")
                                val currencyList = byCurrency.keys().asSequence().toList().sorted()
                                for (currency in currencyList) {
                                    val jsonAmount = byCurrency.getJSONObject(currency).getJSONObject("available")
                                    val amount = Amount.fromJson(jsonAmount)
                                    balanceList.add(amount)
                                }
                                balances.postValue(WalletBalances(true, balanceList))
                            }
                            "preparePay" -> {
                                Log.v(TAG, "got preparePay result")
                                val result = message.getJSONObject("result")
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
                                    "payment-possible" -> PayStatus.Prepared(contractTerms!!, proposalId!!, totalFees!!)
                                    "paid" -> PayStatus.AlreadyPaid(contractTerms!!)
                                    "insufficient-balance" -> PayStatus.InsufficientBalance(contractTerms!!)
                                    "error" -> PayStatus.Error("got some error")
                                    else -> PayStatus.Error("unkown status")
                                }
                                payStatus.postValue(res)
                            }
                            "confirmPay" -> {
                                payStatus.postValue(PayStatus.Success())
                            }
                        }

                    }
                }
            }
        })

        myAkono.evalNodeCode("console.log('hello world from taler wallet-android')")
        myAkono.evalNodeCode("tw = require('taler-wallet');")
        myAkono.evalNodeCode("tw.installAndroidWalletListener();")

        sendInitMessage()

        this.initialized = true
    }

    private fun sendInitMessage() {
        val msg = JSONObject()
        msg.put("operation", "init")
        val args = JSONObject()
        msg.put("args", args)
        args.put("persistentStoragePath", "${app.filesDir}/talerwalletdb.json")

        Log.v(TAG, "sending message ${msg}")

        myAkono.sendMessage(msg.toString())
    }

    fun getBalances() {
        if (!initialized) {
            Log.e(TAG, "WalletViewModel not initialized")
            return
        }

        val msg = JSONObject()
        msg.put("operation", "getBalances")

        myAkono.sendMessage(msg.toString())
    }

    fun withdrawTestkudos() {
        if (!initialized) {
            Log.e(TAG, "WalletViewModel not initialized")
            return
        }

        withdrawInProgress++
        this.isBalanceLoading.value = true

        val msg = JSONObject()
        msg.put("operation", "withdrawTestkudos")

        myAkono.sendMessage(msg.toString())
    }

    fun preparePay(url: String) {
        val msg = JSONObject()
        msg.put("operation", "preparePay")

        val args = JSONObject()
        msg.put("args", args)
        args.put("url", url)

        myAkono.sendMessage(msg.toString())
    }

    fun confirmPay(proposalId: Int) {
        val msg = JSONObject()
        msg.put("operation", "confirmPay")

        val args = JSONObject()
        msg.put("args", args)
        args.put("proposalId", proposalId)

        myAkono.sendMessage(msg.toString())
    }

    fun dangerouslyReset() {
        val msg = JSONObject()
        msg.put("operation", "reset")

        myAkono.sendMessage(msg.toString())

        sendInitMessage()

        isBalanceLoading.value = false
        balances.value = WalletBalances(false, listOf())

        getBalances()
    }
}