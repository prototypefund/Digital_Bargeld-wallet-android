package net.taler.wallet.backend

import akono.AkonoJni
import akono.ModuleResult
import android.app.Service
import android.content.Intent
import android.content.res.AssetManager
import android.os.*
import android.util.Log
import android.util.SparseArray
import android.widget.Toast
import androidx.core.util.set
import net.taler.wallet.HostCardEmulatorService
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.lang.Process
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.exitProcess

private const val TAG = "taler-wallet-backend"

/**
 * Module loader to handle module loading requests from the wallet-core running on node/v8.
 */
private class AssetModuleLoader(
    private val assetManager: AssetManager,
    private val rootPath: String = "node_modules"
) : AkonoJni.LoadModuleHandler {

    private fun makeResult(localPath: String, stream: InputStream): ModuleResult {
        val moduleString = stream.bufferedReader().use {
            it.readText()
        }
        return ModuleResult("/vmodroot/$localPath", moduleString)
    }

    private fun tryPath(rawAssetPath: String): ModuleResult? {
        //val assetPath = Paths.get(rawAssetPath).normalize().toString()
        val assetPath = File(rawAssetPath).normalize().path
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
            try {
                //val modPath = Paths.get("$assetPath/$mainFile").normalize().toString()
                val modPath = File("$assetPath/$mainFile").normalize().path
                return makeResult(modPath, assetManager.open(modPath))
            } catch (e: Exception) {
                // ignore
            }
            try {
                //val modPath = Paths.get("$assetPath/$mainFile.js").normalize().toString()
                val modPath = File("$assetPath/$mainFile.js").normalize().path
                return makeResult(modPath, assetManager.open(modPath))
            } catch (e: Exception) {
            }
        } catch (e: Exception) {
        }
        try {
            val jsPath = "$assetPath/index.js"
            val moduleStream = assetManager.open(jsPath)
            return makeResult(jsPath, moduleStream)
        } catch (e: Exception) {
        }
        return null
    }

    override fun loadModule(name: String, paths: Array<String>): ModuleResult? {
        for (path in paths) {
            val prefix = "/vmodroot"
            if (!path.startsWith(prefix)) {
                continue
            }
            if (path == prefix) {
                val res = tryPath("$rootPath/$name")
                if (res != null)
                    return res
            } else {
                val res = tryPath(path.drop(prefix.length + 1) + "/$name")
                if (res != null)
                    return res
            }
        }
        return null
    }
}


private class AssetDataHandler(private val assetManager: AssetManager) : AkonoJni.GetDataHandler {
    override fun handleGetData(what: String): ByteArray? {
        return null
    }
}

class RequestData(val clientRequestID: Int, val messenger: Messenger)


class WalletBackendService : Service() {
    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    private val messenger: Messenger = Messenger(IncomingHandler(this))

    private lateinit var akono: AkonoJni

    private var initialized = false

    private var nextRequestID = 1

    private val requests = ConcurrentHashMap<Int, RequestData>()

    private val subscribers = LinkedList<Messenger>()

    override fun onCreate() {
        Log.i(TAG, "onCreate in wallet backend service")
        akono = AkonoJni()
        akono.setLoadModuleHandler(AssetModuleLoader(application.assets))
        akono.setGetDataHandler(AssetDataHandler(application.assets))
        akono.setMessageHandler(object : AkonoJni.MessageHandler {
            override fun handleMessage(message: String) {
                this@WalletBackendService.handleAkonoMessage(message)
            }
        })
        akono.evalNodeCode("console.log('hello world from taler wallet-android')")
        akono.evalNodeCode("require('source-map-support').install();")
        akono.evalNodeCode("tw = require('taler-wallet');")
        akono.evalNodeCode("tw.installAndroidWalletListener();")
        sendInitMessage()
        initialized = true
        super.onCreate()
    }

    private fun sendInitMessage() {
        val msg = JSONObject()
        msg.put("operation", "init")
        val args = JSONObject()
        msg.put("args", args)
        args.put("persistentStoragePath", "${application.filesDir}/talerwalletdb.json")
        akono.sendMessage(msg.toString())
    }

