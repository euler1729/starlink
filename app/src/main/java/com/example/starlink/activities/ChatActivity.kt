package com.example.starlink.activities

import android.annotation.SuppressLint
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.support.v7.app.AlertDialog
import android.support.v7.widget.AppCompatButton
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import com.example.starlink.services.SendService
import com.example.starlink.services.ServerService
import com.example.starlink.services.TCPSendService
import com.example.starlink.services.TCPServerService
import java.io.File
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.content.ContentUris
import android.database.Cursor
import android.graphics.BitmapFactory
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.widget.ImageButton
import com.example.starlink.R
import com.example.starlink.adapters.ChatAdapter
import com.example.starlink.dataclass.ChatMessage
import com.example.starlink.dataclass.DeviceInfo


class ChatActivity : BaseActivity() {

    private var path: String? = null
    private var size: Long? = null
    private var connected = false
    private var messageList: MutableList<ChatMessage> = mutableListOf()
    private lateinit var adapter: ChatAdapter
    private var recyclerView: RecyclerView? = null
    private var targetName = "default"

    private val receiver = ChatReceiver()

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        connected = false
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        //load component
        val sendButton: ImageButton = findViewById(R.id.send_button)
//        val icon = ContextCompat.getDrawable(this, R.drawable.ic_menu_send)
//        sendButton.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
        val toolbar: Toolbar = findViewById(R.id.toolbar_chat)
        val editText: EditText = findViewById(R.id.edit_text)
        recyclerView = findViewById(R.id.recycler_chat)

        setSupportActionBar(toolbar)

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
        //Click the send button to send the data
        sendButton.setOnClickListener {
            if (editText.text.toString() != "") {
                send(editText.text.toString())
                editText.setText("")
            }
        }

        recyclerView!!.layoutManager = LinearLayoutManager(this)
        //Set the corresponding IP address
        deviceAddress = intent.getStringExtra("deviceAddress").toString()
        //Get the corresponding chat records according to the device IP
        getList(deviceAddress)
        adapter = ChatAdapter(messageList)
        recyclerView!!.adapter = adapter
        if (MainActivity.debugMode == 1) {
            messageList.add(ChatMessage("Hello", ChatMessage.TYPE_RECEIVED))
            messageList.add(ChatMessage("I'm fine. Thank you. And you?", ChatMessage.TYPE_SEND))
            adapter.notifyDataSetChanged()
        }

        //set title
        setTitle(false)
        isConnected(deviceAddress)

        //Receive file transfers when opening an activity
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

    //Get the chat history of the corresponding device
    private fun getList(address: String) {
        for (de in deviceList) {
            if (de.address == address) {
                messageList = de.chatList
                targetName = de.name
            }
        }
    }

    //set title
    private fun setTitle(connect: Boolean) {
        supportActionBar?.run {
            title = if (targetName != "default") {
                targetName + if (connect) {
                    "(connected)"
                } else {
                    "(Not Connected)"
                }
            } else {
                deviceAddress + if (connect) {
                    "(connected)"
                } else {
                    "(Not connected)"
                }
            }
        }
    }

    //Determine whether the other party is online
    private fun isConnected(address: String) {
        for (device in deviceList) {
            if (device.address == address) {
                connected = true
                setTitle(true)
                device.deviceCnt = 0
            }
        }
    }

    //Receive files
    private fun receiveFile(size: Long, uri: String, name: String) {
        // TODO("Decryption here")
        Log.e("ChatActivity", "size:$size, name: $name")
        AlertDialog.Builder(this).setTitle("The other party wants to send you a file")
            .setIcon(android.R.drawable.sym_def_app_icon)
            .setMessage("file name$name\nsize is${getReadSize(size)}")
            .setPositiveButton("take over") { _, _ ->
                //Start the service of the TCP server
                startTcpServer(size, uri)
                //send acknowledgment
                send("#broadcast#file#confirm#")
            }.setNegativeButton("reject") { _, _ ->
                //send reject
                send("#broadcast#file#refuse#")
            }.show()
    }

    //Unregister broadcast receiver when activity is not visible
    override fun onPause() {
        super.onPause()
        Local_Broadcast_Manager.unregisterReceiver(receiver)
    }

    //Register broadcast receiver
    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
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

