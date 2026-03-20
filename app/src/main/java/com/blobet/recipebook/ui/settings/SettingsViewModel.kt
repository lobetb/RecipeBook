package com.blobet.recipebook.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blobet.recipebook.R
import com.blobet.recipebook.data.local.RecipeDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

data class SettingsUiState(
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val canRestartApp: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: RecipeDatabase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun backupDatabaseToUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackingUp = true, message = null, canRestartApp = false) }

            try {
                withContext(Dispatchers.IO) {
                    val sqliteDb = database.openHelper.writableDatabase
                    sqliteDb.query("PRAGMA wal_checkpoint(FULL)").close()

                    val dbFile = context.getDatabasePath(DB_NAME)
                    if (!dbFile.exists()) {
                        throw IllegalStateException(context.getString(R.string.backup_db_not_found))
                    }

                    val walFile = context.getDatabasePath("$DB_NAME-wal")
                    val shmFile = context.getDatabasePath("$DB_NAME-shm")

                    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        ZipOutputStream(output).use { zipOutput ->
                            addFileToZip(zipOutput, dbFile, DB_NAME)
                            if (walFile.exists()) {
                                addFileToZip(zipOutput, walFile, "$DB_NAME-wal")
                            }
                            if (shmFile.exists()) {
                                addFileToZip(zipOutput, shmFile, "$DB_NAME-shm")
                            }
                        }
                    } ?: throw IllegalStateException(context.getString(R.string.backup_cannot_open_destination))
                }

                _uiState.update { it.copy(isBackingUp = false, message = context.getString(R.string.backup_success)) }
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                _uiState.update {
                    it.copy(
                        isBackingUp = false,
                        message = context.getString(R.string.backup_failed, reason),
                    )
                }
            }
        }
    }

    fun restoreDatabaseFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true, message = null, canRestartApp = false) }

            try {
                withContext(Dispatchers.IO) {
                    database.close()

                    val dbFile = context.getDatabasePath(DB_NAME)
                    val dbParent = dbFile.parentFile
                    if (dbParent != null && !dbParent.exists()) {
                        dbParent.mkdirs()
                    }

                    val walFile = context.getDatabasePath("$DB_NAME-wal")
                    val shmFile = context.getDatabasePath("$DB_NAME-shm")
                    if (walFile.exists()) walFile.delete()
                    if (shmFile.exists()) shmFile.delete()

                    val mimeType = context.contentResolver.getType(uri).orEmpty()
                    val isZip = mimeType.contains("zip", ignoreCase = true) ||
                        uri.toString().lowercase().endsWith(".zip")

                    if (isZip) {
                        var restoredMainDb = false

                        context.contentResolver.openInputStream(uri)?.use { input ->
                            ZipInputStream(input).use { zipInput ->
                                while (true) {
                                    val entry = zipInput.nextEntry ?: break
                                    when (entry.name) {
                                        DB_NAME -> {
                                            FileOutputStream(dbFile, false).use { output ->
                                                zipInput.copyTo(output)
                                            }
                                            restoredMainDb = true
                                        }

                                        "$DB_NAME-wal" -> {
                                            FileOutputStream(walFile, false).use { output ->
                                                zipInput.copyTo(output)
                                            }
                                        }

                                        "$DB_NAME-shm" -> {
                                            FileOutputStream(shmFile, false).use { output ->
                                                zipInput.copyTo(output)
                                            }
                                        }
                                    }
                                    zipInput.closeEntry()
                                }
                            }
                        } ?: throw IllegalStateException(context.getString(R.string.restore_cannot_open_source))

                        check(restoredMainDb) {
                            context.getString(R.string.restore_missing_db_entry)
                        }
                    } else {
                        // Legacy single-file .db backup support
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(dbFile, false).use { output ->
                                input.copyTo(output)
                            }
                        } ?: throw IllegalStateException(context.getString(R.string.restore_cannot_open_source))
                    }
                }

                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        canRestartApp = true,
                        message = context.getString(R.string.restore_success_restart_required),
                    )
                }
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        message = context.getString(R.string.restore_failed, reason),
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun clearRestartFlag() {
        _uiState.update { it.copy(canRestartApp = false) }
    }

    companion object {
        private const val DB_NAME = "recipe_database"
    }

    private fun addFileToZip(zipOutput: ZipOutputStream, file: java.io.File, entryName: String) {
        zipOutput.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { input ->
            input.copyTo(zipOutput)
        }
        zipOutput.closeEntry()
    }
}
