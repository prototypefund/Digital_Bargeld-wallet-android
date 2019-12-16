/*
 This file is part of GNU Taler
 (C) 2019 Taler Systems S.A.

 GNU Taler is free software; you can redistribute it and/or modify it under the
 terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3, or (at your option) any later version.

 GNU Taler is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 A PARTICULAR PURPOSE.  See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 GNU Taler; see the file COPYING.  If not, see <http://www.gnu.org/licenses/>
 */


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
    var connectedHandler: (() -> Unit)? = null

    private val walletBackendConn = object : ServiceConnection {
        override fun onServiceDisconnected(p0: ComponentName?) {
            Log.w(TAG, "wallet backend service disconnected (crash?)")
            walletBackendMessenger = null
        }

        override fun onServiceConnected(componentName: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "connected to wallet backend service")
            val bm = Messenger(binder)
            walletBackendMessenger = bm
            pumpQueue(bm)
            val msg = Message.obtain(null, WalletBackendService.MSG_SUBSCRIBE_NOTIFY)
            msg.replyTo = incomingMessenger
            bm.send(msg)
            val ch = connectedHandler
            if (ch != null) {
                ch()
            }
        }
    }

    private class IncomingHandler(strongApi: WalletBackendApi) : Handler() {
        private val weakApi = WeakReference<WalletBackendApi>(strongApi)
        override fun handleMessage(msg: Message) {
            val api = weakApi.get() ?: return
            when (msg.what) {
                WalletBackendService.MSG_REPLY -> {
                    val requestID = msg.data.getInt("requestID", 0)
                    val operation = msg.data.getString("operation", "??")
                    Log.i(TAG, "got reply for operation $operation ($requestID)")
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
        val requestID = nextRequestID++
        Log.i(TAG, "sending request for operation $operation ($requestID)")
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