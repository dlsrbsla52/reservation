package com.media.bus.content.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.content.ContentTestDatabase
import com.media.bus.content.dto.FaqCommand
import com.media.bus.content.repository.FaqRepository
import com.media.bus.content.result.ContentResult
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FaqServiceTest {
    private lateinit var database: Database
    private val service = FaqService(FaqRepository())

    @BeforeAll
    fun setup() {
        database = ContentTestDatabase.connect("faq-service")
    }

    @BeforeEach
    fun reset() = ContentTestDatabase.clear(database)

    @Test
    fun `새 FAQ는 맨 뒤에 붙고 공개 목록에는 숨긴 항목이 빠진다`() {
        transaction(database) {
            service.create(FaqCommand("MEDIA", "Q1", "A1"))
            service.create(FaqCommand("HOSPITAL", "Q2", "A2", visible = false))
            service.create(FaqCommand("MEDIA", "Q3", "A3"))

            assertThat(service.getPublicList().map { it.question }).containsExactly("Q1", "Q3")
            assertThat(service.getAdminList().map { it.question }).containsExactly("Q1", "Q2", "Q3")
        }
    }

    @Test
    fun `순서 변경과 수정이 반영되고 분류는 정해진 값만 받는다`() {
        transaction(database) {
            val q1 = service.create(FaqCommand("MEDIA", "Q1", "A1"))
            val q2 = service.create(FaqCommand("MEDIA", "Q2", "A2"))

            service.reorder(listOf(q2.id, q1.id))
            service.update(q1.id, FaqCommand("HOSPITAL", "Q1 수정", "A1 수정"))

            assertThat(service.getAdminList().map { it.question }).containsExactly("Q2", "Q1 수정")
            val ex = assertThrows<BusinessException> { service.create(FaqCommand("ETC", "Q", "A")) }
            assertThat(ex.result).isEqualTo(ContentResult.INVALID_CATEGORY)
        }
    }
}
