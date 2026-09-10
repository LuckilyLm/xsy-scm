-- Password reset is a dedicated system action; preserve existing grants and make the route auditable.
INSERT INTO sys_permission(code, name, module, resource_type, system_permission, sort_order)
VALUES ('system:user:reset-password', '重置用户密码', 'system', 'API', TRUE, 55) ON CONFLICT DO NOTHING;
