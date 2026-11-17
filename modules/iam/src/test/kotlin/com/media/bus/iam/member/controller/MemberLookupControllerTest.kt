package com.media.bus.iam.member.controller

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.web.advisor.ExceptionAdvisor
import com.media.bus.iam.auth.result.AuthResult
import com.media.bus.iam.member.dto.FindLoginIdResponse
import com.media.bus.iam.member.guard.LoginIdLookupRateLimiter
import com.media.bus.iam.member.service.MemberService
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class MemberLookupControllerTest {
    private val service = mockk<MemberService>()
    private val limiter = mockk<LoginIdLookupRateLimiter>()
    private val mvc = MockMvcBuilders.standaloneSetup(MemberController(service, limiter))
        .setControllerAdvice(ExceptionAdvisor()).build()
    private val body = """{"memberName":"홍길동","phoneNumber":"01000000000"}"""

    @Test
    fun `POST 본문을 받아 제한 확인 후 아이디를 반환한다`() {
        justRun { limiter.check(any(), any()) }
        every { service.findMe(any()) } returns FindLoginIdResponse("dummy-login")
        mvc.perform(post("/api/v1/member/find/me").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.loginId").value("dummy-login"))
        io.mockk.verifyOrder { limiter.check(any(), any()); service.findMe(any()) }
    }

    @Test
    fun `이전 GET 호출은 405이며 POST를 안내한다`() {
        mvc.perform(get("/api/v1/member/find/me")).andExpect(status().isMethodNotAllowed)
            .andExpect(header().string("Allow", "POST"))
    }

    @Test
    fun `제한 초과는 429이고 회원 조회를 실행하지 않는다`() {
        every { limiter.check(any(), any()) } throws BusinessException(AuthResult.LOGIN_ID_LOOKUP_RATE_LIMITED)
        mvc.perform(post("/api/v1/member/find/me").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isTooManyRequests).andExpect(jsonPath("$.code").value("A0022"))
        verify(exactly = 0) { service.findMe(any()) }
    }
}
