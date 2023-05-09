package com.example.starlink.services

import android.app.IntentService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class TCPSendService : IntentService("TCPSendService") {

    companion object {
        //intent
        const val URL: String = "tcp_send_service_url"
        const val ADDRESS: String = "tcp_send_service_address"
        const val PORT: String = "tcp_send_service_port"
        const val LENGTH_PROGRESS: String = "tcp_send_service_length_progress"
        const val NOW_PROGRESS: String = "tcp_send_service_now_progress"

        //action
        const val PROGRESS_UPDATE: String = "tcp_send_service_progress_update"
        const val SEND_FINISH: String = "tcp_send_service_send_finish"
        const val SEND_SHUTDOWN: String = "tcp_send_service_send_shutdown"
    }

    private val Local_Broadcast_Manager = LocalBroadcastManager.getInstance(this)
    private lateinit var client: Socket

    @Deprecated("Deprecated in Java")
    override fun onHandleIntent(intent: Intent?) {
        val url: String = intent?.getStringExtra(URL) ?: ""
        val address: String = intent?.getStringExtra(ADDRESS) ?: "127.0.0.1"
        val port: Int = intent?.getIntExtra(PORT, 11697) ?: 11697
        val length: Long = intent?.getLongExtra(LENGTH_PROGRESS, 100) ?: 100
        try {
            client = Socket(address, port)
            val out: OutputStream = client.getOutputStream()
            val contentResolver = applicationContext.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(Uri.parse(url))
            var len = 0

            //window size
            val buffer = ByteArray(10240)
            var total: Long = 0

            //send data
            while (inputStream!!.read(buffer).also { len = it } != -1){
                out.write(buffer, 0, len)
                total += len
                updateProgress(total, length)
            }
            out.close()
            inputStream.close()
            client.takeIf { it.isConnected }?.close()
        }catch (exp: Exception) {
            exp.printStackTrace()
        }finally {
            sendFinish()
        }
    }

    //Update progress
    private fun updateProgress(now: Long, length: Long) {
        Log.e("TCPSendService", "now: $now, length: $length")
        val intent = Intent(PROGRESS_UPDATE)
        intent.putExtra(NOW_PROGRESS, now)
        intent.putExtra(LENGTH_PROGRESS, length)
        Local_Broadcast_Manager.sendBroadcast(intent)
    }

    //Notification sent
    private fun sendFinish() {
        Log.e("TCPSendService", "sent file finish")
        val intent = Intent(SEND_FINISH)
        Local_Broadcast_Manager.sendBroadcast(intent)
    }

    // Shutdown Service asynchronously
    inner class TCP_send_receive : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                SEND_SHUTDOWN -> {
                    client.close()
                    Log.e("TCPreceiver", "TCP send_receive closed")
                }
            }
        }

    }

}