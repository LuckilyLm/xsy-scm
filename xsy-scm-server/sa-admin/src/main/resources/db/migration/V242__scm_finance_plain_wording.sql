-- 只更新菜单名称和帮助文案，不改变路由、权限、财务记录或已保存的打印内容。
UPDATE t_menu m SET menu_name = wording.new_name
FROM (VALUES
    (1501, '应收管理', '客户应收款'),
    (1502, '应付管理', '供应商应付款'),
    (1505, '核销管理', '账款抵扣'),
    (1506, '过账单据', '外部账单记录'),
    (1515, '核销查询', '账款抵扣查询'),
    (1523, '登记核销', '登记抵扣'),
    (1524, '反向核销', '撤销抵扣'),
    (1525, '登记红字应付', '登记应付扣减'),
    (1526, '反向收款', '撤销收款记录'),
    (1527, '反向付款', '撤销付款记录'),
    (1529, '反向抹零', '撤销抹零'),
    (1532, '过账查询', '外部账单查询'),
    (1533, '登记过账', '登记外部账单'),
    (1534, '修改过账', '修改外部账单'),
    (1535, '作废过账', '作废外部账单'),
    (1219, '应收应付账龄', '账款到期情况'),
    (1220, '账龄查询', '账款到期查询')
) AS wording(menu_id, old_name, new_name)
WHERE m.menu_id = wording.menu_id AND m.menu_name = wording.old_name AND m.deleted_flag = FALSE;

-- 对系统财务帮助文档逐词替换，保留管理员补充的其它内容，不覆盖整篇正文。
DO $$
DECLARE
    wording RECORD;
BEGIN
    FOR wording IN SELECT * FROM (VALUES
            ('反向核销', '撤销抵扣'),
            ('核销', '账款抵扣'),
            ('红字应收', '应收扣减'),
            ('红字应付', '应付扣减'),
            ('红字', '扣减记录'),
            ('账龄', '账款到期情况'),
            ('过账', '外部账单'),
            ('净额', '扣减后金额'),
            ('超额账款抵扣', '抵扣超额'),
            ('账款抵扣把收款匹配到应收', '账款抵扣是把已收款用于结清客户账单'),
            ('把收款匹配到应收', '把已收款用于结清客户账单'),
            ('付款后同样账款抵扣', '登记付款后，将这笔付款用于抵扣供应商账单'),
            ('账款抵扣错了', '抵扣错了'),
            ('收款与账款抵扣', '收款与账单结算')
        ) AS phrases(old_text, new_text)
    LOOP
        UPDATE t_help_doc
        SET title = replace(title, wording.old_text, wording.new_text),
            content_text = replace(content_text, wording.old_text, wording.new_text),
            content_html = replace(content_html, wording.old_text, wording.new_text)
        WHERE help_doc_id = 15 AND author = 'system';
    END LOOP;
END $$;
