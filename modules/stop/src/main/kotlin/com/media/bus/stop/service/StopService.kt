package com.media.bus.stop.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.common.validation.InputValidator
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.stop.dto.request.SimpleStopCreateRequest
import com.media.bus.stop.dto.request.StopSearchCriteria
import com.media.bus.stop.dto.response.BusStopResponse
import com.media.bus.stop.entity.StopEntity
import com.media.bus.stop.entity.enums.StopType
import com.media.bus.stop.guard.StopCommandGuard
import com.media.bus.stop.repository.StopPriceRepository
import com.media.bus.stop.repository.StopRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class StopService(
    private val stopRepository: StopRepository,
    private val stopPriceRepository: StopPriceRepository,
    private val stopCommandGuard: StopCommandGuard,
) {

    /**
     * 정류소 단건 수기 등록 (등록자 id 직접 지정).
     *
     * 관리자 기능 전용이다. 진입점은 iam `POST /api/v1/admin/stop/simple` → `StopAdminPort` → app 어댑터 하나뿐이고,
     * stop 모듈에는 이 기능의 HTTP 엔드포인트를 두지 않는다(관리자 API 는 `/api/v1/admin` 하위에서 발급처 검사를 받아야 한다).
     * 어댑터는 회원 컨텍스트(`MemberPrincipal`)를 넘기지 않으므로 호출자가 전달한 등록자 id를 그대로 감사 필드에 기록한다.
     * Guard는 stopId 중복 등 비즈니스 규칙만 검증한다.
     *
     * 파라미터의 `@Valid`는 클래스에 `@Validated`가 없어 실행되지 않았으므로, DTO 제약을 직접 검증한다.
     * 모듈 간 직접 호출에서는 컨트롤러 `@Valid`가 거치지 않기 때문이다.
     *
     * @param registeredById 등록한 관리자 회원 id
     * @throws BusinessException `request`가 DTO 제약(길이 50 초과 등)을 어기면 400 `VALIDATION_FAIL`
     */
    @Transactional
    fun createOneStop(registeredById: UUID, request: SimpleStopCreateRequest) {
        InputValidator.validate(request)
        stopCommandGuard.validateNotDuplicate(request.stopId)
        StopEntity.createFromRequest(request, registeredById)
    }

    /**
     * pk, stopId, stopName 중 하나의 기준으로 정류소 조회.
     * - pk / stopId : unique 컬럼이므로 0~1건 반환
     * - stopName    : non-unique이므로 동명 정류소 다건 반환 가능
     */
    @Transactional(readOnly = true)
    fun getBusStop(criteria: StopSearchCriteria): List<BusStopResponse> {
        val entities = when (criteria) {
            is StopSearchCriteria.ByPk       -> stopRepository.findByIdIn(criteria.pk)
            is StopSearchCriteria.ByStopId   -> listOfNotNull(stopRepository.findByStopId(criteria.stopId))
            is StopSearchCriteria.ByStopName -> stopRepository.findByStopNameStartingWith(criteria.stopName)
        }
        if (criteria is StopSearchCriteria.ByStopName && entities.size > StopRepository.MAX_NAME_RESULTS) {
            throw BusinessException(CommonResult.VALIDATION_FAIL, "정류소 이름 조회는 최대 100건입니다. keyword와 페이지 조회를 사용해주세요.")
        }
        return withUnitPrice(entities)
    }

    /**
     * 전체 정류소를 페이지네이션으로 조회한다 (관리자 목록 화면용).
     * keyword가 주어지면 정류소명/정류소번호 부분 일치로, stopsType이 주어지면 해당 유형으로 필터링한다.
     * 두 조건은 AND로 결합된다.
     *
     * 단가는 현재 페이지의 정류소 pk 집합으로 **1회 배치 조회**하여 붙인다(정류소당 1회 호출 금지).
     *
     * @param stopsType 유형 필터 문자열. 알 수 없는 값이면 [StopType.parseFilter]가 400을 던진다.
     */
    @Transactional(readOnly = true)
    fun getBusStopPage(
        page: Int,
        size: Int,
        keyword: String?,
        stopsType: String? = null,
    ): PageResult<BusStopResponse> {
        if (page < 0 || size !in 1..100 || (keyword?.length ?: 0) > 200) {
            throw BusinessException(CommonResult.VALIDATION_FAIL, "page는 0 이상, size는 1~100, keyword는 200자 이하여야 합니다.")
        }
        val kw = keyword?.trim()?.takeIf { it.isNotEmpty() }
        val type = StopType.parseFilter(stopsType)
        return PageResult(
            items = withUnitPrice(stopRepository.findListPaged(kw, type, page, size)),
            totalCnt = stopRepository.countList(kw, type),
            pageRows = size,
            pageNum = page,
        )
    }

    /**
     * pk(UUID) 복수 기반 일괄 조회.
     *
     * 예약 모듈이 예약·계약 목록 조회 시 각 행의 정류소 정보를 **한 번의 조회로** 붙이기 위해 사용한다(`StopLookupPort` 경유).
     * 반환 순서는 입력 순서와 무관하며, 일부 id가 누락되어도 정상 처리한다(예약 목록에서 stopName null로 fallback).
     *
     * @throws BusinessException `ids`가 비었거나 [MAX_BULK_LOOKUP_SIZE]개를 넘으면 400 `VALIDATION_FAIL`
     *   (예전 internal API 요청 DTO의 `@NotEmpty`·`@Size(max = 200)`과 같은 규칙)
     */
    @Transactional(readOnly = true)
    fun getBusStopsByIds(ids: Collection<UUID>): List<BusStopResponse> {
        if (ids.isEmpty() || ids.size > MAX_BULK_LOOKUP_SIZE) throw BusinessException(CommonResult.VALIDATION_FAIL)
        return withUnitPrice(stopRepository.findByIdIn(ids))
    }

    /**
     * 정류소 목록에 현재 단가를 붙인다.
     *
     * 설계 의도:
     * - 모든 조회 경로가 동일한 방식으로 단가를 채워, 응답의 `unitPrice == null`이
     *   항상 "**단가 미등록**"만 의미하도록 보장한다(경로별로 "조회 안 함"이 섞이지 않게).
     * - 단가 조회는 정류소 건수와 무관하게 **쿼리 1회**다.
     *
     * 사이드 이펙트 주의: 정류소 목록이 비어 있으면 DB 조회 자체를 생략한다.
     */
    private fun withUnitPrice(entities: List<StopEntity>): List<BusStopResponse> {
        if (entities.isEmpty()) return emptyList()
        val priceMap = stopPriceRepository.findUnitPriceMapByStopIds(entities.map { it.id.value })
        return entities.map { BusStopResponse.of(it, priceMap[it.id.value]) }
    }

    companion object {
        /** 일괄 조회 최대 건수. 예전 internal API 요청 DTO(`BulkStopLookupRequest.ids`)의 제약과 같다. */
        const val MAX_BULK_LOOKUP_SIZE = 200
    }
}
