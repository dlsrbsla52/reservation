package com.media.bus.common.utils

/**
 * ## Path name을 조합하기 위한 유틸 클래스
 */
@Suppress("unused")
object PathNameBuilder {

    @JvmStatic
    fun get(vararg strings: String): String = get(false, *strings)

    @JvmStatic
    fun get(isDirectory: Boolean, vararg strings: String): String {
        val sb = StringBuilder()
        strings.forEach { s -> sb.append("/").append(s) }
        return if (isDirectory) {
            sb.append("/").substring(1)
        } else {
            sb.substring(1)
        }
    }
}
