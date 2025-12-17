package com.pbrp.manager

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import java.io.DataOutputStream
import android.content.Intent

object RootUtils {
    fun isRooted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c ls")
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun execute(command: String): Boolean {
        return try {
            val p = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(p.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            p.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}

object DownloadUtils {
    fun startDownload(context: Context, url: String, fileName: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription("Downloading PBRP...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(context, "Download started!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Opening Browser...", Toast.LENGTH_SHORT).show()
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}
