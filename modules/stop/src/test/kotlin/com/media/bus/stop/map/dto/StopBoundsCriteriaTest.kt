package com.media.bus.stop.map.dto

import com.media.bus.common.exceptions.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class StopBoundsCriteriaTest {

    @Test
    fun `정상_영역은_좌표가_정규화되어_생성된다`() {
        val criteria = StopBoundsCriteria.of("37.4", "126.8", "37.7", "127.2", null)

        assertThat(criteria.swLat).isEqualByComparingTo(BigDecimal("37.4"))
        assertThat(criteria.neLng).isEqualByComparingTo(BigDecimal("127.2"))
        assertThat(criteria.limit).isEqualTo(StopBoundsCriteria.DEFAULT_LIMIT)
    }

    @Test
    fun `limit_미지정시_기본값을_사용한다`() {
        assertThat(StopBoundsCriteria.of("37.4", "126.8", "37.7", "127.2", null).limit)
            .isEqualTo(StopBoundsCriteria.DEFAULT_LIMIT)
    }

    @Test
    fun `limit_상한_초과는_MAX_LIMIT으로_보정된다`() {
        val criteria = StopBoundsCriteria.of("37.4", "126.8", "37.7", "127.2", 999_999)

        assertThat(criteria.limit).isEqualTo(StopBoundsCriteria.MAX_LIMIT)
    }

    @Test
    fun `limit_0이하는_기본값으로_보정된다`() {
        assertThat(StopBoundsCriteria.of("37.4", "126.8", "37.7", "127.2", 0).limit)
            .isEqualTo(StopBoundsCriteria.DEFAULT_LIMIT)
        assertThat(StopBoundsCriteria.of("37.4", "126.8", "37.7", "127.2", -10).limit)
            .isEqualTo(StopBoundsCriteria.DEFAULT_LIMIT)
    }

    @Test
    fun `남서_위도가_북동_위도보다_크면_예외`() {
        assertThatThrownBy { StopBoundsCriteria.of("37.9", "126.8", "37.7", "127.2", null) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("swLat")
    }

    @Test
    fun `날짜변경선을_넘는_영역은_거부한다`() {
        assertThatThrownBy { StopBoundsCriteria.of("37.4", "179.0", "37.7", "-179.0", null) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("swLng")
    }

    @Test
    fun `좌표가_숫자가_아니면_예외`() {
        assertThatThrownBy { StopBoundsCriteria.of("null", "126.8", "37.7", "127.2", null) }
            .isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun `좌표가_유효_범위를_벗어나면_예외`() {
        assertThatThrownBy { StopBoundsCriteria.of("37.4", "126.8", "95.0", "127.2", null) }
            .isInstanceOf(BusinessException::class.java)
    }
}
