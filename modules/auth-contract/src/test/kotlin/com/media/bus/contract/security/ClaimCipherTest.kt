package com.media.bus.contract.security

import com.media.bus.common.exceptions.ServiceException
import com.media.bus.common.result.type.CommonResult
import io.jsonwebtoken.MalformedJwtException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.*

class ClaimCipherTest {

    private val cipher = ClaimCipher("dGVzdC1vbmx5LWR1bW15LWNsYWltLWtleS0zMmJ5dGU=")

    @Test
    fun `암호화한 값을 복호화하면 원문이 된다`() {
        val plain = "admin@example.com"

        assertThat(cipher.decrypt(cipher.encrypt(plain))).isEqualTo(plain)
    }

    @Test
    fun `같은 평문도 매번 다른 암호문이 된다`() {
        assertThat(cipher.encrypt("ADMIN_MASTER")).isNotEqualTo(cipher.encrypt("ADMIN_MASTER"))
    }

    @Test
    fun `암호문을 변조하면 복호화가 실패한다`() {
        val encrypted = cipher.encrypt("MEMBER")
        val tampered = encrypted.dropLast(1) + if (encrypted.last() == 'A') 'B' else 'A'

        assertThatThrownBy { cipher.decrypt(tampered) }.isInstanceOf(MalformedJwtException::class.java)
    }

    @Test
    fun `다른 키로 암호화한 값은 복호화가 실패한다`() {
        val other = ClaimCipher(Base64.getEncoder().encodeToString(ByteArray(32) { 1 }))

        assertThatThrownBy { cipher.decrypt(other.encrypt("MEMBER")) }.isInstanceOf(MalformedJwtException::class.java)
    }

    @Test
    fun `평문을 복호화하려 하면 실패한다`() {
        assertThatThrownBy { cipher.decrypt(UUID.randomUUID().toString()) }.isInstanceOf(MalformedJwtException::class.java)
    }

    @Test
    fun `키가 32바이트가 아니면 기동 시점에 실패한다`() {
        assertThatThrownBy { ClaimCipher(Base64.getEncoder().encodeToString(ByteArray(16))) }
            .isInstanceOf(ServiceException::class.java)
            .extracting("result").isEqualTo(CommonResult.CLAIM_ENCRYPTION_KEY_INVALID_LENGTH)
    }
}
