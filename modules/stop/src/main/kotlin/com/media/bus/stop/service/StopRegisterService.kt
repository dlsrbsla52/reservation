package com.media.bus.stop.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.stop.client.SeoulBusApiClient
import com.media.bus.stop.dto.response.StopBulkRegisterResult
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.http.HttpStatus
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

        /**
         * 락 TTL. 정상 완료 시에는 즉시 해제하고, 프로세스가 죽어 해제하지 못했을 때만 이 시간 뒤 풀린다.
         * 전체 적재(약 1만 건, 1000건 페이지)가 수 분이면 끝나므로 넉넉히 30분으로 둔다.
         */
        private val LOCK_TTL: Duration = Duration.ofMinutes(30)

        /** 내가 잡은 락일 때만 지운다 — TTL 만료 후 다른 실행이 잡은 락을 지우지 않기 위함. */
        private val RELEASE_SCRIPT: RedisScript<Long> = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long::class.java,
        )
    }

    /**
     * 공공 API 전체 정류소 일괄 등록 — **동시에 하나만 실행**한다.
     *
     * 관리자의 재시도·중복 클릭이나 타임아웃 후 재요청으로 같은 작업이 겹치면
     * 같은 stopId 를 동시에 insert/update 해 유니크 위반·중복 변경 이력이 생긴다.
     * 인스턴스가 여러 대여도 막히도록 Redis 락(SET NX + TTL)을 쓴다.
     *
     * @throws BusinessException 이미 실행 중이면 409 CONFLICT
     */
    fun registerAllFromPublicApi(): StopBulkRegisterResult {
        val token = UUID.randomUUID().toString()
        val acquired = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, token, LOCK_TTL) == true
        if (!acquired) {
            throw BusinessException(HttpStatus.CONFLICT, "정류소 일괄 등록이 이미 진행 중입니다. 완료 후 다시 시도해주세요.")
        }
        try {
            return runBulkRegister()
        } finally {
            redisTemplate.execute(RELEASE_SCRIPT, listOf(LOCK_KEY), token)
        }
    }

    /**
     * 서울 열린데이터광장 공공 API에서 전체 버스 정류소를 가져와 DB에 저장한다.
     * - 신규 stopId → insert
     * - 기존 stopId + 필드 변경 → Stop 업데이트 + StopUpdateHistory 기록
     * - 기존 stopId + 변경 없음 → 건너뜀
     * - 좌표 파싱/범위 검증 실패 → 등록 거부 후 WARN 로그 (배치는 계속 진행)
     *
     * `@Transactional` 없음 — `StopBulkPersistService`가 `REQUIRES_NEW`로 페이지별 독립 트랜잭션을 생성한다.
     * 전체 루프를 단일 트랜잭션으로 묶으면 장시간 DB 커넥션 점유 및 중간 실패 시 전체 롤백이 발생한다.
     */
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

            // 페이지 단위 독립 트랜잭션 커밋 — 중간 실패 시 해당 페이지만 롤백
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
