package com.media.bus.auth.member.service;

import com.media.bus.auth.member.dto.MemberResponse;
import com.media.bus.auth.member.entity.Member;
import com.media.bus.auth.member.repository.MemberRepository;
import com.media.bus.common.exceptions.ServiceException;
import com.media.bus.common.result.type.CommonResult;
import com.media.bus.contract.security.JwtProvider;
import com.media.bus.contract.security.MemberPrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;

    /**
     * 로그인시 사용된 jwt 토큰으로 회원 조회.
     *
     * @param jwt jwt 토큰
     * @return MemberResponse
     */
    @Transactional(readOnly = true)
    public MemberResponse findByJwtMember(@NotBlank @NotEmpty String jwt) {
        MemberPrincipal principal = jwtProvider.getPrincipalFromClaims(
            jwtProvider.parseClaimsFromToken(jwt)
        );

        Member member = memberRepository.findById(principal.id())
            .orElseThrow(() -> new ServiceException(CommonResult.USER_NOT_FOUND_FAIL));

        return new MemberResponse(
            member.getId(),
            member.getLoginId(),
            member.getEmail(),
            member.getPhoneNumber(),
            member.getMemberType(),
            member.getStatus(),
            member.getBusinessNumber(),
            member.getCreatedAt(),
            member.getUpdatedAt()
        );
    }

    /**
     * memberId를 통한 회원 조회.
     *
     * @param memberId memberId(pk)
     * @return MemberResponse
     */
    @Transactional(readOnly = true)
    public MemberResponse findByMemberId(@NotBlank @NotEmpty String memberId) {

        Member member = memberRepository.findById(UUID.fromString(memberId))
            .orElseThrow(() -> new ServiceException(CommonResult.USER_NOT_FOUND_FAIL));

        return new MemberResponse(
            member.getId(),
            member.getLoginId(),
            member.getEmail(),
            member.getPhoneNumber(),
            member.getMemberType(),
            member.getStatus(),
            member.getBusinessNumber(),
            member.getCreatedAt(),
            member.getUpdatedAt()
        );
    }

    /**
     * 로그인 아이디를 통한 회원 조회
     *
     * @param loginId 로그인시 사용되는 아이디
     * @return MemberResponse
     */
    public MemberResponse findByLoginId(@NotBlank @NotEmpty String loginId) {

        Member member = memberRepository.findByLoginId(loginId)
            .orElseThrow(() -> new ServiceException(CommonResult.USER_NOT_FOUND_FAIL));

        return new MemberResponse(
            member.getId(),
            member.getLoginId(),
            member.getEmail(),
            member.getPhoneNumber(),
            member.getMemberType(),
            member.getStatus(),
            member.getBusinessNumber(),
            member.getCreatedAt(),
            member.getUpdatedAt()
        );
    }



    /**
     * 로그인 아이디를 통한 회원 조회
     *
     * @param email 로그인시 사용되는 아이디
     * @return MemberResponse
     */
    public MemberResponse findByEmail(@NotBlank @NotEmpty String email) {

        Member member = memberRepository.findByEmail(email)
            .orElseThrow(() -> new ServiceException(CommonResult.USER_NOT_FOUND_FAIL));

        return new MemberResponse(
            member.getId(),
            member.getLoginId(),
            member.getEmail(),
            member.getPhoneNumber(),
            member.getMemberType(),
            member.getStatus(),
            member.getBusinessNumber(),
            member.getCreatedAt(),
            member.getUpdatedAt()
        );
    }
}