    //Button monitoring for transferring files
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item!!.itemId) {
            android.R.id.home -> quit()
            R.id.action_file_send -> chooseFile()
        }
        return super.onOptionsItemSelected(item)
    }

    //Get the path of the file to send
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1) {
            val uri: Uri? = data?.data
            path = getPath(this, uri)
            Log.e("ChatActivity", "Get the path of the file to send$path")
            val file = File(path)
            Log.e(
                "ChatActivity",
                "Transfer request sent，Waiting for confirmation，file name${file.name}, file size is${file.length()}"
            )
            send("#broadcast#file#size:${file.length()}#name:${file.name}#")
            size = file.length()
            path = data?.dataString
            if (!connected)
                Toast.makeText(this, "The other party is not online", Toast.LENGTH_LONG).show()
        }
    }

    //Corresponding processing when receiving information
    private fun receive(msgText: String, address: String) {

        //Judging whether it is special news
        if (Regex("#broadcast#").containsMatchIn(msgText)) {
            val name: String = Regex("(?<=#name:).*?(?=#)").find(msgText)?.value ?: "default"
            //Determine if someone is online
            when {
                Regex("#connect#").containsMatchIn(msgText) -> {
                    if (!hasDevice(address)) {
                        deviceList.add(DeviceInfo(name, address, mutableListOf(), 0))
                    }
                    if (address == deviceAddress) {
                        connected = true
                        setTitle(true)
                    }
                    sendToTarget("#broadcast#confirm#name:$deviceName", address)
                }

                Regex("#disconnect#").containsMatchIn(msgText) -> {
                    //Determine if someone is offline
                    for (de in deviceList) {
                        if (de.address == address) {
                            deviceList -= de
                            //If it is the person who is currently chatting, set it to not connected
                            if (address == deviceAddress) {
                                connected = false
                                setTitle(false)
                            }
                        }
                    }
                }

                Regex("#broadcast#confirm#").containsMatchIn(msgText) -> {
                    if (!hasDevice(address)) {
                        deviceList.add(DeviceInfo(name, address, mutableListOf(), 0))
                    }
                    if (address == deviceAddress) {
                        connected = true
                        setTitle(true)
                    }
                }

                Regex("#broadcast#file#").containsMatchIn(msgText) -> //If it is a file transfer request
                    when {
                        //The other party refuses to accept the document
                        Regex("#refuse#").containsMatchIn(msgText) -> {
                            Log.e("ChatActivity", "The other party refuses to accept")
                            Toast.makeText(
                                this,
                                "The other party refuses to accept",
                                Toast.LENGTH_LONG
                            ).show()
                            val intent = Intent(TCPSendService.SEND_SHUTDOWN)
                            Local_Broadcast_Manager.sendBroadcast(intent)
                        }
                        //received confirmation from the other party
                        Regex("#confirm#").containsMatchIn(msgText) -> {
                            Log.e(
                                "ChatActivity",
                                "Acknowledgment of receipt of transfer request，start file transfer"
                            )
                            fileTrans()
                        }
                        //transfer request received
                        else -> {
                            val fileSize: Long =
                                Regex("(?<=#size:).*?(?=#)").find(msgText)?.value?.toLong() ?: 0
                            val fileName: String =
                                Regex("(?<=#name:).*?(?=#)").find(msgText)?.value ?: "download"
                            val uri =
                                Environment.getExternalStorageDirectory().absolutePath + "/Download/" + fileName

                            receiveFile(fileSize, uri, fileName)
                        }
                    }
            }
        } else {
            if (address == deviceAddress) {
                messageList.add(ChatMessage(msgText, ChatMessage.TYPE_RECEIVED))
                adapter.notifyItemChanged(messageList.size - 1)
                recyclerView!!.scrollToPosition(messageList.size - 1)
            } else {
                for (de in deviceList) {
                    if (de.address == address) {
                        de.chatList.add(ChatMessage(msgText, ChatMessage.TYPE_RECEIVED))
                        de.deviceCnt++
                    }
                }
            }
        }
    }

    //Open the TCP server service
    private fun startTcpServer(size: Long, uri: String) {
        val intent = Intent(this, TCPServerService::class.java)
        intent.putExtra(TCPServerService.PORT, TCP_PORT)
        intent.putExtra(TCPServerService.LENGTH_PROGRESS, size)
        intent.putExtra(TCPServerService.FILE_URL, uri)
        startService(intent)
    }

    //Refresh messages sent in chat
    private fun refreshSendMsg(msgText: String) {
        if (!Regex("#broadcast#").containsMatchIn(msgText)) {
            //If connected, send normally
            messageList.run {
                if (connected)
                    add(ChatMessage(msgText, ChatMessage.TYPE_SEND))
                else
                    add(ChatMessage(msgText, ChatMessage.TYPE_WRONG))
            }
            adapter.notifyItemChanged(messageList.size - 1)
            recyclerView!!.scrollToPosition(messageList.size - 1)
        }
    }

    //Select a document
    private fun chooseFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(intent, 1)
    }

    //Start the TCP client service
    private fun fileTrans() {
        val intent = Intent(this, TCPSendService::class.java)
        intent.putExtra(TCPSendService.ADDRESS, deviceAddress)
        intent.putExtra(TCPSendService.URL, path)
        intent.putExtra(TCPSendService.PORT, TCP_PORT)
        intent.putExtra(TCPSendService.LENGTH_PROGRESS, size)
        startService(intent)

    }

    //quit
    private fun quit() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    //update progress bar
    private fun updateProgress(now: Long, length: Long, id: Int) {
        val progress: Int = ((now * 100) / length).toInt()
        val notification = NotificationCompat.Builder(this, "Progress")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setContentTitle("file transfer")
            .setContentText("$progress%")
            .setProgress(100, progress, false)
            .build()
        notification.flags = Notification.FLAG_ONGOING_EVENT
        Notification_Manager.notify(id, notification)
    }

    //Notifications received successfully
    private fun tranSucceed(id: Int) {
        Notification_Manager.cancel(id)
        val notification = NotificationCompat.Builder(this, "Progress")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setContentTitle("transfer files")
            .setContentText("completed")
            .build()
        Notification_Manager.notify(3, notification)
    }

    inner class ChatReceiver : BroadcastReceiver() {
        override fun onReceive(p0: Context?, intent: Intent?) {
            when (intent?.action) {
                //Receive UDP information
                ServerService.RECEIVE_MSG -> intent.getStringExtra(ServerService.FROM_ADDRESS)
                    ?.let {
                        receive(
                            intent.getStringExtra("msg")!!,
                            it
                        )
                    }
                //Send UDP successfully
                SendService.SEND_FINISH -> intent.getStringExtra("msg")?.let { refreshSendMsg(it) }
                //Send TCP successfully
                TCPSendService.SEND_FINISH -> tranSucceed(1)
                //Update the progress bar
                TCPSendService.PROGRESS_UPDATE ->
                    updateProgress(
                        intent.getLongExtra(TCPSendService.NOW_PROGRESS, 0),
                        intent.getLongExtra(TCPSendService.LENGTH_PROGRESS, 0), 1
                    )
                //Receive TCP complete
                TCPServerService.RECEIVE_FINISH -> tranSucceed(2)
                //Update the progress bar
                TCPServerService.PROGRESS_UPDATE ->
                    updateProgress(
                        intent.getLongExtra(TCPServerService.NOW_PROGRESS, 0),
                        intent.getLongExtra(TCPServerService.LENGTH_PROGRESS, 0), 2
                    )
            }
        }
    }

    companion object {
        @JvmStatic
        fun startThisActivity(
            context: Context,
            deviceAddress: String,
            readyToReceive: Boolean,
            msg: String
        ) {
            val intent = Intent(context, ChatActivity::class.java)
            intent.putExtra("msg", msg)
            intent.putExtra("readyToReceive", readyToReceive)
            intent.putExtra("deviceAddress", deviceAddress)
            context.startActivity(intent)
        }
    }

    //Determine whether the device is in the list
    private fun hasDevice(address: String): Boolean {
        var has = false
        for (de in deviceList) {
            if (address == de.address)
                has = true
        }
        if (address == localIP) {
            has = true
        }
        return has
    }

    private fun getReadSize(size: Long): String {
        var t = 0
        var temp = size
        while (temp >= 1024 && t < 3) {
            temp /= 1024
            t++
        }
        return when (t) {
            0 -> "${temp}B"
            1 -> "${temp}KB"
            2 -> "${temp}MB"
            3 -> "${temp}GB"
            else -> "${size}B"
        }

    }

    //uri-parsing
    private fun getPath(context: Context?, uri: Uri?): String? {
        if (context == null || uri == null)
            return null
        if (DocumentsContract.isDocumentUri(context, uri)
        ) {
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }
            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"),
                    java.lang.Long.valueOf(id)
                )
                return getDataColumn(context, contentUri, null, null)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val type = split[0]
                var contentUri: Uri? = null
                when (type) {
                    "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val selection = MediaStore.Images.Media._ID + "=?"
                val selectionArgs = arrayOf(split[1])
                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        } else if ("content".equals(uri.scheme!!, ignoreCase = true)) {
            return if (isGooglePhotosUri(uri)) uri.lastPathSegment
            else getDataColumn(context, uri, null, null)
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

    private fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
    }

    private fun getDataColumn(
        context: Context,
        uri: Uri?,
        selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        var cursor: Cursor? = null
        val column = MediaStore.Images.Media.DATA
        val projection = arrayOf(column)
        try {
            cursor =
                context.contentResolver.query(uri!!, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(index)
            }
        } finally {
            cursor!!.close()
        }
        return null
    }

}
