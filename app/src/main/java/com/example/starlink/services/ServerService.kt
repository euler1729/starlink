package com.example.starlink.services

import android.app.IntentService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket


class ServerService : IntentService("ServerService") {
    companion object{
        const val RECEIVE_MSG: String = "receive_msg"
        const val PORT: String = "port"
        const val STOP_SERVER: String = "stop_server"
        const val LOCAL_HOST: String = "local_host"
        const val FROM_ADDRESS: String = "from_address"
    }

    private val Local_Broadcast_Manager = LocalBroadcastManager.getInstance(this)
    private lateinit var service: DatagramSocket
    private var running: Boolean = true

    @Deprecated("Deprecated in Java")
    override fun onHandleIntent(intent: Intent?) {
        running = true

        val intentFilter = IntentFilter()
        intentFilter.addAction(STOP_SERVER)
        val receiver = ServerReceiver()
        Local_Broadcast_Manager.registerReceiver(receiver, intentFilter)
        val port: Int = intent?.getIntExtra(PORT, 12345) ?: 12345
        val localIp: String? = intent?.getStringExtra(LOCAL_HOST)
        //Wrap IP address
        //To create a DatagramSocket object on the server side, the port number needs to be passed in
        service = DatagramSocket(port)
        service.reuseAddress = true

        var receiveAddress: String
        var receiveMsg: String
        try {
            val receiveBytes = ByteArray(2048)
            //Create a package object that accepts the message
            val receivePacket = DatagramPacket(receiveBytes, receiveBytes.size)

            Log.e("ServerService", "successfully started the server")
            //Open an infinite loop and continue to receive data
            while (running) {
                try {
                    //Receive data, the program will block until a data packet is received
                    service.receive(receivePacket)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Log.e("ServerService", "Successfully received information")
                //parse the received data
                receiveAddress = receivePacket.address.toString()
                receiveAddress = receiveAddress.substring(1, receiveAddress.length)
                Log.e("ServerService", "This packet comes from:$receiveAddress")
                //Skip the data sent by yourself
                if (receiveAddress == localIp)
                    continue
                receiveMsg = String(receivePacket.data, 0, receivePacket.length)

                receiveMsg(receiveMsg, receiveAddress)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            //Close the DatagramSocket object
            service.takeIf { service.isConnected }?.close()
        }
        Local_Broadcast_Manager.unregisterReceiver(receiver)
        Log.e("ServerService", "server shut down")
    }

    //processing received information
    private fun receiveMsg(receiveMsg: String, address: String){
        Log.e("ServerService", "Receive information：$receiveMsg")
        val intent = Intent(RECEIVE_MSG)
        intent.putExtra("msg", receiveMsg)
        intent.putExtra(FROM_ADDRESS, address)
        Local_Broadcast_Manager.sendBroadcast(intent)
    }

    //The broadcast receiver is used to end the service
    inner class ServerReceiver: BroadcastReceiver(){
        override fun onReceive(p0: Context?, intent: Intent?) {
            when (intent?.action){
                STOP_SERVER -> {
                    service.takeIf { it.isConnected }?.close()
                    running = false
                }
            }
        }

    }
}
