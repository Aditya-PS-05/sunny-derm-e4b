package com.sunny.skin.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.sunny.skin.data.crypto.CryptoManager
import com.sunny.skin.data.crypto.EncryptedPreferenceValue
import com.sunny.skin.data.db.ScanType
import com.sunny.skin.data.model.ApproximateMeasurement
import com.sunny.skin.data.model.BodyPart
import com.sunny.skin.data.model.CaptureAlignment
import java.io.ByteArrayOutputStream
import java.io.File
import org.json.JSONObject

data class PendingCapture(
    val bitmap: Bitmap,
    val bodyPart: BodyPart,
    val scanType: ScanType,
    val targetScanId: String?,
    val referenceImagePath: String?,
    val measurement: ApproximateMeasurement? = null,
    val alignment: CaptureAlignment? = null,
    val checkSessionId: String? = null,
)

/** Encrypted crash/process-death recovery for the single unsaved capture. */
@SuppressLint("ApplySharedPref") // Recovery metadata must be durable before the process can die.
class PendingCaptureStore(context: Context) {
    private val app = context.applicationContext
    private val file = File(app.cacheDir, "pending_capture.enc")
    private val prefs = app.getSharedPreferences("sunny_pending_capture", Context.MODE_PRIVATE)

    @Synchronized
    fun save(capture: PendingCapture) {
        val jpeg = ByteArrayOutputStream().use { output ->
            check(capture.bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output))
            output.toByteArray()
        }
        file.writeBytes(CryptoManager.encrypt(app, jpeg))
        val metadata = JSONObject().apply {
            put("bodyPart", capture.bodyPart.name)
            put("scanType", capture.scanType.name)
            put("targetScanId", capture.targetScanId ?: JSONObject.NULL)
            put("referenceImagePath", capture.referenceImagePath ?: JSONObject.NULL)
            put("checkSessionId", capture.checkSessionId ?: JSONObject.NULL)
            capture.measurement?.takeIf { it.isValid }?.let { measurement ->
                put("measurement", JSONObject().apply {
                    put("referenceSizeMm", measurement.referenceSizeMm)
                    put("referenceSpan", measurement.referenceSpan)
                    put("targetSpan", measurement.targetSpan)
                })
            }
            capture.alignment?.let { alignment ->
                put("alignment", JSONObject().apply {
                    put("score", alignment.score)
                    put("translationX", alignment.translationX)
                    put("translationY", alignment.translationY)
                    put("scale", alignment.scale)
                    put("rotationDegrees", alignment.rotationDegrees)
                })
            }
        }.toString()
        prefs.edit().putString(KEY, EncryptedPreferenceValue.encode(app, metadata)).commit()
    }

    @Synchronized
    fun restore(): PendingCapture? = runCatching {
        val stored = prefs.getString(KEY, null) ?: return null
        val metadata = EncryptedPreferenceValue.decode(app, stored) ?: return null
        val json = JSONObject(metadata)
        val plain = CryptoManager.decrypt(app, file.readBytes())
        val bitmap = BitmapFactory.decodeByteArray(plain, 0, plain.size) ?: return null
        val measurementJson = json.optJSONObject("measurement")
        val alignmentJson = json.optJSONObject("alignment")
        PendingCapture(
            bitmap = bitmap,
            bodyPart = BodyPart.valueOf(json.getString("bodyPart")),
            scanType = ScanType.valueOf(json.getString("scanType")),
            targetScanId = json.optString("targetScanId").takeIf { it.isNotBlank() && it != "null" },
            referenceImagePath = json.optString("referenceImagePath")
                .takeIf { it.isNotBlank() && it != "null" },
            measurement = measurementJson?.let {
                ApproximateMeasurement(
                    referenceSizeMm = it.getDouble("referenceSizeMm").toFloat(),
                    referenceSpan = it.getDouble("referenceSpan").toFloat(),
                    targetSpan = it.getDouble("targetSpan").toFloat(),
                ).takeIf(ApproximateMeasurement::isValid)
            },
            alignment = alignmentJson?.let {
                CaptureAlignment(
                    score = it.getDouble("score").toFloat(),
                    translationX = it.getDouble("translationX").toFloat(),
                    translationY = it.getDouble("translationY").toFloat(),
                    scale = it.getDouble("scale").toFloat(),
                    rotationDegrees = it.getDouble("rotationDegrees").toFloat(),
                )
            },
            checkSessionId = json.optString("checkSessionId")
                .takeIf { it.isNotBlank() && it != "null" },
        )
    }.getOrElse {
        clear()
        null
    }

    @Synchronized
    fun clear() {
        runCatching { file.delete() }
        prefs.edit().clear().commit()
    }

    private companion object { const val KEY = "metadata" }
}
