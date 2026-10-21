-- 企业与公告迁入正式平台菜单，保留页面 ID、路径和既有角色授权。
UPDATE xsy_v2.t_menu AS enterprise
SET parent_id = 45,
    sort = COALESCE((
        SELECT MIN(sibling.sort)
        FROM xsy_v2.t_menu AS sibling
        WHERE sibling.parent_id = 45
          AND sibling.menu_type IN (1, 2)
          AND sibling.menu_id <> enterprise.menu_id
          AND sibling.deleted_flag = FALSE
    ), 1) - 1,
    update_time = now()
WHERE enterprise.menu_id = 144;

UPDATE xsy_v2.t_menu
SET parent_id = 144,
    update_time = now()
WHERE menu_id = 145;

-- 排序字段为空的旧子菜单先落到明确位置，避免 NULLS LAST 把公告排到它们前面。
UPDATE xsy_v2.t_menu
SET sort = 0,
    update_time = now()
WHERE parent_id = 50
  AND menu_type IN (1, 2)
  AND menu_id <> 132
  AND deleted_flag = FALSE
  AND sort IS NULL;

UPDATE xsy_v2.t_menu AS notice
SET parent_id = 50,
    sort = COALESCE((
        SELECT MAX(sibling.sort)
        FROM xsy_v2.t_menu AS sibling
        WHERE sibling.parent_id = 50
          AND sibling.menu_type IN (1, 2)
          AND sibling.menu_id <> notice.menu_id
          AND sibling.deleted_flag = FALSE
    ), 0) + 1,
    update_time = now()
WHERE notice.menu_id = 132;

-- 迁移原菜单授权。角色只拥有业务页时，也要能看到新的父目录。
INSERT INTO xsy_v2.t_role_menu (role_id, menu_id, update_time, create_time)
SELECT DISTINCT granted.role_id, destination.menu_id, now(), now()
FROM xsy_v2.t_role_menu AS granted
JOIN (VALUES (144::bigint, 45::bigint), (132::bigint, 50::bigint))
    AS destination(source_menu_id, menu_id)
    ON granted.menu_id = destination.source_menu_id
WHERE NOT EXISTS (
    SELECT 1
    FROM xsy_v2.t_role_menu AS existing
    WHERE existing.role_id = granted.role_id
      AND existing.menu_id = destination.menu_id
);

-- 两个退役入口及其剩余子节点一并移除；公告和企业子树已迁出。
WITH RECURSIVE retired AS (
    SELECT menu_id FROM xsy_v2.t_menu WHERE menu_id IN (85, 138, 151)
    UNION
    SELECT child.menu_id
    FROM xsy_v2.t_menu AS child
    JOIN retired AS parent
      ON child.parent_id = parent.menu_id OR child.context_menu_id = parent.menu_id
)
DELETE FROM xsy_v2.t_role_menu
WHERE menu_id IN (SELECT menu_id FROM retired);

WITH RECURSIVE retired AS (
    SELECT menu_id FROM xsy_v2.t_menu WHERE menu_id IN (85, 138, 151)
    UNION
    SELECT child.menu_id
    FROM xsy_v2.t_menu AS child
    JOIN retired AS parent
      ON child.parent_id = parent.menu_id OR child.context_menu_id = parent.menu_id
)
DELETE FROM xsy_v2.t_menu
WHERE menu_id IN (SELECT menu_id FROM retired);

-- 保留明确设置的图标，给仍显示但缺图标的菜单一个可识别的默认值。
UPDATE xsy_v2.t_menu
SET icon = CASE WHEN menu_type = 1 THEN 'AppstoreOutlined' ELSE 'FileOutlined' END,
    update_time = now()
WHERE menu_type IN (1, 2)
  AND visible_flag = TRUE
  AND deleted_flag = FALSE
  AND (icon IS NULL OR btrim(icon) = '');

DROP TABLE IF EXISTS xsy_v2.t_code_generator_config;
