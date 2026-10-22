package com.mipresupuestocr.app.ui.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The installer must never hand a missing or empty file to the system, and when the app is not yet
 * allowed to install packages it has to send the user to that setting instead of failing silently.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateInstallerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a missing apk is reported instead of installed`() {
        assertEquals(
            InstallOutcome.MISSING_FILE,
            installApk(context, File(context.cacheDir, "updates/nope.apk").absolutePath),
        )
    }

    @Test
    fun `an empty apk is reported instead of installed`() {
        val empty = File(context.cacheDir, "updates/empty.apk")
        empty.parentFile?.mkdirs()
        empty.writeBytes(ByteArray(0))

        assertEquals(InstallOutcome.MISSING_FILE, installApk(context, empty.absolutePath))
    }

    @Test
    fun `without install permission the settings screen is offered`() {
        val apk = File(context.cacheDir, "updates/ready.apk")
        apk.parentFile?.mkdirs()
        apk.writeBytes(ByteArray(1024))

        // Robolectric's default: the app may not install packages yet.
        assertEquals(InstallOutcome.NEEDS_PERMISSION, installApk(context, apk.absolutePath))
    }
}
