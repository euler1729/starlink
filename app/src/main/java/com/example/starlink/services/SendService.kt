package com.example.starlink.services

import android.app.IntentService
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class SendService: IntentService("SendService") {
    companion object{
        const val SEND_FINISH = "send_finish"
        const val CONTENT = "msg_text"
        const val IP_ADDRESS = "ip_address"
        const val PORT = "port"
    }
    private var message: String? = ""
    private lateinit var Local_Broadcast_Manager: LocalBroadcastManager

    @Deprecated("Deprecated in Java")
    override fun onCreate() {
        super.onCreate()
        Local_Broadcast_Manager = LocalBroadcastManager.getInstance(this)
        Log.d("SendService", "onCreate si-1")
    }
    @Deprecated("Deprecated in Java")
    override fun onHandleIntent(intent: Intent?) {
        Log.d("SendService", "onHandleIntent si-2")
        message = intent?.getStringExtra(CONTENT)
        val ipAddress: String? = intent?.getStringExtra(IP_ADDRESS)
        val port: Int? = intent?.getIntExtra(PORT, 11791)
        val receiver = DatagramSocket()
        Log.d("SendService", "UDP send message: $message to $ipAddress:$port ")
        try {
            // Creating packet to send to the receiver
            val sendBytes: ByteArray? = message?.toByteArray()
            val address: InetAddress = InetAddress.getByName(ipAddress)
            val sendPacket = DatagramPacket(sendBytes, sendBytes!!.size, address, port!!)

            try {
                receiver.send(sendPacket)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            receiver.close()
        }
        sendFinish()
    }
    private fun sendFinish(){
        Log.d("SendService", "sendFinish si-3")
        Log.d("SendService", "Successful UDP send message")
        val intent = Intent(SEND_FINISH)
        intent.putExtra("msg", message)
        Local_Broadcast_Manager.sendBroadcast(intent)
    }
}