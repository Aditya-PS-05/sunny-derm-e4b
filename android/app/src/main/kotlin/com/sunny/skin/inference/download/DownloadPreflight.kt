package com.sunny.skin.inference.download

import android.app.ActivityManager
import android.content.Context

/** Result of the pre-download device check. */
sealed interface Preflight {
    data object Ok : Preflight
    data class Blocked(val reason: String) : Preflight
}

/**
 * Guards the ~6 GB model install: refuses to start unless there is enough free
 * internal storage (with headroom) and enough RAM to actually load a 5 GB model.
 */
object DownloadPreflight {
    private const val MIN_RAM_BYTES = 5_500_000_000L

    fun check(context: Context): Preflight {
        val need = (ModelAsset.totalBytes * 1.1).toLong()
        val free = context.filesDir.usableSpace
        if (free < need) {
            return Preflight.Blocked(
                "Not enough storage — the model needs about ${gb(need)} free, " +
                    "but only ${gb(free)} is available.",
            )
        }
        val am = context.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        if (mi.totalMem in 1 until MIN_RAM_BYTES) {
            return Preflight.Blocked(
                "This device has about ${gb(mi.totalMem)} of RAM. The model needs ~6 GB " +
                    "and would not run reliably here.",
            )
        }
        return Preflight.Ok
    }

    private fun gb(bytes: Long): String = "%.1f GB".format(bytes / 1_000_000_000.0)
}
