package com.media.bus.iam.client.reservation.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** reservation 내부 계약 생성 응답 래퍼. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ContractResponse(val data: ContractView?)
