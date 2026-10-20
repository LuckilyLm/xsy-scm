-- 存量分拣小票模板补「供应商来源」。
--
-- V162 只给默认模板（default_flag=TRUE）加了 supplierName；此前建的自定义模板
-- 不会自动跟进，代分拣的货交给供应商时票面仍说不清是谁家的货。
-- 这里对所有仍启用的 SORTING_TICKET 模板追加字段；已停用的不动，重新启用时再加不迟。
-- jsonb 数组追加 + 存在性判断：重复执行不产生重复字段。

UPDATE xsy_print_template
SET model = jsonb_set(model, '{headerFields}',
                      (model -> 'headerFields') || '["supplierName"]'::jsonb, true),
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'system'
WHERE document_type = 'SORTING_TICKET'
  AND enabled_flag = TRUE
  AND NOT (model -> 'headerFields') @> '["supplierName"]'::jsonb;
