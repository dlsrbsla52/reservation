package com.media.bus.common.web.wrapper;

import com.media.bus.common.result.type.CommonResult;
import com.media.bus.common.web.response.DataView;
import com.media.bus.common.web.response.ErrorView;
import com.media.bus.common.web.response.NoDataView;
import com.media.bus.common.web.response.PageView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ResponseBodyWrapper 구현체 단위 테스트.
 *
 * <p>새로운 Wrapper를 추가할 때 참고용 예제를 포함한다.
 * {@link CustomWrapperExample} 참고.
 */
class ResponseBodyWrapperTest {

    // ── 테스트용 DTO ──────────────────────────────────────────────────────────
    record SampleDto(String name, int value) {}

    // ── 커스텀 Wrapper 예제 ───────────────────────────────────────────────────

    /**
     * ResponseBodyWrapper 확장 예제.
     *
     * <p>SampleDto를 특수한 형태로 래핑해야 하는 경우를 가정한다.
     * 실제 사용 시 Bean으로 등록하면 ResponseAdvisor가 자동으로 주입받아 사용한다.
     *
     * <pre>{@code
     * // CommonWebMvcAutoConfiguration 또는 각 모듈의 @Configuration에 추가
     * @Bean
     * @Order(3)   // 기존 Wrapper보다 앞에 두어 우선 처리
     * public SampleDtoBodyWrapper sampleDtoBodyWrapper() {
     *     return new SampleDtoBodyWrapper();
     * }
     * }</pre>
     */
    static class CustomWrapperExample implements ResponseBodyWrapper {

        @Override
        public boolean supports(Object body) {
            return body instanceof SampleDto;
        }

        @Override
        public Object wrap(Object body) {
            SampleDto dto = (SampleDto) body;
            // SampleDto를 DataView로 래핑하되, 커스텀 메시지를 함께 담는다.
            return DataView.builder()
                    .result(CommonResult.SUCCESS)
                    .message("SampleDto 전용 응답: " + dto.name())
                    .data(dto)
                    .build();
        }
    }

    // ── PassthroughBodyWrapper ────────────────────────────────────────────────

    @Nested
    @DisplayName("PassthroughBodyWrapper")
    class PassthroughBodyWrapperTest {

        private final PassthroughBodyWrapper wrapper = new PassthroughBodyWrapper();

        @Test
        @DisplayName("AbstractView 구현체(DataView)는 supports가 true를 반환한다")
        void supportsAbstractView() {
            DataView<String> view = DataView.<String>builder()
                    .result(CommonResult.SUCCESS).data("data").build();

            assertThat(wrapper.supports(view)).isTrue();
        }

        @Test
        @DisplayName("String은 supports가 true를 반환한다")
        void supportsString() {
            assertThat(wrapper.supports("plain text")).isTrue();
        }

        @Test
        @DisplayName("일반 객체는 supports가 false를 반환한다")
        void doesNotSupportPlainObject() {
            assertThat(wrapper.supports(new SampleDto("test", 1))).isFalse();
        }

        @Test
        @DisplayName("wrap은 입력 객체를 그대로 반환한다")
        void wrapReturnsSameInstance() {
            DataView<String> view = DataView.<String>builder()
                    .result(CommonResult.SUCCESS).data("data").build();

            assertThat(wrapper.wrap(view)).isSameAs(view);
        }
    }

    // ── NullBodyWrapper ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("NullBodyWrapper")
    class NullBodyWrapperTest {

        private final NullBodyWrapper wrapper = new NullBodyWrapper();

        @Test
        @DisplayName("null은 supports가 true를 반환한다")
        void supportsNull() {
            assertThat(wrapper.supports(null)).isTrue();
        }

        @Test
        @DisplayName("non-null 객체는 supports가 false를 반환한다")
        void doesNotSupportNonNull() {
            assertThat(wrapper.supports("anything")).isFalse();
        }

        @Test
        @DisplayName("null body를 NoDataView로 래핑한다")
        void wrapsNullToNoDataView() {
            Object result = wrapper.wrap(null);

            assertThat(result).isInstanceOf(NoDataView.class);
            NoDataView view = (NoDataView) result;
            assertThat(view.getCode()).isEqualTo(CommonResult.REQUEST_SUCCESS.getCode());
        }
    }

    // ── PageResultBodyWrapper ─────────────────────────────────────────────────

    @Nested
    @DisplayName("PageResultBodyWrapper")
    class PageResultBodyWrapperTest {

        private final PageResultBodyWrapper wrapper = new PageResultBodyWrapper();

