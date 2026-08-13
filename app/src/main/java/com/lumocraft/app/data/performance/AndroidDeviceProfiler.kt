package com.lumocraft.app.data.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.lumocraft.app.domain.performance.DeviceProfile
import com.lumocraft.app.domain.performance.DeviceProfiler
import com.lumocraft.app.domain.runtime.RuntimeArchitecture

/**
 * Detects the device profile from the Android platform: total RAM via
 * [ActivityManager.MemoryInfo], CPU cores via [Runtime], Android version
 * via [Build] and the low-RAM device flag. Detected once, then cached —
 * no repeated system scans.
 */
class AndroidDeviceProfiler(context: Context) : DeviceProfiler {

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    @Volatile
    private var detected: DeviceProfile? = null

    override fun detect(): DeviceProfile = detected ?: run {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val profile = DeviceProfile(
            architecture = detectArchitecture(),
            totalRamMB = memoryInfo.totalMem / (1024 * 1024),
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            androidSdk = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE,
            lowRamDevice = memoryInfo.totalMem / (1024 * 1024) < LOW_RAM_MB
        )
        detected = profile
        profile
    }

    private fun detectArchitecture(): RuntimeArchitecture {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return RuntimeArchitecture.fromAbi(abi) ?: when {
            abi.contains("arm64") || abi.contains("aarch64") -> RuntimeArchitecture.ARM64_V8A
            abi.contains("v7a") || abi.contains("armeabi") -> RuntimeArchitecture.ARMEABI_V7A
            else -> RuntimeArchitecture.X86_64
        }
    }

    private companion object {
        const val LOW_RAM_MB = 2048L
    }
}