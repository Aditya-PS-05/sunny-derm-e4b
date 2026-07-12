package com.sunny.skin.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens the device's maps app to a "dermatologist near you" search. This is the
 * app's bridge to care: Sunny doesn't diagnose, so it helps the user reach
 * someone who can. Only a location query leaves the device — never a photo or any
 * health data — so the "photos never leave this phone" promise is preserved.
 * Falls back to a web maps URL when no maps app can handle the geo intent (the
 * try/catch avoids needing a package-visibility <queries> entry).
 */
fun findDermatologistNearby(context: Context) {
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=dermatologist near me"))
    val web = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/maps/search/dermatologist+near+me"),
    )
    runCatching { context.startActivity(geo) }
        .onFailure { runCatching { context.startActivity(web) } }
}
