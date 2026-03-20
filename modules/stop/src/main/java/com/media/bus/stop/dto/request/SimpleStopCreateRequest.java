package com.media.bus.stop.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO for {@link com.media.bus.stop.entity.Stop}
 */
public record SimpleStopCreateRequest(

    @NotNull
    @Size(max = 50)
    String stopId,

    @NotNull
    @Size(max = 50)
    String stopName,

    @NotNull
    @Size(max = 50)
    String xCrd,

    @NotNull
    @Size(max = 50)
    String yCrd,

    @NotNull
    @Size(max = 50)
    String nodeId,

    @NotNull
    @Size(max = 50)
    String stopsType

) {}
