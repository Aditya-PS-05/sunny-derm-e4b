package com.sunny.skin.inference.download

import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Downloads a single [ModelAsset] with HTTP Range **resume** support: a partial
 * `.part` file survives interruptions and the next attempt continues from the
 * last byte instead of restarting a multi-GB transfer. Verifies the sha256
 * digest (or the debug-only prefix) on completion, then atomically renames it.
 *
 * Uses only java.net (no extra deps). Cancellable via the coroutine scope.
 */
class WeightDownloader(private val modelsDir: File) {

    /** @param onProgress called with (bytesForThisAsset, totalBytesForThisAsset). */
    suspend fun download(
        asset: ModelAsset,
        onProgress: (Long, Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        val target = File(modelsDir, asset.fileName)
        if (target.exists() && target.length() == asset.sizeBytes) return@withContext Result.success(target)

        val part = File(modelsDir, "${asset.fileName}.part")
        var existing = if (part.exists()) part.length() else 0L
        if (existing > asset.sizeBytes) { part.delete(); existing = 0L }

        val url = ModelSource.urlFor(asset)
        if (!url.startsWith("https://")) {
            return@withContext Result.failure(SecurityException("model weights must be served over HTTPS"))
        }

        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
            }
            conn.connect()
            val code = conn.responseCode
            // 206 = resumed partial; 200 = full (server ignored Range -> restart).
            if (code == HttpURLConnection.HTTP_OK && existing > 0) { part.delete(); existing = 0L }
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                return@withContext Result.failure(RuntimeException("HTTP $code for ${asset.fileName}"))
            }

            RandomAccessFile(part, "rw").use { out ->
                out.seek(existing)
                conn.inputStream.use { input ->
                    val buf = ByteArray(1 shl 16)
                    var downloaded = existing
                    while (true) {
                        coroutineContext.ensureActive()   // cooperative cancellation
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        onProgress(downloaded, asset.sizeBytes)
                    }
                }
            }

            if (part.length() != asset.sizeBytes) {
                return@withContext Result.failure(
                    RuntimeException("size mismatch: ${part.length()} != ${asset.sizeBytes}"),
                )
            }
            if (!verifyDigest(part, asset.sha256)) {
                part.delete()
                return@withContext Result.failure(RuntimeException("checksum mismatch for ${asset.fileName}"))
            }
            if (!part.renameTo(target)) {
                return@withContext Result.failure(RuntimeException("could not finalise ${asset.fileName}"))
            }
            Result.success(target)
        } catch (t: Throwable) {
            Result.failure(t)   // .part is kept for resume unless it was corrupt
        }
    }

    private suspend fun verifyDigest(file: File, expected: String): Boolean =
        withContext(Dispatchers.IO) {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(1 shl 20)
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            hex.startsWith(expected.lowercase())
        }
}
