-- ============================================================================
-- P1-C 分拣角色与授权矩阵增量（data-only）
--
-- 裁决依据：docs/decisions.md「P1 分拣管理裁决」第 7 条与补充第 15 条。
--   * 新增正式非管理员角色 SCM_SORTER（分拣员）。角色本身不带 administrator_flag，
--     使用它的员工必须 administrator_flag=false，否则权限验收不构成证据（P0 裁决第 5 条）。
--   * 队列管理类权限（建单 / 指派 / 取消 / 重开 / 汇总）授给既有 SCM_STOREKEEPER_LEAD，
--     不新建「分拣主管」角色：仓库主管已经是仓内队列的负责人。
--   * 分拣员只拿到「看到派给自己的任务 + 干活 + 出单」，拿不到建单与指派，因此不需要
--     订单域与候选行入口。
--   * 两者都补 751（仓库只读）：任务页要展示与筛选仓库名，缺它会在新页面加载时 30005。
--
-- 与 V56 一致：按 role_code 种，不硬编码 role_id（长驻开发库里 E2E 会临时插入真实 t_role 行）。
-- ============================================================================
INSERT INTO t_role (role_name, role_code, remark, update_time, create_time)
VALUES ('分拣员', 'SCM_SORTER',
        '只能处理派给自己的分拣任务，且任务所在仓必须在自己的授权仓内；不能建单、指派、取消或重开',
        '2026-09-24 00:00:00', '2026-09-24 00:00:00')
ON CONFLICT (role_code) DO NOTHING;

WITH grants(role_code, menu_ids) AS (
    VALUES ('SCM_SORTER', ARRAY[1400, 1401, 1411, 1414, 1415, 1418, 751]),
           ('SCM_STOREKEEPER_LEAD',
            ARRAY[1400, 1401, 1402, 1411, 1412, 1413, 1414, 1415, 1416, 1417, 1418, 1421])
)
INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM grants g
         JOIN t_role r ON r.role_code = g.role_code
         JOIN t_menu m ON m.menu_id = ANY (g.menu_ids)
WHERE NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

UPDATE t_role
SET remark = '仓管全部权限 + 成本字段可见 + 仓库授权行维护 + 全部仓库范围 + 分拣队列管理（建单 / 指派 / 取消 / 重开 / 汇总）',
    update_time = '2026-09-24 00:00:00'
WHERE role_code = 'SCM_STOREKEEPER_LEAD';
