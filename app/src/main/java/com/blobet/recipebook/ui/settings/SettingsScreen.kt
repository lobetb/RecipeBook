package com.blobet.recipebook.ui.settings

import android.content.Intent
import android.net.Uri
import kotlin.system.exitProcess
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.blobet.recipebook.R

private const val LANGUAGE_EN = "en"
private const val LANGUAGE_FR = "fr"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val currentTag = AppCompatDelegate.getApplicationLocales()[0]?.language ?: LANGUAGE_EN
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.backupDatabaseToUri(uri)
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreDatabaseFromUri(uri)
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_language_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FilterChip(
                    selected = currentTag == LANGUAGE_EN,
                    onClick = {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(LANGUAGE_EN))
                    },
                    label = { Text(stringResource(R.string.settings_english)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                FilterChip(
                    selected = currentTag == LANGUAGE_FR,
                    onClick = {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(LANGUAGE_FR))
                    },
                    label = { Text(stringResource(R.string.settings_french)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.settings_backup),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_backup_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        val filename = "recipebook-backup-${System.currentTimeMillis()}.zip"
                        backupLauncher.launch(filename)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isBackingUp && !uiState.isRestoring,
                ) {
                    Text(
                        if (uiState.isBackingUp) {
                            stringResource(R.string.backup_in_progress)
                        } else {
                            stringResource(R.string.settings_backup_to_drive)
                        }
                    )
                }

                Button(
                    onClick = {
                        restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-sqlite3", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isBackingUp && !uiState.isRestoring,
                ) {
                    Text(
                        if (uiState.isRestoring) {
                            stringResource(R.string.restore_in_progress)
                        } else {
                            stringResource(R.string.settings_restore_from_drive)
                        }
                    )
                }

                if (uiState.canRestartApp) {
                    Button(
                        onClick = {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                context.startActivity(launchIntent)
                                exitProcess(0)
                            } else {
                                viewModel.clearRestartFlag()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.restart_app_now))
                    }
                }

                Text(
                    text = stringResource(R.string.icon_credit_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.support_kofi_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/blobet"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.kofi_logo),
                        contentDescription = stringResource(R.string.support_kofi_message),
                        modifier = Modifier
                            .height(18.dp)
                            .width(56.dp),
                    )
                }
            }
        }
    }
}
