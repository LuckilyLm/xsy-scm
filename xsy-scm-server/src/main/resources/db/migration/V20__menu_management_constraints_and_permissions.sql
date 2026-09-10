ALTER TABLE sys_menu DROP CONSTRAINT ck_sys_menu_type;
ALTER TABLE sys_menu DROP CONSTRAINT ck_sys_menu_page_fields;
UPDATE sys_menu
SET type='MENU'
WHERE type = 'PAGE';
ALTER TABLE sys_menu
    ADD CONSTRAINT ck_sys_menu_type CHECK (type IN ('DIRECTORY', 'MENU'));
ALTER TABLE sys_menu
    ADD CONSTRAINT ck_sys_menu_page_fields CHECK (
        (type = 'MENU' AND path IS NOT NULL AND route_key IS NOT NULL) OR type = 'DIRECTORY'
        );

INSERT INTO sys_permission(code, name, module, resource_type, system_permission, sort_order)
VALUES ('system:menu:list', '菜单查询', 'system', 'API', TRUE, 240),
       ('system:menu:create', '菜单新增', 'system', 'API', TRUE, 241),
       ('system:menu:update', '菜单编辑', 'system', 'API', TRUE, 242),
       ('system:menu:status', '菜单启停', 'system', 'API', TRUE, 243),
       ('system:menu:delete', '菜单删除', 'system', 'API', TRUE, 244);
