-- ADM-12 3-5d：分拣小票的默认模板补「来源」列。
--
-- 背景：满赠赠品随订单一起拣（分拣清单是「订单行 + 赠品权益」的只读合并模型），
-- 但赠品**不挂订单行**。小票必须自己说清哪一行是赠品，否则仓库会拿订单量去核。
--
-- 只追加、不重写：`||` 把 sourceType 追加到既有 columns 末尾，且只在还没有这一列时执行，
-- 因此不会覆盖使用方对默认模板做过的其它调整。
-- 注意页脚说明也在 model 里（本表没有独立的 footer_note 列）。
--
-- 字段白名单已在 ScmPrintDocumentTypeEnum.SORTING_TICKET 里登记（新增字段必须同时登记，
-- 否则模板校验会拒收）。

UPDATE scm_print_template
SET model      = jsonb_set(model, '{columns}', (model -> 'columns') || '["sourceType"]'::JSONB),
    version    = version + 1,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'system'
WHERE document_type = 'SORTING_TICKET'
  AND NOT (model -> 'columns') @> '["sourceType"]'::JSONB;

-- 页脚补一句赠品说明：空白实分量对赠品是「无需录入」，不是「尚未录入」。
UPDATE scm_print_template
SET model      = jsonb_set(model, '{footerNote}',
                           '"空白实分量表示尚未录入（赠品行无需录入）；分拣不回写订单、不改库存。"'::JSONB),
    version    = version + 1,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'system'
WHERE document_type = 'SORTING_TICKET'
  AND model ->> 'footerNote' = '空白实分量表示尚未录入；分拣不回写订单、不改库存。';
