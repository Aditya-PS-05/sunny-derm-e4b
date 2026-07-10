package com.sunny.skin.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Date/time formatting matching the reference UI ("23 Feb 2026", "3:42 pm"). */
object Format {
    private val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    private val time = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val reportId = SimpleDateFormat("yyyyMMdd", Locale.US)

    fun date(ts: Long): String = date.format(Date(ts))
    fun time(ts: Long): String = time.format(Date(ts)).lowercase(Locale.getDefault())

    /** e.g. "SUN-20260223-2767" — a stable, on-device report identifier. */
    fun reportId(ts: Long): String {
        val suffix = (ts % 10000).toString().padStart(4, '0')
        return "SUN-${reportId.format(Date(ts))}-$suffix"
    }
}
