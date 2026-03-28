package com.media.bus.stop.dto.request;

import com.media.bus.stop.entity.enums.StopType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/// DTO for [com.media.bus.stop.entity.Stop]
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
    StopType stopsType

) {}
