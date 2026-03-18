package com.media.bus.stop.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link com.media.bus.stop.entity.Stop}
 */
public record SimpleStopCreateRequest(

    @NotNull
    @Size(max = 50)
    String stopId,
    
    @NotNull
    @Size(max = 50)
    String tmX,
    
    @NotNull
    @Size(max = 50)
    String tmY,
    
    @NotNull
    @Size(max = 50)
    String arsId,
    
    @NotNull
    @Size(max = 50)
    String posX,
    
    @NotNull
    @Size(max = 50)
    String posY,
    
    @NotNull
    @Size(max = 50)
    String stopName
    
) {
    
}
    