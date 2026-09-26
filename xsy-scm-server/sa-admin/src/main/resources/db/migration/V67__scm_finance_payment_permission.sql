-- ============================================================================
-- V67 SCM Finance R1 F1-3B：付款登记能力（data-only）
--
-- 背景：POST /scm/finance/payment/add 是财务域第二条用户命令（F1-3B），
-- 因此按「哪个阶段第一次出现受保护 API，就由那个阶段的迁移种」发布 scm:finance:payment:add。
-- 父目录 1500 已在 V66 建好（隐藏目录、无 component），本迁移不再新增目录行。
--
-- 形态照 V28（900/901）与 V46（1100/1101）：能力点 menu_type=3、component 为 NULL，
-- 因此不会出现「已授权的页面菜单指向不存在的 .vue」这种点开空白页的形态
-- （该风险由 SmartAdminMenuComponentPgIT 全仓门禁强制）。
--
-- 号段重扫结论（本迁移编写时实测）：t_menu 财务段已被占用的是 1500 与 1521（V66），
-- 本地 db/migration 最大版本 V66、origin/main 最大 V64 ⇒ 1522 与 V67 均空闲。
--
-- 只种 F1-3B 真实用到的这一条：
--   * 不种 scm:finance:payment:query（1514）—— 本阶段没有任何读取端点，
--     「已授权但无端点使用它」与「菜单指向不存在的页面」是同一类错误；
--   * 不种 scm:finance:payment:reverse（F1-3C）与 write-off / payable:red / export（F1-4 / F1-5）；
--   * 不种任何页面菜单（1501–1505 随 F1-6 的 .vue 落库）；
--   * 不新增任何 *:scope:all:query（第三批 D-5：财务的全范围来自 V56 授的 1302 / 1311 / 1322
--     与 V57 补的 1331，不靠角色名绕过范围判定）。
--
-- 授权按 role_code 种给正式岗位 SCM_FINANCE（V56 起同一口径，不硬编码 role_id），另给超管兜底；
-- 两条 t_role_menu 都带 NOT EXISTS，可重入。
-- ============================================================================

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1522, '付款登记', 3, 1500, 1522, NULL, NULL, 1,
        'scm:finance:payment:add', 'scm:finance:payment:add', NULL, 1500, true, 1)
ON CONFLICT (menu_id) DO NOTHING;

-- 超管兜底。
INSERT INTO t_role_menu(role_id, menu_id)
SELECT 1, m.menu_id
FROM t_menu m
WHERE m.menu_id = 1522
  AND NOT EXISTS(SELECT 1 FROM t_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.menu_id);

-- 正式岗位：财务岗可登记付款。岗位不存在（未跑过 V56 的环境）时自然 0 行，不报错。
INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, 1522
FROM t_role r
WHERE r.role_code = 'SCM_FINANCE'
  AND NOT EXISTS(SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = 1522);

-- 显式主键插入不会推进 menu_id 序列，必须补到 MAX+1，否则后续在管理界面新建菜单会撞主键。
SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
