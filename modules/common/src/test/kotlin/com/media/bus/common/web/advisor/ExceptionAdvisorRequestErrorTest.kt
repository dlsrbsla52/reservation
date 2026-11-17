package com.media.bus.common.web.advisor

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.*

/**
 * ## 요청 경로·메서드·형식 오류의 응답 코드
 *
 * 이 처리기들이 없으면 `@ExceptionHandler(Exception::class)` catch-all이 먼저 잡아서
 * 클라이언트 오류(404·405·415·400)가 모두 500/`00100`과 ERROR 스택트레이스가 된다.
 */
@DisplayName("ExceptionAdvisor — 요청 경로·형식 오류")
class ExceptionAdvisorRequestErrorTest {

    @RestController
    class ProbeController {
        @GetMapping("/probe")
        fun get(@RequestParam("keyword") keyword: String): String = keyword

        @PostMapping("/probe/json", consumes = [MediaType.APPLICATION_JSON_VALUE])
        fun post(@RequestBody body: Map<String, Any>): String = body.keys.joinToString()
    }

    private val mockMvc = MockMvcBuilders.standaloneSetup(ProbeController())
        .setControllerAdvice(ExceptionAdvisor())
        .build()

    @Test
    fun `매핑이 없는 경로는 500 이 아니라 404 ROUTE_NOT_FOUND 다`() {
        mockMvc.perform(get("/nope"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("00280"))
    }

    @Test
    fun `지원하지 않는 메서드는 405 이고 Allow 헤더로 허용 메서드를 알린다`() {
        mockMvc.perform(post("/probe"))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(header().string("Allow", "GET"))
            .andExpect(jsonPath("$.code").value("00281"))
    }

    @Test
    fun `지원하지 않는 Content-Type 은 415 다`() {
        mockMvc.perform(post("/probe/json").contentType(MediaType.TEXT_PLAIN).content("x"))
            .andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.code").value("00282"))
    }

    @Test
    fun `필수 요청 파라미터가 없으면 400 VALIDATION_FAIL 이고 필드를 알려 준다`() {
        mockMvc.perform(get("/probe"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("00260"))
            .andExpect(jsonPath("$.errors[0].field").value("keyword"))
    }
}
