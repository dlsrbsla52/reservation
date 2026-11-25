package com.media.bus.content.scheduler

import com.media.bus.content.service.InquiryService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime

/** 처리 완료 후 보관 기간이 지난 온라인 문의를 매일 삭제하는 스케줄러 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "content.inquiry", name = ["purge-enabled"], havingValue = "true", matchIfMissing = true)
class InquiryPurgeScheduler(
    private val inquiryService: InquiryService,
    @param:Value("\${content.inquiry.retention:90d}") private val retention: Duration,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 매일 새벽에 보관 기간이 지난 처리 완료 문의를 지운다. */
    @Scheduled(cron = "\${content.inquiry.purge-cron:0 30 3 * * *}", zone = "Asia/Seoul")
    fun purgeExpiredInquiries() {
        try {
            val cutoff = OffsetDateTime.now(Clock.systemUTC()).minus(retention)
            val deleted = inquiryService.purgeHandledBefore(cutoff)
            log.info("[InquiryPurgeScheduler] 보관 기간이 지난 문의 {}건 삭제", deleted)
        } catch (e: Exception) {
            log.error("[InquiryPurgeScheduler] 문의 파기 실패", e)
        }
    }
}
