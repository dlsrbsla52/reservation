package com.media.bus.iam.audit.service

import com.media.bus.iam.audit.entity.AuditLogEntity
import com.media.bus.iam.audit.entity.enumerated.AuditActorType
import com.media.bus.iam.audit.entity.enumerated.AuditResult
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.*

/**
 * ## 감사 로그 기록 서비스
 *
 * 민감 작업 수행 지점에서 호출하여 `audit.audit_log` 테이블에 이벤트를 적재한다.
 *
 * **설계 결정**
 * - AOP 방식 대신 **명시적 호출(imperative)** 로 구현하여 프록시·컨텍스트 손실 위험을 회피한다.
 * - 요청 IP/User-Agent는 [RequestContextHolder]에서 자동 추출하므로 호출부에서 신경 쓰지 않아도 된다.
 * - 쓰기 실패가 원 트랜잭션을 망가뜨리지 않도록 `Propagation.REQUIRES_NEW`로 분리한다.
 * - 로그 적재 자체가 실패해도 업무 로직은 계속 진행되도록 예외를 삼킨다 — 단, 에러 로그는 남긴다.
 *
 * **Virtual Threads 주의**
 * [RequestContextHolder]는 기본적으로 InheritableThreadLocal을 사용한다. MDC와 동일하게
 * 요청 처리 스레드 내에서만 동작하며, 수동으로 생성한 스레드에는 전파되지 않는다.
 */
@Service
class AuditLogService {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 감사 이벤트를 기록한다.
     *
     * @param actorId    행위자 회원 ID — 미인증/시스템 이벤트는 null
     * @param actorType  행위자 유형
     * @param action     동작 식별자 ([com.media.bus.iam.audit.AuditAction] 상수 사용)
     * @param targetType 대상 객체 유형 — 선택
     * @param targetId   대상 객체 식별자 (UUID 또는 코드) — 선택
     * @param result     처리 결과 (기본 SUCCESS)
     * @param detail     추가 상세 정보 JSON — 실패 사유, 변경 전후 값 등
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(
        actorId: UUID?,
        actorType: AuditActorType,
        action: String,
        targetType: String? = null,
        targetId: String? = null,
        result: AuditResult = AuditResult.SUCCESS,
        detail: String? = null,
    ) {
        try {
            val request = currentRequest()
            AuditLogEntity.create(
                actorId = actorId,
                actorType = actorType,
                action = action,
                targetType = targetType,
                targetId = targetId,
                ip = request?.let(::extractClientIp),
                userAgent = request?.getHeader("User-Agent")?.take(500),
                result = result,
                detail = detail,
            )
        } catch (e: Exception) {
            // 감사 로그 실패가 비즈니스 흐름을 끊지 않도록 예외를 삼킨다.
            // 단, 이벤트가 유실되지 않도록 반드시 에러 로그로 관측 가능하게 남긴다.
            log.error(
                "[AuditLogService.record] 감사 로그 적재 실패. action={}, actorId={}, result={}, cause={}",
                action, actorId, result, e.message, e,
            )
        }
    }

    /** 성공 이벤트 기록 — 가독성을 위한 헬퍼. */
    fun success(
        actorId: UUID?,
        actorType: AuditActorType,
        action: String,
        targetType: String? = null,
        targetId: String? = null,
        detail: String? = null,
    ) = record(actorId, actorType, action, targetType, targetId, AuditResult.SUCCESS, detail)

    /** 실패 이벤트 기록 — 로그인 실패, 비밀번호 불일치 등. */
    fun failure(
        actorId: UUID?,
        actorType: AuditActorType,
        action: String,
        targetType: String? = null,
        targetId: String? = null,
        detail: String? = null,
    ) = record(actorId, actorType, action, targetType, targetId, AuditResult.FAILURE, detail)

    private fun currentRequest(): HttpServletRequest? =
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request

    private fun extractClientIp(request: HttpServletRequest): String? {
        val forwarded = request.getHeader("X-Forwarded-For")
        if (!forwarded.isNullOrBlank()) {
            return forwarded.substringBefore(",").trim().ifBlank { null }
        }
        return request.remoteAddr
    }
}
