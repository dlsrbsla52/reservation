package com.media.bus.content.repository

internal object ContentQueries {
    /** LIKE 와일드카드와 이스케이프 문자를 리터럴로 바꾼다. */
    fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
