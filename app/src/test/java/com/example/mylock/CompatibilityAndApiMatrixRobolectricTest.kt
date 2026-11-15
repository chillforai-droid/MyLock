package com.example.mylock

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.mylock.compatibility.AndroidVersionCompatibilityMatrix
import com.example.mylock.compatibility.DeviceCompatibilityInspector
import com.example.mylock.oem.OemRegistrationResult
import com.example.mylock.oem.StandardSafeCompatibilityBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class CompatibilityAndApiMatrixRobolectricTest {

    @Test
    fun `compatibility matrix covers every Android API level from 26 through 36`() {
        val matrix = AndroidVersionCompatibilityMatrix.getFullMatrix()
        val apiLevels = matrix.map { it.apiLevel }
        assertEquals((26..36).toList(), apiLevels)
        assertTrue(matrix.all { it.restrictedCapabilities.isNotBlank() && it.requiredSigningLevel.isNotBlank() })
    }

    @Test
    @Config(sdk = [26, 28, 30, 33, 34, 35, 36])
    fun `standard app honors Keyguard isolation and reports system unlock unsupported across API 26 to 36`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bridge = StandardSafeCompatibilityBridge(context)
        val inspector = DeviceCompatibilityInspector(context, bridge)

        val report = inspector.inspect()
        assertEquals(Build.VERSION.SDK_INT, report.apiLevel)
        assertFalse(report.systemIntegrationStatus.systemKeyguardUnlockSupported)
        assertTrue(report.systemIntegrationStatus.compatibilityModeActive)

        val regResult = bridge.requestAuthorizedSystemRegistration("p1", byteArrayOf(1, 2, 3))
        assertTrue(regResult is OemRegistrationResult.Unsupported)

        // Verify offline-only guarantee: app manifest must NOT declare INTERNET permission
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requestedPerms = pkgInfo.requestedPermissions?.toList().orEmpty()
        assertFalse(requestedPerms.contains(Manifest.permission.INTERNET))
        assertTrue(requestedPerms.contains(Manifest.permission.RECORD_AUDIO))
        assertTrue(requestedPerms.contains(Manifest.permission.USE_BIOMETRIC))
    }
}
