package com.media.bus.reservation.reservation.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.entity.member.MemberCategory
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.reservation.reservation.dto.response.MyReservationResponse
import com.media.bus.reservation.reservation.service.ReservationFacade
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * ## 어드민 전용 예약 조회 컨트롤러
 *
 * 운영팀 등 어드민(`ADMIN` 카테고리 + `READ` 권한)이 특정 회원의 예약 이력을 조회한다.
 *
 * **설계 의도(모듈 경계)**: iam의 회원 검색과 reservation의 예약 조회를 분리한다.
 * 프론트는 회원 상세(iam) + 예약 목록(reservation)을 각각 호출하며, reservation은 회원 정보를
 * 합성하지 않는다. 회원별 예약을 회원 검색 결과에 join 하면 행마다 S2S 호출이 발생하므로 분리한다.
 *
 * 게이트웨이 라우팅: `/api/v1/reservation/` 이하 → 8183.
 */
@Tag(name = "어드민 예약 조회 API", description = "어드민이 특정 회원의 예약 이력을 조회하는 API. ADMIN 카테고리 + READ 권한 필요.")
@RestController
@RequestMapping("/api/v1/reservation/admin")
@Authorize(categories = [MemberCategory.ADMIN], permissions = [Permission.READ])
class AdminReservationController(
    private val facade: ReservationFacade,
) {
    companion object {
        /** 페이지 크기 상한. 과대 요청으로 인한 부하(DoS 표면)를 막는다. */
        private const val MAX_PAGE_SIZE = 100
    }

    /**
     * 특정 회원의 예약 목록을 페이지 단위로 조회한다. 정류소 정보가 결합되어 반환된다.
     *
     * **존재하지 않는 회원**: 회원 존재 여부는 iam 책임이므로 여기서 검증하지 않는다(모듈 경계).
     * 어드민 UI 흐름상 회원 상세(iam) 확인 후 진입하므로, 미존재 회원이면 404 대신 빈 목록을 반환한다
     * (서브 컬렉션은 부모 부재 시에도 빈 컬렉션이 RESTful 하게 유효). 회원 존재 확인이 필요하면 iam을 호출한다.
     */
    @Operation(
        summary = "회원별 예약 목록 조회",
        description = "특정 회원(memberId)의 예약 목록을 페이지 단위로 조회합니다. ADMIN 카테고리 + READ 권한 필요.",
    )
    @GetMapping("/members/{memberId}/reservations")
    fun getMemberReservations(
        @PathVariable memberId: UUID,
        @Parameter(description = "0-base 페이지 번호") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResult<MyReservationResponse>> {
        // 음수 page 방지, size 상한 보정
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)
        return ApiResponse.success(facade.getMemberReservations(memberId, safePage, safeSize))
    }
}
