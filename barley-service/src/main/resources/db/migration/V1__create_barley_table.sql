-- V1: Initial schema for barley_inventory table
-- Flyway applies this exactly once, tracked in flyway_schema_history

CREATE TABLE barley_inventory (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100)   NOT NULL,
    variety       VARCHAR(100)   NOT NULL,
    origin        VARCHAR(100)   NOT NULL,
    quantity_kg   DECIMAL(12, 3) NOT NULL CHECK (quantity_kg >= 0),
    price_per_kg  DECIMAL(10, 2) NOT NULL CHECK (price_per_kg >= 0),
    harvest_year  INT            NOT NULL,
    in_stock      BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version       BIGINT         NOT NULL DEFAULT 0   -- optimistic locking
);

-- Indexes for common query patterns
CREATE INDEX idx_barley_variety   ON barley_inventory (variety);
CREATE INDEX idx_barley_origin    ON barley_inventory (origin);
CREATE INDEX idx_barley_in_stock  ON barley_inventory (in_stock);
CREATE INDEX idx_barley_harvest   ON barley_inventory (harvest_year);

-- Seed data
INSERT INTO barley_inventory (name, variety, origin, quantity_kg, price_per_kg, harvest_year, in_stock)
VALUES
    ('Premium Malt Barley',  'Two-Row',  'Scotland', 5000.000, 1.25, 2023, TRUE),
    ('Organic Spring Barley','Six-Row',  'Germany',  3200.500, 1.80, 2023, TRUE),
    ('Winter Barley Select', 'Winter',   'France',   7800.000, 1.10, 2022, TRUE),
    ('Heritage Hull-less',   'Hull-less','Canada',    500.000, 2.50, 2023, FALSE),
    ('Distillers Grade',     'Two-Row',  'Ireland',  10000.000,1.05, 2022, TRUE);
