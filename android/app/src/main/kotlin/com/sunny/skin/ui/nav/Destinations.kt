package com.sunny.skin.ui.nav

/** All navigation routes in one place. */
object Routes {
    const val OVERVIEW = "overview"
    const val SAVED = "saved"
    const val SETTINGS = "settings"

    const val CAPTURE = "capture"
    const val CAMERA = "camera"
    const val BODY_GUIDE = "body_guide"
    const val REVIEW = "review"
    const val GENERATE_REPORT = "generate_report"
    const val REPORTS = "reports"
    const val MODEL_SETUP = "model_setup"
    const val PRIVACY = "privacy"
    const val PIN_SETUP = "pin_setup/{change}"
    fun pinSetup(change: Boolean) = "pin_setup/$change"

    const val SCAN_DETAIL = "scan/{scanId}"
    fun scanDetail(scanId: String) = "scan/$scanId"

    const val EDIT_SCAN = "edit_scan/{scanId}"
    fun editScan(scanId: String) = "edit_scan/$scanId"

    const val COMPARE = "compare/{scanId}"
    fun compare(scanId: String) = "compare/$scanId"

    const val REPORT_DETAIL = "report/{reportId}"
    fun reportDetail(reportId: String) = "report/$reportId"

    /** The three destinations that show the bottom tab bar. */
    val topLevel = setOf(OVERVIEW, SAVED, SETTINGS)
}
