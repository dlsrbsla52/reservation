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

/**
 * ## MemberRepository.searchByCondition 통합 테스트 (H2)
 *
 * 동적 검색의 핵심 위험 지점인 **auth.member ⋈ member_role ⋈ role 조인 + 술어 누적**을
 * 실제 SQL 실행으로 검증한다.
 * - 역할(카테고리) 필터: 허용 역할 이름 집합 밖의 회원은 제외되는가
 * - 키워드/상태/사업자번호 필터, 페이지네이션, createdAt 범위
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("MemberRepository 조건 검색 통합 테스트")
class MemberSearchRepositoryTest {

    private lateinit var memberRepository: MemberRepository

    // 일반 회원(USER/BUSINESS) 역할 이름 집합
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

            val memberRole = RoleEntity.of("MEMBER", "일반 회원")
            val businessRole = RoleEntity.of("BUSINESS", "비즈니스 회원")
            val adminRole = RoleEntity.of("ADMIN_USER", "관리회원 일반")

            // m1: 일반 ACTIVE
            seedMember("user_kim", "kim@example.com", "김철수", MemberStatus.ACTIVE, null, memberRole)
            // m2: 비즈니스 ACTIVE (사업자번호 보유)
            seedMember("biz_lee", "lee@example.com", "이영희", MemberStatus.ACTIVE, "123-45-67890", businessRole)
            // m3: 일반 SUSPENDED
            seedMember("user_park", "park@example.com", "박지성", MemberStatus.SUSPENDED, null, memberRole)
            // m4: 어드민 ACTIVE (일반 검색에서 제외되어야 함)
            seedMember("admin_choi", "choi@example.com", "최관리", MemberStatus.ACTIVE, null, adminRole)
            // m5/m6: 와일드카드 이스케이프 검증용 한 쌍.
            //  - m5 는 loginId 에 리터럴 '%' 포함, m6 는 같은 자리에 일반 문자.
            //  - 이스케이프가 없으면 'promo%end' 키워드의 '%' 가 와일드카드로 동작해 둘 다 매칭되지만,
            //    이스케이프되면 리터럴 '%' 를 가진 m5 만 매칭되어야 한다.
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
        MemberRoleEntity.of(member, role)
    }

    @Test
    @DisplayName("역할 필터: 일반 역할 집합으로 조회하면 어드민 회원은 제외된다")
    fun `카테고리 역할 필터`() {
        transaction {
            val rows = memberRepository.searchByCondition(MemberSearchCondition(), generalRoleNames)
            val total = memberRepository.countByCondition(MemberSearchCondition(), generalRoleNames)

            // 일반/비즈니스 회원(m1,m2,m3,m5,m6) 5건만 조회되고 m4(admin)는 제외된다
            assertThat(rows).hasSize(5)
            assertThat(total).isEqualTo(5L)
            assertThat(rows.map { it.first.loginId }).doesNotContain("admin_choi")
            // 반환된 역할 이름은 허용 집합 내에 있어야 한다
            assertThat(rows.map { it.second }).allMatch { it in generalRoleNames }
        }
    }

    @Test
    @DisplayName("역할 필터: 어드민 역할 집합으로 조회하면 어드민 회원만 조회된다")
    fun `어드민 역할 필터`() {
        transaction {
            val rows = memberRepository.searchByCondition(
                MemberSearchCondition(),
                setOf("ADMIN_USER", "ADMIN_MASTER", "ADMIN_DEVELOPER"),
            )
            assertThat(rows).hasSize(1)
            assertThat(rows[0].first.loginId).isEqualTo("admin_choi")
            assertThat(rows[0].second).isEqualTo("ADMIN_USER")
        }
    }

    @Test
    @DisplayName("키워드 필터: loginId 부분 일치")
    fun `키워드 필터`() {
        transaction {
            val rows = memberRepository.searchByCondition(
                MemberSearchCondition(keyword = "park"),
                generalRoleNames,
            )
            assertThat(rows).hasSize(1)
            assertThat(rows[0].first.loginId).isEqualTo("user_park")
        }
    }

    @Test
    @DisplayName("상태 필터: SUSPENDED 만 조회")
    fun `상태 필터`() {
        transaction {
            val rows = memberRepository.searchByCondition(
                MemberSearchCondition(status = MemberStatus.SUSPENDED),
                generalRoleNames,
            )
            assertThat(rows).hasSize(1)
            assertThat(rows[0].first.loginId).isEqualTo("user_park")
        }
    }

    @Test
    @DisplayName("사업자번호 필터: 정확 일치하는 비즈니스 회원만 조회")
    fun `사업자번호 필터`() {
        transaction {
            val rows = memberRepository.searchByCondition(
                MemberSearchCondition(businessNumber = "123-45-67890"),
                generalRoleNames,
            )
            assertThat(rows).hasSize(1)
            assertThat(rows[0].first.loginId).isEqualTo("biz_lee")
        }
    }

    @Test
    @DisplayName("페이지네이션: size=2 면 2건만 반환하고 count 는 전체 건수다")
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
    @DisplayName("가입일 범위 필터: 범위 안이면 포함, 과거 상한이면 모두 제외")
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
    @DisplayName("키워드 와일드카드 이스케이프: '%' 는 리터럴로 매칭되어 over-match 가 발생하지 않는다")
    fun `키워드 와일드카드 이스케이프`() {
        transaction {
            // 'promo%end' 키워드. 이스케이프가 없으면 promoXXXend(m6)까지 매칭되지만,
            // 리터럴 '%' 를 가진 promo%end(m5)만 매칭되어야 한다.
            val rows = memberRepository.searchByCondition(
                MemberSearchCondition(keyword = "promo%end"),
                generalRoleNames,
            )
            assertThat(rows).hasSize(1)
            assertThat(rows[0].first.loginId).isEqualTo("promo%end")
        }
    }
}
