package com.media.bus.iam.auth.guard

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.iam.auth.dto.PasswordResetRequest
import com.media.bus.iam.auth.result.AuthResult
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.function.Consumer

@DisplayName("PasswordResetValidator 단위 테스트")
class PasswordResetValidatorTest {

    private val validator = PasswordResetValidator()

    @Test
    fun `loginId만 있으면 통과`() {
        val request = PasswordResetRequest(loginId = "testuser")

        assertThatNoException().isThrownBy { validator.validate(request) }
    }

    @Test
    fun `email만 있으면 통과`() {
        val request = PasswordResetRequest(email = "test@example.com")

        assertThatNoException().isThrownBy { validator.validate(request) }
    }

    @Test
    fun `둘 다 있으면 통과`() {
        val request = PasswordResetRequest(loginId = "testuser", email = "test@example.com")

        assertThatNoException().isThrownBy { validator.validate(request) }
    }

    @Test
    fun `둘 다 null`() {
        val request = PasswordResetRequest()

        assertThatThrownBy { validator.validate(request) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies(Consumer { ex ->
                assertThat((ex as BusinessException).result).isEqualTo(AuthResult.PASSWORD_RESET_IDENTIFIER_REQUIRED)
            })
    }

    @Test
    fun `둘 다 빈 문자열`() {
        val request = PasswordResetRequest(loginId = "  ", email = "")

        assertThatThrownBy { validator.validate(request) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies(Consumer { ex ->
                assertThat((ex as BusinessException).result).isEqualTo(AuthResult.PASSWORD_RESET_IDENTIFIER_REQUIRED)
            })
    }
}
