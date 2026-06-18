package com.media.bus.reservation.reservation.controller

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "예약 조회 API S2S 전용", description = "현재 예약에 관련된 internal API")
@RestController
@RequestMapping("/api/v1/internal")
class InternalReservationController(

) {

}