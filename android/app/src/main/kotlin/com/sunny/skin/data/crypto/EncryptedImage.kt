package com.sunny.skin.data.crypto

/**
 * Coil model for an app-private, AES-GCM-encrypted photo file at [path].
 * Load with `AsyncImage(model = EncryptedImage(path))`; the registered
 * [EncryptedImageFetcher] decrypts it in-flight (Coil disk cache is disabled so
 * plaintext never touches disk).
 */
data class EncryptedImage(val path: String)