    /**
     * Handler of incoming messages from clients.
     */
    class IncomingHandler(
        service: WalletBackendService
    ) : Handler() {

        private val serviceWeakRef = WeakReference(service)

        override fun handleMessage(msg: Message) {
            val svc = serviceWeakRef.get() ?: return
            when (msg.what) {
                MSG_COMMAND -> {
                    val data = msg.getData()
                    val serviceRequestID = svc.nextRequestID++
                    val clientRequestID = data.getInt("requestID", 0)
                    if (clientRequestID == 0) {
                        Log.e(TAG, "client requestID missing")
                        return
                    }
                    val args = data.getString("args")
                    val argsObj = if (args == null) {
                        JSONObject()
                    } else {
                        JSONObject(args)
                    }
                    val operation = data.getString("operation", "")
                    if (operation == "") {
                        Log.e(TAG, "client command missing")
                        return
                    }
                    Log.i(TAG, "got request for operation $operation")
                    val request = JSONObject()
                    request.put("operation", operation)
                    request.put("id", serviceRequestID)
                    request.put("args", argsObj)
                    svc.akono.sendMessage(request.toString(2))
                    Log.i(TAG, "mapping service request ID $serviceRequestID to client request ID $clientRequestID")
                    svc.requests.put(
                        serviceRequestID,
                        RequestData(clientRequestID, msg.replyTo)
                    )
                }
                MSG_SUBSCRIBE_NOTIFY -> {
                    Log.i(TAG, "subscribing client")
                    val r = msg.replyTo
                    if (r == null) {
                        Log.e(
                            TAG,
                            "subscriber did not specify replyTo object in MSG_SUBSCRIBE_NOTIFY"
                        )
                    } else {
                        svc.subscribers.add(msg.replyTo)
                    }
                }
                MSG_UNSUBSCRIBE_NOTIFY -> {
                    Log.i(TAG, "unsubscribing client")
                    svc.subscribers.remove(msg.replyTo)
                }
                else -> {
                    Log.e(TAG, "unknown message from client")
                    super.handleMessage(msg)
                }
            }
        }
    }

    override fun onBind(p0: Intent?): IBinder? {
        return messenger.binder
    }

    private fun sendNotify() {
        var rm: LinkedList<Messenger>? = null
        for (s in subscribers) {
            val m = Message.obtain(null, MSG_NOTIFY)
            try {
                s.send(m)
            } catch (e: RemoteException) {
                if (rm == null) {
                    rm = LinkedList<Messenger>()
                }
                rm.add(s)
                subscribers.remove(s)
            }
        }
        if (rm != null) {
            for (s in rm) {
                subscribers.remove(s)
            }
        }
    }

    private fun handleAkonoMessage(messageStr: String) {
        Log.v(TAG, "got back message: ${messageStr}")
        val message = JSONObject(messageStr)
        val type = message.getString("type")
        when (type) {
            "notification" -> {
                sendNotify()
            }
            "tunnelHttp" -> {
                Log.v(TAG, "got http tunnel request!")
                Intent().also { intent ->
                    intent.action = HostCardEmulatorService.HTTP_TUNNEL_REQUEST
                    intent.putExtra("tunnelMessage", messageStr)
                    application.sendBroadcast(intent)
                }
            }
            "response" -> {
                val operation = message.getString("operation")
                when (operation) {
                    "init" -> {
                        Log.v(TAG, "got response for init operation")
                        sendNotify()
                    }
                    "reset" -> {
                        exitProcess(1)
                    }
                    else -> {
                        val id = message.getInt("id")
                        Log.v(TAG, "got response for operation $operation")
                        val rd = requests.get(id)
                        if (rd == null) {
                            Log.e(TAG, "wallet returned unknown request ID ($id)")
                            return
                        }
                        val m = Message.obtain(null, MSG_REPLY)
                        val b = m.data
                        if (message.has("result")) {
                            val respJson = message.getJSONObject("result")
                            b.putString("response", respJson.toString(2))
                        } else {
                            b.putString("response", "{}")
                        }
                        b.putInt("requestID", rd.clientRequestID)
                        b.putString("operation", operation)
                        rd.messenger.send(m)
                    }
                }
            }
        }
    }

    companion object {
        const val MSG_SUBSCRIBE_NOTIFY = 1
        const val MSG_UNSUBSCRIBE_NOTIFY = 2
        const val MSG_COMMAND = 3
        const val MSG_REPLY = 4
        const val MSG_NOTIFY = 5
    }
}
