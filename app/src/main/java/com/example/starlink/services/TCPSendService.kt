package com.example.starlink.services

import android.app.IntentService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import java.io.*
import java.lang.Exception
import java.net.Socket
import java.security.Key
import java.security.spec.KeySpec
import java.util.Random
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec


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
        val port: Int = intent?.getIntExtra(PORT, 12346) ?: 12346
        val length: Long = intent?.getLongExtra(LENGTH_PROGRESS, 100) ?: 100
        try {
            client = Socket(address, port)
            val out: OutputStream = client.getOutputStream()
            val cr = applicationContext.contentResolver
            val inputStream: InputStream? = cr.openInputStream(Uri.parse(url))
//            val fileInputStream = FileInputStream(File(url))
            var len = 0
            //window size
            val buffer = ByteArray(10240)
            var total: Long = 0

            /********AES encryption***********************/
            val secretKey = "8y/A?D(G+KbPeShV".toByteArray()
            val cipher = Cipher.getInstance("AES")
            val keySpec = SecretKeySpec(secretKey, "AES")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            val cipherOutputStream = CipherOutputStream(out, cipher)
            /*********************************************/


            val mss = 1024 //initial segment size
            var cwnd = mss //congestion window size
            var ssthresh = Integer.MAX_VALUE //slow start threshold
            var duplicateAck = 1 //duplicate ack

            //send data
            while (inputStream!!.read(buffer,0,cwnd).also { len = it } != -1) {
                out.write(buffer, 0, len)
                total += len
                updateProgress(total, length)
                // Congestion Control
                if (duplicateAck==2) {
                    ssthresh = cwnd / 2
                    cwnd = ssthresh + 3 * mss
                    duplicateAck = 0
                    Thread.sleep(100)
                }else if(ssthresh>cwnd) {
                    cwnd += mss
                }
                else {
                    cwnd += mss * mss / cwnd
                }
                duplicateAck = Random().nextInt(3)
            }
            cipherOutputStream.flush()
            cipherOutputStream.close()
            out.close()
            inputStream.close()
            client.takeIf { it.isConnected }?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            sendFinish()
        }
    }

    //Notification sent successfully
    private fun sendFinish() {
        Log.e("TCPSendService", "File sent successfully")
        val intent = Intent(SEND_FINISH)
        Local_Broadcast_Manager.sendBroadcast(intent)
    }

    //update progress bar
    private fun updateProgress(now: Long, length: Long) {
        Log.d("TcpSendService", "updating progress bar：$now / $length")
        val intent = Intent(PROGRESS_UPDATE)
        intent.putExtra(NOW_PROGRESS, now)
        intent.putExtra(LENGTH_PROGRESS, length)
        Local_Broadcast_Manager.sendBroadcast(intent)
    }

    //Shut down the service asynchronously
    inner class TcpSendReceiver : BroadcastReceiver() {
        override fun onReceive(p0: Context?, intent: Intent?) {
            when (intent?.action) {
                SEND_SHUTDOWN -> {
                    client.close()
                    Log.e("TCPSendService", "TCP send is closed")
                }
            }
        }
    }
}
