package com.hig.gateway.healthcheck;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class HealthCheck {

    @GetMapping("/health-check")
    public ResponseEntity<Object> healthCheck() {

        log.debug("테스트 로그");

        return ResponseEntity.ok().build();
    }

}
