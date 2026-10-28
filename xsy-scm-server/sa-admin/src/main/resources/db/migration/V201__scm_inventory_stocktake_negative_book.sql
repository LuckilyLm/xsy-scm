-- 负库存出库（仓库级开关）上线后，库存余额可以是负数；
-- 盘点单的账面量是「签发快照那一刻的余额」，必须能原样落库，
-- 否则负库存仓连盘点单都建不出来（导入接口 10001，撞 ck_inventory_stocktake_item_book）。
-- 实盘量仍然要求非负：清点出来的实物数量不会是负的，那条约束保持不动。
ALTER TABLE xsy_inventory_stocktake_item DROP CONSTRAINT ck_inventory_stocktake_item_book;

COMMENT ON COLUMN xsy_inventory_stocktake_item.book_quantity IS
    '账面量快照：签发那一刻该 (仓库, SKU) 的余额数量；负库存仓允许为负，实盘量仍必须非负。';
