-- V26: 销售订单「预留库存」权限，2026-09-19。
--
-- 背景：库存预留能力（V25 的 inventory_reservation）已就绪，但触发点不能是「订单确认」——
-- 本业务的链路是「先接单 → 聚合 → 采购 → 收货 → 才到货」，订单确认时库存尚未产生；
-- 把预留挂在确认上会让整条链路无法运转（实测 82 个 IT 报 41011，见 docs/decisions.md）。
--
-- 因此本波次把预留做成**订单侧的显式操作**：货到之后，由业务人员对已确认的订单执行
-- 「预留库存」，占用可用量。业务依据仍是销售订单（预留的来源单据就是订单行），
-- 只是把「什么时候占」交给业务判断，而不是由状态机硬编码。
--
-- Data-only：1 条 t_menu（620，parent 602 订单列表）+ role_id = 1 授权 + 序列推进。
-- 620 已核对空闲（订单权限段 611-619 已用、621-625 已用）。
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type, api_perms, web_perms,
                   icon, context_menu_id, visible_flag, create_user_id)
VALUES (620, '预留库存', 3, 602, 620, NULL, NULL, 1, 'scm:order:reserve-stock', 'scm:order:reserve-stock', NULL, NULL,
        true, 1)
ON CONFLICT(menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id IN (620)
  AND NOT EXISTS (SELECT 1 FROM t_role_menu r WHERE r.role_id = 1 AND r.menu_id = m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