        @Test
        @DisplayName("PageResult는 supports가 true를 반환한다")
        void supportsPageResult() {
            assertThat(wrapper.supports(PageResult.builder().build())).isTrue();
        }

        @Test
        @DisplayName("일반 List는 supports가 false를 반환한다")
        void doesNotSupportPlainList() {
            assertThat(wrapper.supports(java.util.List.of("a", "b"))).isFalse();
        }

        @Test
        @DisplayName("PageResult를 PageView로 래핑하고 페이징 메타를 보존한다")
        void wrapsPageResultToPageView() {
            PageResult<String> pageResult = PageResult.<String>builder()
                    .item("item1")
                    .item("item2")
                    .totalCnt(100L)
                    .pageNum(2)
                    .pageRows(10)
                    .build();

            Object result = wrapper.wrap(pageResult);

            assertThat(result).isInstanceOf(PageView.class);
            PageView<?> pageView = (PageView<?>) result;
            assertThat(pageView.getData().getPageData().getTotalCnt()).isEqualTo(100L);
            assertThat(pageView.getData().getPageData().getPageNum()).isEqualTo(2);
            assertThat(pageView.getData().getPageData().getPageRows()).isEqualTo(10);
            assertThat(pageView.getData().getList()).hasSize(2);
        }
    }

    // ── DefaultObjectBodyWrapper ──────────────────────────────────────────────

    @Nested
    @DisplayName("DefaultObjectBodyWrapper")
    class DefaultObjectBodyWrapperTest {

        private final DefaultObjectBodyWrapper wrapper = new DefaultObjectBodyWrapper();

        @Test
        @DisplayName("어떤 객체든 supports가 true를 반환한다")
        void supportsAnything() {
            assertThat(wrapper.supports(new SampleDto("x", 1))).isTrue();
            assertThat(wrapper.supports(42)).isTrue();
            assertThat(wrapper.supports(null)).isTrue();
        }

        @Test
        @DisplayName("일반 객체를 DataView로 래핑한다")
        void wrapsObjectToDataView() {
            SampleDto dto = new SampleDto("hello", 99);

            Object result = wrapper.wrap(dto);

            assertThat(result).isInstanceOf(DataView.class);
            DataView<?> dataView = (DataView<?>) result;
            assertThat(dataView.getData()).isEqualTo(dto);
            assertThat(dataView.getCode()).isEqualTo(CommonResult.REQUEST_SUCCESS.getCode());
        }
    }

    // ── CustomWrapperExample ──────────────────────────────────────────────────

    @Nested
    @DisplayName("커스텀 Wrapper 확장 예제 (CustomWrapperExample)")
    class CustomWrapperExampleTest {

        private final CustomWrapperExample wrapper = new CustomWrapperExample();

        @Test
        @DisplayName("SampleDto만 supports가 true를 반환한다")
        void supportsSampleDtoOnly() {
            assertThat(wrapper.supports(new SampleDto("x", 1))).isTrue();
            assertThat(wrapper.supports("string")).isFalse();
            assertThat(wrapper.supports(42)).isFalse();
        }

        @Test
        @DisplayName("SampleDto를 DataView로 래핑하고 커스텀 메시지를 포함한다")
        void wrapsSampleDtoWithCustomMessage() {
            SampleDto dto = new SampleDto("myItem", 7);

            Object result = wrapper.wrap(dto);

            assertThat(result).isInstanceOf(DataView.class);
            DataView<?> dataView = (DataView<?>) result;
            assertThat(dataView.getData()).isEqualTo(dto);
            assertThat(dataView.getMessage()).contains("myItem");
        }

        @Test
        @DisplayName("ErrorView 같은 AbstractView 구현체는 PassthroughBodyWrapper가 먼저 가로채야 한다 (우선순위 확인용)")
        void errorViewShouldBeHandledByPassthroughNotCustom() {
            ErrorView errorView = ErrorView.builder()
                    .result(CommonResult.ERROR)
                    .error("Internal Server Error")
                    .status(500)
                    .build();

            // CustomWrapperExample은 AbstractView를 처리하지 않으므로 supports = false
            assertThat(wrapper.supports(errorView)).isFalse();

            // PassthroughBodyWrapper가 AbstractView를 처리한다
            PassthroughBodyWrapper passthrough = new PassthroughBodyWrapper();
            assertThat(passthrough.supports(errorView)).isTrue();
            assertThat(passthrough.wrap(errorView)).isSameAs(errorView);
        }
    }
}
