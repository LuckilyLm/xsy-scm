ALTER TABLE xsy_finance_customer_expense ADD COLUMN goods_cost_amount NUMERIC(18,4)
    CHECK (goods_cost_amount >= 0);
ALTER TABLE xsy_finance_customer_expense DROP CONSTRAINT xsy_finance_customer_expense_check;
ALTER TABLE xsy_finance_customer_expense ADD CONSTRAINT ck_customer_expense_amount CHECK (
    goods_cost_amount IS NOT NULL OR tax_amount + service_amount + freight_amount + commission_amount > 0
);
COMMENT ON COLUMN xsy_finance_customer_expense.goods_cost_amount IS
    '按客户月份登记的实际商品成本，含赠品及分摊价差；NULL表示未登记，0表示已确认零成本；多笔有效记录相加';
UPDATE t_menu SET menu_name = CASE menu_id
    WHEN 1536 THEN '客户成本费用查询' WHEN 1537 THEN '登记客户成本费用' WHEN 1538 THEN '作废客户成本费用' END
WHERE menu_id IN (1536, 1537, 1538);
