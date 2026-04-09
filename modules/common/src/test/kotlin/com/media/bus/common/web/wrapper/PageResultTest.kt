package com.media.bus.common.web.wrapper

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PageResultTest {

    // ── 빌더 기본 동작 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("빌더 기본 동작")
    inner class BuilderTest {

        @Test
        @DisplayName("단건 item 추가 후 items에 포함된다")
        fun singleItemIsIncluded() {
            val result = PageResult.builder<String>()
                .item("hello")
                .totalCnt(1L)
                .pageNum(1)
                .pageRows(10)
                .build()

            assertThat(result.items).containsExactly("hello")
            assertThat(result.totalCnt).isEqualTo(1L)
            assertThat(result.pageNum).isEqualTo(1)
            assertThat(result.pageRows).isEqualTo(10)
        }

        @Test
        @DisplayName("다건 item 추가 후 순서가 보존된다")
        fun multipleItemsPreserveOrder() {
            val result = PageResult.builder<Int>()
                .item(10)
                .item(20)
                .item(30)
                .totalCnt(3L)
                .pageNum(1)
                .pageRows(10)
                .build()

            assertThat(result.items).containsExactly(10, 20, 30)
        }

        @Test
        @DisplayName("items(List) 벌크 추가가 동작한다")
        fun bulkItemsAreAdded() {
            val source = listOf("a", "b", "c")

            val result = PageResult.builder<String>()
                .items(source)
                .totalCnt(3L)
                .pageNum(1)
                .pageRows(10)
                .build()

            assertThat(result.items).containsExactlyElementsOf(source)
        }

        @Test
        @DisplayName("item을 추가하지 않으면 빈 리스트가 반환된다")
        fun emptyItemsWhenNoItemAdded() {
            val result = PageResult.builder<String>()
                .totalCnt(0L)
                .pageNum(1)
                .pageRows(10)
                .build()

            assertThat(result.items).isEmpty()
        }
    }

    // ── 불변성 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("불변성")
    inner class ImmutabilityTest {

        @Test
        @DisplayName("빌드 후 items 리스트는 수정이 불가하다")
        fun itemsListIsUnmodifiable() {
            val result = PageResult.builder<String>()
                .item("x")
                .totalCnt(1L)
                .pageNum(1)
                .pageRows(10)
                .build()

            assertThatThrownBy { (result.items as MutableList).add("y") }
                .isInstanceOf(UnsupportedOperationException::class.java)
        }

        @Test
        @DisplayName("원본 리스트를 변경해도 PageResult의 items에 영향을 주지 않는다")
        fun mutatingSourceListDoesNotAffectItems() {
            val result = PageResult.builder<String>()
                .item("original")
                .totalCnt(1L)
                .pageNum(1)
                .pageRows(10)
                .build()

            assertThat(result.items).containsExactly("original")
        }
    }

    // ── equals / hashCode ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("equals / hashCode")
    inner class EqualsHashCodeTest {

        @Test
        @DisplayName("동일한 내용의 두 PageResult는 동등하다")
        fun sameContentIsEqual() {
            val a = PageResult.builder<String>()
                .item("x").totalCnt(10L).pageNum(1).pageRows(5).build()
            val b = PageResult.builder<String>()
                .item("x").totalCnt(10L).pageNum(1).pageRows(5).build()

            assertThat(a).isEqualTo(b)
            assertThat(a.hashCode()).isEqualTo(b.hashCode())
        }

        @Test
        @DisplayName("items 내용이 다르면 동등하지 않다")
        fun differentItemsAreNotEqual() {
            val a = PageResult.builder<String>()
                .item("x").totalCnt(10L).pageNum(1).pageRows(5).build()
            val b = PageResult.builder<String>()
                .item("y").totalCnt(10L).pageNum(1).pageRows(5).build()

            assertThat(a).isNotEqualTo(b)
        }

        @Test
        @DisplayName("totalCnt가 다르면 동등하지 않다")
        fun differentTotalCntIsNotEqual() {
            val a = PageResult.builder<String>()
                .item("x").totalCnt(10L).pageNum(1).pageRows(5).build()
            val b = PageResult.builder<String>()
                .item("x").totalCnt(99L).pageNum(1).pageRows(5).build()

            assertThat(a).isNotEqualTo(b)
        }
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toString")
    inner class ToStringTest {

        @Test
        @DisplayName("toString에 items, totalCnt, pageRows, pageNum이 포함된다")
        fun toStringContainsAllFields() {
            val result = PageResult.builder<String>()
                .item("hello")
                .totalCnt(42L)
                .pageNum(3)
                .pageRows(20)
                .build()

            val str = result.toString()
            assertThat(str).contains("hello", "42", "3", "20")
        }
    }

    // ── 페이징 시나리오 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("페이징 시나리오")
    inner class PagingScenarioTest {

        @Test
        @DisplayName("마지막 페이지: totalCnt보다 items가 적을 수 있다")
        fun lastPageHasFewerItemsThanPageRows() {
            val result = PageResult.builder<Int>()
                .item(1)
                .item(2)
                .totalCnt(12L)
                .pageNum(2)
                .pageRows(10)
                .build()

            assertThat(result.items).hasSize(2)
            assertThat(result.totalCnt).isEqualTo(12L)
        }

        @Test
        @DisplayName("빈 결과: totalCnt=0, items는 비어있다")
        fun emptyResult() {
            val result = PageResult.builder<String>()
                .totalCnt(0L)
                .pageNum(1)
                .pageRows(10)
                .build()

            assertThat(result.items).isEmpty()
            assertThat(result.totalCnt).isZero()
        }
    }
}
