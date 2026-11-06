-- 只补齐缺失权限，主键使用现有identity；不覆盖自建菜单和原授权。
LOCK TABLE xsy.t_menu, xsy.t_role_menu IN SHARE ROW EXCLUSIVE MODE;

-- 导入或历史手工编号可能使序列落后于现有数据，只前移到可用位置，不能回退。
SELECT setval('xsy.t_menu_menu_id_seq',
    GREATEST(sequence.last_value, COALESCE((SELECT max(menu_id) FROM xsy.t_menu), 1)),
    sequence.is_called OR COALESCE((SELECT max(menu_id) FROM xsy.t_menu), 0) >= sequence.last_value)
FROM xsy.t_menu_menu_id_seq sequence;
SELECT setval('xsy.t_role_menu_role_menu_id_seq',
    GREATEST(sequence.last_value, COALESCE((SELECT max(role_menu_id) FROM xsy.t_role_menu), 1)),
    sequence.is_called OR COALESCE((SELECT max(role_menu_id) FROM xsy.t_role_menu), 0) >= sequence.last_value)
FROM xsy.t_role_menu_role_menu_id_seq sequence;

WITH permissions(menu_name, api_perms, parent_id, sort) AS (
    VALUES ('首页工作台', 'scm:dashboard:query', 0::bigint, 1),
           ('待办查询', 'scm:todo:query', 0::bigint, 2),
           ('提交', 'scm:purchase:submit', 703::bigint, 724)
)
INSERT INTO xsy.t_menu (menu_name, menu_type, parent_id, sort, perms_type,
                           api_perms, web_perms, visible_flag, create_user_id)
SELECT permission.menu_name, 3, permission.parent_id, permission.sort, 1,
       permission.api_perms, permission.api_perms, FALSE, 1
FROM permissions permission
WHERE NOT EXISTS (
    SELECT 1 FROM xsy.t_menu existing
    WHERE existing.api_perms = permission.api_perms AND NOT existing.deleted_flag
);

-- 首页与待办的业务内容仍由各领域权限和数据范围过滤，所有现有有效角色可进入首页。
INSERT INTO xsy.t_role_menu (role_id, menu_id)
SELECT role.role_id, menu.menu_id
FROM xsy.t_role role
CROSS JOIN xsy.t_menu menu
WHERE NOT menu.deleted_flag
  AND menu.api_perms IN ('scm:dashboard:query', 'scm:todo:query')
  AND NOT EXISTS (
      SELECT 1 FROM xsy.t_role_menu existing
      WHERE existing.role_id = role.role_id AND existing.menu_id = menu.menu_id
  );

-- 提交权只补给已有采购新增权的有效角色，不给只读或财务角色增加采购操作权。
INSERT INTO xsy.t_role_menu (role_id, menu_id)
SELECT DISTINCT source.role_id, submit.menu_id
FROM xsy.t_role_menu source
JOIN xsy.t_role role ON role.role_id = source.role_id
JOIN xsy.t_menu permission ON permission.menu_id = source.menu_id
    AND permission.api_perms = 'scm:purchase:add'
    AND NOT permission.deleted_flag AND NOT permission.disabled_flag
CROSS JOIN xsy.t_menu submit
WHERE submit.api_perms = 'scm:purchase:submit' AND NOT submit.deleted_flag
  AND NOT EXISTS (
      SELECT 1 FROM xsy.t_role_menu existing
      WHERE existing.role_id = source.role_id AND existing.menu_id = submit.menu_id
  );
