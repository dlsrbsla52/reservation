package com.media.bus.content.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.content.ContentTestDatabase
import com.media.bus.content.dto.InquiryCreateRequest
import com.media.bus.content.dto.InquiryStatusCommand
import com.media.bus.content.entity.InquiryTable
import com.media.bus.content.entity.enums.InquiryStatus
import com.media.bus.content.guard.InquiryRateLimiter
import com.media.bus.content.repository.InquiryRepository
import com.media.bus.content.result.ContentResult
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InquiryServiceTest {
    private lateinit var database: Database
    private val rateLimiter = mockk<InquiryRateLimiter>()
    private val service = InquiryService(InquiryRepository(), rateLimiter)

    @BeforeAll
    fun setup() {
        database = ContentTestDatabase.connect("inquiry-service")
    }

    @BeforeEach
    fun reset() {
        ContentTestDatabase.clear(database)
        clearMocks(rateLimiter)
        justRun { rateLimiter.check(any()) }
    }

    @Test
    fun `동의한 문의는 접수 상태로 저장하고 접수 IP를 남긴다`() {
        transaction(database) {
            service.create(request(), "203.0.113.7")

            val saved = service.getAdminPage(null, 0, 20).items.single()
            assertThat(saved.status).isEqualTo(InquiryStatus.RECEIVED)
            assertThat(saved.clientIp).isEqualTo("203.0.113.7")
            verify { rateLimiter.check("203.0.113.7") }
        }
    }

    @Test
    fun `개인정보 동의가 없으면 호출 제한을 소모하지 않고 거절한다`() {
        transaction(database) {
            val ex = assertThrows<BusinessException> { service.create(request().copy(privacyAgreed = false), "203.0.113.7") }
            assertThat(ex.result).isEqualTo(ContentResult.PRIVACY_CONSENT_REQUIRED)
            val missing = assertThrows<BusinessException> { service.create(request().copy(privacyAgreed = null), "203.0.113.7") }
            assertThat(missing.result).isEqualTo(ContentResult.PRIVACY_CONSENT_REQUIRED)
            verify(exactly = 0) { rateLimiter.check(any()) }
        }
    }

    @Test
    fun `형식이 틀린 입력은 검증에서 거절한다`() {
        transaction(database) {
            val ex = assertThrows<BusinessException> { service.create(request().copy(email = "not-mail"), null) }
            assertThat(ex.result).isEqualTo(CommonResult.VALIDATION_FAIL)
        }
    }

    @Test
    fun `호출 제한에 걸리면 저장하지 않는다`() {
        transaction(database) {
            every { rateLimiter.check(any()) } throws BusinessException(ContentResult.INQUIRY_RATE_LIMITED)
            assertThrows<BusinessException> { service.create(request(), "203.0.113.7") }
            assertThat(service.getAdminPage(null, 0, 20).totalCnt).isZero()
        }
    }

    @Test
    fun `처리 상태를 바꾸면 메모와 처리자를 기록하고 상태로 거를 수 있다`() {
        transaction(database) {
            service.create(request(), null)
            service.create(request().copy(name = "두번째"), null)
            val target = service.getAdminPage(null, 0, 20).items.first { it.name == "두번째" }
            val adminId = UUID.randomUUID()

            val changed = service.changeStatus(target.id, InquiryStatusCommand("DONE", " 전화 안내 완료 "), adminId)

            assertThat(changed.status).isEqualTo(InquiryStatus.DONE)
            assertThat(changed.adminMemo).isEqualTo("전화 안내 완료")
            assertThat(changed.handledById).isEqualTo(adminId)
            assertThat(service.getAdminPage("RECEIVED", 0, 20).items.map { it.name }).containsExactly("홍길동")
            val bad = assertThrows<BusinessException> { service.changeStatus(target.id, InquiryStatusCommand("CLOSED"), adminId) }
            assertThat(bad.result).isEqualTo(ContentResult.INVALID_INQUIRY_STATUS)
        }
    }

    @Test
    fun `메모를 생략하면 기존 메모를 유지하고 빈 문자열이면 지우며 값이 있으면 다듬어 저장한다`() {
        transaction(database) {
            service.create(request(), null)
            val id = service.getAdminPage(null, 0, 20).items.single().id
            val adminId = UUID.randomUUID()

            service.changeStatus(id, InquiryStatusCommand("RECEIVED", " 첫 메모 "), adminId)
            assertThat(service.changeStatus(id, InquiryStatusCommand("DONE", null), adminId).adminMemo).isEqualTo("첫 메모")
            assertThat(service.changeStatus(id, InquiryStatusCommand("DONE", ""), adminId).adminMemo).isNull()
            assertThat(service.changeStatus(id, InquiryStatusCommand("DONE", "   "), adminId).adminMemo).isNull()
            assertThat(service.changeStatus(id, InquiryStatusCommand("DONE", " 새 메모 "), adminId).adminMemo).isEqualTo("새 메모")
        }
    }

    @Test
    fun `문의를 삭제하면 목록에서 사라지고 없는 문의는 404 코드로 거절한다`() {
        transaction(database) {
            service.create(request(), null)
            val id = service.getAdminPage(null, 0, 20).items.single().id

            service.delete(id)

            assertThat(service.getAdminPage(null, 0, 20).totalCnt).isZero()
            val ex = assertThrows<BusinessException> { service.delete(id) }
            assertThat(ex.result).isEqualTo(ContentResult.INQUIRY_NOT_FOUND)
        }
    }

    @Test
    fun `보관 기간이 지난 처리 완료 문의만 파기하고 건수를 돌려준다`() {
        transaction(database) {
            val adminId = UUID.randomUUID()
            repeat(4) { service.create(request().copy(name = "문의$it"), null) }
            val items = service.getAdminPage(null, 0, 20).items.associateBy { it.name }
            val oldDone = items.getValue("문의0").id
            val recentDone = items.getValue("문의1").id
            val oldReceived = items.getValue("문의2").id
            listOf(oldDone, recentDone).forEach { service.changeStatus(it, InquiryStatusCommand("DONE"), adminId) }
            val now = OffsetDateTime.now()
            InquiryTable.update({ InquiryTable.id eq oldDone }) { it[handledAt] = now.minusDays(91) }
            InquiryTable.update({ InquiryTable.id eq oldReceived }) { it[createdAt] = now.minusDays(200) }

            val deleted = service.purgeHandledBefore(now.minusDays(90))

            assertThat(deleted).isEqualTo(1)
            assertThat(service.getAdminPage(null, 0, 20).items.map { it.id }).doesNotContain(oldDone).contains(recentDone, oldReceived)
        }
    }

    private fun request() = InquiryCreateRequest(
        name = "홍길동",
        phone = "010-1234-5678",
        email = "user@example.com",
        content = "광고 단가 문의드립니다.",
        privacyAgreed = true,
    )
}
