-- 包装码标签补溯源二维码地址。
--
-- V154 的默认模板没有 traceUrl，而前端 qrBlock 只从 headerFields 取链接，
-- 因此即使配置了 scm.trace.public-base-url，标签上也不会出现二维码。
-- 对所有仍启用的 TRACE_UNIT 模板追加；已停用的不动，重新启用时再补。
-- jsonb 数组追加 + 存在性判断：重复执行不产生重复字段。

UPDATE xsy_print_template
SET model = jsonb_set(model, '{headerFields}',
                      COALESCE(model -> 'headerFields', '[]'::jsonb) || '["traceUrl"]'::jsonb, true),
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'system'
WHERE document_type = 'TRACE_UNIT'
  AND enabled_flag = TRUE
  AND deleted = FALSE
  AND NOT COALESCE(model -> 'headerFields', '[]'::jsonb) @> '["traceUrl"]'::jsonb;
