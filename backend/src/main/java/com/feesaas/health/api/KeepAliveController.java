package com.feesaas.health.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KeepAliveController {

    @GetMapping("/api/health/keep-alive")
    public Map<String, String> keepAlive() {
        return Map.of("status", "ok");
    }
}
