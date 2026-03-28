package com.media.bus.iam.member.repository;

import com.media.bus.iam.modules.member.entity.Member;
import com.media.bus.iam.modules.member.entity.enumerated.MemberStatus;
import com.media.bus.iam.modules.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MemberRepository 테스트 클래스.
 * 엔티티의 PK(UUID) 수동 삽입 및 자동 생성 동작을 검증합니다.
 */
@DataJpaTest
@ActiveProfiles("test")
class MemberRepositoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("의도적으로 PK(UUID)를 설정하여 DB에 직접 Insert 할 때, 해당 ID가 잘 반영되어야 한다.")
    void manualInsertTest() {
        // given
        UUID manualId = UUID.randomUUID();
        String sql = """
                        INSERT INTO auth.member (id, login_id, password, email, phone_number, email_verified, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
                     """;

        // when
        // JPA의 영속성 컨텍스트 관리를 우회하여 저수준(JdbcTemplate)으로 직접 삽입하는 방식입니다.
        // 데이터 마이그레이션이나 레거시 PK 유지보수 시 주로 사용됩니다.
        jdbcTemplate.update(sql,
                manualId,
                "manual_user",
                "password123!",
                "manual@example.com",
                "010-1234-5678",
                false,
                MemberStatus.ACTIVE.name(),
                OffsetDateTime.now(),
                OffsetDateTime.now());

        // then
        // JPA Repository를 통해 조회하여 데이터가 정상적으로 저장되었는지 확인
        Member foundMember = memberRepository.findById(manualId).orElseThrow();
        assertThat(foundMember.getId()).isEqualTo(manualId);
        assertThat(foundMember.getLoginId()).isEqualTo("manual_user");
    }

    @Test
    @DisplayName("JPA를 사용하여 PK를 설정하지 않고 저장할 때, UUID가 자동으로 생성되어야 한다.")
    void saveWithAutomaticIdTest() {
        // given
        Member member = Member.builder()
                .loginId("auto_user")
                .password("password123!")
                .email("auto@example.com")
                .phoneNumber("010-8765-4321")
                .status(MemberStatus.ACTIVE)
                .build();

        // when
        Member savedMember = memberRepository.save(member);

        // then
        assertThat(savedMember.getId()).isNotNull();
        assertThat(savedMember.getLoginId()).isEqualTo("auto_user");
    }
}
