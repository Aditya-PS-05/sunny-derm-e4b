package com.sunny.skin.inference.download

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import android.os.storage.StorageManager

/** Result of the pre-download device check. */
sealed interface Preflight {
    data object Ok : Preflight
    data class Blocked(val reason: String) : Preflight
}

/**
 * Refuses a pack unless there is enough internal storage (including download
 * headroom) and enough RAM to run that pack's runtime reliably.
 */
object DownloadPreflight {
    fun check(context: Context, pack: ModelPack): Preflight {
        val need = (pack.totalBytes * 1.1).toLong()
        val storage = context.getSystemService(StorageManager::class.java)
        val free = runCatching {
            storage?.getAllocatableBytes(StorageManager.UUID_DEFAULT)
                ?: StatFs(context.filesDir.absolutePath).availableBytes
        }.getOrElse { StatFs(context.filesDir.absolutePath).availableBytes }
        if (free < need) {
            return Preflight.Blocked(
                "Not enough storage — the model needs about ${gb(need)} free, " +
                    "but only ${gb(free)} is available.",
            )
        }
        val am = context.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        if (mi.totalMem in 1 until pack.minimumRamBytes) {
            return Preflight.Blocked(
                "This device has about ${gb(mi.totalMem)} of RAM. ${pack.tier.displayName} needs " +
                    "about ${gb(pack.minimumRamBytes)} " +
                    "and would not run reliably here.",
            )
        }
        return Preflight.Ok
    }

    private fun gb(bytes: Long): String = "%.1f GB".format(bytes / 1_000_000_000.0)
}
