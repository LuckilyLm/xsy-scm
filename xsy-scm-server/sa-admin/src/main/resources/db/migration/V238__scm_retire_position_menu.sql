-- 职务与角色在职员权限上重复：角色决定菜单/接口权限，职务只是标签。
-- 下线「职务管理」入口，保留 t_position 与 t_employee.position_id 供历史数据回查。
UPDATE t_menu
SET visible_flag = FALSE,
    disabled_flag = TRUE,
    deleted_flag = TRUE
WHERE menu_id = 228
  AND path = '/organization/position';

DELETE FROM t_role_menu rm
USING t_menu m
WHERE rm.menu_id = m.menu_id
  AND m.menu_id = 228
  AND m.deleted_flag = TRUE;
