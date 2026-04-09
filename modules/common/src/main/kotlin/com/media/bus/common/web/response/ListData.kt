package com.media.bus.common.web.response

/** List 형태의 응답 데이터 */
data class ListData<E>(val pageData: PageData?, val list: List<E>)
