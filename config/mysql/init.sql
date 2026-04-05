USE demodb;

CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    total_price DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_sku (sku),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(50) UNIQUE NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    quantity INT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sku (sku)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO orders (user_id, sku, quantity, status, total_price) VALUES
('user-001', 'SKU-001', 2, 'COMPLETED', 59.98),
('user-002', 'SKU-003', 1, 'COMPLETED', 149.99),
('user-001', 'SKU-002', 3, 'PENDING', 29.97),
('user-003', 'SKU-004', 1, 'COMPLETED', 79.99),
('user-002', 'SKU-001', 5, 'SHIPPED', 149.95);

INSERT INTO inventory (sku, product_name, quantity, price) VALUES
('SKU-001', 'Wireless Mouse', 100, 29.99),
('SKU-002', 'USB-C Cable', 500, 9.99),
('SKU-003', 'Mechanical Keyboard', 50, 149.99),
('SKU-004', 'Monitor Stand', 30, 79.99),
('SKU-005', 'Webcam HD', 0, 59.99);
