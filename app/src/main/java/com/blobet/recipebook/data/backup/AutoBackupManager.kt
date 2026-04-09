package com.blobet.recipebook.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.blobet.recipebook.data.local.RecipeDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: RecipeDatabase,
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun hasDirectoryConfigured(): Boolean = !prefs.getString(KEY_TREE_URI, null).isNullOrBlank()

    fun enableWithDirectory(treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs.edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_TREE_URI, treeUri.toString())
            .apply()
    }

    fun disable() {
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
    }

    fun runDailyAutoBackupIfNeeded() {
        if (!isEnabled()) return

        val treeUriString = prefs.getString(KEY_TREE_URI, null) ?: return
        val treeUri = try {
            Uri.parse(treeUriString)
        } catch (_: Exception) {
            return
        }

        val directory = DocumentFile.fromTreeUri(context, treeUri) ?: return
        if (!directory.exists() || !directory.isDirectory) return

        val latest = directory.findFile(LATEST_FILE_NAME)
        if (latest != null && isToday(latest.lastModified())) {
            return
        }

        try {
            rotateBackupFiles(directory)
            val out = directory.createFile("application/zip", LATEST_FILE_NAME)
                ?: throw IllegalStateException("Cannot create auto-backup file")

            context.contentResolver.openOutputStream(out.uri, "w")?.use { output ->
                writeDatabaseBackupToStream(output)
            } ?: throw IllegalStateException("Cannot open auto-backup destination")
        } catch (sec: SecurityException) {
            Log.w(TAG, "Auto-backup permission revoked, disabling", sec)
            disable()
        } catch (e: Exception) {
            Log.w(TAG, "Auto-backup failed", e)
        }
    }

    private fun rotateBackupFiles(directory: DocumentFile) {
        val previous = directory.findFile(PREVIOUS_FILE_NAME)
        previous?.delete()

        val latest = directory.findFile(LATEST_FILE_NAME) ?: return
        val renamed = latest.renameTo(PREVIOUS_FILE_NAME)
        if (!renamed) {
            val copied = copyDocument(directory, latest, PREVIOUS_FILE_NAME)
            if (copied) {
                latest.delete()
            }
        }
    }

    private fun copyDocument(directory: DocumentFile, source: DocumentFile, targetName: String): Boolean {
        return try {
            val target = directory.createFile("application/zip", targetName) ?: return false
            val input = context.contentResolver.openInputStream(source.uri) ?: return false
            val output = context.contentResolver.openOutputStream(target.uri, "w") ?: return false
            input.use { i -> output.use { o -> i.copyTo(o) } }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun writeDatabaseBackupToStream(output: OutputStream) {
        val sqliteDb = database.openHelper.writableDatabase
        sqliteDb.query("PRAGMA wal_checkpoint(FULL)").close()

        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            throw IllegalStateException("Database file not found")
        }
        val walFile = context.getDatabasePath("$DB_NAME-wal")
        val shmFile = context.getDatabasePath("$DB_NAME-shm")

        ZipOutputStream(output).use { zipOutput ->
            addFileToZip(zipOutput, dbFile, DB_NAME)
            if (walFile.exists()) addFileToZip(zipOutput, walFile, "$DB_NAME-wal")
            if (shmFile.exists()) addFileToZip(zipOutput, shmFile, "$DB_NAME-shm")
        }
    }

    private fun addFileToZip(zipOutput: ZipOutputStream, file: java.io.File, entryName: String) {
        zipOutput.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { input -> input.copyTo(zipOutput) }
        zipOutput.closeEntry()
    }

    private fun isToday(timestamp: Long): Boolean {
        if (timestamp <= 0L) return false
        val day = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault()).toLocalDate()
        return day == today
    }

    private companion object {
        private const val TAG = "AutoBackupManager"
        private const val PREFS_NAME = "auto_backup_prefs"
        private const val KEY_ENABLED = "auto_backup_enabled"
        private const val KEY_TREE_URI = "auto_backup_tree_uri"

        private const val DB_NAME = "recipe_database"
        private const val LATEST_FILE_NAME = "recipebook-autobackup-latest.zip"
        private const val PREVIOUS_FILE_NAME = "recipebook-autobackup-previous.zip"
    }
}
