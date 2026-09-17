-- ============================
-- Table: inventory
-- ============================
CREATE TABLE IF NOT EXISTS inventory (
    product_id  VARCHAR(50) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    stock       INTEGER NOT NULL CHECK (stock >= 0)
);

-- ============================
-- Table: orders
-- ============================
CREATE TABLE IF NOT EXISTS orders (
    order_id    SERIAL PRIMARY KEY,
    product_id  VARCHAR(50) NOT NULL REFERENCES inventory(product_id),
    quantity    INTEGER NOT NULL CHECK (quantity > 0),
    status      VARCHAR(20) NOT NULL,
    reason      TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================
-- Seed data: inventory
-- ============================
INSERT INTO inventory (product_id, name, stock) VALUES
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0)
ON CONFLICT (product_id) DO UPDATE
    SET name = EXCLUDED.name,
        stock = EXCLUDED.stock;





-- UPDATE SCHEMA

-- Drop in dependency order
DROP TABLE IF EXISTS notifications CASCADE;
DROP TABLE IF EXISTS order_items CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS inventory CASCADE;

-- Inventory: unchanged from Lab 1
CREATE TABLE inventory (
    product_id VARCHAR(20) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    stock INTEGER NOT NULL CHECK (stock >= 0)
);

-- Orders: no longer holds product_id/quantity directly (moved to order_items),
-- since an order can now have multiple line items. status now also allows CANCELLED.
CREATE TABLE orders (
    order_id SERIAL PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- One row per product line in an order
CREATE TABLE order_items (
    order_item_id SERIAL PRIMARY KEY,
    order_id INTEGER NOT NULL REFERENCES orders(order_id),
    product_id VARCHAR(20) NOT NULL REFERENCES inventory(product_id),
    quantity INTEGER NOT NULL CHECK (quantity > 0)
);

-- Activity feed written by the Notification module
CREATE TABLE notifications (
    notification_id SERIAL PRIMARY KEY,
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seed data (same as Lab 1)
INSERT INTO inventory (product_id, name, stock) VALUES
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0);