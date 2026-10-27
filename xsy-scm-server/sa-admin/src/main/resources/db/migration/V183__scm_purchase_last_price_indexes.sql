-- 录单最近采购价与采购成本公式的读取路径索引。
CREATE INDEX IF NOT EXISTS idx_purchase_order_item_sku_live
    ON xsy_purchase_order_item (sku_id, purchase_order_id, purchase_price)
    WHERE deleted = FALSE AND purchase_price IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_purchase_order_committed_submitted
    ON xsy_purchase_order (status, submitted_at DESC, id DESC)
    WHERE deleted = FALSE;

-- 新增协议价公式：最近采购价 + 商品损耗率 + 商品税率 + 加价率。
-- 数据库约束必须与 AgreementFormulaType 同步，否则新公式无法落库。
ALTER TABLE xsy_customer_agreement_price DROP CONSTRAINT IF EXISTS ck_agreement_price_formula;
ALTER TABLE xsy_customer_agreement_price ADD CONSTRAINT ck_agreement_price_formula
    CHECK (
        (formula_type = 'FIXED' AND unit_price IS NOT NULL AND formula_parameter IS NULL)
        OR (formula_type IN ('MARKET_PLUS_AMOUNT', 'COST_MARKUP_RATE', 'MARKET_MARKUP_RATE',
            'PURCHASE_COST_LOSS_TAX_MARKUP')
            AND unit_price IS NULL AND formula_parameter IS NOT NULL AND formula_parameter >= 0)
    );
