package com.media.bus.iam.auth.service;

import com.media.bus.common.exceptions.BaseException;
import com.media.bus.common.exceptions.NoAuthenticationException;
import com.media.bus.common.result.type.CommonResult;
import com.media.bus.contract.entity.member.MemberType;
import com.media.bus.contract.security.JwtProvider;
import com.media.bus.contract.security.MemberPrincipal;
import com.media.bus.iam.auth.dto.LoginRequest;
import com.media.bus.iam.auth.dto.RegisterRequest;
import com.media.bus.iam.auth.dto.TokenResponse;
import com.media.bus.iam.auth.entity.MemberRole;
import com.media.bus.iam.auth.entity.Role;
import com.media.bus.iam.auth.guard.RegisterRequestValidator;
import com.media.bus.iam.auth.repository.MemberRoleRepository;
import com.media.bus.iam.auth.repository.RolePermissionRepository;
import com.media.bus.iam.auth.repository.RoleRepository;
import com.media.bus.iam.auth.result.AuthResult;
import com.media.bus.iam.member.entity.Member;
import com.media.bus.iam.member.repository.MemberRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 회원 인증/인가 비즈니스 로직 서비스.
 * 핵심 비즈니스 규칙:
 * 1. 입력 검증은 RegisterRequestValidator(Guard 계층)에 위임합니다.
 * 2. 이메일 인증 전 로그인은 허용하나, emailVerified = false 상태의 토큰을 발급하여
 *    Gateway / 각 서비스에서 이메일 미인증 사용자를 주요 기능에서 차단합니다.
 * 3. 이메일 인증 토큰은 UUID로 생성, Redis에 TTL 24h로 저장합니다.
 *    보안상 이유로 토큰은 HTTP 응답에 포함하지 않습니다 (이메일 발송 전용).
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
    private final RolePermissionRepository rolePermissionRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final RoleRepository roleRepository;
    private final RegisterRequestValidator registerRequestValidator;

    /**
     * 회원가입 처리.
     * 1. Guard 계층(RegisterRequestValidator)에 입력 검증 위임
     * 2. BCrypt 비밀번호 암호화 저장
     * 3. 역할 조회 및 member_role 저장 (같은 트랜잭션)
     * 4. 이메일 인증 토큰 발급 및 Redis 저장 (응답에는 포함하지 않음)
     */
    @Transactional
    public void register(RegisterRequest request) {
        // 1. 입력 검증 (Guard 계층에 위임)
        registerRequestValidator.validate(request);

        // 2. Member 생성 및 저장
        Member member = Member.builder()
                .loginId(request.loginId())
                .password(passwordEncoder.encode(request.password()))
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .businessNumber(request.businessNumber())
                .emailVerified(false)
                .build();
        memberRepository.save(member);

        // 3. role 마스터 테이블에서 MemberType에 해당하는 Role 엔티티 조회 및 역할 부여
        Role role = roleRepository.findByName(request.memberType().name())
                .orElseThrow(() -> new BaseException(AuthResult.ROLE_NOT_FOUND));
        memberRoleRepository.save(MemberRole.of(member, role));

        // 4. 이메일 인증 토큰 생성 및 Redis 저장
        //    보안상 응답에 포함하지 않음 — 실제 서비스에서는 이메일로 발송
        String verifyToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                EMAIL_VERIFY_KEY_PREFIX + verifyToken,
                member.getId().toString(),
                EMAIL_VERIFY_TTL);

        log.info("[AuthService.register] 회원가입 완료. memberId={}, email={}", member.getId(), member.getEmail());
        // TODO: AWS SES 또는 SMTP를 통해 인증 링크를 이메일로 발송해야 합니다.
        log.debug("[개발용] 이메일 인증 토큰: {}", verifyToken);
    }

    /**
     * 로그인 처리.
     * 1. 자격증명(아이디/비밀번호) 검증
     * 2. 계정 상태(ACTIVE) 검증
     * 3. JOIN FETCH로 역할 조회 (N+1 방지)
     * 4. Access Token + Refresh Token 발급
     * emailVerified = false 상태에서도 로그인은 허용합니다.
     */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Member member = memberRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new NoAuthenticationException(CommonResult.BAD_CREDENTIAL_FAIL));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new NoAuthenticationException(CommonResult.BAD_CREDENTIAL_FAIL);
        }

        // 이용 정지 / 탈퇴 계정 로그인 차단
        switch (member.getStatus()) {
            case SUSPENDED -> throw new NoAuthenticationException(CommonResult.ACCOUNT_SUSPENDED_FAIL);
            case WITHDRAWN -> throw new NoAuthenticationException(CommonResult.ACCOUNT_WITHDRAWN_FAIL);
            default -> { /* ACTIVE: 정상 처리 */ }
        }

        // JOIN FETCH로 역할 조회 — N+1 방지
        List<MemberRole> memberRoles = memberRoleRepository.findWithRoleByMemberId(member.getId());
        if (memberRoles.isEmpty()) {
            throw new BaseException(AuthResult.ROLE_NOT_FOUND);
        }
        if (memberRoles.size() > 1) {
            // 단일 역할 정책 위반 — 데이터 이상 감지
            log.warn("[AuthService.login] 회원 [{}]에게 복수 역할이 존재합니다. 첫 번째 역할을 사용합니다.", member.getId());
        }
        MemberType memberType = MemberType.fromName(memberRoles.get(0).getRole().getName())
                .orElseThrow(() -> new BaseException(AuthResult.ROLE_NOT_FOUND));

        MemberPrincipal principal = MemberPrincipal.builder()
                .id(member.getId())
                .loginId(member.getLoginId())
                .email(member.getEmail())
                .memberType(memberType)
                .emailVerified(member.isEmailVerified())
                .build();

        // DB에서 역할에 매핑된 권한 목록을 조회하여 JWT claim에 포함
        Set<String> permissionNames = rolePermissionRepository
                .findPermissionNamesByRoleName(memberType.name());

        String accessToken = jwtProvider.generateAccessToken(principal, permissionNames);
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
            throw new NoAuthenticationException(CommonResult.EMAIL_TOKEN_INVALID_FAIL);
        }

        Member member = memberRepository.findById(UUID.fromString(memberId))
                .orElseThrow(() -> new NoAuthenticationException(CommonResult.USER_NOT_FOUND_FAIL));

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
        if (jwtProvider.isInvalidToken(refreshToken)) {
            throw new NoAuthenticationException(CommonResult.ACCESS_TOKEN_EXPIRED_FAIL);
        }
        Claims claims = jwtProvider.parseClaimsFromToken(refreshToken);
        String memberId = claims.getSubject();

        // Redis에 저장된 토큰과 비교 (Refresh Token Rotation 지원)
        if (!jwtProvider.validateRefreshToken(memberId, refreshToken)) {
            throw new NoAuthenticationException(CommonResult.ACCESS_TOKEN_VERIFICATION_FAIL);
        }

        Member member = memberRepository.findById(UUID.fromString(memberId))
                .orElseThrow(() -> new NoAuthenticationException(CommonResult.USER_NOT_FOUND_FAIL));

        // JOIN FETCH로 역할 조회 — 최신 역할 정보 반영
        List<MemberRole> memberRoles = memberRoleRepository.findWithRoleByMemberId(member.getId());
        if (memberRoles.isEmpty()) {
            throw new BaseException(AuthResult.ROLE_NOT_FOUND);
        }
        if (memberRoles.size() > 1) {
            log.warn("[AuthService.refreshAccessToken] 회원 [{}]에게 복수 역할이 존재합니다. 첫 번째 역할을 사용합니다.", member.getId());
        }
        MemberType memberType = MemberType.fromName(memberRoles.get(0).getRole().getName())
                .orElseThrow(() -> new BaseException(AuthResult.ROLE_NOT_FOUND));

        MemberPrincipal principal = MemberPrincipal.builder()
                .id(member.getId())
                .loginId(member.getLoginId())
                .email(member.getEmail())
                .memberType(memberType)
                .emailVerified(member.isEmailVerified())
                .build();

        // 토큰 재발급 시에도 DB에서 최신 권한을 조회하여 갱신된 권한이 즉시 반영되도록 함
        Set<String> permissionNames = rolePermissionRepository
                .findPermissionNamesByRoleName(memberType.name());

        String newAccessToken = jwtProvider.generateAccessToken(principal, permissionNames);
        String newRefreshToken = jwtProvider.generateRefreshToken(memberId); // Token Rotation

        return TokenResponse.of(newAccessToken, newRefreshToken);
    }

    /**
     * 로그아웃 처리.
     * Redis에서 Refresh Token을 삭제하여 서버 측 무효화합니다.
     */
    public void logout(String memberId) {
        jwtProvider.deleteRefreshToken(memberId);
        log.info("[AuthService.logout] 로그아웃 처리. memberId={}", memberId);
    }
}
