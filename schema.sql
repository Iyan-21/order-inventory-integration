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