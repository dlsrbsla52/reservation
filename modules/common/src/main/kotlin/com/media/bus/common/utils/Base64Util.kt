package com.media.bus.common.utils

import java.util.*

/**
 * ## Base64 인코딩 및 디코딩
 */
@Suppress("unused")
object Base64Util {

    @JvmStatic
    fun encode(plainText: String): String =
        String(encodeBytes(plainText.toByteArray(Charsets.UTF_8)), Charsets.UTF_8)

    @JvmStatic
    fun encodeBytes(bytes: ByteArray): ByteArray =
        Base64.getEncoder().encode(bytes)

    @JvmStatic
    fun decode(encodedText: String): String =
        String(decodeBytes(encodedText.toByteArray(Charsets.UTF_8)), Charsets.UTF_8)

    @JvmStatic
    fun decodeBytes(bytes: ByteArray): ByteArray =
        Base64.getDecoder().decode(bytes)
}
