CREATE TABLE IF NOT EXISTS inventory (
    sku_id   VARCHAR(255) NOT NULL,
    quantity INT          NOT NULL,
    CONSTRAINT pk_inventory PRIMARY KEY (sku_id)
);
