package com.gateway.gateway.healthcheck;

import com.common.result.type.CommonResult;
import com.common.web.response.NoDataView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class HealthCheck {

    @GetMapping("/health-check")
    public NoDataView healthCheck() {
        return NoDataView.builder()
                .result(CommonResult.REQUEST_SUCCESS)
                .build();
    }
}
