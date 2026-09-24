-- ============================================================================
-- P0-H 正式角色矩阵裁决增量（data-only）
--
-- 裁决依据：docs/decisions.md「P0 基线收口裁决（第二批）」第 2 条。V56 已被真实库应用、
-- 不可修改，因此矩阵与裁决之间的差值在这里补。
--
--   1. 销售主管补退货批准(623)/驳回(624)与退款完成(632)：这是业务审批入口，
--      不代表资金操作 —— 实际退款付款仍属后续 Finance 权限，本次一并不给。
--      普通销售员不自动获得任何审批权（其授权集不变）。
--   2. 财务的仓库维度按「总部统一财务」改为全部仓库，并且只用显式范围权限点(1331)表达：
--      代码里不允许出现「是财务就绕过范围」的分支，将来出现区域财务时改授权即可，不动业务代码。
--   3. 采购员的库存数量权 V56 已给（811 = scm:inventory:balance:query），而成本列由独立的
--      scm:report:cost:query(1215) 控制、采购员不持有 → 均价与账面金额在读取时被抹成 null。
--      「库存数量权限 ≠ 库存成本权限」这条已经成立，故此处无需变更，只在注释里钉住口径。
--
-- 与 V56 同一纪律：按 role_code 关联，不硬编码 role_id（长驻开发库里 E2E 夹具会临时插入真实角色行）。
-- ============================================================================

WITH grants(role_code, menu_ids) AS (VALUES
    ('SCM_SALES_LEAD', ARRAY[623, 624, 632]),
    ('SCM_FINANCE', ARRAY[1331])
)
INSERT INTO t_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM grants g
         JOIN t_role r ON r.role_code = g.role_code
         CROSS JOIN LATERAL unnest(g.menu_ids) AS m(menu_id)
WHERE NOT EXISTS(SELECT 1 FROM t_role_menu x WHERE x.role_id = r.role_id AND x.menu_id = m.menu_id);

-- 角色备注会显示在角色管理页上，必须与实际授权一致：V56 里「仓库范围仍来自授权行」的说法
-- 对本条之后的财务已经失真。
UPDATE t_role
SET remark      = '报表与查询按授权范围生效：跨业务员/采购员范围与全部仓库范围都是显式授权，' ||
                  '导出与页面同一套范围；成本列由 scm:report:cost:query 单独控制',
    update_time = '2026-09-24 00:00:00'
WHERE role_code = 'SCM_FINANCE';

UPDATE t_role
SET remark      = '销售全部权限 + 客户归属分配权 + 全部客户/订单范围（含未分配）' ||
                  '+ 退货/退款业务审批（不含资金操作）',
    update_time = '2026-09-24 00:00:00'
WHERE role_code = 'SCM_SALES_LEAD';
