package com.example.starlink

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.DhcpInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.starlink.dataClass.DeviceInfo
import com.example.starlink.services.SendService
import com.example.starlink.services.ServerService

open class BaseActivity : AppCompatActivity() {
    companion object {
        const val UDP_PORT = 11791
        const val TCP_PORT = 11697
        var deviceAddress = ""
        var deviceName = "default"
        val deviceList = mutableListOf<DeviceInfo>()
    }

    var localIP = ""
    lateinit var DHCP_Info: DhcpInfo
    private lateinit var wifiManager: WifiManager
    lateinit var Local_Broadcast_Manager: LocalBroadcastManager
    lateinit var Notification_Manager: NotificationManager

    // Initializes Notification and all Network Services
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Local_Broadcast_Manager = LocalBroadcastManager.getInstance(this)
        Notification_Manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notificationChannel =
            NotificationChannel("Progress", "Translate", NotificationManager.IMPORTANCE_HIGH)
        Notification_Manager.createNotificationChannel(notificationChannel)
        wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        DHCP_Info = wifiManager.dhcpInfo
        localIP = intToStringIP(DHCP_Info.ipAddress)
    }

    //Converts Integer IP address to String
    fun intToStringIP(dhcpIP: Int): String {
        return ((dhcpIP and 0xFF).toString() + "."
                + (0xFF and (dhcpIP shr 8)) + "."
                + (0xFF and (dhcpIP shr 16)) + "."
                + (0xFF and (dhcpIP shr 24)))
    }

    //This server will listen to the incoming UDP Packets
    fun startServer(port: Int, localHost: String) {
        val intent = Intent(this, ServerService::class.java)
        intent.putExtra(ServerService.PORT, port)
        intent.putExtra(ServerService.LOCAL_HOST, localHost)
        startService(intent)
    }

    //Start the service sent by UDP
    fun send(msgText: String) {
        val intent = Intent(this, SendService::class.java)
        intent.putExtra(SendService.CONTENT, msgText)
        intent.putExtra(SendService.IP_ADDRESS, deviceAddress)
        intent.putExtra(SendService.PORT, UDP_PORT)
        Log.d("BaseActivity","sender-> txt:$msgText ip:$deviceAddress udp_port:$UDP_PORT")
        startService(intent)
    }

    // Send UDP to a specific IP address
    fun sendToTarget(msgText: String, ip: String) {
        val intent = Intent(this, ServerService::class.java)
        intent.putExtra(SendService.CONTENT, msgText)
        intent.putExtra(SendService.IP_ADDRESS, ip)
        intent.putExtra(SendService.PORT, UDP_PORT)
        startService(intent)
    }

    //Stop UDP receive monitoring
    fun stopServer() {
        Local_Broadcast_Manager.sendBroadcast(Intent(ServerService.STOP_SERVER))
    }
}