-- ============================================================================
-- P3 Finance R1 角色授权矩阵（data-only）
--
-- 裁决依据 docs/decisions.md「P3 Finance R1 裁决」第二批 Q24 与「第三批正式裁决」D-5；
-- 正式设计见 docs/plan/finance-r1-design.md §16。
--
--   * 本期 Finance R1 的全部权限点只授既有正式角色 SCM_FINANCE（V56 已种，不新建财务角色）。
--     Q24：财务页面本期只面向正式财务权限用户。
--   * 超管兜底已在 V66 里按 role_id = 1 种完，本迁移不重复。
--   * **不给销售 / 采购 / 仓库 / 配送 / 分拣 / 司机任何 Finance 权限**：
--     他们既不需要看财务事实，也不应因为「审批过退货」就顺带拿到资金操作权
--     （V57 已明确销售主管的退款完成是业务审批、不代表资金操作，Q19 同向）。
--   * 按 role_code 种，不硬编码 role_id（V56 起同一口径：长驻开发库里 E2E 会临时插入真实 t_role 行）。
--   * D-5：SCM_FINANCE 的全范围来自**正式权限配置**（1302 / 1311 / 1322 / 1331 已在 V56 / V57 授予），
--     本迁移不新增任何范围放宽点，也不新增 supplier 范围维度。
--     将来开放给部分范围的财务岗位时只调授权，不改业务代码。
--   * D-1 不回填，因此没有任何「历史补生成」权限需要授予。
-- ============================================================================

WITH grants(role_code, menu_ids) AS (
    VALUES ('SCM_FINANCE', ARRAY[
        -- 目录与五个页面
        1500, 1501, 1502, 1503, 1504, 1505,
        -- 五个查询点
        1511, 1512, 1513, 1514, 1515,
        -- 登记类与破坏性类写点（含 D-3 的 1526 / 1527 两个反向权限）
        1521, 1522, 1523, 1524, 1525, 1526, 1527,
        -- 导出（不扩大查询范围）
        1531])
)
INSERT INTO t_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM grants g
         JOIN t_role r ON r.role_code = g.role_code
         JOIN t_menu m ON m.menu_id = ANY (g.menu_ids)
WHERE NOT EXISTS(SELECT 1 FROM t_role_menu x WHERE x.role_id = r.role_id AND x.menu_id = m.menu_id);

-- 角色备注会显示在角色管理页上，必须与实际授权一致（V57 同一条纪律）。
UPDATE t_role
SET remark      = '报表与查询按授权范围生效：跨业务员/采购员范围与全部仓库范围都是显式授权，' ||
                  '导出与页面同一套范围；成本列由 scm:report:cost:query 单独控制；' ||
                  'Finance R1 应收/应付/收款/付款/核销全部权限（含反向核销与收付款反向等破坏性动作），' ||
                  '全范围同样来自显式授权而非角色判断',
    update_time = '2026-09-26 00:00:00'
WHERE role_code = 'SCM_FINANCE';
