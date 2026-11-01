-- 客户不再区分个人 / 企业 / 集团，按客户直接定价；客户类型与其上层客户类型价整体下线。
-- 历史成交单保留 price source = CUSTOMER_TYPE 的字符串，仅用于回显，不再产生新数据。
-- 价格变更日志是 append-only（触发器禁止 DELETE），CUSTOMER_TYPE 日志原样保留，
-- 由 PriceHistoryDao 的查询过滤掉，不再出现在价格历史页。

DROP TABLE IF EXISTS xsy_customer_price_batch_audit;
DROP TABLE IF EXISTS xsy_customer_type_price;
DROP INDEX IF EXISTS idx_customer_type_id;
ALTER TABLE xsy_customer DROP COLUMN IF EXISTS customer_type_id;
DROP TABLE IF EXISTS xsy_customer_type;

-- 撤回客户类型、客户类型价与类型价批量调价的菜单授权和入口。
DELETE FROM t_role_menu WHERE menu_id IN (433, 451, 452, 453, 454, 503, 504, 521, 522, 523, 524, 525);
UPDATE t_menu
SET disabled_flag = TRUE,
    deleted_flag = TRUE,
    visible_flag = FALSE,
    update_user_id = 1,
    update_time = CURRENT_TIMESTAMP
WHERE menu_id IN (433, 451, 452, 453, 454, 503, 504, 521, 522, 523, 524, 525);
