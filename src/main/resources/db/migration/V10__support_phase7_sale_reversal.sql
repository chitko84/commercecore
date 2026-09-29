ALTER TABLE inventory_history DROP CONSTRAINT IF EXISTS ck_inventory_history_type;
ALTER TABLE inventory_history ADD CONSTRAINT ck_inventory_history_type CHECK (
    movement_type IN ('RESTOCK','ADJUSTMENT_INCREASE','ADJUSTMENT_DECREASE',
                      'RESERVATION','RELEASE','SALE','RESERVATION_EXPIRED',
                      'SALE_REVERSAL')
);
