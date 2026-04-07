package com.media.bus.iam.member.service;

import com.media.bus.common.exceptions.BaseException;
import com.media.bus.common.exceptions.BusinessException;
import com.media.bus.common.result.type.CommonResult;
import com.media.bus.contract.entity.member.MemberType;
import com.media.bus.contract.security.JwtProvider;
import com.media.bus.contract.security.MemberPrincipal;
import com.media.bus.iam.auth.entity.MemberRole;
import com.media.bus.iam.auth.repository.MemberRoleRepository;
import com.media.bus.iam.member.dto.MemberResponse;
import com.media.bus.iam.member.entity.Member;
import com.media.bus.iam.member.repository.MemberRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;
    private final MemberRoleRepository memberRoleRepository;

    /// 로그인시 사용된 jwt 토큰으로 회원 조회.
    ///
    /// @param jwt jwt 토큰
    /// @return MemberResponse
    @Transactional(readOnly = true)
    public MemberResponse findByJwtMember(@NotBlank @NotEmpty String jwt) {
        MemberPrincipal principal = jwtProvider.getPrincipalFromClaims(
            jwtProvider.parseClaimsFromToken(jwt)
        );

        Member member = memberRepository.findById(principal.id())
            .orElseThrow(() -> new BusinessException(CommonResult.USER_NOT_FOUND_FAIL));

        return toResponse(member);
    }

    /// memberId를 통한 회원 조회.
    ///
    /// @param memberId memberId(pk)
    /// @return MemberResponse
    @Transactional(readOnly = true)
    public MemberResponse findByMemberId(@NotBlank @NotEmpty String memberId) {

        Member member = memberRepository.findById(UUID.fromString(memberId))
            .orElseThrow(() -> new BusinessException(CommonResult.USER_NOT_FOUND_FAIL));

        return toResponse(member);
    }

    /// 로그인 아이디를 통한 회원 조회.
    ///
    /// @param loginId 로그인시 사용되는 아이디
    /// @return MemberResponse
    public MemberResponse findByLoginId(@NotBlank @NotEmpty String loginId) {

        Member member = memberRepository.findByLoginId(loginId)
            .orElseThrow(() -> new BusinessException(CommonResult.USER_NOT_FOUND_FAIL));

        return toResponse(member);
    }

    /// 이메일을 통한 회원 조회.
    ///
    /// @param email 회원 이메일
    /// @return MemberResponse
    public MemberResponse findByEmail(@NotBlank @NotEmpty String email) {

        Member member = memberRepository.findByEmail(email)
            .orElseThrow(() -> new BusinessException(CommonResult.USER_NOT_FOUND_FAIL));

        return toResponse(member);
    }

    /// Member 엔티티를 MemberResponse로 변환한다.
    /// 역할 조회, MemberType 결정, 응답 생성을 통합 처리.
    ///
    /// @param member 변환할 Member 엔티티
    /// @return MemberResponse
    private MemberResponse toResponse(Member member) {
        // JOIN FETCH로 역할 조회 (member.member_type 컬럼 제거에 따른 변경)
        List<MemberRole> memberRoles = memberRoleRepository.findWithRoleByMemberId(member.getId());
        if (memberRoles.isEmpty()) {
            throw new BaseException(CommonResult.USERNAME_NOT_FOUND_FAIL);
        }
        MemberType memberType = MemberType.fromName(memberRoles.getFirst().getRole().getName())
            .orElseThrow(() -> new BaseException(CommonResult.INTERNAL_ERROR));

        return new MemberResponse(
            member.getId(),
            member.getLoginId(),
            member.getEmail(),
            member.getPhoneNumber(),
            memberType,
            member.getStatus(),
            member.getBusinessNumber(),
            member.getCreatedAt(),
            member.getUpdatedAt()
        );
    }
}
