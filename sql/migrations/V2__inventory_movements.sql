-- Migration: add inventory_movements table and nullable columns

ALTER TABLE materials ADD COLUMN IF NOT EXISTS last_purchase_price DECIMAL(14,2) NULL;

ALTER TABLE assignments ADD COLUMN IF NOT EXISTS processed_by INT NULL;
ALTER TABLE assignments ADD COLUMN IF NOT EXISTS processed_at TIMESTAMP NULL;

CREATE TABLE IF NOT EXISTS inventory_movements (
  id INT AUTO_INCREMENT PRIMARY KEY,
  material_id INT NULL,
  recipe_id INT NULL,
  type VARCHAR(10) NOT NULL,
  quantity DOUBLE NOT NULL,
  unit_price DECIMAL(14,2) NULL,
  total_price DECIMAL(16,2) NULL,
  user_id INT NULL,
  related_assignment_id INT NULL,
  related_task_id INT NULL,
  notes TEXT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inv_mov_created_at ON inventory_movements(created_at);
CREATE INDEX idx_inv_mov_material ON inventory_movements(material_id);
CREATE INDEX idx_inv_mov_recipe ON inventory_movements(recipe_id);

-- Optional view for finished goods
CREATE OR REPLACE VIEW finished_goods_available AS
SELECT recipe_id, SUM(CASE WHEN type = 'IN' THEN quantity ELSE -quantity END) AS boxes_available
FROM inventory_movements WHERE recipe_id IS NOT NULL
GROUP BY recipe_id;

