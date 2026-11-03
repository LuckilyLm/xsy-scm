-- 帮助中心正文改成岗位能直接看懂的说法：冻结/快照/回滚/归属/回填/服务端等术语迁移为业务用语。
--
-- V165 已按 help_doc_id 写入种子正文，这里不整篇覆盖：只替换术语，保留管理员在后台改过的其余内容。
-- 只处理 system 种子文档，避免动到用户自建的帮助文档。逐条替换，便于按顺序处理互相包含的词。

UPDATE t_help_doc
SET content_text = replace(content_text, '归属业务员', '负责业务员'),
    content_html = replace(content_html, '归属业务员', '负责业务员')
WHERE author = 'system';

UPDATE t_help_doc
SET content_text = replace(content_text, '不改归属', '不改负责业务员'),
    content_html = replace(content_html, '不改归属', '不改负责业务员')
WHERE author = 'system';

UPDATE t_help_doc
SET content_text = replace(content_text, '归属与全量', '本人与全部'),
    content_html = replace(content_html, '归属与全量', '本人与全部')
WHERE author = 'system';

UPDATE t_help_doc
SET content_text = replace(content_text, '冻结', '生成'),
    content_html = replace(content_html, '冻结', '生成')
WHERE author = 'system';

UPDATE t_help_doc
SET content_text = replace(content_text, '快照', '内容'),
    content_html = replace(content_html, '快照', '内容')
WHERE author = 'system';

UPDATE t_help_doc
SET content_text = replace(content_text, '回滚', '恢复'),
    content_html = replace(content_html, '回滚', '恢复')
WHERE author = 'system';

UPDATE t_help_doc
SET content_text = replace(content_text, '回填', '录入'),
    content_html = replace(content_html, '回填', '录入')
WHERE author = 'system';

UPDATE t_help_doc
SET content_text = replace(content_text, '服务端', '系统'),
    content_html = replace(content_html, '服务端', '系统')
WHERE author = 'system';

UPDATE t_help_doc
SET content_text = replace(content_text, '菜单与接口权限一一对应', '菜单与操作权限一一对应'),
    content_html = replace(content_html, '菜单与接口权限一一对应', '菜单与操作权限一一对应')
WHERE author = 'system';

UPDATE t_help_doc
SET content_text = replace(content_text, '接口会直接拒绝', '系统会直接拒绝'),
    content_html = replace(content_html, '接口会直接拒绝', '系统会直接拒绝')
WHERE author = 'system';

UPDATE t_help_doc
SET content_text = replace(content_text, '事实', '记录'),
    content_html = replace(content_html, '事实', '记录')
WHERE author = 'system';

UPDATE t_help_doc
SET content_text = replace(content_text, '净额口径', '净额'),
    content_html = replace(content_html, '净额口径', '净额')
WHERE author = 'system';

UPDATE t_help_doc
SET content_text = replace(content_text, '重报价', '批量核价'),
    content_html = replace(content_html, '重报价', '批量核价')
WHERE author = 'system';

UPDATE t_help_doc
SET content_text = replace(content_text, '登记实际数量', '录入实际数量'),
    content_html = replace(content_html, '登记实际数量', '录入实际数量')
WHERE author = 'system';
