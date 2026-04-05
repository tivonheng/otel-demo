#!/bin/bash
set -e

echo "========================================="
echo " OTel Observability Demo - Quick Start"
echo "========================================="

echo "[1/2] Building and starting all services with Docker Compose..."
echo "  (Maven build runs inside Docker — no local Maven needed)"
docker compose up -d --build

echo "[2/2] Waiting for services to be ready..."
echo "  Waiting for Grafana..."
until curl -sf http://localhost:3000/api/health > /dev/null 2>&1; do sleep 2; done
echo "  Waiting for order-service..."
until curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; do sleep 2; done
echo "  Waiting for inventory-service..."
until curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1; do sleep 2; done

echo ""
echo "========================================="
echo " All services are up and running!"
echo "========================================="
echo ""
echo " Grafana:            http://localhost:3000"
echo " Order Service:      http://localhost:8080"
echo " Inventory Service:  http://localhost:8081"
echo " Prometheus:         http://localhost:9090"
echo " Tempo:              http://localhost:3200"
echo ""
echo " Chaos Engineering:"
echo "   Status:         curl http://localhost:8080/chaos/status"
echo "   Slow DB:        curl -X POST http://localhost:8081/chaos/scenario/slow-db"
echo "   High errors:    curl -X POST http://localhost:8081/chaos/scenario/high-error-rate"
echo "   Cascade fail:   curl -X POST http://localhost:8081/chaos/scenario/cascade-failure"
echo "   Reset:          curl -X POST http://localhost:8081/chaos/reset"
echo ""
echo " Load generator is running automatically."
echo " Wait ~1 minute for data to appear in Grafana."
echo "========================================="
