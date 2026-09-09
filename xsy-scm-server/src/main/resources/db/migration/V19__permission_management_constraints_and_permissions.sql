-- Preserve historical grant identities; only live permission codes must be unique.
CREATE UNIQUE INDEX uk_sys_permission_normalized_code_active
    ON sys_permission (LOWER(BTRIM(code))) WHERE deleted = FALSE;

INSERT INTO sys_permission(code, name, module, resource_type, system_permission, sort_order)
VALUES ('system:permission:list', '权限查询', 'system', 'API', TRUE, 230),
       ('system:permission:create', '权限新增', 'system', 'API', TRUE, 231),
       ('system:permission:update', '权限编辑', 'system', 'API', TRUE, 232),
       ('system:permission:status', '权限启停', 'system', 'API', TRUE, 233),
       ('system:permission:delete', '权限删除', 'system', 'API', TRUE, 234);
