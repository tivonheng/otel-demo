package com.demo.inventory.service;

import com.demo.inventory.chaos.ChaosMonkey;
import com.demo.inventory.model.InventoryItem;
import com.demo.inventory.model.ReserveRequest;
import com.demo.inventory.repository.InventoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChaosMonkey chaosMonkey;

    public InventoryService(InventoryRepository inventoryRepository,
                            StringRedisTemplate redisTemplate,
                            ObjectMapper objectMapper,
                            ChaosMonkey chaosMonkey) {
        this.inventoryRepository = inventoryRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.chaosMonkey = chaosMonkey;
    }

    private void simulateDbLatency() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(30, 201));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public List<InventoryItem> getAllInventory() {
        simulateDbLatency();
        return inventoryRepository.findAll();
    }

    public Optional<InventoryItem> getInventoryBySku(String sku) {
        log.info("Checking inventory for sku={}", sku);
        simulateDbLatency();
        String cacheKey = "inventory:" + sku;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("Inventory cache HIT for sku={}", sku);
                return Optional.of(objectMapper.readValue(cached, InventoryItem.class));
            }
        } catch (Exception e) {
            log.warn("Redis read error for sku={}: {}", sku, e.getMessage());
        }

        log.info("Inventory cache MISS for sku={}, querying database", sku);
        Optional<InventoryItem> item = inventoryRepository.findBySku(sku);
        item.ifPresent(i -> {
            try {
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(i), Duration.ofSeconds(120));
            } catch (Exception e) {
                log.warn("Redis write error for sku={}: {}", sku, e.getMessage());
            }
        });
        return item;
    }

    @Transactional
    public InventoryItem reserveStock(ReserveRequest request) {
        chaosMonkey.maybeInjectFault();

        String sku = request.getSku();
        int quantity = request.getQuantity();
        log.info("Reserving {} units for sku={}", quantity, sku);

        InventoryItem item = inventoryRepository.findBySku(sku)
                .orElseThrow(() -> new RuntimeException("SKU not found: " + sku));

        if (item.getQuantity() < quantity) {
            log.warn("Insufficient stock: sku={}, available={}, requested={}", sku, item.getQuantity(), quantity);
            throw new InsufficientStockException(sku, item.getQuantity(), quantity);
        }

        item.setQuantity(item.getQuantity() - quantity);
        item = inventoryRepository.save(item);
        log.info("Reservation successful: sku={}, remaining={}", sku, item.getQuantity());

        // Update cache
        try {
            String cacheKey = "inventory:" + sku;
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(item), Duration.ofSeconds(120));
        } catch (Exception e) {
            log.warn("Redis cache update error for sku={}: {}", sku, e.getMessage());
        }

        return item;
    }

    public static class InsufficientStockException extends RuntimeException {
        private final String sku;
        private final int available;
        private final int requested;

        public InsufficientStockException(String sku, int available, int requested) {
            super("Insufficient stock for " + sku);
            this.sku = sku;
            this.available = available;
            this.requested = requested;
        }

        public String getSku() { return sku; }
        public int getAvailable() { return available; }
        public int getRequested() { return requested; }
    }
}
