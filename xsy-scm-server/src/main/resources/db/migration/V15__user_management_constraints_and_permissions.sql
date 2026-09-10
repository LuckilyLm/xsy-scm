-- Fail rather than silently merging existing case-colliding principals.
CREATE UNIQUE INDEX uk_sys_user_username_lower_active
    ON sys_user (LOWER(username)) WHERE deleted = FALSE;

INSERT INTO sys_permission(code,name,module,resource_type,system_permission,sort_order)
VALUES ('system:user:read','查看用户','system','API',TRUE,10),
       ('system:user:create','创建用户','system','API',TRUE,20),
       ('system:user:update','编辑用户及分配部门','system','API',TRUE,30),
       ('system:user:status','启停用户','system','API',TRUE,40),
       ('system:user:delete','删除用户','system','API',TRUE,50);
