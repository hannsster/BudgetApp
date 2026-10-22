package com.mipresupuestocr.app.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

enum class InstallOutcome {
    /** Android's package installer was opened; the user still sees the confirm screen. */
    LAUNCHED,

    /** The app may not install packages yet; the relevant settings screen was opened. */
    NEEDS_PERMISSION,

    /** The cached APK is gone, for example after the system cleared the cache. */
    MISSING_FILE,

    /** No installer app handled the request. */
    UNAVAILABLE,
}

/**
 * Hands a downloaded APK to Android's own installer.
 *
 * This is a normal update: same package name, and because the data directory is untouched, budgets
 * and holdings survive. Android only accepts it when both APKs share a signing key, so releases must
 * be signed with the project's own keystore rather than a throwaway debug key.
 */
fun installApk(context: Context, apkPath: String): InstallOutcome {
    val file = File(apkPath)
    if (!file.exists() || file.length() == 0L) return InstallOutcome.MISSING_FILE

    if (!context.packageManager.canRequestPackageInstalls()) {
        val settings = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(settings)
            InstallOutcome.NEEDS_PERMISSION
        }.getOrDefault(InstallOutcome.UNAVAILABLE)
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    return runCatching {
        context.startActivity(intent)
        InstallOutcome.LAUNCHED
    }.getOrDefault(InstallOutcome.UNAVAILABLE)
}

/** Opens a browser page, used for the release notes when installing is not possible. */
fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
