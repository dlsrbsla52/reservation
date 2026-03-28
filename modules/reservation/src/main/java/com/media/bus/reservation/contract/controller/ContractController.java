package com.media.bus.reservation.contract.controller;

import com.media.bus.common.result.type.CommonResult;
import com.media.bus.common.web.response.DataView;
import com.media.bus.contract.entity.member.MemberCategory;
import com.media.bus.contract.entity.member.Permission;
import com.media.bus.contract.security.MemberPrincipal;
import com.media.bus.contract.security.annotation.Authorize;
import com.media.bus.contract.security.annotation.CurrentMember;
import com.media.bus.reservation.contract.dto.request.CreateContractRequest;
import com.media.bus.reservation.contract.dto.response.ContractResponse;
import com.media.bus.reservation.contract.service.ContractFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "계약 API", description = "정류소 광고 계약 생성 및 관리 API")
@Slf4j
@RestController
@RequestMapping("/api/v1/contract")
@RequiredArgsConstructor
public class ContractController {

    private final ContractFacade facade;

    /// 계약 생성.
    /// - @Authorize: JWT 인증 필수
    /// - HttpServletRequest: IAM 재검증을 위해 원본 JWT 문자열 추출에 필요
    /// 어드민만 등록 가능(write 권한 필수)
    @Authorize(categories = {MemberCategory.ADMIN}, permissions = {Permission.WRITE})
    @Operation(summary = "계약 생성", description = "정류소 광고 계약을 생성합니다. IAM DB 회원 재검증 후 계약을 저장합니다.")
    @PostMapping
    public DataView<ContractResponse> createContract(
            @CurrentMember MemberPrincipal principal,
            @RequestBody @Valid CreateContractRequest request,
            HttpServletRequest httpRequest
    ) {

        return DataView.<ContractResponse>builder()
                .result(CommonResult.SUCCESS)
                .data(facade.createContract(
                    principal,
                    MemberPrincipal.extractBearerToken(httpRequest),
                    request))
                .build();
    }
}