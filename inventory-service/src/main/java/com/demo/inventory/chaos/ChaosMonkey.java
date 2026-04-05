package com.demo.inventory.chaos;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ChaosMonkey {

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private volatile int latencyMs = 0;
    private volatile int latencyJitterMs = 0;
    private volatile double exceptionRate = 0.0;
    private volatile String exceptionMessage = "Chaos monkey injected fault";
    private volatile double httpErrorRate = 0.0;
    private volatile int httpErrorCode = 500;

    public void maybeInjectFault() {
        if (!enabled.get()) return;

        if (latencyMs > 0) {
            int jitter = latencyJitterMs > 0 ? ThreadLocalRandom.current().nextInt(latencyJitterMs) : 0;
            try {
                Thread.sleep(latencyMs + jitter);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (exceptionRate > 0 && ThreadLocalRandom.current().nextDouble() < exceptionRate) {
            throw new RuntimeException(exceptionMessage);
        }
    }

    public Optional<Integer> maybeHttpError() {
        if (!enabled.get()) return Optional.empty();
        if (httpErrorRate > 0 && ThreadLocalRandom.current().nextDouble() < httpErrorRate) {
            return Optional.of(httpErrorCode);
        }
        return Optional.empty();
    }

    public void enable() { enabled.set(true); }
    public void disable() { enabled.set(false); }
    public boolean isEnabled() { return enabled.get(); }

    public void setLatency(int ms, int jitterMs) {
        this.latencyMs = ms;
        this.latencyJitterMs = jitterMs;
    }

    public void setException(double rate, String message) {
        this.exceptionRate = rate;
        this.exceptionMessage = message;
    }

    public void setHttpError(double rate, int statusCode) {
        this.httpErrorRate = rate;
        this.httpErrorCode = statusCode;
    }

    public void reset() {
        enabled.set(false);
        latencyMs = 0;
        latencyJitterMs = 0;
        exceptionRate = 0.0;
        exceptionMessage = "Chaos monkey injected fault";
        httpErrorRate = 0.0;
        httpErrorCode = 500;
    }

    public Map<String, Object> getStatus() {
        return Map.of(
            "enabled", enabled.get(),
            "latencyMs", latencyMs,
            "latencyJitterMs", latencyJitterMs,
            "exceptionRate", exceptionRate,
            "exceptionMessage", exceptionMessage,
            "httpErrorRate", httpErrorRate,
            "httpErrorCode", httpErrorCode
        );
    }
}
