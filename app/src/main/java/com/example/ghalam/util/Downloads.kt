package com.example.ghalam.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.example.ghalam.data.Graph
import com.example.ghalam.data.RemoteFile

object Downloads {
    /** Queues the file in the system download manager (with the Bearer header) -> Downloads/Ghalam. */
    fun enqueue(ctx: Context, file: RemoteFile): Boolean {
        val token = Graph.store.token ?: return false
        val safe = file.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "file" }
        val req = DownloadManager.Request(Uri.parse(file.fullUrl))
            .addRequestHeader("Authorization", "Bearer $token")
            .addRequestHeader("Accept", "*/*")
            .setTitle(safe)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Ghalam/$safe")
        return runCatching {
            (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        }.isSuccess
    }

    /** Opens an http(s) link in the browser. */
    fun openLink(ctx: Context, url: String): Boolean {
        val u = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        return runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}
