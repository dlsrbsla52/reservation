package com.media.bus.iam.client.reservation.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** reservation 내부 계약 상세 응답 래퍼. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AdminContractDetailResponse(val data: AdminContractView?)
