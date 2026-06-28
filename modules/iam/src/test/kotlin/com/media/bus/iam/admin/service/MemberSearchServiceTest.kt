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

/**
 * ## MemberSearchService 단위 테스트
 *
 * 핵심 검증 포인트는 **권한 경계 강제**다.
 * 허용 카테고리와 조건의 정밀 유형(type)을 교집합한 역할 이름 집합으로만 조회하며,
 * 경계를 벗어나는 type 지정은 DB 조회 없이 빈 결과로 차단되어야 한다.
 */
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
        @DisplayName("type 미지정 시 USER/BUSINESS 역할만 검색하고 결과를 매핑한다")
        fun `정상 검색`() {
            val roleNamesSlot = slot<Set<String>>()
            every { memberRepository.searchByCondition(any(), capture(roleNamesSlot)) } returns
                listOf(MemberWithRole(mockMember(), "MEMBER"))
            every { memberRepository.countByCondition(any(), any()) } returns 1L

            val result = memberSearchService.search(
                MemberSearchCondition(),
                setOf(MemberCategory.USER, MemberCategory.BUSINESS),
            )

            // USER→MEMBER, BUSINESS→BUSINESS 역할 이름만 조회 대상에 포함
            assertThat(roleNamesSlot.captured).containsExactlyInAnyOrder("MEMBER", "BUSINESS")
            assertThat(result.items).hasSize(1)
            assertThat(result.totalCnt).isEqualTo(1L)
            assertThat(result.items[0].memberType).isEqualTo(MemberType.MEMBER)
            assertThat(result.items[0].loginId).isEqualTo("user_kim")
        }

        @Test
        @DisplayName("type 을 허용 카테고리 내 값으로 지정하면 해당 역할만 조회한다")
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
        @DisplayName("허용 카테고리를 벗어난 type(ADMIN) 지정 시 DB 조회 없이 빈 결과를 반환한다")
        fun `경계 위반 차단`() {
            val result = memberSearchService.search(
                MemberSearchCondition(type = MemberType.ADMIN_MASTER),
                setOf(MemberCategory.USER, MemberCategory.BUSINESS),
            )

            assertThat(result.items).isEmpty()
            assertThat(result.totalCnt).isEqualTo(0L)
            // 역할 집합이 비므로 리포지토리를 호출하지 않아야 한다
            verify(exactly = 0) { memberRepository.searchByCondition(any(), any()) }
            verify(exactly = 0) { memberRepository.countByCondition(any(), any()) }
        }
    }

    @Nested
    @DisplayName("어드민 회원 검색 (ADMIN 허용)")
    inner class AdminSearch {

        @Test
        @DisplayName("ADMIN 카테고리의 모든 역할을 검색 대상으로 삼는다")
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
        @DisplayName("type 을 ADMIN 카테고리 내 값으로 지정하면 해당 역할만 조회한다")
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
        @DisplayName("허용 카테고리를 벗어난 type(MEMBER) 지정 시 DB 조회 없이 빈 결과를 반환한다")
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
        @DisplayName("음수 page/과대 size 는 보정되어 응답 메타에 반영된다")
        fun `page size 보정`() {
            every { memberRepository.searchByCondition(any(), any()) } returns emptyList()
            every { memberRepository.countByCondition(any(), any()) } returns 0L

            val result = memberSearchService.search(
                MemberSearchCondition(page = -5, size = 100_000),
                setOf(MemberCategory.USER, MemberCategory.BUSINESS),
            )

            // page 는 0 이상, size 는 상한(100)으로 보정
            assertThat(result.pageNum).isEqualTo(0)
            assertThat(result.pageRows).isEqualTo(100)
        }
    }
}
