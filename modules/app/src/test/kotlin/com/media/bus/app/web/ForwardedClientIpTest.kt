package com.media.bus.app.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicReference

/** 신뢰하는 프록시(기본값: 루프백)가 보낸 X-Forwarded-For만 클라이언트 IP로 반영한다 */
@BusAppContextTest
@Import(RemoteAddressTestConfig::class)
class ForwardedClientIpTest {

    @Autowired
    private lateinit var recorder: AtomicReference<String>

    @Value("\${local.server.port}")
    private var port: Int = 0

    @Test
    fun `기본 설정은 루프백 프록시가 보낸 X-Forwarded-For를 클라이언트 IP로 쓴다`() {
        send("203.0.113.9")

        assertThat(recorder.get()).isEqualTo("203.0.113.9")
    }

    private fun send(forwardedFor: String) {
        val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/error"))
            .header("X-Forwarded-For", forwardedFor).GET().build()
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding())
    }
}

/** 신뢰 목록에 없는 주소가 보낸 X-Forwarded-For는 위조로 보고 무시한다 */
@BusAppContextTest
@Import(RemoteAddressTestConfig::class)
@TestPropertySource(properties = ["server.tomcat.remoteip.internal-proxies=192.0.2.10"])
class UntrustedForwardedClientIpTest {

    @Autowired
    private lateinit var recorder: AtomicReference<String>

    @Value("\${local.server.port}")
    private var port: Int = 0

    @Test
    fun `신뢰 목록 밖의 주소가 보낸 X-Forwarded-For는 무시하고 접속한 주소를 쓴다`() {
        val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/error"))
            .header("X-Forwarded-For", "203.0.113.9").GET().build()
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding())

        assertThat(recorder.get()).isEqualTo("127.0.0.1")
    }
}
