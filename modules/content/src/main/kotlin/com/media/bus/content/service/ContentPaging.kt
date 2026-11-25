package com.media.bus.content.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.content.result.ContentResult

internal object ContentPaging {
    /** 페이지 요청을 검증하고 검색어를 정리해 돌려준다. */
    fun normalize(page: Int, size: Int, keyword: String?): String? {
        if (page < 0 || size !in 1..100 || (keyword?.length ?: 0) > 100) {
            throw BusinessException(ContentResult.INVALID_PAGE_REQUEST)
        }
        return keyword?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** 요청 ID 목록이 현재 항목 ID 집합과 정확히 같은지 확인한다. */
    fun <T> requireSameSet(requested: List<T>, current: Collection<T>) {
        if (requested.size != current.size || requested.toSet() != current.toSet()) {
            throw BusinessException(ContentResult.INVALID_SORT_ORDER)
        }
    }
}
