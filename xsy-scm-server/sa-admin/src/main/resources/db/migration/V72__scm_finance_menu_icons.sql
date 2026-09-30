-- Collapsed SmartAdmin navigation hides menu labels, so these real pages and
-- their parent directory need registered Ant Design icon names for narrow views.
UPDATE t_menu AS menu
SET icon = icons.icon_name
FROM (VALUES
    (1500::bigint, 'DollarOutlined'),
    (1501::bigint, 'AccountBookOutlined'),
    (1502::bigint, 'FileTextOutlined'),
    (1503::bigint, 'ImportOutlined'),
    (1504::bigint, 'ExportOutlined'),
    (1505::bigint, 'RetweetOutlined')
) AS icons(menu_id, icon_name)
WHERE menu.menu_id = icons.menu_id
  AND menu.deleted_flag = FALSE;
