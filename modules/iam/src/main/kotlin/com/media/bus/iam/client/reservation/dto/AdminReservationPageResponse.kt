package com.media.bus.iam.client.reservation.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class AdminReservationPageResponse(val data: AdminReservationPage?) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AdminReservationPage(
        val items: List<AdminReservationView>?,
        val totalCnt: Long?,
        val pageRows: Int?,
        val pageNum: Int?,
    )
}
