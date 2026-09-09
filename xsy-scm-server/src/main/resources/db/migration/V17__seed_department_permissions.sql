INSERT INTO sys_permission(code, name, module, resource_type, system_permission, sort_order)
VALUES ('system:department:list', '部门查询', 'system', 'API', TRUE, 210),
       ('system:department:create', '部门新增', 'system', 'API', TRUE, 211),
       ('system:department:update', '部门编辑', 'system', 'API', TRUE, 212),
       ('system:department:status', '部门启停', 'system', 'API', TRUE, 213),
       ('system:department:delete', '部门删除', 'system', 'API', TRUE, 214);
