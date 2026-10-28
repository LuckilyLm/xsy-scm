-- 对账单可选展示过账记录（D-1028-16）。
--
-- 现场口径：给客户出具的正式对账单可以选择是否展示过账相关数据。过账不是财务事实，
-- 它不参与任何余额计算 —— 所以这里只加「这一份版本要不要带过账行」的标记与行上的过账金额，
-- 不加任何应收/收款/核销变动列，也不改期末净应收。
--
-- 冻结版本必须自己记住 include_posting：打印与详情读的是冻结行，重新生成同一期间时
-- 也不能因为默认值变化而把过账行悄悄塞进旧版本。

ALTER TABLE xsy_customer_statement
    ADD COLUMN include_posting BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE xsy_customer_statement_item
    ADD COLUMN posting_amount NUMERIC(18,4);

COMMENT ON COLUMN xsy_customer_statement.include_posting IS
    '生成时是否包含过账记录；只影响对账单行，不影响任何余额口径';
COMMENT ON COLUMN xsy_customer_statement_item.posting_amount IS
    '过账行的过账金额；其它事实类型为空。它不是应收/收款变动，不参与滚动余额';

-- 默认打印模板补一列「过账金额」，位置在退款变动之后、滚动净应收之前。
-- 没有过账行时该列是空值，不影响其它对账单的可读性。
UPDATE xsy_print_template
SET model = jsonb_set(
        model,
        '{columns}',
        '["eventAt", "factType", "documentNo", "relatedNo", "receivableDelta", "receiptDelta",
          "writeOffDelta", "refundDelta", "postingAmount", "receivableBalance"]'::jsonb,
        TRUE)
WHERE document_type = 'STATEMENT'
    AND template_code = 'STATEMENT_DEFAULT'
    AND default_flag = TRUE
    AND deleted = FALSE;
