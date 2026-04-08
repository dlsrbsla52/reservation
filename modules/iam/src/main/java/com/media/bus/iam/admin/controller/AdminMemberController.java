package com.media.bus.iam.admin.controller;

import com.media.bus.common.web.response.ApiResponse;
import com.media.bus.contract.entity.member.MemberType;
import com.media.bus.contract.entity.member.Permission;
import com.media.bus.contract.security.annotation.Authorize;
import com.media.bus.iam.admin.dto.AdminMemberResponse;
import com.media.bus.iam.admin.dto.CreateAdminMemberRequest;
import com.media.bus.iam.admin.service.AdminMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// 어드민 사이트 전용 멤버 관리 컨트롤러.
/// ADMIN_MASTER + MANAGE 권한을 가진 사용자(manager)만 접근 가능합니다.
/// 인증/인가 흐름:
/// Gateway(JWT 검증, X-User-* 헤더 주입)
///   → MemberPrincipalExtractFilter (헤더 → MemberPrincipal 복원)
///   → AuthorizeHandlerInterceptor (@Authorize 검사: ADMIN_MASTER + MANAGE)
///   → AdminMemberController
@Tag(name = "어드민 멤버 관리 API", description = "어드민 사이트 전용 멤버 생성 API. ADMIN_MASTER + MANAGE 권한 필요.")
@RestController
@RequestMapping("/api/v1/admin")
@Authorize(types = {MemberType.ADMIN_MASTER}, permissions = {Permission.MANAGE})
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    /// 어드민 멤버 생성.
    /// ADMIN_MASTER + MANAGE 권한 보유 시에만 호출 가능합니다.
    /// 생성된 어드민 계정은 emailVerified = true 상태로 즉시 활성화됩니다.
    @Operation(
        summary = "어드민 멤버 생성",
        description = "어드민 사이트에서 새 어드민 계정을 생성합니다. ADMIN_MASTER + MANAGE 권한 필요."
    )
    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminMemberResponse> createAdminMember(
            @RequestBody @Valid CreateAdminMemberRequest request
    ) {
        return ApiResponse.success(adminMemberService.createAdminMember(request));
    }
}