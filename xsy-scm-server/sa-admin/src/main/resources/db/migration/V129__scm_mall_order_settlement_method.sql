-- V129：商城结算方式（余额 / 账期 / 货到付款）
--
-- 只在销售订单上记录「客户在商城选定的结算方式」，本身不产生任何资金事实：
--   WALLET 余额支付、CREDIT 账期结算、COD 货到付款；为空表示未选择（后台订单不受影响）。
--
-- 为什么先只落选择、不动资金：
--   余额扣减、账期占用、货到付款收款分别属于钱包 / 授信 / 收款三套既有事实链，
--   各自的时点与幂等由对应领域负责（见 docs/plan/active/balance-payment-order-settlement-design.md）。
--   订单先保存「约定」，各领域在确认、收款、签收等既有节点按约定驱动，避免在结算入口复制第二套资金逻辑。

ALTER TABLE xsy_sales_order
    ADD COLUMN IF NOT EXISTS settlement_method VARCHAR(32);

-- 只允许三种已启用的结算方式；在线支付在真实渠道接入前不开放，因此不在取值内。
ALTER TABLE xsy_sales_order
    ADD CONSTRAINT ck_sales_order_settlement_method
        CHECK (settlement_method IS NULL OR settlement_method IN ('WALLET', 'CREDIT', 'COD'));
