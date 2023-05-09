package com.example.starlink

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.starlink.adapter.ChatAdapter
import com.example.starlink.dataClass.ChatMessage
import com.example.starlink.dataClass.DeviceInfo
import com.example.starlink.services.SendService
import com.example.starlink.services.ServerService
import com.example.starlink.services.TCPSendService
import com.example.starlink.services.TCPServerService
import java.io.File

class ChatActivity : BaseActivity() {
    companion object {
        @JvmStatic
        fun startThisActivity(
            context: Context,
            deviceAddress: String,
            readyToReceive: Boolean,
            mgs: String
        ) {
            val intent = Intent(context, ChatActivity::class.java)  // Create intent
            intent.putExtra("msg", mgs)  // Put device address
            intent.putExtra("readyToReceive", readyToReceive)  // Put device address
            intent.putExtra("deviceAddress", deviceAddress)  // Put device address
            context.startActivity(intent)  // Start activity
        }
    }

    private var path: String? = null;
    private var size: Long? = null;
    private var messageList: MutableList<ChatMessage> = mutableListOf()
    private var recyclerView: RecyclerView? = null
    private var adapter: ChatAdapter? = null
    private var targetName: String = "default"
    private var connected: Boolean = false

    private val receiver = ChatReceiver()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        //load content
        val sendBtn: AppCompatButton = findViewById(R.id.send_button)
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar_chat)
        val editText: EditText = findViewById(R.id.edit_text)
        recyclerView = findViewById(R.id.recycler_chat)
