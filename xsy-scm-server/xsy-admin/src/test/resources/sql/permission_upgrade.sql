-- 只允许在独立测试库执行，覆盖自建菜单、原编号占用、高序列和重复执行。
DO $$ BEGIN
    IF current_database() NOT LIKE '%_it' THEN RAISE EXCEPTION 'independent test database required'; END IF;
END $$;
BEGIN;
INSERT INTO xsy.t_role(role_id,role_name,role_code)
VALUES (1,'管理员','test_admin'),(12,'采购员','test_buyer'),(13,'只读人员','test_reader');
INSERT INTO xsy.t_menu(menu_id,menu_name,menu_type,parent_id,api_perms,create_user_id)
VALUES (721,'采购新增',3,703,'scm:purchase:add',1),
       (47626,'自建菜单甲',2,0,'custom:one',1),(47627,'自建菜单乙',2,0,'custom:two',1),
       (50000,'自建首页权限名称',3,0,'scm:dashboard:query',1);
INSERT INTO xsy.t_role_menu(role_menu_id,role_id,menu_id) VALUES (1139,12,721),(1160,13,50000),(1161,14,721);
SELECT setval('xsy.t_menu_menu_id_seq',90000,TRUE);
SELECT setval('xsy.t_role_menu_role_menu_id_seq',190000,TRUE);

-- APPLY_PERMISSION_MIGRATION

CREATE TEMP TABLE expected_counts AS SELECT
    (SELECT count(*) FROM xsy.t_menu) AS menus,
    (SELECT count(*) FROM xsy.t_role_menu) AS grants;

-- APPLY_PERMISSION_MIGRATION

DO $$ BEGIN
    IF (SELECT count(*) FROM xsy.t_menu WHERE menu_id IN (47626,47627) AND api_perms LIKE 'custom:%') <> 2
        THEN RAISE EXCEPTION 'custom menus changed'; END IF;
    IF (SELECT menu_name FROM xsy.t_menu WHERE menu_id=50000) <> '自建首页权限名称'
        THEN RAISE EXCEPTION 'existing permission menu changed'; END IF;
    IF (SELECT count(*) FROM xsy.t_role_menu WHERE (role_menu_id=1139 AND role_id=12 AND menu_id=721)
        OR (role_menu_id=1160 AND role_id=13 AND menu_id=50000)) <> 2
        THEN RAISE EXCEPTION 'existing grants changed'; END IF;
    IF NOT EXISTS (SELECT 1 FROM xsy.t_role_menu r JOIN xsy.t_menu m ON m.menu_id=r.menu_id
        WHERE r.role_id=12 AND m.api_perms='scm:purchase:submit')
        THEN RAISE EXCEPTION 'buyer submit permission missing'; END IF;
    IF EXISTS (SELECT 1 FROM xsy.t_role_menu r JOIN xsy.t_menu m ON m.menu_id=r.menu_id
        WHERE r.role_id=13 AND m.api_perms='scm:purchase:submit')
        THEN RAISE EXCEPTION 'reader gained purchase submit permission'; END IF;
    IF (SELECT count(*) FROM xsy.t_role_menu WHERE role_id=14) <> 1
        THEN RAISE EXCEPTION 'missing role received new grants'; END IF;
    IF (SELECT last_value FROM xsy.t_menu_menu_id_seq) < 90000
        OR (SELECT last_value FROM xsy.t_role_menu_role_menu_id_seq) < 190000
        THEN RAISE EXCEPTION 'sequence moved backwards'; END IF;
    IF (SELECT count(*) FROM xsy.t_menu) <> (SELECT menus FROM expected_counts)
        OR (SELECT count(*) FROM xsy.t_role_menu) <> (SELECT grants FROM expected_counts)
        THEN RAISE EXCEPTION 'repeated migration changed counts'; END IF;
    IF EXISTS (SELECT role_id,menu_id FROM xsy.t_role_menu GROUP BY role_id,menu_id HAVING count(*)>1)
        THEN RAISE EXCEPTION 'duplicate role grants'; END IF;
END $$;
ROLLBACK;

-- 历史手工编号已占用序列当前值，且is_called仍为false时也不能撞号。
BEGIN;
INSERT INTO xsy.t_role(role_id,role_name,role_code) VALUES (12,'只读人员','test_stale');
INSERT INTO xsy.t_menu(menu_id,menu_name,menu_type,parent_id,api_perms,create_user_id)
VALUES (90002,'原有菜单',2,0,'custom:stale',1);
INSERT INTO xsy.t_role_menu(role_menu_id,role_id,menu_id) VALUES (1139,12,90002);
SELECT setval('xsy.t_menu_menu_id_seq',90002,FALSE);
SELECT setval('xsy.t_role_menu_role_menu_id_seq',1139,FALSE);

-- APPLY_PERMISSION_MIGRATION

DO $$ BEGIN
    IF (SELECT menu_name FROM xsy.t_menu WHERE menu_id=90002) <> '原有菜单'
        THEN RAISE EXCEPTION 'stale sequence overwrote existing menu'; END IF;
    IF (SELECT last_value FROM xsy.t_menu_menu_id_seq) <= 90002
        OR (SELECT last_value FROM xsy.t_role_menu_role_menu_id_seq) <= 1139
        THEN RAISE EXCEPTION 'stale sequence did not advance'; END IF;
END $$;
ROLLBACK;
