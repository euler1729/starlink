package com.example.starlink.activities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.DhcpInfo
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AppCompatActivity
import com.example.starlink.dataclass.DeviceInfo
import com.example.starlink.services.SendService
import com.example.starlink.services.ServerService

open class BaseActivity: AppCompatActivity(){

    companion object{
        const val UDP_PORT: Int = 12345
        const val TCP_PORT: Int = 12346
        var deviceAddress: String = ""
        var deviceName: String = "default"
        val deviceList = mutableListOf<DeviceInfo>()
    }
    var localIP: String = ""
    lateinit var DHCP_INFO: DhcpInfo
    private lateinit var wifiManager: WifiManager
    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var wifiP2pChannel: WifiP2pManager.Channel
    lateinit var Local_Broadcast_Manager: LocalBroadcastManager
    lateinit var Notification_Manager: NotificationManager

    // Initializes Notification and all Network Services
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Local_Broadcast_Manager = LocalBroadcastManager.getInstance(this)
        Notification_Manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationChannel = NotificationChannel("Progress", "Translate", NotificationManager.IMPORTANCE_HIGH)
        Notification_Manager.createNotificationChannel(notificationChannel)
        wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        wifiP2pChannel = wifiP2pManager.initialize(this, mainLooper, null)
        DHCP_INFO = wifiManager.dhcpInfo
        localIP = intToStringIP(DHCP_INFO.ipAddress)
    }
    //Integer IP to String IP
    fun intToStringIP(ip: Int): String {
        return ((ip and 0xFF).toString() + "."
                + (0xFF and (ip shr 8)) + "."
                + (0xFF and (ip shr 16)) + "."
                + (0xFF and (ip shr 24)))
    }
    //Enable UDP receiver server
    fun startServer(port: Int, localHost: String){
        val intent = Intent(this, ServerService::class.java)
        intent.putExtra(ServerService.PORT, port)
        intent.putExtra(ServerService.LOCAL_HOST, localHost)
        startService(intent)
    }
    //Start the service sent by UDP
    fun send(messageTxt: String){
        val intent = Intent(this, SendService::class.java)
        intent.putExtra(SendService.CONTENT, messageTxt)
        intent.putExtra(SendService.IP_ADDRESS, deviceAddress)
        intent.putExtra(SendService.PORT, UDP_PORT)
        startService(intent)
    }
    //Send UDP to a specific IP address
    fun sendToTarget(messageTxt: String, ip: String){
        val intent = Intent(this, SendService::class.java)
        intent.putExtra(SendService.CONTENT, messageTxt)
        intent.putExtra(SendService.IP_ADDRESS, ip)
        intent.putExtra(SendService.PORT, UDP_PORT)
        startService(intent)
    }
    //Stops UDP receive monitoring
    fun stopServer(){
        Local_Broadcast_Manager.sendBroadcast(Intent(ServerService.STOP_SERVER))
    }
}
