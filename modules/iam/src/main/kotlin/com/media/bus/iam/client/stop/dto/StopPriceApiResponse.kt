package com.media.bus.iam.client.stop.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class StopPriceApiResponse(val data: StopPriceInfo?)
