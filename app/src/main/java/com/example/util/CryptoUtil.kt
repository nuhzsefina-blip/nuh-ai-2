package com.example.util

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtil {
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    fun sha256Hex(file: File): String = file.inputStream().use { sha256Hex(it) }

    fun sha256Hex(stream: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var read = stream.read(buffer)
        while (read != -1) {
            md.update(buffer, 0, read)
            read = stream.read(buffer)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun encryptAesGcm(plaintext: ByteArray, keyBytes: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(keyBytes.copyOf(32), "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        val encrypted = cipher.doFinal(plaintext)
        return iv + encrypted
    }

    fun decryptAesGcm(ciphertextWithIv: ByteArray, keyBytes: ByteArray): ByteArray {
        require(ciphertextWithIv.size > GCM_IV_LENGTH) { "Invalid encrypted payload" }
        val iv = ciphertextWithIv.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = ciphertextWithIv.copyOfRange(GCM_IV_LENGTH, ciphertextWithIv.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyBytes.copyOf(32), "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        return cipher.doFinal(ciphertext)
    }
}
