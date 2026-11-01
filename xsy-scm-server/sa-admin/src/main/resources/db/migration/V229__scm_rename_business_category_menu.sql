-- 客户/供应商业务分类入口统一挂在「客户管理」下，菜单名称改为「客户分类」，
-- 与客户档案、客户类型的叫法保持一致；页面内的供应商分类页签不受影响。
UPDATE t_menu
SET menu_name = '客户分类',
    update_time = NOW()
WHERE menu_id = 47604
  AND menu_name = '客户供应商分类';

UPDATE t_menu
SET menu_name = REPLACE(menu_name, '客户供应商分类', '客户分类'),
    update_time = NOW()
WHERE menu_id BETWEEN 47600 AND 47603
  AND menu_name LIKE '客户供应商分类%';
