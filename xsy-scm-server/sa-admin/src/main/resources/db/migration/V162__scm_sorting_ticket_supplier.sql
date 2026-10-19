-- 分拣小票补「供应商来源」。
--
-- 货由供应商代分拣时，小票要能直接交给对方：谁家的货、哪个仓库、谁负责，一张纸说清，
-- 不必再靠口头交代。字段在 ScmPrintDocumentTypeEnum.SORTING_TICKET 里新增，
-- 这里把默认模板的抬头字段同步补上，否则新字段不会出现在已发布的小票上。
--
-- 用 jsonb 数组追加并加存在性判断：重复执行不会产生重复字段。

UPDATE xsy_print_template
SET model = jsonb_set(model, '{headerFields}',
                      (model -> 'headerFields') || '["supplierName"]'::jsonb, true),
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'system'
WHERE document_type = 'SORTING_TICKET'
  AND default_flag = TRUE
  AND NOT (model -> 'headerFields') @> '["supplierName"]'::jsonb;
