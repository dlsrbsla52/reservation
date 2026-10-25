package com.media.bus.stop.support

import com.media.bus.common.exceptions.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CoordinateTest {

    @Test
    fun `정상_좌표는_scale_7로_정규화된다`() {
        val parsed = Coordinate.parseLongitude("127.0627647339")

        // NUMERIC(10,7) 이므로 소수 8자리 이하는 반올림된다
        assertThat(parsed.scale()).isEqualTo(Coordinate.SCALE)
        assertThat(parsed).isEqualByComparingTo(BigDecimal("127.0627647"))
    }

    @Test
    fun `앞뒤_공백은_허용한다`() {
        assertThat(Coordinate.parseLatitude("  37.6472686025 "))
            .isEqualByComparingTo(BigDecimal("37.6472686"))
    }

    @Test
    fun `숫자가_아니면_예외를_던진다`() {
        assertThatThrownBy { Coordinate.parseLongitude("N/A") }
            .isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun `빈_문자열은_예외를_던진다`() {
        assertThatThrownBy { Coordinate.parseLatitude("   ") }
            .isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun `경도_범위를_벗어나면_예외를_던진다`() {
        assertThatThrownBy { Coordinate.parseLongitude("180.0000001") }
            .isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun `위도_범위를_벗어나면_예외를_던진다`() {
        assertThatThrownBy { Coordinate.parseLatitude("-90.0000001") }
            .isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun `지수_표기는_범위_검증을_거쳐_거부되거나_정규화된다`() {
        // 1e2 = 100 → 경도로는 유효
        assertThat(Coordinate.parseOrNullLongitude("1e2")).isEqualByComparingTo(BigDecimal("100"))
        // 1e3 = 1000 → 경도 범위 초과이므로 거부되어야 한다
        assertThat(Coordinate.parseOrNullLongitude("1e3")).isNull()
    }

    @Test
    fun `parseOrNull_계열은_실패_시_null을_반환한다`() {
        assertThat(Coordinate.parseOrNullLatitude("abc")).isNull()
        assertThat(Coordinate.parseOrNullLatitude(null)).isNull()
        assertThat(Coordinate.parseOrNullLatitude("37.5")).isNotNull()
    }

    @Test
    fun `isSame은_scale_차이를_무시한다`() {
        // BigDecimal.equals 였다면 false — 이 차이가 변경 오탐지의 원인이다
        assertThat(BigDecimal("37.0") == BigDecimal("37.0000000")).isFalse()
        assertThat(Coordinate.isSame(BigDecimal("37.0"), BigDecimal("37.0000000"))).isTrue()
        assertThat(Coordinate.isSame(null, null)).isTrue()
        assertThat(Coordinate.isSame(BigDecimal.ONE, null)).isFalse()
    }
}
