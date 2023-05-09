package com.example.starlink

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.navigation.NavigationView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.starlink.adapter.MessageAdapter
import com.example.starlink.dataClass.ChatMessage
import com.example.starlink.dataClass.DeviceInfo
import com.example.starlink.services.SendService
import com.example.starlink.services.ServerService
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar

class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    companion object{
        var debugMode = 0;
    }

    //recyclerView
    private val adapter = MessageAdapter(deviceList, this)
    private var msgRecyclerView: RecyclerView? = null
    private val context = this
    private val receiver: ServiceBroadcastReceiver = ServiceBroadcastReceiver()


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //load widget
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
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
        requestPermission(listOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE))
        //Button to change device name
        renameButton.setOnClickListener {
            val editText = EditText(this)
            AlertDialog.Builder(this)
                .setTitle("Enter the device name")
                .setView(editText)
                .setPositiveButton("OK") { _, _ ->
                    deviceName = editText.text.toString()
                    nameTextView.text = deviceName
                }
                .setNegativeButton("Cancel", null)
                .show()
            send("#broadcast#connect#name:$deviceName#")
        }
        //Set the the floating to let other  to discover
        fab.setOnClickListener {
            send("#broadcast#connect#name:$deviceName#")
            Snackbar.make(it, "Refreshing...", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }
        //Side drawer menu
        val toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        //set the listener for the drawer button
        navView.setNavigationItemSelectedListener(this)

         //initialize the recyclerView
        msgRecyclerView?.layoutManager = LinearLayoutManager(this)
        msgRecyclerView?.adapter = adapter

        Log.d("MainActivity", "ip address：${intToStringIP(DHCP_Info.ipAddress)}")
        Log.d("MainActivity", "subnet mask：${intToStringIP(DHCP_Info.netmask)}")
        Log.d("MainActivity", "broadcast number：${getBroadcastIp(DHCP_Info.ipAddress, DHCP_Info.netmask)}")
        Log.d("MainActivity", "gateway：${intToStringIP(DHCP_Info.gateway)}")
        Log.d("MainActivity", "Device name：${deviceName}")
        send("#broadcast#connect#name:$deviceName#")
        startServer(UDP_PORT, localIP)
    }
    //Obtain the broadcast address
    private fun getBroadcastIp(ipAddress: Int, netmask: Int): String{
        var net: Int = ipAddress and netmask
        net = net or (netmask.inv())
        return intToStringIP(net)
    }
    //Convert int to ip address
    private fun intToIp(ipAddress: Int): String{
        return "${ipAddress and 0xFF}.${ipAddress shr 8 and 0xFF}.${ipAddress shr 16 and 0xFF}.${ipAddress shr 24 and 0xFF}"
    }
    //Dynamic application permissions
    private fun requestPermission(permissionList: List<String>){
        for (permission: String in permissionList){
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(context, arrayOf(permission), 1)
            }
        }
    }

    //Extended sidebar menu
    @android.annotation.SuppressLint("NotifyDataSetChanged")
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
            R.id.nav_tools->{
                if (debugMode == 0) {
                    debugMode = 1
                    deviceList.add(DeviceInfo("Device1", "1.1.1.1", mutableListOf(),0))
                    deviceList.add(DeviceInfo("Device2", "1.1.1.2", mutableListOf(),0))
                    adapter.notifyItemChanged(deviceList.size-1)
                    msgRecyclerView!!.scrollToPosition(deviceList.size-1)
                }else{
                    debugMode = 0;
                    deviceList.clear()
                    adapter?.notifyDataSetChanged()
                    msgRecyclerView?.scrollToPosition(0)
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

    //broadcast receiver
    inner class ServiceBroadcastReceiver: BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?){
            when(intent?.action){
                //Receive UDP
                ServerService.RECEIVE_MSG ->{
                    //get content from intent
                    val msg:String? = intent.getStringExtra("msg")
                    //Obtain the available device list
                    val address: String? = intent.getStringExtra(ServerService.FROM_ADDRESS)
                    Log.e("MainActivity", "Received msg from $address: $msg")
                    //Check is it a special message
                    val tst = msg?.let { Regex("#broadcast#").containsMatchIn(it) }
                    Log.e("MainActivity", "msg: $tst")
                    if (msg?.let { Regex("#broadcast#").containsMatchIn(it) } == true){
                        val name: String = Regex("(?<=#name:).*?(?=#)").find(msg)?.value ?: "default"
                        Log.e("MainActivity", "received broadcast")
                        Log.e("MainActivity", "name: $name")
                        when {
                            Regex("#connect#").containsMatchIn(msg) -> {
                                address?.let {
                                    DeviceInfo(name,
                                        it, mutableListOf(), 0)
                                }?.let { refreshDeviceList(it, true) }
                            }
                            Regex("#disconnect#").containsMatchIn(msg) -> {
                                if (address != null) {
                                    removeDevice(address)
                                }
                            }
                            Regex("#broadcast#confirm#").containsMatchIn(msg) -> {
                                address?.let {
                                    DeviceInfo(name,
                                        it, mutableListOf(), 0)
                                }?.let { refreshDeviceList(it, false) }
                            }
                            Regex("#file#").containsMatchIn(msg) ->
                                address?.let { receiveFileRequest(it, msg) }
                        }
                    }else {
                        //If it is not a special message, it will be judged as sent by the user, record and prompt how many unread messages there are
                        if (address != null) {
                            if (msg != null) {
                                receiveChatMsg(address, msg)
                            }
                            else{
                                Log.e("MainActivity", "receive null msg")
                            }
                        }
                    }
                }
            }
        }
    }
    private fun refreshDeviceList(device: DeviceInfo, reply: Boolean){
        var has = false
        for (de in deviceList){
            if (device.address == de.address){
                de.name = device.name
                adapter!!.notifyDataSetChanged()
                has = true
            }
        }
        if (device.address == localIP){
            has = true
        }
        if (!has){
            Log.e("MainActivity", "add the${deviceList.size + 1}个equipment")
            Log.e("MainActivity", "The name of the device being added is: ${device.name}")
            deviceList.add(device)
            adapter.notifyItemChanged(deviceList.size - 1)
            msgRecyclerView!!.scrollToPosition(deviceList.size - 1)
        }
        if (reply)
            sendToTarget("#broadcast#confirm#name:$deviceName#", device.address)
    }
    private fun removeDevice(deviceAddress: String){
        for (de in deviceList){
            if (de.address == deviceAddress){
                Log.e("MainActivity", "deleting$deviceAddress，There are${deviceList.size}个")
                deviceList -= de
                adapter.notifyDataSetChanged()
            }
        }
    }
    //When a request to transfer a file is received
    private fun receiveFileRequest(address: String, msg: String){
        for (de in deviceList){
            //If someone wants to send files to this device, a dialog box will pop up
            if (de.address == address){
                AlertDialog.Builder(this@MainActivity).setTitle("${
                    if(de.name != "default"){de.name}else{de.address}}want to send you files")
                    .setIcon(android.R.drawable.sym_def_app_icon)
                    .setPositiveButton("OK to receive") { p0, p1 ->
                        ChatActivity.startThisActivity(
                            this@MainActivity, address, true, msg)
                    }.setNegativeButton("refuse to accept"){ p0, p1 ->
                        send("#broadcast#file#refuse#")
                    }.show()
            }
        }
    }
    //Receive the chat history, update the small dot prompt
    private fun receiveChatMsg(address: String, msg: String){
        Log.e("MainActivity", "received chat message")
        Log.e("MainActivity", "laddress: $address")
        for (device in deviceList){
            Log.e("MainActivity", "address: ${device.address}")
            if (device.address == address){
                device.chatList.add(ChatMessage(msg, ChatMessage.TYPE_RECEIVED))
                device.new++
                adapter.notifyDataSetChanged()
            }
        }
    }


    // Monitoring menu bar
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_quit -> {
                finish()//closes the app
                true
            }
            R.id.action_clear ->{
                deviceList.clear()//deletes all device info
                adapter!!.notifyDataSetChanged()
                msgRecyclerView!!.scrollToPosition(0)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    //load menu bar
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }
    //Back button press event
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
    //Register Broadcast receiver
    override fun onResume() {
        super.onResume()
        //Set the other devices
        deviceAddress = getBroadcastIp(DHCP_Info.ipAddress, DHCP_Info.netmask)
        adapter!!.notifyDataSetChanged()
        //Set up a broadcast receiver
        val intentFilter = IntentFilter()
        intentFilter.addAction(ServerService.RECEIVE_MSG)
        intentFilter.addAction(SendService.SEND_FINISH)
        Local_Broadcast_Manager.registerReceiver(receiver, intentFilter)
    }
    //Unregister broadcast receiver when invisible
    override fun onPause() {
        super.onPause()
        Local_Broadcast_Manager.unregisterReceiver(receiver)
    }
    //Emit offline message when an activity is closed
    override fun onDestroy() {
        send("#broadcast#disconnect#name:$deviceName#")
        stopServer()
        super.onDestroy()
    }
}