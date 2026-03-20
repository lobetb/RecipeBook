package com.blobet.recipebook

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.appcompat.app.AppCompatActivity
import com.blobet.recipebook.ui.navigation.NavGraph
import com.blobet.recipebook.ui.theme.RecipeBookTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private var sharedText by mutableStateOf<String?>(null)
    private val urlRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)

        setContent {
            RecipeBookTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(sharedText = sharedText)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handleShareIntent(intent)
        }
    }

    private fun handleShareIntent(intent: Intent) {
        val action = intent.action

        if (action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = extractSharedText(intent)
            if (!text.isNullOrBlank()) {
                sharedText = text
            }
        } else if (action == Intent.ACTION_VIEW) {
            val data = intent.dataString
            if (!data.isNullOrBlank()) {
                sharedText = data
            }
        }
    }

    private fun extractSharedText(intent: Intent): String? {
        intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let { text ->
            if (text.isNotBlank()) return text
        }

        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
            if (text.isNotBlank()) return text
        }

        intent.clipData?.let { clipData ->
            for (i in 0 until clipData.itemCount) {
                val item = clipData.getItemAt(i)
                val rawText = item.text?.toString()
                if (!rawText.isNullOrBlank()) return rawText

                val asText = item.coerceToText(this)?.toString()
                if (!asText.isNullOrBlank()) return asText

                val asUri = item.uri?.toString()
                if (!asUri.isNullOrBlank()) return asUri

                val nestedIntentText = item.intent?.getStringExtra(Intent.EXTRA_TEXT)
                if (!nestedIntentText.isNullOrBlank()) return nestedIntentText
            }
        }

        intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()?.let { subject ->
            if (subject.isNotBlank()) return subject
        }

        intent.getStringExtra(Intent.EXTRA_SUBJECT)?.let { subject ->
            if (subject.isNotBlank()) return subject
        }

        intent.extras?.keySet()?.forEach { key ->
            val value = intent.extras?.get(key)
            if (value is CharSequence && value.isNotBlank()) {
                val text = value.toString()
                if (urlRegex.containsMatchIn(text)) return text
            }
        }

        intent.toUri(0).let { serialized ->
            if (urlRegex.containsMatchIn(serialized)) {
                return urlRegex.find(serialized)?.value
            }
        }

        return intent.dataString
    }
}
