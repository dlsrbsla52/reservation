package com.media.bus.lint.ktlint

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KdocSummaryTest {

    @Test
    fun `목록 항목은 요약에 합치지 않는다`() {
        val kdoc = "/**\n * 서울 공공 API 통합 테스트\n * - 네트워크 필요\n * - 컨텍스트 없음\n */"
        assertThat(kdocSummary(kdoc)).isEqualTo("서울 공공 API 통합 테스트")
    }

    @Test
    fun `첫 문장에서 자른다`() {
        assertThat(kdocSummary("/**\n * 찾는다. 없으면 null이다.\n */")).isEqualTo("찾는다.")
    }

    @Test
    fun `태그만 있으면 빈 문자열이다`() {
        assertThat(kdocSummary("/**\n * @param a 값\n */")).isEmpty()
    }
}
