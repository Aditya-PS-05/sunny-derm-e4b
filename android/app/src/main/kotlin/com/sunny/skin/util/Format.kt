package com.sunny.skin.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Date/time formatting matching the reference UI ("23 Feb 2026", "3:42 pm"). */
object Format {
    fun date(ts: Long): String =
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ts))

    fun time(ts: Long): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))
            .lowercase(Locale.getDefault())

    /**
     * e.g. "SUN-20260223-143052871" — a stable, on-device report identifier
     * unique to the millisecond, so two reports made in the same day (or the
     * same 10-second window) never collide and overwrite each other's PDF.
     */
    fun reportId(ts: Long): String =
        "SUN-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ts))}-" +
            SimpleDateFormat("HHmmssSSS", Locale.US).format(Date(ts))
}
