package com.example.starlink.services

import android.app.IntentService
import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import java.lang.Exception
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class SendService : IntentService("SendService") {
    companion object{
        const val SEND_FINISH: String = "send_finsih"
        const val CONTENT: String = "msg_text"
        const val IP_ADDRESS: String = "ip_address"
        const val PORT: String = "port"
    }
    private var msg: String? = ""
    private lateinit var Local_Broadcast_Manager: LocalBroadcastManager
    @Deprecated("Deprecated in Java")
    override fun onCreate() {
        super.onCreate()
        Local_Broadcast_Manager = LocalBroadcastManager.getInstance(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onHandleIntent(intent: Intent?) {
        msg = intent?.getStringExtra(CONTENT)
        val ipAddress: String? = intent?.getStringExtra(IP_ADDRESS)
        val port: Int? = intent?.getIntExtra(PORT, 12345)
        val client = DatagramSocket()
        try {
            //Convert string to Byte array
            val sendBytes: ByteArray? = msg?.toByteArray()
            val address: InetAddress = InetAddress.getByName(ipAddress)
            //Pack and send
            val sendPacket = DatagramPacket(sendBytes, sendBytes!!.size, address, port!!)

            try {
                client.send(sendPacket)
            }catch (e: Exception){
                e.printStackTrace()
            }
        }catch (e: Exception){
            e.printStackTrace()
        }finally {
            client.close()
        }
        sendFinish()
    }

    private fun sendFinish(){
        Log.e("SendService", "Successful UDP send message")
        val intent = Intent(SEND_FINISH)
        intent.putExtra("msg", msg)
        Local_Broadcast_Manager.sendBroadcast(intent)
    }
}
