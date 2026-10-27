package com.healthee.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/utilities/public")
public class RenderHealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        String message = "🟢 I'm up and active! 🚀";

        log.info("{}", message);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "success", true,
                        "message", message
                ));
    }
}