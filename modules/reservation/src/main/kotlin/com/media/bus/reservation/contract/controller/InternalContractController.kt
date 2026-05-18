package com.media.bus.reservation.contract.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.reservation.contract.dto.response.AdminContractView
import com.media.bus.reservation.contract.service.InternalContractFacade
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * ## MSA 내부 서비스 간 통신 전용 계약 컨트롤러
 *
 * 외부 노출 금지 — `SecurityConfig`에 등록된 `S2STokenFilter`가
 * `/api/v1/internal` 하위 전체 경로를 보호한다.
 * `@Authorize` 없이 S2S 토큰(`X-Service-Token`) 검증만으로 접근을 허용한다.
 *
 * 설계 의도:
 * - 사용자 노출 API(`ContractController.getMyContracts`)는 본인 계약만 조회 가능하지만,
 *   이 엔드포인트는 호출 측(iam)이 이미 ADMIN 권한 검증을 마쳤다고 전제하고
 *   인증된 회원과 무관하게 임의 매니저(memberId)의 계약 목록을 반환한다.
 * - 정류소 정보(stop) 결합은 facade 내부에서 bulk S2S 조회로 처리한다.
 */
@Tag(name = "계약 내부 API", description = "MSA 내부 서비스 간 통신 전용 — 외부 호출 금지, S2S 토큰 필수")
@RestController
@RequestMapping("/api/v1/internal/reservation/contract")
class InternalContractController(
    private val internalContractFacade: InternalContractFacade,
) {

    /**
     * 매니저(memberId)의 계약 목록을 페이지 단위로 조회한다.
     *
     * 호출 사이드: iam 어드민 모듈(`AdminBusinessContractFacade`).
     * 응답에는 정류소 정보가 결합된 어드민 view 객체가 포함된다.
     */
    @Operation(
        summary = "매니저 계약 목록 조회 (내부 전용)",
        description = "특정 매니저(memberId)의 계약 목록을 페이지 단위로 조회한다. 정류소 정보가 결합되어 반환된다.",
    )
    @GetMapping("/member/{memberId}")
    fun getContractsByMember(
        @PathVariable memberId: UUID,
        @Parameter(description = "0-base 페이지 번호") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResult<AdminContractView>> =
        ApiResponse.success(internalContractFacade.getContractsByMemberId(memberId, page, size))
}
