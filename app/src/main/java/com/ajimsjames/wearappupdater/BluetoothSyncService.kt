package com.ajimsjames.wearappupdater

import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class BluetoothSyncService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "BluetoothSyncService"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        val sourceNodeId = messageEvent.sourceNodeId

        when (path) {
            "/request_dir_list" -> {
                val dirPath = String(messageEvent.data, Charsets.UTF_8)
                serviceScope.launch {
                    val fileListJson = listDirectory(dirPath)
                    try {
                        Tasks.await(
                            Wearable.getMessageClient(this@BluetoothSyncService)
                                .sendMessage(sourceNodeId, "/files_response", fileListJson.toByteArray(Charsets.UTF_8))
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed response send", e)
                    }
                }
            }
            "/delete_file" -> {
                val filePath = String(messageEvent.data, Charsets.UTF_8)
                val success = File(filePath).deleteRecursively()
                val response = if (success) "SUCCESS" else "FAILED"
                Wearable.getMessageClient(this)
                    .sendMessage(sourceNodeId, "/delete_response", response.toByteArray(Charsets.UTF_8))
            }
            "/create_folder" -> {
                val folderPath = String(messageEvent.data, Charsets.UTF_8)
                val success = File(folderPath).mkdirs()
                val response = if (success) "SUCCESS" else "FAILED"
                Wearable.getMessageClient(this)
                    .sendMessage(sourceNodeId, "/create_response", response.toByteArray(Charsets.UTF_8))
            }
        }
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        val path = channel.path
        if (path == "/apk_install_channel" || path == "/upload_file_channel") {
            serviceScope.launch {
                receiveFileFromChannel(channel)
            }
        }
    }

    private suspend fun receiveFileFromChannel(channel: ChannelClient.Channel) {
        val channelClient = Wearable.getChannelClient(this)
        var inputStream: InputStream? = null
        try {
            inputStream = Tasks.await(channelClient.getInputStream(channel))
            val isApk = channel.path == "/apk_install_channel"
            
            // Protocol header: 4-byte name length + UTF-8 name
            val nameLengthBytes = ByteArray(4)
            var bytesRead = inputStream.read(nameLengthBytes)
            if (bytesRead != 4) return
            val nameLength = java.nio.ByteBuffer.wrap(nameLengthBytes).int
            
            val nameBytes = ByteArray(nameLength)
            bytesRead = inputStream.read(nameBytes)
            if (bytesRead != nameLength) return
            val filename = String(nameBytes, Charsets.UTF_8)
            
            val targetFile = if (isApk) {
                File(cacheDir, filename)
            } else {
                File(filename)
            }
            
            targetFile.parentFile?.mkdirs()
            
            val outputStream = FileOutputStream(targetFile)
            val buffer = ByteArray(16384)
            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) break
                outputStream.write(buffer, 0, read)
            }
            outputStream.flush()
            outputStream.close()
            
            if (isApk) {
                launchInstaller(targetFile)
            }
            
            Tasks.await(channelClient.close(channel))
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving file from channel", e)
            inputStream?.close()
        }
    }

    private fun launchInstaller(file: File) {
        try {
            val authority = "$packageName.fileprovider"
            val apkUri = FileProvider.getUriForFile(this, authority, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
        }
    }

    private fun listDirectory(path: String): String {
        val root = File(path)
        val filesJson = JSONArray()
        
        if (root.exists() && root.isDirectory) {
            val list = root.listFiles()
            if (list != null) {
                for (file in list) {
                    val item = JSONObject()
                    item.put("name", file.name)
                    item.put("isDirectory", file.isDirectory)
                    item.put("size", file.length())
                    filesJson.put(item)
                }
            }
        }
        return filesJson.toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
