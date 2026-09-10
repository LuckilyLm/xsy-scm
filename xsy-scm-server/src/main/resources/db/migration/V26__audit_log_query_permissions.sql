-- Dedicated read permissions; existing role grants are preserved.
INSERT INTO sys_permission(code, name, module, resource_type, system_permission, sort_order)
VALUES ('system:login-log:list', '查询登录日志', 'system', 'API', TRUE, 90),
       ('system:operation-log:list', '查询操作日志', 'system', 'API', TRUE, 91) ON CONFLICT DO NOTHING;
