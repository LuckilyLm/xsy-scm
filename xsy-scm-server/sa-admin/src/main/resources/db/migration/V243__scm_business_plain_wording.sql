-- 仅更新系统菜单和帮助文案，不更改权限、路由、财务或库存记录。
UPDATE t_menu m SET menu_name = wording.new_name
FROM (VALUES
    (714, '净需求批次创建', '创建采购需求汇总'),
    (715, '净需求批次生成', '按汇总生成采购需求'),
    (716, '净需求批次回看', '查看采购需求汇总'),
    (47502, '授信例外确认', '特殊情况确认接单')
) AS wording(menu_id, old_name, new_name)
WHERE m.menu_id = wording.menu_id AND m.menu_name = wording.old_name AND m.deleted_flag = FALSE;

-- 逐词更新系统帮助，保留其它正文，不覆盖管理员补充内容。
DO $$
DECLARE
    wording RECORD;
BEGIN
    FOR wording IN SELECT * FROM (VALUES
        ('授信额度', '赊欠额度'),
        ('校验授信', '核对赊欠额度'),
        ('净需求批次', '采购需求汇总'),
        ('净采购缺口', '还需采购数量'),
        ('解析单价', '报价单价'),
        ('锁定单价', '订单单价')
    ) AS phrases(old_text, new_text)
    LOOP
        UPDATE t_help_doc
        SET content_text = replace(content_text, wording.old_text, wording.new_text),
            content_html = replace(content_html, wording.old_text, wording.new_text)
        WHERE author = 'system' AND help_doc_id IN (8, 10, 11, 13, 14, 15, 17);
    END LOOP;
END $$;
