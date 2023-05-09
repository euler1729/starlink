package com.example.starlink.services

import android.app.IntentService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.starlink.services.TCPSendService.Companion.NOW_PROGRESS
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket
import java.net.Socket

class TCPServerService : IntentService("TCPServerService") {
    companion object {
        const val PORT: String = "tcp_server_service_port"
        const val FILE_URL: String = "tcp_server_service_file_url"
        const val LENGTH_PROGRESS: String = "tcp_server_service_length_progress"
        const val PROGRESS_UPDATE: String = "tcp_server_service_progress_update"
        const val RECEIVE_FINISH: String = "tcp_server_service_receive_finish"
        const val SHUTDOWN: String = "tcp_server_service_receive_shutdown"
    }

    private val Local_Broadcast_Manager = LocalBroadcastManager.getInstance(this)
    private val receiver = TCPServerReceiver()
    private lateinit var serverSocket: ServerSocket
    private lateinit var connect: Socket

    @Deprecated("Deprecated in Java", ReplaceWith("TODO(\"Not yet implemented\")"))
    override fun onHandleIntent(intent: Intent?) {
        val port: Int = intent?.getIntExtra(PORT, 11697) ?: 11697
        val fileUrl: String = intent?.getStringExtra(FILE_URL) ?: ""
        val fileSize: Long = intent?.getLongExtra(LENGTH_PROGRESS, 100) ?: 100
        val intentFilter = IntentFilter(SHUTDOWN)
        Local_Broadcast_Manager.registerReceiver(receiver, intentFilter)

        try {
            serverSocket = ServerSocket(port)
            serverSocket.soTimeout = 3000
            //Connect socket
            connect = serverSocket.accept()
            Log.e("TCP Server", "TCP Server has been successfully started")
            Log.e("TCP Server", "The path is $fileUrl")
            //open a file
            val file = File(fileUrl)
            val dirs = file.parent?.let { File(it) }
            Log.e("TCP Server", "The file name being received is ${file.name}")
            if (!dirs?.exists()!!) {
                dirs.mkdirs()
                Log.e("TCP Server", "Create Download directory")
            }
            //Create a file
            if(file.createNewFile()){
                Log.e("TCP Server", "Create file success $fileUrl")
            }else{
                Log.e("TCP Server", "Create file failed $fileUrl")
            }
            // Getting the input stream
            val inputStream = connect.getInputStream()
            val fileOutputStream = FileOutputStream(file)
            val buffer = ByteArray(10240)
            var len: Int = 0
            var total: Long = 0
            while (inputStream.read(buffer).also { len = it } != -1) {
                fileOutputStream.write(buffer, 0, len)
                total += len
                updateProgress(total, fileSize)
            }
            fileOutputStream.close()
            inputStream.close()
            serverSocket.close()
            receiveFinish()
        } catch (exp: Exception) {
            exp.printStackTrace()
        }finally {
            Log.e("TCP Server", "TCP Server has been closed")
        }
        //unregister receiver
        Local_Broadcast_Manager.unregisterReceiver(receiver)
    }


    //Close TCP Server
    inner class TCPServerReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                SHUTDOWN -> {
                    serverSocket.takeIf { !it.isClosed }?.close()
                    connect.takeIf { it.isConnected }?.close()
                }
            }
        }
    }

    // update progress bar
    private fun updateProgress(now: Long, length: Long) {
        Log.e("TCP Server", "now: $now, length: $length")
        val intent = Intent(PROGRESS_UPDATE)
        intent.putExtra(NOW_PROGRESS, now)
        intent.putExtra(LENGTH_PROGRESS, length)
        Local_Broadcast_Manager.sendBroadcast(intent)
    }

    // File receive success notification
    private fun receiveFinish() {
        Log.e("TCP Server", "receive finish")
        val intent = Intent(RECEIVE_FINISH)
        Local_Broadcast_Manager.sendBroadcast(intent)
    }
}