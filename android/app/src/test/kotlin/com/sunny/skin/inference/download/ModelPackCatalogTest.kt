package com.sunny.skin.inference.download

import com.sunny.skin.inference.tier.SunnyModelTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPackCatalogTest {
    @Test
    fun onlySunnyMoeHasADownloadablePack() {
        assertEquals(ModelPackCatalog.sunnyMoe, ModelPackCatalog.publishedPack(SunnyModelTier.SUNNY_MOE))
        assertNull(ModelPackCatalog.publishedPack(SunnyModelTier.PRO_CLOUD))
    }

    @Test
    fun catalogPinsTheCompleteMobileGgufPack() {
        val pack = ModelPackCatalog.sunnyMoe
        val expectedFiles = setOf(
            "sunny-pad-smolvlm-500m-Q8_0.gguf",
            "sunny-pad-smolvlm-500m-mmproj-mobile256-F16.gguf",
            "derm.gbnf",
            "THIRD_PARTY_NOTICES.txt",
            "Apache-2.0.txt",
            "manifest.json",
        )

        assertEquals(6, pack.assets.size)
        assertEquals(expectedFiles, pack.assets.map { it.fileName }.toSet())
        assertEquals(633_931_364L, pack.totalBytes)
        assertTrue(pack.assets.all { it.sha256.matches(Regex("[0-9a-f]{64}")) })
        assertTrue(pack.assets.all {
            it.remotePath == "${pack.version}/${it.fileName}"
        })
        assertTrue(pack.assets.any { it.fileName == "manifest.json" })
        assertEquals(2, pack.assets.count { it.fileName.endsWith(".gguf") })
        assertTrue(pack.assets.none { it.fileName.endsWith(".onnx") || it.fileName.endsWith(".safetensors") })
    }
}
