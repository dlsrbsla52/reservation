package com.media.bus.app.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiPathPolicyTest {

    @Test
    fun `라우트 prefix 는 정확히 일치하거나 슬래시로 이어져야 한다`() {
        assertThat(ApiPathPolicy.isRoutable("/api/v1/stop", false)).isTrue()
        assertThat(ApiPathPolicy.isRoutable("/api/v1/stop/map/bounds", false)).isTrue()
        assertThat(ApiPathPolicy.isRoutable("/api/v1/stopx", false)).isFalse()
        assertThat(ApiPathPolicy.isRoutable("/api/v1/internal/stop", false)).isFalse()
    }

    @Test
    fun `관리자 경로 판정`() {
        assertThat(ApiPathPolicy.isAdmin("/api/v1/admin")).isTrue()
        assertThat(ApiPathPolicy.isAdmin("/api/v1/admin/stop/register/bulk")).isTrue()
        assertThat(ApiPathPolicy.isAdmin("/api/v1/administrator")).isFalse()
        assertThat(ApiPathPolicy.isAdmin("/api/v1/auth/admin/login")).isFalse()
    }

    @Test
    fun `핸들러가 없던 health-check 는 공개 경로가 아니다(보호 핸들러 reservation 상세에 매칭되던 예외 제거)`() {
        listOf("auth", "member", "reservation", "stop").forEach {
            assertThat(ApiPathPolicy.isPublic("GET", "/api/v1/$it/health-check")).describedAs(it).isFalse()
        }
        assertThat(ApiPathPolicy.isPublic("POST", "/api/v1/auth/login")).isTrue()
    }

    @Test
    fun `문서 경로는 노출 설정이 켜졌을 때만 라우팅된다`() {
        assertThat(ApiPathPolicy.isRoutable("/swagger-ui/index.html", false)).isFalse()
        assertThat(ApiPathPolicy.isRoutable("/swagger-ui/index.html", true)).isTrue()
        assertThat(ApiPathPolicy.isRoutable("/api-docs.yaml", true)).isTrue()
    }

    @Test
    fun `아이디 찾기는 POST만 공개한다`() {
        assertThat(ApiPathPolicy.isPublic("POST", "/api/v1/member/find/me")).isTrue()
        assertThat(ApiPathPolicy.isPublic("GET", "/api/v1/member/find/me")).isFalse()
    }
}
