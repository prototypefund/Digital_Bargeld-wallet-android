package net.taler.wallet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentLinkedQueue

class Utils {
    companion object {
        private val HEX_CHARS = "0123456789ABCDEF"
        fun hexStringToByteArray(data: String) : ByteArray {

            val result = ByteArray(data.length / 2)

            for (i in 0 until data.length step 2) {
                val firstIndex = HEX_CHARS.indexOf(data[i]);
                val secondIndex = HEX_CHARS.indexOf(data[i + 1]);

                val octet = firstIndex.shl(4).or(secondIndex)
                result.set(i.shr(1), octet.toByte())
            }

            return result
        }

        private val HEX_CHARS_ARRAY = "0123456789ABCDEF".toCharArray()
        fun toHex(byteArray: ByteArray) : String {
            val result = StringBuffer()

            byteArray.forEach {
                val octet = it.toInt()
                val firstIndex = (octet and 0xF0).ushr(4)
                val secondIndex = octet and 0x0F
                result.append(HEX_CHARS_ARRAY[firstIndex])
                result.append(HEX_CHARS_ARRAY[secondIndex])
            }

            return result.toString()
        }
    }
}


fun makeApduSuccessResponse(payload: ByteArray): ByteArray {
    val stream = ByteArrayOutputStream()
    stream.write(payload)
    stream.write(0x90)
    stream.write(0x00)
    return stream.toByteArray()
}


fun makeApduFailureResponse(): ByteArray {
    val stream = ByteArrayOutputStream()
    stream.write(0x6F)
    stream.write(0x00)
    return stream.toByteArray()
}


fun readApduBodySize(stream: ByteArrayInputStream): Int {
    val b0 = stream.read()
    if (b0 == -1) {
        return 0;
    }
    if (b0 != 0) {
        return b0
    }
    val b1 = stream.read()
    val b2 = stream.read()

    return (b1 shl 8) and b2
}


class HostCardEmulatorService: HostApduService() {

    val queuedRequests: ConcurrentLinkedDeque<String> = ConcurrentLinkedDeque()

    override fun onCreate() {
        IntentFilter(HTTP_TUNNEL_REQUEST).also { filter ->
            registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(p0: Context?, p1: Intent?) {
                    queuedRequests.addLast(p1!!.getStringExtra("tunnelMessage"))
                }
            }, filter)
        }
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "Deactivated: " + reason)
        Intent().also { intent ->
            intent.action = MERCHANT_NFC_DISCONNECTED
            sendBroadcast(intent)
        }
    }

    override fun processCommandApdu(commandApdu: ByteArray?,
                                    extras: Bundle?): ByteArray {

        //Log.d(TAG, "Processing command APDU")

        if (commandApdu == null) {
            Log.d(TAG, "APDU is null")
            return makeApduFailureResponse()
        }

        val stream = ByteArrayInputStream(commandApdu)

        val command = stream.read()

        if (command != 0) {
            Log.d(TAG, "APDU has invalid command")
            return makeApduFailureResponse()
        }

        val instruction = stream.read()

        val instructionStr = "%02x".format(instruction)

        //Log.v(TAG, "Processing instruction $instructionStr")

        val p1 = stream.read()
        val p2 = stream.read()

        //Log.v(TAG, "instruction paramaters $p1 $p2")

        if (instruction == SELECT_INS) {
            // FIXME: validate body!
            return makeApduSuccessResponse(ByteArray(0))
        }

        if (instruction == GET_INS) {
            val req = queuedRequests.poll()
            return if (req != null) {
                Log.v(TAG,"sending tunnel request")
                makeApduSuccessResponse(req.toByteArray(Charsets.UTF_8))
            } else {
                makeApduSuccessResponse(ByteArray(0))
            }
        }

        if (instruction == PUT_INS) {
            val bodySize = readApduBodySize(stream)


            val talerInstr = stream.read()
            val bodyBytes = stream.readBytes()


            when (talerInstr.toInt()) {
                1 -> {
                    val url = String(bodyBytes, Charsets.UTF_8)

                    Intent().also { intent ->
                        intent.action = TRIGGER_PAYMENT_ACTION
                        intent.putExtra("contractUrl", url)
                        sendBroadcast(intent)
                    }
                }
                2 -> {
                    Log.v(TAG, "got http response: ${bodyBytes.toString(Charsets.UTF_8)}")

                    Intent().also { intent ->
                        intent.action = HTTP_TUNNEL_RESPONSE
                        intent.putExtra("response", bodyBytes.toString(Charsets.UTF_8))
                        sendBroadcast(intent)
                    }
                }
                else -> {
                    Log.v(TAG, "taler instruction $talerInstr unknown")
                }
            }

            return makeApduSuccessResponse(ByteArray(0))
        }

        return makeApduFailureResponse()
    }

    companion object {
        val TAG = "taler-wallet-hce"
        val AID = "A0000002471001"
        val SELECT_INS = 0xA4
        val PUT_INS = 0xDA
        val GET_INS = 0xCA

        val TRIGGER_PAYMENT_ACTION = "net.taler.TRIGGER_PAYMENT_ACTION"

        val MERCHANT_NFC_CONNECTED = "net.taler.MERCHANT_NFC_CONNECTED"
        val MERCHANT_NFC_DISCONNECTED = "net.taler.MERCHANT_NFC_DISCONNECTED"

        val HTTP_TUNNEL_RESPONSE = "net.taler.HTTP_TUNNEL_RESPONSE"
        val HTTP_TUNNEL_REQUEST = "net.taler.HTTP_TUNNEL_REQUEST"
    }
}