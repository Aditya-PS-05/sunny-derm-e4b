package com.sunny.skin.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sunny.skin.data.ImageStore
import java.util.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Change-detection scoring. Runs on encrypted photo files (produced via
 * [ImageStore.save]) so it exercises the real decrypt → align → score path.
 */
@RunWith(AndroidJUnit4::class)
class ChangeAnalysisTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val store = ImageStore(ctx)
    private val created = mutableListOf<String>()

    /** A textured (non-uniform) image so alignment/normalisation is well-defined. */
    private fun texturedPath(seed: Int): String {
        val w = 96; val h = 96
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val rnd = Random(seed.toLong())
        for (y in 0 until h) for (x in 0 until w) {
            val base = (x * 7 + y * 13 + seed * 60) % 256
            val n = rnd.nextInt(40)
            bmp.setPixel(x, y, Color.rgb((base + n) % 256, base % 256, 128))
        }
        return store.save(bmp).also { created.add(it) }
    }

    private fun cleanup() {
        created.forEach { store.delete(it) }
        created.clear()
    }

    @Test
    fun identicalPhotos_readStable() = runBlocking {
        val p = texturedPath(1)
        try {
            val r = ChangeAnalysis.compare(ctx, p, p, fieldsChanged = 0)
            assertNotNull(r)
            assertEquals(ChangeAnalysis.Level.STABLE, r!!.level)
            assertTrue("identical photos should score low, was ${r.score}", r.score < 20)
        } finally { cleanup() }
    }

    @Test
    fun manyChangedDescriptionFields_forceNotable() = runBlocking {
        val a = texturedPath(1)
        val b = texturedPath(2)
        try {
            val r = ChangeAnalysis.compare(ctx, a, b, fieldsChanged = 4)
            assertNotNull(r)
            // fieldsChanged >= 3 must classify as NOTABLE regardless of pixel diff.
            assertEquals(ChangeAnalysis.Level.NOTABLE, r!!.level)
        } finally { cleanup() }
    }

    @Test
    fun unreadablePaths_returnNull() = runBlocking {
        assertNull(ChangeAnalysis.compare(ctx, "/no/before.jpg", "/no/after.jpg", 0))
    }

    @Test
    fun recommendedRecheckDays_matchLevel() {
        assertEquals(14, ChangeAnalysis.recommendedRecheckDays(ChangeAnalysis.Level.NOTABLE))
        assertEquals(30, ChangeAnalysis.recommendedRecheckDays(ChangeAnalysis.Level.MINOR))
        assertEquals(90, ChangeAnalysis.recommendedRecheckDays(ChangeAnalysis.Level.STABLE))
    }
}
