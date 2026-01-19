-- Migration V2: add inventory_movements table and new columns

-- 1) Add last_purchase_price to materials
ALTER TABLE materials
    ADD COLUMN last_purchase_price DECIMAL(12,2) NULL;

-- 2) Add processed_by and processed_at to assignments
ALTER TABLE assignments
    ADD COLUMN processed_by INT NULL,
    ADD COLUMN processed_at TIMESTAMP NULL;

-- 3) Create inventory_movements table
CREATE TABLE IF NOT EXISTS inventory_movements (
    id INT AUTO_INCREMENT PRIMARY KEY,
    material_id INT NULL,
    recipe_id INT NULL,
    type VARCHAR(16) NOT NULL,
    quantity DECIMAL(16,4) NOT NULL DEFAULT 0,
    unit_price DECIMAL(12,4) NULL,
    total_price DECIMAL(16,2) NULL,
    user_id INT NULL,
    related_assignment_id INT NULL,
    related_task_id INT NULL,
    notes VARCHAR(1024) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX (material_id),
    INDEX (type),
    INDEX (user_id),
    INDEX (related_assignment_id)
);

-- Optional: add foreign key constraints if desired (ensure referenced tables exist and use same engine)
-- ALTER TABLE inventory_movements
--   ADD CONSTRAINT fk_inv_mat FOREIGN KEY (material_id) REFERENCES materials(id),
--   ADD CONSTRAINT fk_inv_user FOREIGN KEY (user_id) REFERENCES users(id);

COMMIT;

