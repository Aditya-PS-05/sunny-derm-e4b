package com.sunny.skin.inference

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteInferenceContractTest {
    @Test
    fun request_keepsImageBeforeVerbatimPrompt() {
        val request = JSONObject(
            RemoteInferenceContract.requestPayload("data:image/jpeg;base64,abc")
                .toString(Charsets.UTF_8),
        )
        val content = request.getJSONArray("messages").getJSONObject(0).getJSONArray("content")

        assertEquals("image_url", content.getJSONObject(0).getString("type"))
        assertEquals("data:image/jpeg;base64,abc",
            content.getJSONObject(0).getJSONObject("image_url").getString("url"))
        assertEquals("text", content.getJSONObject(1).getString("type"))
        assertEquals(Prompt.SCHEMA_PROMPT, content.getJSONObject(1).getString("text"))
        assertEquals(1024, request.getInt("max_tokens"))
        assertEquals(0.0, request.getDouble("temperature"), 0.0)
    }

    @Test
    fun response_keepsLastSchemaAndRemovesChannelMarkers() {
        val finalSchema = """
            Lesion Type: Flat spot
            Colour: Brown
            Symmetry: Roughly symmetric
            Borders: Defined
            Texture: Smooth
            Summary: A flat brown spot.
        """.trimIndent()
        val response = JSONObject().put(
            "choices",
            org.json.JSONArray().put(
                JSONObject().put(
                    "message",
                    JSONObject().put(
                        "content",
                        "Lesion Type: draft\nColour: draft\n<|final|>\n$finalSchema<|end|>",
                    ),
                ),
            ),
        ).toString()

        assertEquals(finalSchema, RemoteInferenceContract.parseResponse(response))
    }
}
