-- 订单行备注：客户送货单模板有「备注」列（切段、去皮、分袋等），按行跟着商品走到分拣单和送货单。
ALTER TABLE xsy_sales_order_item
    ADD COLUMN remark VARCHAR(200);

COMMENT ON COLUMN xsy_sales_order_item.remark IS
    '商品行备注；来自录单或送货单导入的备注列，随行打印到客户分拣单/送货单，不与整单备注混用';
