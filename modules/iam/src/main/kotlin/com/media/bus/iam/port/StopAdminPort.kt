package com.media.bus.iam.port

import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.iam.port.dto.stop.*
import java.math.BigDecimal
import java.util.*

/**
 * ## 관리자 기능이 쓰는 stop 모듈 포트
 *
 * 설계 의도:
 * - iam은 stop 모듈을 컴파일 의존하지 않는다. 소비자(iam)가 필요한 기능을 이 포트로 정의하고,
 *   구현은 바깥에서 주입한다.
 * - 구현은 `app` 모듈의 `IamStopAdminAdapter`이고, stop의 공개 진입점(Service)을 직접 호출한다.
 * - 메서드 계약(빈 결과, 예외 상태·코드)은 예전 HTTP 클라이언트(`StopServiceClient`)와 같게 유지한다.
 */
interface StopAdminPort {

    /** pk(UUID) 기준으로 정류소를 조회한다. 없으면 빈 리스트. */
    fun getStopByPk(pk: UUID): List<StopInfo>

    /** stopId(정류소 번호) 기준으로 정류소를 조회한다. 없으면 빈 리스트. */
    fun getStopByStopId(stopId: String): List<StopInfo>

    /** stopName(이름 전방 일치) 기준으로 최대 100건을 조회한다. 초과하면 400으로 페이지 검색을 안내한다. */
    fun getStopByStopName(stopName: String): List<StopInfo>

    /** pk(UUID) 복수 일괄 조회. 누락된 id는 결과에 포함되지 않는다. */
    fun getStopsByPks(pks: Collection<UUID>): List<StopInfo>

    /** 관리자 목록 화면용 정류소 페이지 조회. `keyword`·`stopsType`은 선택 필터. */
    fun getStopPage(page: Int, size: Int, keyword: String?, stopsType: String?): PageResult<StopInfo>

    /** 정류소 단가 조회. 등록된 단가가 없으면 `null`. */
    fun getStopPrice(stopId: UUID): StopPriceInfo?

    /** 정류소 단가 신규 등록. */
    fun createStopPrice(stopId: UUID, unitPrice: BigDecimal, registeredById: UUID): StopPriceInfo

    /** 정류소 단가 수정. */
    fun updateStopPrice(stopId: UUID, unitPrice: BigDecimal, changedById: UUID): StopPriceInfo

    /** 정류소 단가 삭제. */
    fun deleteStopPrice(stopId: UUID, changedById: UUID)

    /** 지도 영역 내 정류소 마커 조회. 응답이 비면 오류로 본다. */
    fun getStopsInBounds(
        swLat: String,
        swLng: String,
        neLat: String,
        neLng: String,
        limit: Int?,
        stopsType: String?,
    ): StopBoundsInfo

    /** 정류소 단건 수기 등록. */
    fun createSimpleStop(request: SimpleStopCreateRequest, registeredById: UUID)

    /** 공공 API 전체 정류소 일괄 등록. 이미 실행 중이면 409. */
    fun registerBulk(): StopBulkRegisterInfo
}
