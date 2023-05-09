package com.example.starlink.services

import android.app.IntentService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.net.DatagramPacket
import java.net.DatagramSocket

class ServerService: IntentService("ServerService") {
    companion object{
        const val PORT = "port"
        const val LOCAL_HOST = "local_host"
        const val STOP_SERVER = "stop_server"
        const val RECEIVE_MSG = "receive_msg"
        const val FROM_ADDRESS = "from_address"
    }
    private val Local_Broadcast_Manager = LocalBroadcastManager.getInstance(this)
    private lateinit var service: DatagramSocket
    private var running = true

    @Deprecated("Deprecated in Java")
    override fun onHandleIntent(intent: Intent?) {
        Log.d("ServerService", "OnHandleIntent si-1")
        running = true
        val intentFilter = IntentFilter()
        intentFilter.addAction(STOP_SERVER)
        val receiver = ServerReceiver()
        Local_Broadcast_Manager.registerReceiver(receiver, intentFilter)
        val port = intent?.getIntExtra(PORT, 11791) ?: 11791
        val localIP = intent?.getStringExtra(LOCAL_HOST)
        //Wrap IP address To create a DatagramSocket object on the server side, the port number needs to be passed in
        service = DatagramSocket(port)
        var receiveAddress: String
        var receiveMsg: String
        try {
            //Create a DatagramPacket object to store the received data
            val receiveBytes = ByteArray(2048)
            val receivePacket = DatagramPacket(receiveBytes, receiveBytes.size)
            //Debug
            Log.d("ServerService", "successfully started the server")
            // The server will Keep Listening to the incoming packets
            while (running) {
                try {
                    service.receive(receivePacket)
                    //Debug
                    Log.d("ServerService", "Successfully received information")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                //sender IP address
                receiveAddress = receivePacket.address.toString()
                receiveAddress = receiveAddress.substring(1, receiveAddress.length)
                Log.d("ServerService", "This packet comes from:$receiveAddress")

                // Skip the data sent by itself
                if (receiveAddress == localIP) {
                    continue
                }
                receiveMsg = String(receivePacket.data, 0, receivePacket.length)
                processReceivedData(receiveMsg, receiveAddress)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }finally {
            service.takeIf { service.isConnected }?.close()
        }
        // Unregister the broadcast receiver
        Local_Broadcast_Manager.unregisterReceiver(receiver)
        Log.d("ServerService", "ServerService has been closed in line 73 of ServerService")
    }

    //Process the received data
    private fun processReceivedData(receiveMsg: String, receiveAddress: String) {
        Log.d("ServerService", "processReceivedData si-2")
        Log.d("ServerService", "Received data: $receiveMsg")
        val intent = Intent(RECEIVE_MSG)
        intent.putExtra(FROM_ADDRESS, receiveAddress)
        intent.putExtra("msg", receiveMsg)
        Local_Broadcast_Manager.sendBroadcast(intent)
    }

    //The Broadcast Receiver is used to end the service
    inner class ServerReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.e("ServerService", "ServerReceiver onReceive si-3")
            when (intent?.action) {
                STOP_SERVER -> {
                    running = false
                    service.takeIf { it.isConnected }?.close()
                }
            }
        }
    }
}