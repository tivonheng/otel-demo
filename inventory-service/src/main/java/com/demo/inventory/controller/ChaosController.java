package com.demo.inventory.controller;

import com.demo.inventory.chaos.ChaosMonkey;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/chaos")
public class ChaosController {

    private final ChaosMonkey chaosMonkey;

    public ChaosController(ChaosMonkey chaosMonkey) {
        this.chaosMonkey = chaosMonkey;
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return chaosMonkey.getStatus();
    }

    @PostMapping("/enable")
    public ResponseEntity<String> enable() {
        chaosMonkey.enable();
        return ResponseEntity.ok("Chaos monkey enabled");
    }

    @PostMapping("/disable")
    public ResponseEntity<String> disable() {
        chaosMonkey.disable();
        return ResponseEntity.ok("Chaos monkey disabled");
    }

    @PostMapping("/latency")
    public ResponseEntity<String> setLatency(@RequestBody Map<String, Integer> config) {
        int latencyMs = config.getOrDefault("latencyMs", 0);
        int jitterMs = config.getOrDefault("jitterMs", 0);
        chaosMonkey.setLatency(latencyMs, jitterMs);
        chaosMonkey.enable();
        return ResponseEntity.ok("Latency set: " + latencyMs + "ms +/- " + jitterMs + "ms");
    }

    @PostMapping("/exception")
    public ResponseEntity<String> setException(@RequestBody Map<String, Object> config) {
        double rate = ((Number) config.getOrDefault("rate", 0.0)).doubleValue();
        String message = (String) config.getOrDefault("message", "Chaos monkey injected fault");
        chaosMonkey.setException(rate, message);
        chaosMonkey.enable();
        return ResponseEntity.ok("Exception rate set: " + (rate * 100) + "%");
    }

    @PostMapping("/http-error")
    public ResponseEntity<String> setHttpError(@RequestBody Map<String, Object> config) {
        double rate = ((Number) config.getOrDefault("rate", 0.0)).doubleValue();
        int statusCode = ((Number) config.getOrDefault("statusCode", 500)).intValue();
        chaosMonkey.setHttpError(rate, statusCode);
        chaosMonkey.enable();
        return ResponseEntity.ok("HTTP error rate set: " + (rate * 100) + "% with status " + statusCode);
    }

    @PostMapping("/reset")
    public ResponseEntity<String> reset() {
        chaosMonkey.reset();
        return ResponseEntity.ok("Chaos monkey reset to defaults");
    }

    @PostMapping("/scenario/{name}")
    public ResponseEntity<String> applyScenario(@PathVariable String name) {
        switch (name) {
            case "slow-db" -> {
                chaosMonkey.setLatency(3000, 1000);
                chaosMonkey.enable();
                return ResponseEntity.ok("Scenario 'slow-db' applied: 3000ms + 1000ms jitter delay");
            }
            case "redis-timeout" -> {
                chaosMonkey.setLatency(5000, 0);
                chaosMonkey.enable();
                return ResponseEntity.ok("Scenario 'redis-timeout' applied: 5000ms delay");
            }
            case "cascade-failure" -> {
                chaosMonkey.setException(0.8, "Connection refused");
                chaosMonkey.enable();
                return ResponseEntity.ok("Scenario 'cascade-failure' applied: 80% exception rate");
            }
            case "high-error-rate" -> {
                chaosMonkey.setHttpError(0.5, 503);
                chaosMonkey.enable();
                return ResponseEntity.ok("Scenario 'high-error-rate' applied: 50% HTTP 503");
            }
            default -> {
                return ResponseEntity.badRequest().body("Unknown scenario: " + name
                        + ". Available: slow-db, redis-timeout, cascade-failure, high-error-rate");
            }
        }
    }
}
