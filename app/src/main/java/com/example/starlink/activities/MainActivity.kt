package com.example.starlink.activities

import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.view.MenuItem
import android.support.v4.widget.DrawerLayout
import android.support.design.widget.NavigationView
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import com.example.starlink.R
import com.example.starlink.adapters.MessageAdapter
import com.example.starlink.dataclass.ChatMessage
import com.example.starlink.dataclass.DeviceInfo
import com.example.starlink.services.SendService
import com.example.starlink.services.ServerService

class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    companion object {
        var debugMode = 0
    }

    //recyclerView related class declaration
    private val adapter = MessageAdapter(deviceList, this)
    private var msgRecyclerView: RecyclerView? = null
    private val context = this
    private val receiver: ServiceBroadcastReceiver = ServiceBroadcastReceiver()
    private val intentFilter: IntentFilter = IntentFilter()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // load widget
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val fab: FloatingActionButton = findViewById(R.id.fab)
        val headView: View = navView.getHeaderView(0)
        val nameTextView: TextView = headView.findViewById(R.id.header_name)
        val renameButton: ImageButton = headView.findViewById(R.id.imageView)
        msgRecyclerView = findViewById(R.id.msg_recycler_view)
        //set toolbar
        setSupportActionBar(toolbar)
        //Dynamic application permissions
        requestPermission(
            listOf(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
        )
        //Button to change device name
        renameButton.setOnClickListener { view ->
            val editText = EditText(this)
            AlertDialog.Builder(this).setTitle("Please enter the device name")
                .setIcon(android.R.drawable.sym_def_app_icon)
                .setView(editText)
                .setPositiveButton("Sure") { p0, p1 ->
                    nameTextView.text = editText.text.toString()
                    deviceName = editText.text.toString()
                }.setNegativeButton("Cancel", null).show()
            send("#broadcast#connect#name:$deviceName#")
        }

        //Set the floating button, click to send a broadcast, let others discover yourself
        fab.setOnClickListener { view ->
            send("#broadcast#connect#name:$deviceName#")
            Snackbar.make(view, "Broadcasting...", Snackbar.LENGTH_LONG)
                .setAction("action", null).show()
        }
        //Side drawer component related settings
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        //Set the listener for the drawer button
        navView.setNavigationItemSelectedListener(this)

        //Initialization of recyclerView
        msgRecyclerView!!.layoutManager = LinearLayoutManager(this)
        msgRecyclerView!!.adapter = adapter

        Log.e("MainActivity", "ip address：${intToStringIP(DHCP_INFO.ipAddress)}")
        Log.e("MainActivity", "subnet mask：${intToStringIP(DHCP_INFO.netmask)}")
        Log.e(
            "MainActivity",
            "broadcast number：${getBroadcastIp(DHCP_INFO.ipAddress, DHCP_INFO.netmask)}"
        )
        Log.e("MainActivity", "gateway：${intToStringIP(DHCP_INFO.gateway)}")

        send("#broadcast#connect#name:$deviceName#")
        startServer(UDP_PORT, localIP)
    }

    //Register broadcast receiver
    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        //Set the other party as all devices in the LAN
        deviceAddress = getBroadcastIp(DHCP_INFO.ipAddress, DHCP_INFO.netmask)
        adapter.notifyDataSetChanged()
        //Set up a broadcast receiver
        intentFilter.addAction(ServerService.RECEIVE_MSG)
        intentFilter.addAction(SendService.SEND_FINISH)
        // Indicates a change in the Wi-Fi Direct status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)

        // Indicates a change in the list of available peers.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)

        // Indicates the state of Wi-Fi Direct connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)

        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        Local_Broadcast_Manager.registerReceiver(receiver, intentFilter)
    }

    //load menu bar
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    //Monitoring the menu bar
    @SuppressLint("NotifyDataSetChanged")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_quit -> {
                //close application
                finish()
                true
            }

            R.id.action_clear -> {
                //Delete all device information
                deviceList.clear()
                adapter.notifyDataSetChanged()
                msgRecyclerView!!.scrollToPosition(0)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    //drawer button monitor
    @SuppressLint("NotifyDataSetChanged")
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.nav_home -> {
                // Handle the camera action
                stopServer()
            }

            R.id.nav_gallery -> {

            }

            R.id.nav_slideshow -> {

            }

            R.id.nav_tools -> {
                if (debugMode == 0) {
                    debugMode = 1
                    deviceList.add(DeviceInfo("test device 1", "1.1.1.1", mutableListOf(), 0))
                    deviceList.add(DeviceInfo("test device 2", "1.1.1.1", mutableListOf(), 0))
                    adapter.notifyItemChanged(deviceList.size - 1)
                    msgRecyclerView!!.scrollToPosition(deviceList.size - 1)
                } else {
                    debugMode = 0
                    deviceList.clear()
                    adapter.notifyDataSetChanged()
                    msgRecyclerView!!.scrollToPosition(0)
                }
            }

            R.id.nav_share -> {

            }

            R.id.nav_send -> {

            }
        }
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }


    //Unregister broadcast receiver when invisible
    override fun onPause() {
        super.onPause()
        Local_Broadcast_Manager.unregisterReceiver(receiver)
    }

    //Traverse the list to find the device to be removed and delete
    private fun removeDevice(deviceAddress: String) {
        for (device in deviceList) {
            if (device.address == deviceAddress) {
                Log.e("MainActivity", "deleting$deviceAddress，There are${deviceList.size}个")
                deviceList -= device
                adapter.notifyDataSetChanged()
            }
        }
    }

    //Update device list
    private fun refreshDeviceList(device: DeviceInfo, reply: Boolean) {
        var has = false
        for (de in deviceList) {
            if (device.address == de.address) {
                de.name = device.name
                adapter.notifyDataSetChanged()
                has = true
            }
        }
        if (device.address == localIP) {
            has = true
        }
        if (!has) {
            Log.e("MainActivity", "add the${deviceList.size + 1}个equipment")
            Log.e("MainActivity", "The name of the device being added is: ${device.name}")
            deviceList.add(device)
            adapter.notifyItemChanged(deviceList.size - 1)
            msgRecyclerView!!.scrollToPosition(deviceList.size - 1)
        }
        if (reply)
            sendToTarget("#broadcast#confirm#name:$deviceName#", device.address)
    }

    //When a request to transfer a file is received
    private fun receiveFileRequest(address: String, msg: String) {
        for (device in deviceList) {
            //If someone wants to send files to this device, a dialog box will pop up
            if (device.address == address) {
                AlertDialog.Builder(this@MainActivity).setTitle(
                    "${
                        if (device.name != "default") {
                            device.name
                        } else {
                            device.address
                        }
                    }want to send you files"
                )
                    .setIcon(android.R.drawable.sym_def_app_icon)
                    .setPositiveButton("OK to receive") { p0, p1 ->
                        ChatActivity.startThisActivity(
                            this@MainActivity, address, true, msg
                        )
                    }.setNegativeButton("refuse to accept") { p0, p1 ->
                        send("#broadcast#file#refuse#")
                    }.show()
            }
        }
    }

    //Receive the chat history, update the small dot prompt
    @SuppressLint("NotifyDataSetChanged")
    private fun receiveChatMsg(address: String, msg: String) {
        Log.e("MainActivity", "received chat message")
        for (device in deviceList) {
            if (device.address == address) {
                device.chatList.add(ChatMessage(msg, ChatMessage.TYPE_RECEIVED))
                device.deviceCnt++
                adapter.notifyDataSetChanged()
            }
        }
    }

    //drawer related operations
    override fun onBackPressed() {
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    //Dynamic application permissions
    private fun requestPermission(permissionList: List<String>) {
        for (permission: String in permissionList) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(context, arrayOf(permission), 1)
            }
        }
    }

    //Obtain the IP address of the broadcast (the host number is all 1)
    private fun getBroadcastIp(ip: Int, netMask: Int): String {
        var net: Int = ip and netMask
        net = net or netMask.inv()
        return intToStringIP(net)
    }

    //Emit an offline message when an activity is closed
    override fun onDestroy() {
        send("#broadcast#disconnect#name:$deviceName#")
        stopServer()
        super.onDestroy()
    }

    //broadcast receiver
    inner class ServiceBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(p0: Context?, intent: Intent?) {
            when (intent?.action) {
                //Receive UDP data
                ServerService.RECEIVE_MSG -> {
                    //get content
                    val msg: String? = intent.getStringExtra("msg")
                    //Obtain the other party's IP address
                    val address: String? = intent.getStringExtra(ServerService.FROM_ADDRESS)
                    Log.e("MainActivity", "Received information for：$msg")
                    //Check if it is a special message
                    if (msg?.let { Regex("#broadcast#").containsMatchIn(it) } == true) {
                        Log.e("MainActivity", "receive received broadcast")
                        val name: String =
                            Regex("(?<=#name:).*?(?=#)").find(msg)?.value ?: "default"
                        when {
                            Regex("#connect#").containsMatchIn(msg) -> {
                                address?.let {
                                    DeviceInfo(
                                        name,
                                        it, mutableListOf(), 0
                                    )
                                }?.let { refreshDeviceList(it, true) }
                            }

                            Regex("#disconnect#").containsMatchIn(msg) -> {
                                if (address != null) {
                                    removeDevice(address)
                                }
                            }

                            Regex("#broadcast#confirm#").containsMatchIn(msg) -> {
                                address?.let {
                                    DeviceInfo(
                                        name,
                                        it, mutableListOf(), 0
                                    )
                                }?.let { refreshDeviceList(it, false) }
                            }

                            Regex("#file#").containsMatchIn(msg) ->
                                address?.let { receiveFileRequest(it, msg) }
                        }
                    } else {
                        //If it is not a special message, it will be judged as sent by the user, record and prompt how many unread messages there are
                        if (address != null) {
                            if (msg != null) {
                                receiveChatMsg(address, msg)
                            }
                        }
                    }

                }
            }
        }
    }
}
