package com.blobet.recipebook.ui.settings

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blobet.recipebook.R
import com.blobet.recipebook.data.local.RecipeDatabase
import com.blobet.recipebook.data.local.entity.toDomainModel
import com.blobet.recipebook.data.repository.RecipeRepository
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

enum class RestoreMode {
    REPLACE,
    MERGE,
}

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
    private val recipeRepository: RecipeRepository,
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

    fun restoreDatabaseFromUri(uri: Uri, mode: RestoreMode) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true, message = null, canRestartApp = false) }

            try {
                when (mode) {
                    RestoreMode.REPLACE -> {
                        withContext(Dispatchers.IO) { restoreDatabaseReplace(uri) }
                        _uiState.update {
                            it.copy(
                                isRestoring = false,
                                canRestartApp = true,
                                message = context.getString(R.string.restore_success_restart_required),
                            )
                        }
                    }
                    RestoreMode.MERGE -> {
                        val result = withContext(Dispatchers.IO) { mergeBackupIntoCurrentDatabase(uri) }
                        _uiState.update {
                            it.copy(
                                isRestoring = false,
                                canRestartApp = false,
                                message = context.getString(
                                    R.string.restore_merge_success,
                                    result.importedCount,
                                    result.skippedDuplicates,
                                ),
                            )
                        }
                    }
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

    private data class MergeResult(
        val importedCount: Int,
        val skippedDuplicates: Int,
    )

    private fun restoreDatabaseReplace(uri: Uri) {
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

    private suspend fun mergeBackupIntoCurrentDatabase(uri: Uri): MergeResult {
        val importedRecipes = loadRecipesFromBackup(uri)
        var imported = 0
        var skipped = 0

        for (recipe in importedRecipes) {
            val duplicate = recipeRepository.findDuplicateForImport(recipe.sourceUrl, recipe.title)
            if (duplicate != null) {
                skipped++
                continue
            }

            val importReadyRecipe = recipe.copy(
                id = 0L,
                tags = recipe.tags.map { it.copy(id = 0L) },
            )

            recipeRepository.saveRecipe(importReadyRecipe)
            imported++
        }

        return MergeResult(importedCount = imported, skippedDuplicates = skipped)
    }

    private suspend fun loadRecipesFromBackup(uri: Uri): List<com.blobet.recipebook.domain.model.Recipe> {
        val tempDir = java.io.File(context.cacheDir, "restore-import-${System.currentTimeMillis()}")
        tempDir.mkdirs()

        val backupDbFile = java.io.File(tempDir, DB_NAME)
        val backupWalFile = java.io.File(tempDir, "$DB_NAME-wal")
        val backupShmFile = java.io.File(tempDir, "$DB_NAME-shm")

        try {
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
                                    FileOutputStream(backupDbFile, false).use { output ->
                                        zipInput.copyTo(output)
                                    }
                                    restoredMainDb = true
                                }
                                "$DB_NAME-wal" -> {
                                    FileOutputStream(backupWalFile, false).use { output ->
                                        zipInput.copyTo(output)
                                    }
                                }
                                "$DB_NAME-shm" -> {
                                    FileOutputStream(backupShmFile, false).use { output ->
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
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(backupDbFile, false).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException(context.getString(R.string.restore_cannot_open_source))
            }

            val tempDbName = "backup-read-${System.currentTimeMillis()}"

            // Ensure any extracted WAL data is merged into the main DB file before Room copies it.
            if (backupDbFile.exists()) {
                val sqliteDb = SQLiteDatabase.openDatabase(
                    backupDbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE,
                )
                try {
                    sqliteDb.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { }
                } finally {
                    sqliteDb.close()
                }
            }

            val backupDb = Room.databaseBuilder(context, RecipeDatabase::class.java, tempDbName)
                .createFromFile(backupDbFile)
                .build()

            return try {
                backupDb.recipeDao().getAllRecipesOnce().map { it.toDomainModel() }
            } finally {
                backupDb.close()
                context.deleteDatabase(tempDbName)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun addFileToZip(zipOutput: ZipOutputStream, file: java.io.File, entryName: String) {
        zipOutput.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { input ->
            input.copyTo(zipOutput)
        }
        zipOutput.closeEntry()
    }
}
