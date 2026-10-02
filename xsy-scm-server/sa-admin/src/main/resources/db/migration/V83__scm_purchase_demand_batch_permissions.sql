-- 净需求冻结批次的三个权限码此前只在 Java 常量里存在，从未落库：
-- `PurchasePermission.DEMAND_BATCH_CREATE` / `DEMAND_BATCH_GENERATE` 已被
-- /scm/purchase/demand/batch/{create,generate} 引用，但没有任何 t_menu 行发布它们，
-- 于是这两个端点对**所有**角色返回 403 —— 冻结批次流程实际上是死的。
-- 本迁移补发三个权限，并新增回看权限（batch/detail）。
--
-- 挂在采购需求 702 下（与 711/712/713 同一父节点），权限与前端指令同名（perms_type=1）。
-- 授予采购员 / 采购主管 / 超管：与 711/712/713 的现有授权面一致，不额外扩大范围。

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (714, '净需求批次创建', 3, 702, 714, NULL, NULL, 1,
        'scm:purchase:demand:batch:create', 'scm:purchase:demand:batch:create', NULL, NULL, TRUE, 1),
       (715, '净需求批次生成', 3, 702, 715, NULL, NULL, 1,
        'scm:purchase:demand:batch:generate', 'scm:purchase:demand:batch:generate', NULL, NULL, TRUE, 1),
       (716, '净需求批次回看', 3, 702, 716, NULL, NULL, 1,
        'scm:purchase:demand:batch:query', 'scm:purchase:demand:batch:query', NULL, NULL, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id FROM t_role r
CROSS JOIN LATERAL unnest(ARRAY[714::bigint,715::bigint,716::bigint]) AS m(menu_id)
WHERE r.role_code IN ('SCM_PURCHASER','SCM_PURCHASER_LEAD','SUPER_ADMIN')
  AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu','menu_id'), (SELECT MAX(menu_id)+1 FROM t_menu), FALSE);
