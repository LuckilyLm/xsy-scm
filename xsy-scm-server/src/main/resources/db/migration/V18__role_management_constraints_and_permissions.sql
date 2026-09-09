-- Preserve stored codes and historical rows; enforce normalized uniqueness for live roles.
CREATE UNIQUE INDEX uk_sys_role_normalized_code_active
    ON sys_role (LOWER(BTRIM(code))) WHERE deleted = FALSE;

INSERT INTO sys_permission(code, name, module, resource_type, system_permission, sort_order)
VALUES ('system:role:list', '角色查询', 'system', 'API', TRUE, 220),
       ('system:role:create', '角色新增', 'system', 'API', TRUE, 221),
       ('system:role:update', '角色编辑', 'system', 'API', TRUE, 222),
       ('system:role:status', '角色启停', 'system', 'API', TRUE, 223),
       ('system:role:delete', '角色删除', 'system', 'API', TRUE, 224);
