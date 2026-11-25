package com.media.bus.content.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.content.ContentTestDatabase
import com.media.bus.content.dto.PortfolioCommand
import com.media.bus.content.entity.PortfolioTable
import com.media.bus.content.entity.enums.PortfolioCategory
import com.media.bus.content.repository.ContentImageRepository
import com.media.bus.content.repository.PortfolioRepository
import com.media.bus.content.result.ContentResult
import com.media.bus.content.storage.ContentStorage
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortfolioServiceTest {
    private lateinit var database: Database
    private val storage = mockk<ContentStorage>()
    private val portfolioRepository = PortfolioRepository()
    private val writer = PortfolioWriter(portfolioRepository, ContentImageRepository(), storage)
    private val service = PortfolioService(portfolioRepository, ContentImageService(storage), writer, storage)

    @BeforeAll
    fun setup() {
        database = ContentTestDatabase.connect("portfolio-service")
    }

    @BeforeEach
    fun reset() {
        ContentTestDatabase.clear(database)
        clearMocks(storage)
        every { storage.exists(any()) } returns true
        every { storage.publicUrl(any()) } answers { "https://img.test/${firstArg<String>()}" }
        justRun { storage.deleteAfterCommit(any()) }
    }

    @Test
    fun `새 항목은 목록 맨 앞에 오고 공개 목록에는 숨긴 항목이 빠진다`() {
        transaction(database) {
            service.create(command("첫 번째"))
            service.create(command("숨김", visible = false))
            service.create(command("두 번째"))

            assertThat(service.getPublicPage(null, null, 0, 12).items.map { it.title }).containsExactly("두 번째", "첫 번째")
            assertThat(service.getAdminPage(null, null, 0, 12).items.map { it.title }).containsExactly("두 번째", "숨김", "첫 번째")
            assertThat(service.getPublicPage(null, null, 0, 12).totalCnt).isEqualTo(2)
        }
    }

    @Test
    fun `분류와 제목 검색어로 거르고 이미지 주소는 공개 주소로 만든다`() {
        transaction(database) {
            service.create(command("오목교역 8번출구", category = "HOSPITAL"))
            service.create(command("목동사거리", category = "ACADEMY"))

            val page = service.getPublicPage("HOSPITAL", "오목교", 0, 12)
            assertThat(page.items).singleElement().satisfies({
                assertThat(it.category).isEqualTo(PortfolioCategory.HOSPITAL)
                assertThat(it.imageUrl).startsWith("https://img.test/portfolio/")
            })
        }
    }

    @Test
    fun `같은 이관 번호로 다시 등록하면 새로 만들지 않고 기존 항목을 돌려준다`() {
        transaction(database) {
            val first = service.create(command("기존 글", legacyIdx = 118))
            val again = service.create(command("다른 제목", legacyIdx = 118))

            assertThat(again.id).isEqualTo(first.id)
            assertThat(service.getAdminPage(null, null, 0, 12).totalCnt).isEqualTo(1)
        }
    }

    @Test
    fun `알 수 없는 분류나 업로드되지 않은 이미지는 등록하지 않는다`() {
        transaction(database) {
            val badCategory = assertThrows<BusinessException> { service.create(command("x", category = "SCHOOL")) }
            assertThat(badCategory.result).isEqualTo(ContentResult.INVALID_CATEGORY)

            every { storage.exists(any()) } returns false
            val notUploaded = assertThrows<BusinessException> { service.create(command("x")) }
            assertThat(notUploaded.result).isEqualTo(ContentResult.IMAGE_NOT_UPLOADED)

            val badKey = assertThrows<BusinessException> { service.create(command("x").copy(imageKey = "../etc/passwd")) }
            assertThat(badKey.result).isEqualTo(ContentResult.INVALID_IMAGE_KEY)
        }
    }

    @Test
    fun `이미지를 바꾸면 이전 이미지만 커밋 후 삭제 대상으로 넘긴다`() {
        transaction(database) {
            val created = service.create(command("교체 전"))
            val newImage = key()

            service.update(created.id, command("교체 후").copy(imageKey = newImage, thumbnailKey = created.thumbnailKey))

            verify { storage.deleteAfterCommit(listOf(created.imageKey)) }
            assertThat(service.get(created.id).imageKey).isEqualTo(newImage)
        }
    }

    @Test
    fun `원본과 썸네일 키를 맞바꾸면 아무것도 지우지 않고 업로드도 다시 확인하지 않는다`() {
        transaction(database) {
            val created = service.create(command("맞바꾸기"))
            clearMocks(storage, answers = false)

            service.update(created.id, command("맞바꾸기").copy(imageKey = created.thumbnailKey, thumbnailKey = created.imageKey))

            verify { storage.deleteAfterCommit(emptyList()) }
            verify(exactly = 0) { storage.exists(any()) }
            assertThat(service.get(created.id).imageKey).isEqualTo(created.thumbnailKey)
        }
    }

    @Test
    fun `같은 키를 두 칸에 쓰면 아직 쓰는 키는 지우지 않고 빠진 키만 삭제 대상이 된다`() {
        transaction(database) {
            val created = service.create(command("같은 키"))

            service.update(created.id, command("같은 키").copy(imageKey = created.imageKey, thumbnailKey = created.imageKey))

            verify { storage.deleteAfterCommit(listOf(created.thumbnailKey)) }
        }
    }

    @Test
    fun `다른 항목이 이미 쓰는 이미지 키는 등록도 수정도 거절한다`() {
        transaction(database) {
            val first = service.create(command("첫째"))
            val other = service.create(command("둘째"))

            val onCreate = assertThrows<BusinessException> {
                service.create(command("셋째").copy(imageKey = first.imageKey))
            }
            val onUpdate = assertThrows<BusinessException> {
                service.update(other.id, command("둘째").copy(thumbnailKey = first.thumbnailKey))
            }

            assertThat(onCreate.result).isEqualTo(ContentResult.IMAGE_KEY_IN_USE)
            assertThat(onUpdate.result).isEqualTo(ContentResult.IMAGE_KEY_IN_USE)
            assertThat(service.getAdminPage(null, null, 0, 12).totalCnt).isEqualTo(2)
        }
    }

    @Test
    fun `정렬 값과 생성 시각이 같으면 ID 역순으로 안정적으로 정렬한다`() {
        transaction(database) {
            repeat(3) { service.create(command("항목$it")) }
            val fixed = java.time.OffsetDateTime.parse("2026-01-01T00:00:00+09:00")
            PortfolioTable.update { it[sortOrder] = 1; it[createdAt] = fixed }

            val ids = service.getAdminPage(null, null, 0, 12).items.map { it.id }

            assertThat(ids).isSortedAccordingTo(Comparator.reverseOrder())
        }
    }

    @Test
    fun `이관 번호가 동시에 등록돼 저장이 충돌하면 먼저 저장된 항목을 돌려준다`() {
        var firstLookup = true
        val racing = object : PortfolioWriter(portfolioRepository, ContentImageRepository(), storage) {
            override fun findByLegacyIdx(legacyIdx: Int) = transaction(database) {
                if (firstLookup) {
                    firstLookup = false
                    null
                } else {
                    super.findByLegacyIdx(legacyIdx)
                }
            }

            override fun insert(command: PortfolioCommand, category: PortfolioCategory) =
                transaction(database) { super.insert(command, category) }
        }
        val racingService = PortfolioService(portfolioRepository, ContentImageService(storage), racing, storage)
        val winner = transaction(database) { service.create(command("먼저", legacyIdx = 7)) }

        val result = racingService.create(command("나중", legacyIdx = 7))

        assertThat(result.id).isEqualTo(winner.id)
        assertThat(transaction(database) { service.getAdminPage(null, null, 0, 12).totalCnt }).isEqualTo(1)
    }

    @Test
    fun `삭제하면 원본과 썸네일을 함께 삭제 대상으로 넘긴다`() {
        transaction(database) {
            val created = service.create(command("삭제"))
            service.delete(created.id)

            verify { storage.deleteAfterCommit(listOf(created.imageKey, created.thumbnailKey)) }
            assertThrows<BusinessException> { service.get(created.id) }
        }
    }

    @Test
    fun `순서 변경은 전체 ID가 맞을 때만 받고 보낸 순서대로 정렬한다`() {
        transaction(database) {
            val a = service.create(command("A"))
            val b = service.create(command("B"))
            val c = service.create(command("C"))

            service.reorder(listOf(a.id, c.id, b.id))
            assertThat(service.getAdminPage(null, null, 0, 12).items.map { it.title }).containsExactly("A", "C", "B")

            val partial = assertThrows<BusinessException> { service.reorder(listOf(a.id, b.id)) }
            assertThat(partial.result).isEqualTo(ContentResult.INVALID_SORT_ORDER)
        }
    }

    private fun key(thumb: Boolean = false) = "portfolio/${UUID.randomUUID()}${if (thumb) "_thumb" else ""}.webp"

    private fun command(title: String, category: String = "HOSPITAL", visible: Boolean = true, legacyIdx: Int? = null) =
        PortfolioCommand(
            title = title,
            category = category,
            description = "하루 799회 반복으로 단독 송출",
            imageKey = key(),
            thumbnailKey = key(thumb = true),
            visible = visible,
            legacyIdx = legacyIdx,
        )
}
