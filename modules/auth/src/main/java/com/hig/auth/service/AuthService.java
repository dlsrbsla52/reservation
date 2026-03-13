package com.hig.auth.service;

import com.hig.auth.api.dto.LoginRequest;
import com.hig.auth.api.dto.RegisterRequest;
import com.hig.auth.api.dto.TokenResponse;
import com.hig.auth.entity.member.entity.Member;
import com.hig.auth.entity.member.repository.MemberRepository;
import com.hig.entity.member.MemberType;
import com.hig.security.JwtProvider;
import com.hig.security.MemberPrincipal;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

/**
 * 회원 인증/인가 비즈니스 로직 서비스.
 *
 * 핵심 비즈니스 규칙:
 * 1. 비즈니스 회원(BUSINESS) 가입 시 businessNumber 필수.
 * 2. 이메일 인증 전 로그인은 허용하나, emailVerified = false 상태의 토큰을 발급하여
 * Gateway / 각 서비스에서 이메일 미인증 사용자를 주요 기능에서 차단.
 * 3. 이메일 인증 토큰은 UUID로 생성, Redis에 TTL 24h로 저장.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String EMAIL_VERIFY_KEY_PREFIX = "email-verify:";
    private static final Duration EMAIL_VERIFY_TTL = Duration.ofHours(24);

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;

    /**
     * 회원가입 처리.
     * 1. loginId / email 중복 검사
     * 2. 비즈니스 회원 유효성 검사 (businessNumber 필수)
     * 3. BCrypt 비밀번호 암호화 저장
     * 4. 이메일 인증 토큰 발급 및 Redis 저장
     *
     * @return 이메일 인증 토큰 (실제 서비스에서는 이메일로 발송. 현재는 응답으로 반환)
     */
    @Transactional
    public String register(RegisterRequest request) {
        // 아이디 중복 검사
        log.debug("회원가입 요청 ID : {}", request.loginId());
        if (memberRepository.existsByLoginId(request.loginId())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }

        // 이메일 중복 검사
        log.debug("회원가입 요청 email : {}", request.email());
        if (memberRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // 비즈니스 회원 전용 유효성 검사
        if (MemberType.BUSINESS.equals(request.memberType())
                && (request.businessNumber() == null || request.businessNumber().isBlank())) {
            throw new IllegalArgumentException("비즈니스 회원 가입 시 사업자 번호는 필수입니다.");
        }

        Member member = Member.builder()
                .loginId(request.loginId())
                .password(passwordEncoder.encode(request.password()))
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .memberType(request.memberType())
                .businessNumber(request.businessNumber())
                .emailVerified(false)
                .build();

        memberRepository.save(member);

        // 이메일 인증 토큰 생성 및 Redis 저장
        String verifyToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                EMAIL_VERIFY_KEY_PREFIX + verifyToken,
                member.getId().toString(),
                EMAIL_VERIFY_TTL);

        log.info("[AuthService.register] 회원가입 완료. memberId={}, email={}, 이메일 인증 토큰 발급",
                member.getId(), member.getEmail());

        // TODO: AWS SES 또는 SMTP를 통해 인증 링크를 이메일로 발송.
        // 현재는 응답 바디를 통해 토큰을 직접 반환합니다. (개발/테스트 단계)
        return verifyToken;
    }

    /**
     * 로그인 처리.
     * 1. 자격증명(아이디/비밀번호) 검증
     * 2. 계정 상태(ACTIVE) 검증
     * 3. Access Token + Refresh Token 발급
     *
     * emailVerified = false 상태에서도 로그인은 허용합니다.
     * 이메일 미인증 사실은 JWT 클레임에 포함되어, 이를 활용한 기능 접근 제한은
     * Gateway 또는 개별 서비스 레이어에 위임합니다.
     */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Member member = memberRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        // 이용 정지 / 탈퇴 계정 로그인 차단
        switch (member.getStatus()) {
            case SUSPENDED -> throw new IllegalStateException("이용이 정지된 계정입니다. 고객센터에 문의해주세요.");
            case WITHDRAWN -> throw new IllegalStateException("탈퇴된 계정입니다.");
            default -> {
                /* ACTIVE: 정상 처리 */ }
        }

        MemberPrincipal principal = MemberPrincipal.builder()
                .id(member.getId().toString())
                .loginId(member.getLoginId())
                .email(member.getEmail())
                .memberType(member.getMemberType())
                .emailVerified(member.isEmailVerified())
                .build();

        String accessToken = jwtProvider.generateAccessToken(principal);
        String refreshToken = jwtProvider.generateRefreshToken(member.getId().toString());

        log.info("[AuthService.login] 로그인 성공. memberId={}", member.getId());
        return TokenResponse.of(accessToken, refreshToken);
    }

    /**
     * 이메일 인증 처리.
     * Redis에서 인증 토큰으로 회원 ID를 조회하고, emailVerified 플래그를 업데이트합니다.
     */
    @Transactional
    public void verifyEmail(String token) {
        String memberId = redisTemplate.opsForValue().get(EMAIL_VERIFY_KEY_PREFIX + token);
        if (memberId == null) {
            throw new IllegalArgumentException("유효하지 않거나 만료된 이메일 인증 토큰입니다.");
        }

        Member member = memberRepository.findById(UUID.fromString(memberId))
                .orElseThrow(() -> new IllegalStateException("회원 정보를 찾을 수 없습니다."));

        member.verifyEmail();
        // 인증 완료 후 Redis 토큰 즉시 삭제 (1회성 토큰)
        redisTemplate.delete(EMAIL_VERIFY_KEY_PREFIX + token);

        log.info("[AuthService.verifyEmail] 이메일 인증 완료. memberId={}", memberId);
    }

    /**
     * Refresh Token을 검증하고 새로운 Access Token을 발급합니다.
     * Redis에 저장된 토큰과 요청 토큰을 비교하여 탈취 여부를 검증합니다.
     */
    @Transactional(readOnly = true)
    public TokenResponse refreshAccessToken(String refreshToken) {
        // 서명/만료 검증
        if (jwtProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("유효하지 않은 Refresh Token입니다.");
        }
        Claims claims = jwtProvider.parseClaimsFromToken(refreshToken);
        String userId = claims.getSubject();

        // Redis에 저장된 토큰과 비교 (Refresh Token Rotation 지원)
        if (!jwtProvider.validateRefreshToken(userId, refreshToken)) {
            throw new IllegalArgumentException("Refresh Token이 일치하지 않습니다. 재로그인이 필요합니다.");
        }

        Member member = memberRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new IllegalStateException("회원 정보를 찾을 수 없습니다."));

        MemberPrincipal principal = MemberPrincipal.builder()
                .id(member.getId().toString())
                .loginId(member.getLoginId())
                .email(member.getEmail())
                .memberType(member.getMemberType())
                .emailVerified(member.isEmailVerified())
                .build();

        String newAccessToken = jwtProvider.generateAccessToken(principal);
        String newRefreshToken = jwtProvider.generateRefreshToken(userId); // Token Rotation

        return TokenResponse.of(newAccessToken, newRefreshToken);
    }

    /**
     * 로그아웃 처리.
     * Redis에서 Refresh Token을 삭제하여 서버 측 무효화합니다.
     */
    public void logout(String userId) {
        jwtProvider.deleteRefreshToken(userId);
        log.info("[AuthService.logout] 로그아웃 처리. memberId={}", userId);
    }
}