//        setSupportActionBar(toolbar)

        //Enter to send data
        editText.setOnEditorActionListener { textView, i, keyEvent ->
            if (i == EditorInfo.IME_ACTION_DONE) {
                if (editText.text.toString() != "") {
                    send(editText.text.toString())
                    editText.setText("")
                }
            }
            false
        }
        //Click the send button to send data
        sendBtn.setOnClickListener {
            if (editText.text.toString() != "") {
                send(editText.text.toString())
                editText.setText("")
            }
        }
        recyclerView!!.layoutManager = LinearLayoutManager(this)
        //Set the corresponding ip address
        deviceAddress = intent.getStringExtra("deviceAddress")!!.toString()
        //Get the corresponding chat records
        getList(deviceAddress)
        adapter = ChatAdapter(messageList)
        recyclerView!!.adapter = adapter
        if (MainActivity.debugMode == 1) {
            Log.d("ChatActivity", "debugmode: $deviceAddress")
            messageList.add(ChatMessage("Hello", ChatMessage.TYPE_RECEIVED))
            messageList.add(ChatMessage("I'm fine", ChatMessage.TYPE_SEND))
            adapter!!.notifyDataSetChanged()
        }
        //Set the title
        setTitle(false)
        checkOnline(deviceAddress)

        //Receive file transfer when opening an activity
        if (intent.getBooleanExtra("readyToReceive", false)) {
            val msgText: String? = intent.getStringExtra("msg")
            val fileSize: Long =
                msgText?.let { Regex("(?<=#size:).*?(?=#)").find(it)?.value?.toLong() }
                    ?: 0
            val fileName: String =
                msgText?.let { Regex("(?<=#name:).*?(?=#)").find(it)?.value } ?: "download"
            val uri = Environment.DIRECTORY_DOWNLOADS + "/" + fileName
            receiveFile(fileSize, uri, fileName)
        }
    }


    inner class ChatReceiver : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                // Receive UDP info
                ServerService.RECEIVE_MSG -> intent.getStringExtra(ServerService.FROM_ADDRESS)
                    ?.let {
                        receive(intent.getStringExtra("msg")!!, it)
                    }
                //Sent UDP success
                SendService.SEND_FINISH -> intent.getStringExtra("msg")?.let {
                    refreshSendMsg(it)
                }
                //Sent TCP success
                TCPSendService.SEND_FINISH -> transmissionSuccess(1)
                //Update the progress bar
                TCPSendService.PROGRESS_UPDATE -> {
                    updateProgressbar(
                        intent.getLongExtra(TCPSendService.NOW_PROGRESS, 0),
                        intent.getLongExtra(TCPSendService.LENGTH_PROGRESS, 0), 1
                    )
                }
                //Receive TCP complete
                TCPServerService.RECEIVE_FINISH -> {
                    transmissionSuccess(2)
                    updateProgressbar(
                        intent.getLongExtra(TCPSendService.NOW_PROGRESS, 0),
                        intent.getLongExtra(TCPSendService.LENGTH_PROGRESS, 0), 2
                    )
                }
            }
        }
    }

    //get the chat history
    private fun getList(deviceAddress: String) {
        for (device in deviceList) {
            if (device.address == deviceAddress) {
                messageList = device.chatList
                targetName = device.name
                break
            }
        }
    }

    //update progress bar
    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateProgressbar(now: Long, length: Long, id: Int) {
        val progress: Int = (now * 100 / length).toInt()
        val notification = Notification.Builder(this, "Progress")
            .setContentTitle("Transmission Progress")
            .setContentText("$progress%")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    resources,
                    R.drawable.ic_launcher_foreground
                )
            )
            .setProgress(100, progress, false)
            .setAutoCancel(true)
            .build()
        notification.flags = Notification.FLAG_ONGOING_EVENT
        Notification_Manager.notify(id, notification)
    }

    //Notification of successful transmission
    @RequiresApi(Build.VERSION_CODES.O)
    private fun transmissionSuccess(type: Int) {
        Notification_Manager.cancel(type)
        val notification = Notification.Builder(this@ChatActivity, "Progress")
            .setContentTitle("Transmission Success")
            .setContentText("The file has been sent successfully")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    resources,
                    R.drawable.ic_launcher_foreground
                )
            )
            .setAutoCancel(true)
            .build()
        Notification_Manager.notify(3, notification)
    }

    //Refresh the message sent in chat
    private fun refreshSendMsg(msg: String) {
        if (!Regex("#broadcast").containsMatchIn(msg)) {
            //If connected send normally
            messageList.run {
                if (connected) {
                    add(ChatMessage(msg, ChatMessage.TYPE_SEND))
                } else {
                    add(ChatMessage(msg, ChatMessage.TYPE_WRONG))
                }
            }
            adapter!!.notifyItemChanged(messageList.size - 1)
            recyclerView!!.scrollToPosition(messageList.size - 1)
        }
    }

    //Corresponding processing when receiving information
    private fun receive(msg: String, address: String) {
        //Judging whether it's a special message
        if (Regex("#broadcast").containsMatchIn(msg)) {
            val name = Regex("(?<=#name:).*?(?=#)").find(msg)?.value ?: "default"
            //checking is anyone online
            when {
                Regex("#connect#").containsMatchIn(msg) -> {
                    //If the device is not in the device list, add it to the device list
                    if (!hasDevice(address)) {
                        //Add to the device list
                        deviceList.add(DeviceInfo(targetName, address, mutableListOf(), 0))
                    }
                    if (address == deviceAddress) {
                        //If the device is connected, set the connected flag to true
                        connected = true
                        setTitle(true)
                    }
                    sendToTarget("#broadcast#confirm#name:$deviceName", address)
                }

                Regex("#disconnect#").containsMatchIn(msg) -> {
                    //Determine if someone is offline
                    for (device in deviceList) {
                        if (device.address == address) {
                            deviceList.remove(device)
                            //If the device is currently chatting, set it to not connected
                            if (address == deviceAddress) {
                                connected = false
                                setTitle(false)
                            }
                        }
                    }
                }

                Regex("#broadcast#confirm#").containsMatchIn(msg) -> {
                    if (!hasDevice(address)) {
                        //Add to the device list
                        deviceList.add(DeviceInfo(targetName, address, mutableListOf(), 0))
                    }
                    if (address == deviceAddress) {
                        //If the device is connected, set the connected flag to true
                        connected = true
                        setTitle(true)
                    }
                }
                // If the message is a file, save the file
                Regex("#broadcast#file#").containsMatchIn(msg) -> {
                    when {
                        //The receiver refuses to accept the file
                        Regex("#refuse#").containsMatchIn(msg) -> {
                            Log.e("Chat Activity", "The receiver refused to accept the file")
                            Toast.makeText(
                                this,
                                "The receiver refused to accept the file",
                                Toast.LENGTH_SHORT
                            ).show()
                            val intent = Intent(TCPSendService.SEND_SHUTDOWN)
                            Local_Broadcast_Manager.sendBroadcast(intent)
                        }
                        //The receiver accepts the file
                        Regex("#confirm#").containsMatchIn(msg) -> {
                            Log.e(
                                "Chat Activity",
                                "Acknowledged receipt of file, starting to send..."
                            )
                            fileTransfer()
                        }
                        //File transfer request received
                        else -> {
                            val fileSize: Long =
                                Regex("(?<=#size:).*?(?=#)").find(msg)?.value?.toLong() ?: 0
                            val fileName: String =
                                Regex("(?<=#name:).*?(?=#)").find(msg)?.value ?: "download"
                            val uri =
                                Environment.getExternalStorageDirectory().absolutePath + "/Download/" + fileName
                            receiveFile(fileSize, uri, fileName)
                        }
                    }
                }
            }
        } else {//If it's not a special message, it's a normal message
            if (address == deviceAddress) {
                //If the message is sent to the current device, add it to the message list
                messageList.add(ChatMessage(msg, ChatMessage.TYPE_RECEIVED))
                adapter!!.notifyItemChanged(messageList.size - 1)
                recyclerView!!.scrollToPosition(messageList.size - 1)
            } else {
                //If the message is sent to another device, add it to the message list of the other device
                for (device in deviceList) {
                    if (device.address == address) {
                        device.chatList.add(ChatMessage(msg, ChatMessage.TYPE_RECEIVED))
                        device.new++
                    }
                }
            }
        }
    }

    //get Read File size
    private fun getReadFileSize(size: Long): String {
        val kb = 1024
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            size < kb -> "$size B"
            size < mb -> "${size / kb} KB"
            size < gb -> "${size / mb} MB"
            else -> "${size / gb} GB"
        }
    }

    //Receive file Popup dialog
    private fun receiveFile(fileSize: Long, uri: String, fileName: String) {
        Log.e("Chat Activity", "Received file request, starting to receive...")
        Log.e("Chat Activity", "File size: $fileSize, name: $fileName")
        AlertDialog.Builder(this)
            .setIcon(android.R.drawable.sym_def_app_icon)
            .setTitle("File transfer request")
            .setMessage("File name: $fileName\n size: ${getReadFileSize(fileSize)}")
            .setPositiveButton("Accept") { _, _ ->
                //Start TCP server
                startTCPServer(fileSize, uri)
                //Send confirmation message
                send("#broadcast#file#confirm#")
            }
            .setNegativeButton("Refuse") { _, _ ->
                //Send refusal message
                send("#broadcast#file#refuse#")
            }
            .show()
    }

    //Start TCP server
    private fun startTCPServer(fileSize: Long, uri: String) {
        val intent = Intent(this, ServerService::class.java)
        intent.putExtra(TCPServerService.PORT, TCP_PORT)
        intent.putExtra(TCPServerService.LENGTH_PROGRESS, fileSize)
        intent.putExtra(TCPServerService.FILE_URL, uri)
        startService(intent)
    }

    //Send file
    private fun fileTransfer() {
        val intent = Intent(this, TCPSendService::class.java)
        intent.putExtra(TCPSendService.ADDRESS, deviceAddress)
        intent.putExtra(TCPSendService.URL, path)
        intent.putExtra(TCPSendService.PORT, TCP_PORT)
        intent.putExtra(TCPSendService.LENGTH_PROGRESS, size)
        startService(intent)
        TODO("Networking algorithm will be implemented later")
    }

    //Determining whether the other parties are online
    private fun checkOnline(address: String) {
        for (device in deviceList) {
            if (device.address == address) {
                connected = true
                setTitle(true)
                device.new = 0
            }
        }
    }

    //set title
    private fun setTitle(connected: Boolean) {
        supportActionBar?.run {
            title = if (targetName != "default") {
                targetName + if (connected) "Connected" else "not connected"
            } else {
                deviceAddress + if (connected) "Connected" else "not connected"
            }
        }
    }

    // Determine whether the device is in the device list
    private fun hasDevice(address: String): Boolean {
        var has = false
        for (device in deviceList) {
            if (device.address == address) {
                has = true;
                break
            }
        }
        if (address == localIP) {
            has = true
        }
        return has;
    }

    //parse uri
    private fun getPath(context: Context?, uri: Uri?): String? {
        if (context == null || uri == null) return null
        if (DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]
                if ("primary".equals(type, true)) {
                    return Environment.getExternalStorageDirectory().absolutePath + "/" + split[1]
                }
            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"),
                    id.toLong()
                )
                return getDataColumn(context, contentUri, null, null)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]
                var contentUri: Uri? = null
                when (type) {
                    "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])
                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        } else if ("content".equals(uri.scheme!!, ignoreCase = true)) {
            return if (isGooglePhotosUri(uri)) {
                uri.lastPathSegment
            } else {
                getDataColumn(context, uri, null, null)
            }
        } else if ("file".equals(uri.scheme!!, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    private fun getDataColumn(
        context: Context,
        uri: Uri?,
        selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(column)
        try {
            cursor =
                context.contentResolver.query(uri!!, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(index)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
    }

    //Unregister broadcast receiver when activity is destroyed
    override fun onDestroy() {
        super.onDestroy()
        Local_Broadcast_Manager.unregisterReceiver(receiver)
    }

    //Register broadcast receiver
    override fun onResume() {
        super.onResume()
        adapter!!.notifyDataSetChanged()
        val intentFilter = IntentFilter()
        intentFilter.addAction(SendService.SEND_FINISH)
        intentFilter.addAction(ServerService.RECEIVE_MSG)
        intentFilter.addAction(TCPSendService.PROGRESS_UPDATE)
        intentFilter.addAction(TCPSendService.SEND_FINISH)
        intentFilter.addAction(TCPServerService.PROGRESS_UPDATE)
        intentFilter.addAction(TCPServerService.RECEIVE_FINISH)
        Local_Broadcast_Manager.registerReceiver(receiver, intentFilter)
    }

    //load menu bar
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return true
    }

    //button monitoring for transferring file
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item?.itemId) {
            android.R.id.home -> quit()
            R.id.action_file_send -> chooseFile()
        }
        return super.onOptionsItemSelected(item)
    }
    //quit
    private fun quit() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
    //select file
    private fun chooseFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(intent, 1)
    }

    //get the path of the file to send
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode==1){
            val uri: Uri? = data?.data
            path = getPath(this, uri)
            Log.e("ChatActivity", "path: $path")
            val file = File(path)
            Log.e("ChatActivity", "file: $file, file size: ${file.length()}")
            send("#broadcast#file#size:${file.length()}#name:${file.name}#")
            size = file.length()
            path =  data?.dataString
            if(!connected){
                Toast.makeText(this, "Please connect to the device first", Toast.LENGTH_SHORT).show()
            }
        }
    }
}