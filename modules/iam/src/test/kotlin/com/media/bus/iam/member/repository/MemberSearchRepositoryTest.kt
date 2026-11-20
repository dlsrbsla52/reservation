package com.media.bus.iam.member.repository

import com.media.bus.common.entity.common.UuidV7
import com.media.bus.iam.auth.entity.MemberRoleEntity
import com.media.bus.iam.auth.entity.MemberRoleTable
import com.media.bus.iam.auth.entity.RoleEntity
import com.media.bus.iam.auth.entity.RoleTable
import com.media.bus.iam.member.dto.MemberSearchCondition
import com.media.bus.iam.member.entity.MemberEntity
import com.media.bus.iam.member.entity.MemberTable
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.OffsetDateTime

/** MemberRepository.searchByCondition 통합 테스트 (H2) */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("MemberRepository 조건 검색 통합 테스트")
class MemberSearchRepositoryTest {

    private lateinit var memberRepository: MemberRepository

    private val generalRoleNames = setOf("MEMBER", "BUSINESS")

    @BeforeAll
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:iamsearchtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        memberRepository = MemberRepository()

        transaction {
            exec("CREATE SCHEMA IF NOT EXISTS auth")
            SchemaUtils.create(MemberTable, RoleTable, MemberRoleTable)

            val memberRole = RoleEntity.create("MEMBER", "일반 회원")
            val businessRole = RoleEntity.create("BUSINESS", "비즈니스 회원")
            val adminRole = RoleEntity.create("ADMIN_USER", "관리회원 일반")

            seedMember("user_kim", "kim@example.com", "김철수", MemberStatus.ACTIVE, null, memberRole)
            seedMember("biz_lee", "lee@example.com", "이영희", MemberStatus.ACTIVE, "123-45-67890", businessRole)
            seedMember("user_park", "park@example.com", "박지성", MemberStatus.SUSPENDED, null, memberRole)
            seedMember("admin_choi", "choi@example.com", "최관리", MemberStatus.ACTIVE, null, adminRole)
            seedMember("promo%end", "promo@example.com", "프로모A", MemberStatus.ACTIVE, null, memberRole)
            seedMember("promoXXXend", "promox@example.com", "프로모B", MemberStatus.ACTIVE, null, memberRole)
        }
    }

    /** 회원 + 역할 매핑을 함께 적재한다. status 제어를 위해 new 팩토리로 직접 생성한다. */
    private fun seedMember(
        loginId: String,
        email: String,
        name: String,
        status: MemberStatus,
        businessNumber: String?,
        role: RoleEntity,
    ) {
        val member = MemberEntity.new(UuidV7.generate()) {
            this.loginId = loginId
            this.password = "password123!"
            this.email = email
            this.phoneNumber = "01000000000"
            this.emailVerified = true
            this.status = status
            this.businessNumber = businessNumber
            this.memberName = name
        }
        MemberRoleEntity.create(member, role)
    }

    @Test
    fun `카테고리 역할 필터`() {
        transaction {
            val rows = memberRepository.searchByCondition(MemberSearchCondition(), generalRoleNames)
            val total = memberRepository.countByCondition(MemberSearchCondition(), generalRoleNames)

            assertThat(rows).hasSize(5)
            assertThat(total).isEqualTo(5L)
            assertThat(rows.map { it.member.loginId }).doesNotContain("admin_choi")
            assertThat(rows.map { it.roleName }).allMatch { it in generalRoleNames }
        }
    }

    @Test
    fun `어드민 역할 필터`() {
        transaction {
            val rows = memberRepository.searchByCondition(
                MemberSearchCondition(),
                setOf("ADMIN_USER", "ADMIN_MASTER", "ADMIN_DEVELOPER"),
            )
            assertThat(rows).hasSize(1)
            assertThat(rows[0].member.loginId).isEqualTo("admin_choi")
            assertThat(rows[0].roleName).isEqualTo("ADMIN_USER")
        }
    }

    @Test
    fun `키워드 필터`() {
        transaction {
            val rows = memberRepository.searchByCondition(
                MemberSearchCondition(keyword = "park"),
                generalRoleNames,
            )
            assertThat(rows).hasSize(1)
            assertThat(rows[0].member.loginId).isEqualTo("user_park")
        }
    }

    @Test
    fun `상태 필터`() {
        transaction {
            val rows = memberRepository.searchByCondition(
                MemberSearchCondition(status = MemberStatus.SUSPENDED),
                generalRoleNames,
            )
            assertThat(rows).hasSize(1)
            assertThat(rows[0].member.loginId).isEqualTo("user_park")
        }
    }

    @Test
    fun `사업자번호 필터`() {
        transaction {
            val rows = memberRepository.searchByCondition(
                MemberSearchCondition(businessNumber = "123-45-67890"),
                generalRoleNames,
            )
            assertThat(rows).hasSize(1)
            assertThat(rows[0].member.loginId).isEqualTo("biz_lee")
        }
    }

    @Test
    fun `페이지네이션`() {
        transaction {
            val condition = MemberSearchCondition(page = 0, size = 2)
            val rows = memberRepository.searchByCondition(condition, generalRoleNames)
            val total = memberRepository.countByCondition(condition, generalRoleNames)

            assertThat(rows).hasSize(2)
            assertThat(total).isEqualTo(5L)
        }
    }

    @Test
    fun `가입일 범위 필터`() {
        transaction {
            val now = OffsetDateTime.now()

            val included = memberRepository.searchByCondition(
                MemberSearchCondition(createdFrom = now.minusDays(1), createdTo = now.plusDays(1)),
                generalRoleNames,
            )
            assertThat(included).hasSize(5)

            val excluded = memberRepository.searchByCondition(
                MemberSearchCondition(createdTo = now.minusDays(1)),
                generalRoleNames,
            )
            assertThat(excluded).isEmpty()
        }
    }

    @Test
    fun `키워드 와일드카드 이스케이프`() {
        transaction {
            val rows = memberRepository.searchByCondition(
                MemberSearchCondition(keyword = "promo%end"),
                generalRoleNames,
            )
            assertThat(rows).hasSize(1)
            assertThat(rows[0].member.loginId).isEqualTo("promo%end")
        }
    }
}
