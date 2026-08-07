package com.sunny.skin.inference.download

import android.content.Context
import android.content.res.AssetManager
import com.sunny.skin.inference.ModelProvider
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Materializes the Play install-time asset pack into app-private files.
 *
 * Play has already downloaded these bytes as part of installing the app. The
 * one-time copy is necessary because llama.cpp opens and mmaps ordinary paths,
 * while install-time asset packs are mounted through Android's AssetManager.
 */
object BundledModelInstaller {
    private const val ASSET_PACK_ROOT = "sunny_model_pack"

    fun contains(context: Context, pack: ModelPack): Boolean {
        val names = context.assets.list(assetDirectory(pack))?.toSet().orEmpty()
        return pack.assets.all { it.fileName in names }
    }

    suspend fun install(
        context: Context,
        pack: ModelPack,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!contains(context, pack)) {
            return@withContext Result.failure(
                IllegalStateException("The install-time Sunny model pack is unavailable."),
            )
        }
        val destination = ModelProvider.modelsDir(context)
        var completed = 0L
        try {
            for (asset in pack.assets) {
                coroutineContext.ensureActive()
                val target = File(destination, asset.fileName)
                if (target.isFile && target.length() == asset.sizeBytes &&
                    digest(target) == asset.sha256
                ) {
                    completed += asset.sizeBytes
                    onProgress(completed, pack.totalBytes)
                    continue
                }

                val part = File(destination, "${asset.fileName}.bundled-part")
                part.delete()
                val messageDigest = MessageDigest.getInstance("SHA-256")
                context.assets.open(
                    "${assetDirectory(pack)}/${asset.fileName}",
                    AssetManager.ACCESS_STREAMING,
                ).use { input ->
                    FileOutputStream(part).use { output ->
                        val buffer = ByteArray(1 shl 20)
                        var copied = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            messageDigest.update(buffer, 0, count)
                            copied += count
                            onProgress(completed + copied, pack.totalBytes)
                        }
                        output.fd.sync()
                    }
                }
                val actualHash = messageDigest.digest().toHex()
                check(part.length() == asset.sizeBytes) {
                    "Bundled model size mismatch for ${asset.fileName}."
                }
                check(actualHash == asset.sha256) {
                    "Bundled model checksum mismatch for ${asset.fileName}."
                }
                if (target.exists()) check(target.delete()) {
                    "Could not replace ${asset.fileName}."
                }
                check(part.renameTo(target)) {
                    "Could not activate ${asset.fileName}."
                }
                completed += asset.sizeBytes
                onProgress(completed, pack.totalBytes)
            }
            Result.success(destination)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private fun assetDirectory(pack: ModelPack) = "$ASSET_PACK_ROOT/${pack.version}"

    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
