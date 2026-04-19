-- Fix foreign key constraint violation when deleting sale items during bill edits
-- By adding ON DELETE CASCADE to return_items referencing sale_items.

PRAGMA foreign_keys=OFF;

CREATE TABLE return_items_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    return_id INTEGER NOT NULL,
    sale_item_id INTEGER NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    refund_amount NUMERIC(10,2) NOT NULL CHECK (refund_amount >= 0),
    FOREIGN KEY (return_id) REFERENCES returns(id) ON DELETE CASCADE,
    FOREIGN KEY (sale_item_id) REFERENCES sale_items(id) ON DELETE CASCADE
);

INSERT INTO return_items_new (id, return_id, sale_item_id, quantity, refund_amount)
SELECT id, return_id, sale_item_id, quantity, refund_amount FROM return_items;

DROP TABLE return_items;

ALTER TABLE return_items_new RENAME TO return_items;

PRAGMA foreign_keys=ON;
