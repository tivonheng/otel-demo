package com.demo.order.controller;

import com.demo.order.chaos.ChaosMonkey;
import com.demo.order.model.CreateOrderRequest;
import com.demo.order.model.Order;
import com.demo.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private final OrderService orderService;
    private final ChaosMonkey chaosMonkey;

    public OrderController(OrderService orderService, ChaosMonkey chaosMonkey) {
        this.orderService = orderService;
        this.chaosMonkey = chaosMonkey;
    }

    @GetMapping
    public ResponseEntity<?> getAllOrders(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        var httpError = chaosMonkey.maybeHttpError();
        if (httpError.isPresent()) {
            return ResponseEntity.status(httpError.get()).body(Map.of("error", "Chaos fault injected"));
        }
        if (page < 0 || size < 1 || size > 200) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid pagination params: page>=0 and 1<=size<=200"));
        }
        return ResponseEntity.ok(orderService.getAllOrders(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOrderById(@PathVariable Long id) {
        var httpError = chaosMonkey.maybeHttpError();
        if (httpError.isPresent()) {
            return ResponseEntity.status(httpError.get()).body(Map.of("error", "Chaos fault injected"));
        }
        return orderService.getOrderById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getOrdersByUserId(@PathVariable String userId) {
        var httpError = chaosMonkey.maybeHttpError();
        if (httpError.isPresent()) {
            return ResponseEntity.status(httpError.get()).body(Map.of("error", "Chaos fault injected"));
        }
        return ResponseEntity.ok(orderService.getOrdersByUserId(userId));
    }

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request) {
        var httpError = chaosMonkey.maybeHttpError();
        if (httpError.isPresent()) {
            return ResponseEntity.status(httpError.get()).body(Map.of("error", "Chaos fault injected"));
        }
        try {
            Order order = orderService.createOrder(request);
            return ResponseEntity.ok(order);
        } catch (OrderService.OrderServiceException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Order creation failed for user={}, sku={}: {}", request.getUserId(), request.getSku(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/slow")
    public ResponseEntity<?> slowEndpoint() {
        try {
            int delay = ThreadLocalRandom.current().nextInt(2000, 5001);
            log.info("Slow endpoint called, sleeping for {}ms", delay);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ResponseEntity.ok(Map.of("message", "Slow response completed"));
    }
}
