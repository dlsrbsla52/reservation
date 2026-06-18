package com.media.bus.iam.client.reservation.dto

data class UpdateReservationStatusRequest(
    val status: String,
    val note: String?,
)
