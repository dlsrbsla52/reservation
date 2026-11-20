package com.media.bus.stop.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.stop.client.SeoulBusApiClient
import com.media.bus.stop.dto.response.StopBulkRegisterResult
import com.media.bus.stop.result.StopResult
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*

@Service
class StopRegisterService(
    private val seoulBusApiClient: SeoulBusApiClient,
    private val stopBulkPersistService: StopBulkPersistService,
    private val redisTemplate: StringRedisTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val LOCK_KEY = "stop:bulk-register:lock"

        private val LOCK_TTL: Duration = Duration.ofMinutes(30)

        private val RELEASE_SCRIPT: RedisScript<Long> = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long::class.java,
        )
    }

    /** 공공 API 전체 정류소 일괄 등록 — **동시에 하나만 실행**한다. */
    fun registerAllFromPublicApi(): StopBulkRegisterResult {
        val token = UUID.randomUUID().toString()
        val acquired = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, token, LOCK_TTL) == true
        if (!acquired) {
            throw BusinessException(StopResult.BULK_REGISTER_IN_PROGRESS)
        }
        try {
            return runBulkRegister()
        } finally {
            redisTemplate.execute(RELEASE_SCRIPT, listOf(LOCK_KEY), token)
        }
    }

    /** 서울 열린데이터광장 공공 API에서 전체 버스 정류소를 가져와 DB에 저장한다. */
    private fun runBulkRegister(): StopBulkRegisterResult {
        val totalCount = seoulBusApiClient.fetchTotalCount()
        val pageSize = seoulBusApiClient.pageSize
        log.info("서울 공공 API 전체 정류소 수: {}", totalCount)

        var savedCount = 0
        var updatedCount = 0
        var skippedCount = 0
        var failedCount = 0

        var start = 1
        while (start <= totalCount) {
            val end = minOf(start + pageSize - 1, totalCount)
            val rows = seoulBusApiClient.fetchStops(start, end)

            val result = stopBulkPersistService.persistPage(rows)
            savedCount += result.saved
            updatedCount += result.updated
            skippedCount += result.skipped
            failedCount += result.failed

            log.info(
                "진행: {}/{} — 신규 {}건, 업데이트 {}건, 변경없음 {}건, 거부 {}건",
                end, totalCount, result.saved, result.updated, result.skipped, result.failed,
            )
            start += pageSize
        }

        log.info(
            "일괄 등록 완료 — 저장: {}건, 업데이트: {}건, 건너뜀: {}건, 거부: {}건",
            savedCount, updatedCount, skippedCount, failedCount,
        )
        return StopBulkRegisterResult(savedCount, updatedCount, skippedCount, failedCount, totalCount)
    }
}
