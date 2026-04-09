package com.media.bus.common.web.wrapper

import java.io.Serializable

/**
 * ## Paging Result 객체
 *
 * ArrayList 상속 대신 컴포지션으로 items 리스트를 보유한다.
 * 빌드 후 items는 불변(unmodifiable)이며, 페이징 메타(totalCnt, pageRows, pageNum)도 변경 불가하다.
 */
data class PageResult<E>(
    val items: List<E>,
    val totalCnt: Long,
    val pageRows: Int,
    val pageNum: Int,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = -4202447654651104371L

        /** 빌더 패턴 호환을 위한 팩토리 */
        fun <E> builder(): Builder<E> = Builder()
    }

    /** Java 코드 호환성을 위한 빌더 */
    class Builder<E> {
        private val items = mutableListOf<E>()
        private var totalCnt: Long = 0
        private var pageRows: Int = 0
        private var pageNum: Int = 0

        fun item(item: E) = apply { items.add(item) }
        fun items(items: Collection<E>) = apply { this.items.addAll(items) }
        fun totalCnt(totalCnt: Long) = apply { this.totalCnt = totalCnt }
        fun pageRows(pageRows: Int) = apply { this.pageRows = pageRows }
        fun pageNum(pageNum: Int) = apply { this.pageNum = pageNum }

        fun build(): PageResult<E> = PageResult(
            items = java.util.Collections.unmodifiableList(ArrayList(items)),
            totalCnt = totalCnt,
            pageRows = pageRows,
            pageNum = pageNum,
        )
    }
}
