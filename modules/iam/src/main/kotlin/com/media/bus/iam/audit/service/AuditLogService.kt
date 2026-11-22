package com.media.bus.iam.audit.service

import com.media.bus.iam.audit.entity.AuditLogEntity
import com.media.bus.iam.audit.entity.enumerated.AuditActorType
import com.media.bus.iam.audit.entity.enumerated.AuditResult
import com.media.bus.iam.support.ClientIpResolver
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.*

/** 감사 로그 기록 서비스 */
@Service
class AuditLogService {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 감사 이벤트를 기록한다. */
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
                ip = ClientIpResolver.resolve(request),
                userAgent = request?.getHeader("User-Agent")?.take(500),
                result = result,
                detail = detail,
            )
        } catch (e: Exception) {
            log.error(
                "[AuditLogService.record] 감사 로그 적재 실패. action={}, actorId={}, result={}, cause={}",
                action, actorId, result, e.message, e,
            )
        }
    }

    /** 성공 이벤트 기록 — 가독성을 위한 헬퍼. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun success(
        actorId: UUID?,
        actorType: AuditActorType,
        action: String,
        targetType: String? = null,
        targetId: String? = null,
        detail: String? = null,
    ) = record(actorId, actorType, action, targetType, targetId, AuditResult.SUCCESS, detail)

    /** 실패 이벤트 기록 — 로그인 실패, 비밀번호 불일치 등. [success] 와 동일한 Self-invocation 회피 사유로 어노테이션 부여. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
}
