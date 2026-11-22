package com.media.bus.stop.client

import com.media.bus.common.exceptions.ServiceException
import com.media.bus.stop.dto.external.SeoulBusStopApiResponse
import com.media.bus.stop.dto.external.SeoulBusStopRow
import com.media.bus.stop.result.StopResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/** 서울 열린데이터광장 버스 정류소 공공 API 클라이언트 */
@Component
class SeoulBusApiClient(
    @param:Value("\${seoul.api.key}") private val apiKey: String,
    @Value("\${seoul.api.base-url:http://openapi.seoul.go.kr:8088}") baseUrl: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    val pageSize: Int = PAGE_SIZE

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build()

    /** 전체 정류소 수를 조회한다 (1건만 요청해 totalCount 추출) */
    fun fetchTotalCount(): Int = callBody(1, 1).totalCount

    /** startIndex ~ endIndex 범위의 정류소 목록을 조회한다 (1-based index) */
    fun fetchStops(startIndex: Int, endIndex: Int): List<SeoulBusStopRow> =
        callBody(startIndex, endIndex).rows ?: emptyList()

    private fun callBody(start: Int, end: Int): SeoulBusStopApiResponse.Body {
        log.debug("서울 공공 API 호출: start={}, end={}", start, end)

        val response = restClient.get()
            .uri("/{apiKey}/json/busStopLocationXyInfo/{start}/{end}/", apiKey, start, end)
            .retrieve()
            .body(SeoulBusStopApiResponse::class.java)
            ?: throw ServiceException(StopResult.SEOUL_API_PARSE_FAIL)

        val body = response.busStopInfo
            ?: throw ServiceException(StopResult.SEOUL_API_PARSE_FAIL)

        body.result?.let { result ->
            if (!result.isSuccess()) {
                log.error("[SeoulBusApiClient] 서울 공공 API 오류: code={}, message={}", result.code, result.message)
                throw ServiceException(StopResult.SEOUL_API_ERROR)
            }
        }

        return body
    }

    companion object {
        private const val PAGE_SIZE = 1000
    }
}
