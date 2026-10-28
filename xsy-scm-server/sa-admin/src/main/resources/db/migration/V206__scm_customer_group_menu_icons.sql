-- 侧边栏菜单缺图标时，这一行的文字会比同级菜单左移一格，展开目录时行首对不齐。
-- V198 新增的客户分组与客户分组价两个二级菜单没有带 icon，这里补上同级的 antd 图标。
UPDATE t_menu SET icon = 'TeamOutlined' WHERE menu_id = 47615 AND (icon IS NULL OR btrim(icon) = '');

UPDATE t_menu SET icon = 'TagsOutlined' WHERE menu_id = 47620 AND (icon IS NULL OR btrim(icon) = '');
