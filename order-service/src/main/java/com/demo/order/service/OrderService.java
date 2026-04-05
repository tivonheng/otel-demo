package com.demo.order.service;

import com.demo.order.chaos.ChaosMonkey;
import com.demo.order.model.CreateOrderRequest;
import com.demo.order.model.Order;
import com.demo.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChaosMonkey chaosMonkey;
    private final String inventoryServiceUrl;

    public OrderService(OrderRepository orderRepository,
                        RestTemplate restTemplate,
                        StringRedisTemplate redisTemplate,
                        ObjectMapper objectMapper,
                        ChaosMonkey chaosMonkey,
                        @Value("${inventory-service.url}") String inventoryServiceUrl) {
        this.orderRepository = orderRepository;
        this.restTemplate = restTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.chaosMonkey = chaosMonkey;
        this.inventoryServiceUrl = inventoryServiceUrl;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Optional<Order> getOrderById(Long id) {
        log.info("Querying order by id={}", id);
        String cacheKey = "order:" + id;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("Cache HIT for order id={}", id);
                return Optional.of(objectMapper.readValue(cached, Order.class));
            }
        } catch (Exception e) {
            log.warn("Redis read error for order id={}: {}", id, e.getMessage());
        }

        log.info("Cache MISS for order id={}, querying database", id);
        Optional<Order> order = orderRepository.findById(id);
        order.ifPresent(o -> {
            try {
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(o), Duration.ofSeconds(60));
            } catch (Exception e) {
                log.warn("Redis write error for order id={}: {}", id, e.getMessage());
            }
        });
        return order;
    }

    public List<Order> getOrdersByUserId(String userId) {
        return orderRepository.findByUserId(userId);
    }

    @SuppressWarnings("unchecked")
    public Order createOrder(CreateOrderRequest request) {
        chaosMonkey.maybeInjectFault();

        String userId = request.getUserId();
        String sku = request.getSku();
        int quantity = request.getQuantity();

        log.info("Creating order: user={}, sku={}, quantity={}", userId, sku, quantity);

        // Check inventory
        log.info("Calling inventory-service to check stock for sku={}", sku);
        ResponseEntity<Map> checkResponse;
        try {
            checkResponse = restTemplate.getForEntity(inventoryServiceUrl + "/api/inventory/" + sku, Map.class);
        } catch (HttpStatusCodeException e) {
            log.warn("Inventory check failed for sku={} with status={}", sku, e.getStatusCode());
            throw new OrderServiceException(e.getStatusCode(), "Inventory check failed: " + e.getResponseBodyAsString());
        }

        if (!checkResponse.getStatusCode().is2xxSuccessful() || checkResponse.getBody() == null) {
            log.warn("Inventory check failed for sku={}: {}", sku, checkResponse.getStatusCode());
            throw new OrderServiceException(HttpStatus.BAD_GATEWAY, "Inventory check failed for sku=" + sku);
        }

        // Reserve inventory
        log.info("Calling inventory-service to reserve stock for sku={}", sku);
        Map<String, Object> reserveBody = Map.of("sku", sku, "quantity", quantity);
        ResponseEntity<Map> reserveResponse;
        try {
            reserveResponse = restTemplate.postForEntity(
                    inventoryServiceUrl + "/api/inventory/reserve", reserveBody, Map.class);
        } catch (HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            log.warn("Inventory reserve failed for sku={} with status={}, body={}", sku, e.getStatusCode(), responseBody);
            throw new OrderServiceException(e.getStatusCode(), "Inventory reserve failed: " + responseBody);
        }

        if (!reserveResponse.getStatusCode().is2xxSuccessful()) {
            String errorMsg = reserveResponse.getBody() != null ? reserveResponse.getBody().toString() : "Reserve failed";
            log.warn("Inventory reserve failed for sku={}: {}", sku, errorMsg);
            throw new OrderServiceException(reserveResponse.getStatusCode(), "Inventory reserve failed: " + errorMsg);
        }

        // Get price from inventory response
        Map<String, Object> inventoryData = checkResponse.getBody();
        BigDecimal price = new BigDecimal(inventoryData.get("price").toString());
        BigDecimal totalPrice = price.multiply(BigDecimal.valueOf(quantity));

        // Create order
        Order order = new Order(userId, sku, quantity, totalPrice);
        order = orderRepository.save(order);
        log.info("Order created successfully: orderId={}, totalPrice={}", order.getId(), order.getTotalPrice());

        // Invalidate cache
        try {
            redisTemplate.delete("order:" + order.getId());
        } catch (Exception e) {
            log.warn("Redis cache invalidation error: {}", e.getMessage());
        }

        return order;
    }

    public static class OrderServiceException extends RuntimeException {
        private final HttpStatusCode statusCode;

        public OrderServiceException(HttpStatusCode statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public HttpStatusCode getStatusCode() {
            return statusCode;
        }
    }
}
