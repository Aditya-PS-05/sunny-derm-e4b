package com.sunny.skin.data

import android.content.Context
import android.net.Uri
import java.io.File

/** App-private storage for password-encrypted backup archives. */
class BackupStore(private val context: Context) {
    private val dir = File(context.filesDir, "encrypted_backups").apply { mkdirs() }

    fun create(name: String): File {
        require(name.matches(Regex("[A-Za-z0-9._-]+"))) { "invalid backup name" }
        return File(dir, name)
    }

    fun file(name: String): File {
        require(name.matches(Regex("[A-Za-z0-9._-]+"))) { "invalid backup name" }
        return File(dir, name)
    }

    fun shareUri(file: File): Uri = Uri.Builder()
        .scheme("content")
        .authority("${context.packageName}.reports")
        .appendPath("backups")
        .appendPath(file.name)
        .build()

    fun deleteAll() {
        runCatching { dir.deleteRecursively() }
        dir.mkdirs()
    }
}
