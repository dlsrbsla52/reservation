package com.media.bus.common.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.media.bus.common.exceptions.BaseException
import java.io.IOException

/**
 * ## 즐겨찾는 유틸 클래스
 */
@Suppress("unused")
object FavoriteUtil {

    /**
     * source가 null이거나 공백이면 replace를 리턴하고 아니면 source를 리턴함
     *
     * @return source or replace
     */
    @JvmStatic
    fun ifEmpty(source: String?, replace: String?): String =
        if (source.isNullOrEmpty()) (replace ?: "") else source

    /**
     * source가 숫자 형식이 아닐 경우 0을 리턴함
     *
     * @return source or 0
     */
    @JvmStatic
    fun ifNotNumericToZero(source: String?): Int = ifNotNumericToInt(source, 0)

    /**
     * source가 숫자 형식이 아닐 경우 replace를 리턴함
     *
     * @return source or replace
     */
    @JvmStatic
    fun ifNotNumericToInt(source: String?, replace: Int): Int =
        if (!isNumeric(source)) replace else source!!.toInt()

    /**
     * 숫자형식 여부를 리턴함
     *
     * @return true or false
     */
    @JvmStatic
    fun isNumeric(str: String?): Boolean {
        if (str.isNullOrEmpty()) return false
        val regEx = "^[+-]?[0-9]+$"
        return str.matches(regEx.toRegex())
    }

    /**
     * target 문장에서 prefix가 존재할 경우 prefix를 제거하고 존재하지 않을 경우 전체 문장을 리턴함
     *
     * @return prefix가 제거된 문장 또는 전체 문장
     */
    @JvmStatic
    fun removeStartWith(target: String?, prefix: String): String? =
        if (target != null && target.startsWith(prefix)) target.replace(prefix, "") else target

    /**
     * 파라미터로 넘어온 JSON 스타일의 메시지를 요청받은 타입의 객체로 변환하여 리턴함
     *
     * @return T타입의 객체
     */
    @JvmStatic
    fun <T> convertTo(message: String, clazz: Class<T>): T =
        try {
            ObjectMapper().readValue(message, clazz)
        } catch (e: IOException) {
            throw BaseException(e)
        }

    /**
     * 파라미터로 받은 객체를 Map으로 변환하여 리턴함
     *
     * @return Map 객체
     */
    @JvmStatic
    fun <T : Any> convertToMap(source: T): Map<String, Any?> {
        val returnMap = HashMap<String, Any?>()
        try {
            val fields = source::class.java.declaredFields
            for (field in fields) {
                field.isAccessible = true
                returnMap[field.name] = field.get(source)
            }
        } catch (e: IllegalArgumentException) {
            throw BaseException(e)
        } catch (e: IllegalAccessException) {
            throw BaseException(e)
        }
        return returnMap
    }

    /**
     * 구분 문자 이후의 문자열을 반환한다. 전체 문자열 또는 구분 문자열이 null일 경우 공백을 리턴함
     *
     * @return 마지막 문자열
     */
    @JvmStatic
    fun getExtractLastString(fullString: String?, delimiter: String?): String =
        if (fullString == null || delimiter == null) "" else fullString.substring(fullString.lastIndexOf(delimiter) + 1)
}
