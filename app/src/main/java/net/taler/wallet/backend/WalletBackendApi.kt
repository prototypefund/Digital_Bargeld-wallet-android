package net.taler.wallet.backend

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import android.util.SparseArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.*

class WalletBackendApi(private val app: Application) {

    private var walletBackendMessenger: Messenger? = null
    private val queuedMessages = LinkedList<Message>()
    private val handlers = SparseArray<(message: JSONObject) -> Unit>()
    private var nextRequestID = 1
    var notificationHandler: (() -> Unit)? = null

    private val walletBackendConn = object : ServiceConnection {
        override fun onServiceDisconnected(p0: ComponentName?) {
            Log.w(TAG, "wallet backend service disconnected (crash?)")
        }

        override fun onServiceConnected(componentName: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "connected to wallet backend service")
            val bm = Messenger(binder)
            walletBackendMessenger = bm
            pumpQueue(bm)
            val msg = Message.obtain(null, WalletBackendService.MSG_SUBSCRIBE_NOTIFY)
            msg.replyTo = incomingMessenger
            bm.send(msg)
        }
    }

    private class IncomingHandler(strongApi: WalletBackendApi) : Handler() {
        private val weakApi = WeakReference<WalletBackendApi>(strongApi)
        override fun handleMessage(msg: Message) {
            val api = weakApi.get() ?: return
            when (msg.what) {
                WalletBackendService.MSG_REPLY -> {
                    val requestID = msg.data.getInt("requestID", 0)
                    val h = api.handlers.get(requestID)
                    if (h == null) {
                        Log.e(TAG, "request ID not associated with a handler")
                        return
                    }
                    val response = msg.data.getString("response")
                    if (response == null) {
                        Log.e(TAG, "response did not contain response payload")
                        return
                    }
                    val json = JSONObject(response)
                    h(json)
                }
                WalletBackendService.MSG_NOTIFY -> {
                    val nh = api.notificationHandler
                    if (nh != null) {
                        nh()
                    }
                }
            }
        }
    }

    private val incomingMessenger = Messenger(IncomingHandler(this))

    init {
        Intent(app, WalletBackendService::class.java).also { intent ->
            app.bindService(intent, walletBackendConn, Context.BIND_AUTO_CREATE)
        }
    }

    private fun pumpQueue(bm: Messenger) {
        while (true) {
            val msg = queuedMessages.pollFirst() ?: return
            bm.send(msg)
        }
    }


    fun sendRequest(
        operation: String,
        args: JSONObject?,
        onResponse: (message: JSONObject) -> Unit = { }
    ) {
        Log.i(TAG, "sending request for operation $operation")
        val requestID = nextRequestID++
        val msg = Message.obtain(null, WalletBackendService.MSG_COMMAND)
        handlers.put(requestID, onResponse)
        msg.replyTo = incomingMessenger
        val data = msg.data
        data.putString("operation", operation)
        data.putInt("requestID", requestID)
        if (args != null) {
            data.putString("args", args.toString())
        }
        val bm = walletBackendMessenger
        if (bm != null) {
            bm.send(msg)
        } else {
            queuedMessages.add(msg)
        }
    }

    fun destroy() {
        // FIXME: implement this!
    }

    companion object {
        const val TAG = "WalletBackendApi"
    }
}