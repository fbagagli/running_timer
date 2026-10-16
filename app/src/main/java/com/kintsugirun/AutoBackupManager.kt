package com.kintsugirun

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutoBackupManager(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    preferences: SharedPreferences? = null
) {
    companion object {
        const val PREFS_NAME = "kintsugi_prefs"
        const val KEY_AUTO_BACKUP_URI = "key_auto_backup_uri"
        const val BACKUP_FILE_NAME = "kintsugirun_auto_backup.json"
        private const val TAG = "AutoBackupManager"
    }

    private val prefs: SharedPreferences by lazy {
        preferences ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAutoBackupEnabled(): Boolean {
        val uriString = prefs.getString(KEY_AUTO_BACKUP_URI, null)
        return !uriString.isNullOrBlank()
    }

    fun getAutoBackupUri(): Uri? {
        val uriString = prefs.getString(KEY_AUTO_BACKUP_URI, null)
        return if (!uriString.isNullOrBlank()) {
            try {
                Uri.parse(uriString)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse stored auto-backup URI: $uriString", e)
                null
            }
        } else {
            null
        }
    }

    fun setAutoBackupUri(uri: Uri?) {
        prefs.edit().apply {
            if (uri != null) {
                putString(KEY_AUTO_BACKUP_URI, uri.toString())
            } else {
                remove(KEY_AUTO_BACKUP_URI)
            }
            apply()
        }
    }

    suspend fun writeBackup(jsonContent: String): Boolean = withContext(ioDispatcher) {
        val treeUri = getAutoBackupUri() ?: return@withContext false
        try {
            val treeDocument = DocumentFile.fromTreeUri(context, treeUri)
            if (treeDocument == null || !treeDocument.canWrite()) {
                Log.w(TAG, "Tree document is null or not writable: $treeUri")
                return@withContext false
            }

            val targetFile = treeDocument.findFile(BACKUP_FILE_NAME)
                ?: treeDocument.createFile("application/json", BACKUP_FILE_NAME)

            if (targetFile == null) {
                Log.e(TAG, "Failed to find or create backup file: $BACKUP_FILE_NAME")
                return@withContext false
            }

            context.contentResolver.openOutputStream(targetFile.uri, "wt")?.use { outputStream ->
                outputStream.bufferedWriter().use { writer ->
                    writer.write(jsonContent)
                }
                Log.d(TAG, "Auto-backup successfully written to ${targetFile.uri}")
                true
            } ?: run {
                Log.e(TAG, "Could not open output stream for ${targetFile.uri}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing auto-backup to folder: $treeUri", e)
            false
        }
    }
}
