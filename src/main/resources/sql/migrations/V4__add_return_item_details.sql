-- V10: Denormalize return_item details for historical accuracy
ALTER TABLE return_items ADD COLUMN product_id INTEGER;
ALTER TABLE return_items ADD COLUMN price_per_unit NUMERIC(10,2);
ALTER TABLE return_items ADD COLUMN sku TEXT;
ALTER TABLE return_items ADD COLUMN product_name TEXT;

-- Backfill existing returns if any (optional but good for consistency)
UPDATE return_items SET 
    product_id = (SELECT product_id FROM sale_items WHERE id = return_items.sale_item_id),
    price_per_unit = (SELECT price_per_unit FROM sale_items WHERE id = return_items.sale_item_id),
    sku = (SELECT sku FROM products WHERE id = (SELECT product_id FROM sale_items WHERE id = return_items.sale_item_id)),
    product_name = (SELECT name FROM products WHERE id = (SELECT product_id FROM sale_items WHERE id = return_items.sale_item_id));
