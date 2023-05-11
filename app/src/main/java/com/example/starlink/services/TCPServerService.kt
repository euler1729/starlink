package com.example.starlink.services

import android.app.IntentService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.Exception
import java.net.ServerSocket
import java.net.Socket
import java.security.Key
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class TCPServerService : IntentService("TCPServerService") {
    companion object{
        const val PORT: String = "tcp_server_service_port"
        const val FILE_URL: String = "tcp_server_service_file_url"
        const val LENGTH_PROGRESS: String = "tcp_server_service_length_progress"
        const val NOW_PROGRESS: String = "tcp_server_service_now_progress"
        const val PROGRESS_UPDATE: String = "tcp_server_service_progress_update"
        const val RECEIVE_FINISH: String = "tcp_server_service_receive_finish"
        const val SHUTDOWN: String = "tcp_server_service_receive_shutdown"
    }

    private val Local_Broadcast_Manager = LocalBroadcastManager.getInstance(this)
    private val receiver = TcpServerReceiver()
    private lateinit var serverSocket: ServerSocket
    private lateinit var connect: Socket

    @Deprecated("Deprecated in Java")
    override fun onHandleIntent(intent: Intent?) {
        val port: Int = intent?.getIntExtra(PORT, 12346) ?: 12346
        val path: String = intent?.getStringExtra(FILE_URL) ?: ""
        val fileSize: Long = intent?.getLongExtra(LENGTH_PROGRESS, 100) ?: 100
        val intentFilter = IntentFilter(SHUTDOWN)
        Local_Broadcast_Manager.registerReceiver(receiver, intentFilter)
        try {
            serverSocket = ServerSocket(port)
            serverSocket.soTimeout = 3000
            //Connect socket
            connect = serverSocket. accept()
            Log.d("TcpServerService", "TcpServer has been successfully started")
            Log.d("TcpServerService", "The path is $path")
            //open a file
            val f = File(path)
            val dirs = File(f.parent)
            Log.d("TcpServerService", "The file name being received is ${f.name}")
            if (!dirs. exists()){
                dirs.mkdirs()
                Log.d("TcpServerService", "Create Download directory")
            }
            //Create a file
            if (f.createNewFile()){
                Log.d("TcpServerService", "Successfully created file $path")
            }
            else{
                Log.d("TcpServerService", "Exists")
            }

            //Get the input stream
            val inStream: InputStream = connect.getInputStream()
            val fileOutputStream  = FileOutputStream(f)
            val buffer = ByteArray(10240)
            var len: Int = 0
            var total: Long = 0

            /******Decryption******/
            val key = "8y/A?D(G+KbPeShV".toByteArray()
            val keySpec = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val cipherInputStream = CipherInputStream(inStream, cipher)
            /******Decryption******/

            while (inStream.read(buffer).also { len = it } != -1 ){
                fileOutputStream.write(buffer, 0, len)
                total += len
                updateProgress(total, fileSize)
            }



            fileOutputStream.close()
            inStream.close()
            serverSocket.close()
            cipherInputStream.close()
            receiveFinish()
        }catch (e: Exception){
            e.printStackTrace()
        }finally {
            Log.e("TcpServerService", "TCP Server has been Closed")
        }
        Local_Broadcast_Manager.unregisterReceiver(receiver)
    }

    //update progress bar
    private fun updateProgress(now: Long, size: Long){
        Log.e("TcpSendService", "updating progress bar：$now / $size")
        val intent = Intent(PROGRESS_UPDATE)
        intent.putExtra(NOW_PROGRESS, now)
        intent.putExtra(LENGTH_PROGRESS, size)
        Local_Broadcast_Manager.sendBroadcast(intent)
    }

    //Notification of success received
    private fun receiveFinish(){
        Log.e("TcpServerService", "File received successfully")
        val intent = Intent(RECEIVE_FINISH)
        Local_Broadcast_Manager.sendBroadcast(intent)
    }

    //Close TCP server
    inner class TcpServerReceiver: BroadcastReceiver(){
        override fun onReceive(p0: Context?, intent: Intent?) {
            when (intent?.action){
                SHUTDOWN -> {
                    serverSocket.takeIf { !it.isClosed }?.close()
                    connect.takeIf { it.isConnected }?.close()
                }
            }
        }

    }
}
