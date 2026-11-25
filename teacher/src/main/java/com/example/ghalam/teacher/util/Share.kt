package com.example.ghalam.teacher.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Copies [text] to the clipboard (the teacher then pastes it into Shad / Eitaa / Bale ...). */
fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
    Toast.makeText(context, "کپی شد", Toast.LENGTH_SHORT).show()
}

/** The system share sheet with [text]. */
fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    runCatching {
        context.startActivity(Intent.createChooser(send, "ارسال با…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
