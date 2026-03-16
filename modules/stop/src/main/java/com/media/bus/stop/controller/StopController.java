package com.media.bus.stop.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "정류장 API", description = "버스 정류장의 정보 등과 관련된 API")
@RestController
@RequestMapping("/api/v1/stop")
public class StopController {


}
