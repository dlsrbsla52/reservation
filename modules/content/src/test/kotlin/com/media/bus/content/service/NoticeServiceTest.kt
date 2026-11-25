package com.media.bus.content.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.content.ContentTestDatabase
import com.media.bus.content.dto.NoticeCommand
import com.media.bus.content.entity.NoticeTable
import com.media.bus.content.repository.ContentImageRepository
import com.media.bus.content.repository.NoticeRepository
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
import java.time.OffsetDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NoticeServiceTest {
    private lateinit var database: Database
    private val storage = mockk<ContentStorage>()
    private val noticeRepository = NoticeRepository()
    private val writer = NoticeWriter(noticeRepository, ContentImageRepository(), storage)
    private val service = NoticeService(noticeRepository, ContentImageService(storage), writer, storage)

    @BeforeAll
    fun setup() {
        database = ContentTestDatabase.connect("notice-service")
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
    fun `공개 목록은 고정 글이 먼저이고 그다음 게시일 역순이며 숨긴 글은 빠진다`() {
        transaction(database) {
            service.create(command("오래된 글", publishedAt = "2026-04-14T10:00:00+09:00"))
            service.create(command("고정 글", publishedAt = "2026-01-01T10:00:00+09:00", pinned = true))
            service.create(command("최근 글", publishedAt = "2026-07-16T10:00:00+09:00"))
            service.create(command("숨긴 글", publishedAt = "2026-08-01T10:00:00+09:00", visible = false))

            assertThat(service.getPublicPage(null, null, 0, 10).items.map { it.title }).containsExactly("고정 글", "최근 글", "오래된 글")
        }
    }

    @Test
    fun `제목 또는 본문으로 검색한다`() {
        transaction(database) {
            service.create(command("행사 소식", body = "부스 참가"))
            service.create(command("브로셔 안내", body = "새 브로셔"))

            assertThat(service.getPublicPage("행사", "TITLE", 0, 10).items.map { it.title }).containsExactly("행사 소식")
            assertThat(service.getPublicPage("브로셔", "BODY", 0, 10).items.map { it.title }).containsExactly("브로셔 안내")
            assertThat(service.getPublicPage("부스", null, 0, 10).items).isEmpty()
        }
    }

    @Test
    fun `공개 상세를 열면 조회수가 오르고 이미지는 등록한 순서대로 나온다`() {
        transaction(database) {
            val first = key()
            val second = key()
            val created = service.create(command("사진 글", imageKeys = listOf(first, second), legacyViewCount = 116))

            val read = service.readPublic(created.id)

            assertThat(read.viewCount).isEqualTo(117)
            assertThat(read.images.map { it.key }).containsExactly(first, second)
            assertThat(service.get(created.id).viewCount).isEqualTo(117)
        }
    }

    @Test
    fun `숨긴 글은 공개 상세에서 찾을 수 없다`() {
        transaction(database) {
            val hidden = service.create(command("숨김", visible = false))
            val ex = assertThrows<BusinessException> { service.readPublic(hidden.id) }
            assertThat(ex.result).isEqualTo(ContentResult.NOTICE_NOT_FOUND)
        }
    }

    @Test
    fun `수정하면 새 이미지만 업로드를 확인하고 빠진 이미지는 삭제 대상으로 넘긴다`() {
        transaction(database) {
            val kept = key()
            val removed = key()
            val created = service.create(command("수정 전", imageKeys = listOf(kept, removed)))
            val added = key()

            service.update(created.id, command("수정 후", imageKeys = listOf(added, kept)))

            verify(exactly = 1) { storage.exists(added) }
            verify { storage.deleteAfterCommit(listOf(removed)) }
            assertThat(service.get(created.id).images.map { it.key }).containsExactly(added, kept)
        }
    }

    @Test
    fun `다른 공지가 이미 쓰는 이미지 키는 등록도 수정도 거절하고 자기 이미지는 그대로 둘 수 있다`() {
        transaction(database) {
            val shared = key()
            val first = service.create(command("첫째", imageKeys = listOf(shared)))
            val other = service.create(command("둘째"))

            val onCreate = assertThrows<BusinessException> { service.create(command("셋째", imageKeys = listOf(shared))) }
            val onUpdate = assertThrows<BusinessException> { service.update(other.id, command("둘째", imageKeys = listOf(shared))) }

            assertThat(onCreate.result).isEqualTo(ContentResult.IMAGE_KEY_IN_USE)
            assertThat(onUpdate.result).isEqualTo(ContentResult.IMAGE_KEY_IN_USE)
            assertThat(service.update(first.id, command("첫째 수정", imageKeys = listOf(shared))).images.map { it.key }).containsExactly(shared)
        }
    }

    @Test
    fun `알 수 없는 검색 대상은 전용 코드로 거절한다`() {
        transaction(database) {
            val ex = assertThrows<BusinessException> { service.getPublicPage("행사", "WRITER", 0, 10) }
            assertThat(ex.result).isEqualTo(ContentResult.INVALID_SEARCH_TYPE)
        }
    }

    @Test
    fun `고정 여부와 게시일이 같으면 ID 역순으로 안정적으로 정렬한다`() {
        transaction(database) {
            repeat(3) { service.create(command("글$it")) }
            NoticeTable.update { it[publishedAt] = OffsetDateTime.parse("2026-01-01T00:00:00+09:00") }

            val ids = service.getAdminPage(null, null, 0, 10).items.map { it.id }

            assertThat(ids).isSortedAccordingTo(Comparator.reverseOrder())
        }
    }

    @Test
    fun `이관 번호가 동시에 등록돼 저장이 충돌하면 먼저 저장된 글을 돌려준다`() {
        var firstLookup = true
        val racing = object : NoticeWriter(noticeRepository, ContentImageRepository(), storage) {
            override fun findByLegacyIdx(legacyIdx: Int) = transaction(database) {
                if (firstLookup) {
                    firstLookup = false
                    null
                } else {
                    super.findByLegacyIdx(legacyIdx)
                }
            }

            override fun insert(command: NoticeCommand, keys: List<String>) = transaction(database) { super.insert(command, keys) }
        }
        val racingService = NoticeService(noticeRepository, ContentImageService(storage), racing, storage)
        val winner = transaction(database) { service.create(command("먼저").copy(legacyIdx = 5)) }

        val result = racingService.create(command("나중").copy(legacyIdx = 5))

        assertThat(result.id).isEqualTo(winner.id)
    }

    @Test
    fun `이미지는 10장까지만 받는다`() {
        transaction(database) {
            val ex = assertThrows<BusinessException> { service.create(command("많음", imageKeys = List(11) { key() })) }
            assertThat(ex.result).isEqualTo(ContentResult.TOO_MANY_NOTICE_IMAGES)
        }
    }

    private fun key() = "notice/${UUID.randomUUID()}.webp"

    private fun command(
        title: String,
        body: String = "본문",
        publishedAt: String? = null,
        pinned: Boolean = false,
        visible: Boolean = true,
        imageKeys: List<String> = emptyList(),
        legacyViewCount: Int? = null,
    ) = NoticeCommand(
        title = title,
        body = body,
        pinned = pinned,
        visible = visible,
        publishedAt = publishedAt?.let(OffsetDateTime::parse),
        imageKeys = imageKeys,
        legacyViewCount = legacyViewCount,
    )
}
