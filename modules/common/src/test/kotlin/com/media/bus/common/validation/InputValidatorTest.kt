package com.media.bus.common.validation

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.type.CommonResult
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.math.BigDecimal

class InputValidatorTest {

    private data class SampleRequest(
        @field:NotBlank
        @field:Size(max = 5)
        val name: String,
        @field:Positive
        val amount: BigDecimal?,
    )

    @Test
    fun `제약을 모두 만족하면 통과한다`() {
        assertDoesNotThrow { InputValidator.validate(SampleRequest("abc", BigDecimal.ONE)) }
        // nullable 필드는 @Positive 만 있으면 null 허용
        assertDoesNotThrow { InputValidator.validate(SampleRequest("abc", null)) }
    }

    @Test
    fun `빈 문자열은 400 VALIDATION_FAIL 과 기본 메시지로 거절한다`() {
        val ex = assertThrows<BusinessException> { InputValidator.validate(SampleRequest(" ", BigDecimal.ONE)) }
        assertEquals(CommonResult.VALIDATION_FAIL, ex.result)
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
        // 기존 S2S 경로와 같은 외부 메시지가 나가도록 기본 메시지를 쓴다
        assertEquals(CommonResult.VALIDATION_FAIL.message, ex.message)
    }

    @Test
    fun `길이 초과와 0 이하 금액을 거절한다`() {
        assertThrows<BusinessException> { InputValidator.validate(SampleRequest("abcdef", BigDecimal.ONE)) }
        assertThrows<BusinessException> { InputValidator.validate(SampleRequest("abc", BigDecimal.ZERO)) }
        assertThrows<BusinessException> { InputValidator.validate(SampleRequest("abc", BigDecimal("-1"))) }
    }
}
