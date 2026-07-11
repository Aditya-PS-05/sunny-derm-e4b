package com.sunny.skin.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Date/time formatting matching the reference UI ("23 Feb 2026", "3:42 pm"). */
object Format {
    private val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    private val time = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val reportDate = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val reportTime = SimpleDateFormat("HHmmssSSS", Locale.US)

    fun date(ts: Long): String = date.format(Date(ts))
    fun time(ts: Long): String = time.format(Date(ts)).lowercase(Locale.getDefault())

    /**
     * e.g. "SUN-20260223-143052871" — a stable, on-device report identifier
     * unique to the millisecond, so two reports made in the same day (or the
     * same 10-second window) never collide and overwrite each other's PDF.
     */
    fun reportId(ts: Long): String =
        "SUN-${reportDate.format(Date(ts))}-${reportTime.format(Date(ts))}"
}
