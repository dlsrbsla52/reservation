package com.media.bus.common.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * ## 액세스 로그 본문 마스킹
 *
 * 필드명을 정확히 일치로 비교하던 때는 `newPassword`·`currentPassword`(비밀번호 변경 요청)가
 * 400 응답이나 DEBUG 토글 시 평문으로 남았다. 필드명에 키워드가 포함되면 가리는지 고정한다.
 */
@DisplayName("AccessLoggingFilter 마스킹")
class AccessLoggingFilterMaskingTest {

    private val filter = AccessLoggingFilter()

    @Test
    fun `비밀번호 변경 요청의 currentPassword 와 newPassword 를 가린다`() {
        val body = """{"currentPassword":"old-secret","newPassword":"new-secret","loginId":"user1"}"""

        val masked = filter.maskSensitive(body)

        assertThat(masked).doesNotContain("old-secret", "new-secret")
        assertThat(masked).contains(""""currentPassword":"***"""", """"newPassword":"***"""", """"loginId":"user1"""")
    }

    @Test
    fun `기존 대상 필드(password, accessToken, refreshToken, token)도 계속 가린다`() {
        val body = """{"password":"p1","accessToken":"a1","refreshToken":"r1","token":"t1"}"""

        val masked = filter.maskSensitive(body)

        assertThat(masked).doesNotContain("p1", "a1", "r1", "t1")
    }

    @Test
    fun `대소문자와 콜론 주변 공백이 달라도 가린다`() {
        val masked = filter.maskSensitive("""{"NewPassword" : "abc123"}""")

        assertThat(masked).doesNotContain("abc123")
    }

    @Test
    fun `값에 escape 된 따옴표가 있어도 뒷부분이 남지 않는다`() {
        val masked = filter.maskSensitive("""{"newPassword":"ab\"cd-tail","x":"y"}""")

        assertThat(masked).doesNotContain("cd-tail")
        assertThat(masked).contains(""""x":"y"""")
    }

    @Test
    fun `폼·쿼리스트링 형식의 변형 필드도 가린다`() {
        val masked = filter.maskSensitive("loginId=user1&newPassword=abc&currentPassword=def")

        assertThat(masked).isEqualTo("loginId=user1&newPassword=***&currentPassword=***")
    }

    @Test
    fun `민감하지 않은 필드는 그대로 둔다`() {
        val body = """{"loginId":"user1","email":"a@b.com"}"""

        assertThat(filter.maskSensitive(body)).isEqualTo(body)
    }
    @Test
    fun `캐시 끝에서 잘린 문자열과 숫자 객체 배열 값을 가린다`() {
        val truncated = "{\"newPassword\":\"" + "secret".repeat(500)
        assertThat(filter.maskSensitive(truncated)).isEqualTo("{\"newPassword\":\"***\"")
        val body = """{"password":12345678,"refreshToken":{"value":"secret"},"accessToken":["hidden"],"ok":true}"""
        assertThat(filter.maskSensitive(body)).isEqualTo(
            """{"password":"***","refreshToken":"***","accessToken":"***","ok":true}""",
        )
    }

    @Test
    fun `인코딩된 쿼리 키와 빈 값도 가린다`() {
        assertThat(filter.maskQueryString("new%50assword=secret&token=&q=normal"))
            .isEqualTo("new%50assword=***&token=***&q=normal")
    }

    @Test
    fun `구분자 없는 긴 쿼리도 제한 시간 안에 처리한다`() {
        val query = "token".repeat(1600)
        org.junit.jupiter.api.Assertions.assertTimeout(java.time.Duration.ofSeconds(2)) {
            repeat(100) { assertThat(filter.maskQueryString(query)).isEqualTo(query) }
        }
    }

}
