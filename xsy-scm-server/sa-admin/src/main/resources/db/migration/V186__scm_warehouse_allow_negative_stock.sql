-- 单仓 + 负库存出库开关。
--
-- 现场没有采购入库动作：采购后直接分拣、配送。系统只保留本地“鲜蔬源仓库”，
-- 该仓默认允许销售出库出现负数量；采购/调拨/盘点/损溢仍按原有守恒约束。
--
-- 只在仍是 V15 种子仓且名称未被人工改过时改名/开关，避免覆盖已投入使用的仓库主数据。

ALTER TABLE xsy_warehouse ADD COLUMN allow_negative_stock BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN xsy_warehouse.allow_negative_stock IS '允许销售出库出现负库存；只影响 SALES_OUT，赠品、盘点、损溢、调拨不受影响';

UPDATE xsy_warehouse
SET name = '鲜蔬源仓库',
    allow_negative_stock = TRUE,
    remark = '单仓口径：采购直送、分拣与配送都在此仓完成',
    updated_at = CURRENT_TIMESTAMP
WHERE warehouse_code = 'WH001'
    AND name = '默认仓库'
    AND deleted = FALSE;
