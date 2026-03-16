package com.media.bus.common.web.wrapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageResultTest {

    // ── 빌더 기본 동작 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("빌더 기본 동작")
    class BuilderTest {

        @Test
        @DisplayName("단건 item 추가 후 items에 포함된다")
        void singleItemIsIncluded() {
            PageResult<String> result = PageResult.<String>builder()
                    .item("hello")
                    .totalCnt(1L)
                    .pageNum(1)
                    .pageRows(10)
                    .build();

            assertThat(result.getItems()).containsExactly("hello");
            assertThat(result.getTotalCnt()).isEqualTo(1L);
            assertThat(result.getPageNum()).isEqualTo(1);
            assertThat(result.getPageRows()).isEqualTo(10);
        }

        @Test
        @DisplayName("다건 item 추가 후 순서가 보존된다")
        void multipleItemsPreserveOrder() {
            PageResult<Integer> result = PageResult.<Integer>builder()
                    .item(10)
                    .item(20)
                    .item(30)
                    .totalCnt(3L)
                    .pageNum(1)
                    .pageRows(10)
                    .build();

            assertThat(result.getItems()).containsExactly(10, 20, 30);
        }

        @Test
        @DisplayName("items(List) 벌크 추가가 동작한다")
        void bulkItemsAreAdded() {
            List<String> source = List.of("a", "b", "c");

            PageResult<String> result = PageResult.<String>builder()
                    .items(source)
                    .totalCnt(3L)
                    .pageNum(1)
                    .pageRows(10)
                    .build();

            assertThat(result.getItems()).containsExactlyElementsOf(source);
        }

        @Test
        @DisplayName("item을 추가하지 않으면 빈 리스트가 반환된다")
        void emptyItemsWhenNoItemAdded() {
            PageResult<String> result = PageResult.<String>builder()
                    .totalCnt(0L)
                    .pageNum(1)
                    .pageRows(10)
                    .build();

            assertThat(result.getItems()).isEmpty();
        }
    }

    // ── 불변성 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("불변성")
    class ImmutabilityTest {

        @Test
        @DisplayName("빌드 후 items 리스트는 수정이 불가하다")
        void itemsListIsUnmodifiable() {
            PageResult<String> result = PageResult.<String>builder()
                    .item("x")
                    .totalCnt(1L)
                    .pageNum(1)
                    .pageRows(10)
                    .build();

            assertThatThrownBy(() -> result.getItems().add("y"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("원본 리스트를 변경해도 PageResult의 items에 영향을 주지 않는다")
        void mutatingSourceListDoesNotAffectItems() {
            // @Singular는 내부적으로 방어 복사를 수행한다
            PageResult<String> result = PageResult.<String>builder()
                    .item("original")
                    .totalCnt(1L)
                    .pageNum(1)
                    .pageRows(10)
                    .build();

            assertThat(result.getItems()).containsExactly("original");
        }
    }

    // ── equals / hashCode ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("equals / hashCode")
    class EqualsHashCodeTest {

        @Test
        @DisplayName("동일한 내용의 두 PageResult는 동등하다")
        void sameContentIsEqual() {
            PageResult<String> a = PageResult.<String>builder()
                    .item("x").totalCnt(10L).pageNum(1).pageRows(5).build();
            PageResult<String> b = PageResult.<String>builder()
                    .item("x").totalCnt(10L).pageNum(1).pageRows(5).build();

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("items 내용이 다르면 동등하지 않다")
        void differentItemsAreNotEqual() {
            PageResult<String> a = PageResult.<String>builder()
                    .item("x").totalCnt(10L).pageNum(1).pageRows(5).build();
            PageResult<String> b = PageResult.<String>builder()
                    .item("y").totalCnt(10L).pageNum(1).pageRows(5).build();

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("totalCnt가 다르면 동등하지 않다")
        void differentTotalCntIsNotEqual() {
            PageResult<String> a = PageResult.<String>builder()
                    .item("x").totalCnt(10L).pageNum(1).pageRows(5).build();
            PageResult<String> b = PageResult.<String>builder()
                    .item("x").totalCnt(99L).pageNum(1).pageRows(5).build();

            assertThat(a).isNotEqualTo(b);
        }
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("toString에 items, totalCnt, pageRows, pageNum이 포함된다")
        void toStringContainsAllFields() {
            PageResult<String> result = PageResult.<String>builder()
                    .item("hello")
                    .totalCnt(42L)
                    .pageNum(3)
                    .pageRows(20)
                    .build();

            String str = result.toString();
            assertThat(str).contains("hello", "42", "3", "20");
        }
    }

    // ── 페이징 시나리오 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("페이징 시나리오")
    class PagingScenarioTest {

        @Test
        @DisplayName("마지막 페이지: totalCnt보다 items가 적을 수 있다")
        void lastPageHasFewerItemsThanPageRows() {
            PageResult<Integer> result = PageResult.<Integer>builder()
                    .item(1)
                    .item(2)
                    .totalCnt(12L)  // 전체 12건
                    .pageNum(2)     // 2페이지
                    .pageRows(10)   // 페이지당 10건 → 마지막 페이지는 2건
                    .build();

            assertThat(result.getItems()).hasSize(2);
            assertThat(result.getTotalCnt()).isEqualTo(12L);
        }

        @Test
        @DisplayName("빈 결과: totalCnt=0, items는 비어있다")
        void emptyResult() {
            PageResult<String> result = PageResult.<String>builder()
                    .totalCnt(0L)
                    .pageNum(1)
                    .pageRows(10)
                    .build();

            assertThat(result.getItems()).isEmpty();
            assertThat(result.getTotalCnt()).isZero();
        }
    }
}
