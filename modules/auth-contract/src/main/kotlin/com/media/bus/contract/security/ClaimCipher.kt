package com.media.bus.contract.security

import io.jsonwebtoken.MalformedJwtException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ## JWT 클레임 값 단위 양방향 암호화 (AES-256-GCM)
 *
 * JWS 페이로드는 Base64 인코딩일 뿐이라 누구나 읽을 수 있다.
 * 회원 식별자·개인정보·권한 클레임 값을 이 컴포넌트로 암호화해서 넣고, 파싱할 때 복호화한다.
 *
 * - 형식: `Base64Url(IV 12바이트 + 암호문 + 인증 태그 16바이트)` (패딩 없음)
 * - 값마다 IV를 새로 만들므로 같은 평문도 매번 다른 암호문이 된다(토큰 간 값 비교로 동일 회원 추적 방지).
 * - GCM 인증 태그가 변조를 검출한다. 토큰 서명과 별개로 키가 다르면 복호화가 실패한다.
 * - `Cipher`는 스레드 안전하지 않아 호출마다 새로 만든다. `ThreadLocal` 캐시는 Virtual Thread 규칙상 쓰지 않는다.
 *
 * @param base64Key Base64로 인코딩한 32바이트 키 (`openssl rand -base64 32`)
 */
class ClaimCipher(base64Key: String) {

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BYTES = 32
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }

    private val key: SecretKeySpec = run {
        val raw = try {
            Base64.getDecoder().decode(base64Key.trim())
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("jwt.claim-encryption-key 가 Base64 형식이 아닙니다.", e)
        }
        check(raw.size == KEY_BYTES) {
            "jwt.claim-encryption-key 는 32바이트(AES-256)여야 합니다. 현재 ${raw.size}바이트. `openssl rand -base64 32`로 생성하세요."
        }
        SecretKeySpec(raw, "AES")
    }

    private val random = SecureRandom()

    fun encrypt(plain: String): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        val out = ByteBuffer.allocate(iv.size + encrypted.size).put(iv).put(encrypted).array()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out)
    }

    /**
     * 암호문을 복호화한다.
     *
     * 형식 오류·키 불일치·변조는 모두 [MalformedJwtException]으로 던진다.
     * `JwtProvider.tryParseClaims`가 서명 오류와 같은 경로(`null`)로 처리하게 하려는 것이다.
     */
    fun decrypt(encoded: String): String =
        try {
            val bytes = Base64.getUrlDecoder().decode(encoded)
            require(bytes.size > IV_BYTES) { "암호문 길이가 너무 짧습니다." }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), StandardCharsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            throw MalformedJwtException("암호화 클레임 복호화 실패", e)
        } catch (e: IllegalArgumentException) {
            throw MalformedJwtException("암호화 클레임 형식 오류", e)
        }
}
