package com.blobet.recipebook.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.blobet.recipebook.R
import java.util.Locale

object BugReportUtils {
    private const val BUG_REPORT_EMAIL = "blobet.dev@gmail.com"

    fun openBugReportEmail(context: Context): Boolean {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName ?: "unknown"
        val versionCode = packageInfo.longVersionCode
        val subject = context.getString(R.string.report_bug_subject)
        val body = context.getString(
            R.string.report_bug_body_template,
            versionName,
            versionCode,
            android.os.Build.VERSION.RELEASE,
            android.os.Build.MANUFACTURER,
            android.os.Build.MODEL,
            Locale.getDefault().toLanguageTag(),
        )

        val intent = Intent(
            Intent.ACTION_SENDTO,
            Uri.parse("mailto:$BUG_REPORT_EMAIL")
        ).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            true
        } else {
            false
        }
    }
}
