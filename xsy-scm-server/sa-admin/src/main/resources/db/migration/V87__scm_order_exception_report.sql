-- 异常分析只读现有事实；不建立新的异常状态表。
CREATE INDEX idx_sorting_item_exception_time ON sorting_task_item(sorted_at, task_id)
    WHERE deleted = FALSE AND occupation_status = 'ACTIVE'
      AND sorted_quantity IS NOT NULL AND sorted_quantity != planned_quantity_snapshot;
CREATE INDEX idx_delivery_order_exception_time ON delivery_route_order(signed_at, route_id)
    WHERE deleted = FALSE AND assignment_status = 'ACTIVE' AND fulfillment_status = 'EXCEPTION';
CREATE INDEX idx_order_return_rejected_time ON order_return(rejected_at, order_id)
    WHERE deleted = FALSE AND status = 'REJECTED';

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (1229, '异常订单分析', 2, 1200, 1229, '/report/report-order-exceptions',
        '/business/scm/report/report-order-exceptions.vue', NULL, NULL, NULL, 'WarningOutlined', NULL, TRUE, 1),
       (1230, '异常订单查询', 3, 1229, 1230, NULL, NULL, 1,
        'scm:report:order:exception:query', 'scm:report:order:exception:query', NULL, 1229, TRUE, 1);

-- 获得报表入口不获得源域权限，数据仍取各角色已有权限与范围的交集。
INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id FROM t_role r
CROSS JOIN LATERAL unnest(ARRAY[1200::bigint,1216::bigint,1229::bigint,1230::bigint]) AS m(menu_id)
WHERE r.role_code IN ('SUPER_ADMIN','SCM_STOREKEEPER_LEAD')
  AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);
SELECT setval(pg_get_serial_sequence('t_menu','menu_id'), (SELECT MAX(menu_id)+1 FROM t_menu), FALSE);
