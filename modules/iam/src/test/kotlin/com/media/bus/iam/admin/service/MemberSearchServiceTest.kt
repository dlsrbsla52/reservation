package com.media.bus.iam.admin.service

import com.media.bus.contract.entity.member.MemberCategory
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.iam.member.dto.MemberSearchCondition
import com.media.bus.iam.member.dto.MemberWithRole
import com.media.bus.iam.member.entity.MemberEntity
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import com.media.bus.iam.member.repository.MemberRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.OffsetDateTime
import java.util.*

/** MemberSearchService 단위 테스트 */
@ExtendWith(MockKExtension::class)
@DisplayName("MemberSearchService 단위 테스트")
class MemberSearchServiceTest {

    @MockK
    private lateinit var memberRepository: MemberRepository

    @InjectMockKs
    private lateinit var memberSearchService: MemberSearchService

    private val memberId = UUID.randomUUID()

    /** 검색 결과 매핑에 필요한 getter만 stub 한 회원 목 객체 */
    private fun mockMember(): MemberEntity {
        val member = mockk<MemberEntity>(relaxed = true)
        val entityId = mockk<EntityID<UUID>>()
        every { entityId.value } returns memberId
        every { member.id } returns entityId
        every { member.loginId } returns "user_kim"
        every { member.email } returns "kim@example.com"
        every { member.memberName } returns "김철수"
        every { member.phoneNumber } returns "01012345678"
        every { member.businessNumber } returns null
        every { member.status } returns MemberStatus.ACTIVE
        every { member.createdAt } returns OffsetDateTime.now()
        return member
    }

    @Nested
    @DisplayName("일반 회원 검색 (USER/BUSINESS 허용)")
    inner class UserSearch {

        @Test
        fun `정상 검색`() {
            val roleNamesSlot = slot<Set<String>>()
            every { memberRepository.searchByCondition(any(), capture(roleNamesSlot)) } returns
                listOf(MemberWithRole(mockMember(), "MEMBER"))
            every { memberRepository.countByCondition(any(), any()) } returns 1L

            val result = memberSearchService.search(
                MemberSearchCondition(),
                setOf(MemberCategory.USER, MemberCategory.BUSINESS),
            )

            assertThat(roleNamesSlot.captured).containsExactlyInAnyOrder("MEMBER", "BUSINESS")
            assertThat(result.items).hasSize(1)
            assertThat(result.totalCnt).isEqualTo(1L)
            assertThat(result.items[0].memberType).isEqualTo(MemberType.MEMBER)
            assertThat(result.items[0].loginId).isEqualTo("user_kim")
        }

        @Test
        fun `정밀 유형 필터`() {
            val roleNamesSlot = slot<Set<String>>()
            every { memberRepository.searchByCondition(any(), capture(roleNamesSlot)) } returns emptyList()
            every { memberRepository.countByCondition(any(), any()) } returns 0L

            memberSearchService.search(
                MemberSearchCondition(type = MemberType.BUSINESS),
                setOf(MemberCategory.USER, MemberCategory.BUSINESS),
            )

            assertThat(roleNamesSlot.captured).containsExactly("BUSINESS")
        }

        @Test
        fun `경계 위반 차단`() {
            val result = memberSearchService.search(
                MemberSearchCondition(type = MemberType.ADMIN_MASTER),
                setOf(MemberCategory.USER, MemberCategory.BUSINESS),
            )

            assertThat(result.items).isEmpty()
            assertThat(result.totalCnt).isEqualTo(0L)
            verify(exactly = 0) { memberRepository.searchByCondition(any(), any()) }
            verify(exactly = 0) { memberRepository.countByCondition(any(), any()) }
        }
    }

    @Nested
    @DisplayName("어드민 회원 검색 (ADMIN 허용)")
    inner class AdminSearch {

        @Test
        fun `정상 검색`() {
            val roleNamesSlot = slot<Set<String>>()
            every { memberRepository.searchByCondition(any(), capture(roleNamesSlot)) } returns
                listOf(MemberWithRole(mockMember(), "ADMIN_USER"))
            every { memberRepository.countByCondition(any(), any()) } returns 1L

            val result = memberSearchService.search(
                MemberSearchCondition(),
                setOf(MemberCategory.ADMIN),
            )

            assertThat(roleNamesSlot.captured)
                .containsExactlyInAnyOrder("ADMIN_USER", "ADMIN_MASTER", "ADMIN_DEVELOPER")
            assertThat(result.items[0].memberType).isEqualTo(MemberType.ADMIN_USER)
        }

        @Test
        fun `정밀 유형 필터`() {
            val roleNamesSlot = slot<Set<String>>()
            every { memberRepository.searchByCondition(any(), capture(roleNamesSlot)) } returns emptyList()
            every { memberRepository.countByCondition(any(), any()) } returns 0L

            memberSearchService.search(
                MemberSearchCondition(type = MemberType.ADMIN_MASTER),
                setOf(MemberCategory.ADMIN),
            )

            assertThat(roleNamesSlot.captured).containsExactly("ADMIN_MASTER")
        }

        @Test
        fun `경계 위반 차단`() {
            val result = memberSearchService.search(
                MemberSearchCondition(type = MemberType.MEMBER),
                setOf(MemberCategory.ADMIN),
            )

            assertThat(result.items).isEmpty()
            assertThat(result.totalCnt).isEqualTo(0L)
            verify(exactly = 0) { memberRepository.searchByCondition(any(), any()) }
            verify(exactly = 0) { memberRepository.countByCondition(any(), any()) }
        }
    }

    @Nested
    @DisplayName("페이지 파라미터 보정")
    inner class PageNormalization {

        @Test
        fun `page size 보정`() {
            every { memberRepository.searchByCondition(any(), any()) } returns emptyList()
            every { memberRepository.countByCondition(any(), any()) } returns 0L

            val result = memberSearchService.search(
                MemberSearchCondition(page = -5, size = 100_000),
                setOf(MemberCategory.USER, MemberCategory.BUSINESS),
            )

            assertThat(result.pageNum).isEqualTo(0)
            assertThat(result.pageRows).isEqualTo(100)
        }
    }
}
