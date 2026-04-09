package com.media.bus.common.utils

/**
 * ## Path name을 조합하기 위한 유틸 클래스
 */
@Suppress("unused")
object PathNameBuilder {

    fun get(vararg strings: String): String = get(false, *strings)

    fun get(isDirectory: Boolean, vararg strings: String): String {
        val path = strings.joinToString("/")
        return if (isDirectory) "$path/" else path
    }
}
