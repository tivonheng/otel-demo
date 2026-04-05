package com.demo.inventory.controller;

import com.demo.inventory.chaos.ChaosMonkey;
import com.demo.inventory.model.InventoryItem;
import com.demo.inventory.model.ReserveRequest;
import com.demo.inventory.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);
    private final InventoryService inventoryService;
    private final ChaosMonkey chaosMonkey;

    public InventoryController(InventoryService inventoryService, ChaosMonkey chaosMonkey) {
        this.inventoryService = inventoryService;
        this.chaosMonkey = chaosMonkey;
    }

    @GetMapping
    public ResponseEntity<?> getAllInventory() {
        var httpError = chaosMonkey.maybeHttpError();
        if (httpError.isPresent()) {
            return ResponseEntity.status(httpError.get()).body(Map.of("error", "Chaos fault injected"));
        }
        return ResponseEntity.ok(inventoryService.getAllInventory());
    }

    @GetMapping("/{sku}")
    public ResponseEntity<?> getInventoryBySku(@PathVariable String sku) {
        var httpError = chaosMonkey.maybeHttpError();
        if (httpError.isPresent()) {
            return ResponseEntity.status(httpError.get()).body(Map.of("error", "Chaos fault injected"));
        }
        return inventoryService.getInventoryBySku(sku)
                .map(item -> ResponseEntity.ok((Object) item))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/reserve")
    public ResponseEntity<?> reserveStock(@RequestBody ReserveRequest request) {
        var httpError = chaosMonkey.maybeHttpError();
        if (httpError.isPresent()) {
            return ResponseEntity.status(httpError.get()).body(Map.of("error", "Chaos fault injected"));
        }
        try {
            InventoryItem item = inventoryService.reserveStock(request);
            return ResponseEntity.ok(item);
        } catch (InventoryService.InsufficientStockException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Insufficient stock",
                    "available", e.getAvailable(),
                    "requested", e.getRequested()
            ));
        } catch (Exception e) {
            log.error("Inventory operation failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
