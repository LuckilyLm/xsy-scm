-- GPS 退出当前配送范围；仅撤回入口授权，不修改历史迁移或轨迹数据。
DELETE FROM t_role_menu rm USING t_menu m
WHERE rm.menu_id = m.menu_id
  AND m.api_perms IN ('scm:delivery:gps:query', 'scm:delivery:gps:report');
UPDATE t_menu SET disabled_flag = TRUE, deleted_flag = TRUE, visible_flag = FALSE,
                  update_user_id = 1, update_time = CURRENT_TIMESTAMP
WHERE api_perms IN ('scm:delivery:gps:query', 'scm:delivery:gps:report');

-- 建议的线路归属、估算声明与输入结果共同冻结；只有状态和处理痕迹可变。
CREATE OR REPLACE FUNCTION reject_delivery_plan_proposal_snapshot_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.route_id IS DISTINCT FROM OLD.route_id
        OR NEW.input_snapshot IS DISTINCT FROM OLD.input_snapshot
        OR NEW.result_snapshot IS DISTINCT FROM OLD.result_snapshot
        OR NEW.provider_code IS DISTINCT FROM OLD.provider_code
        OR NEW.provider_version IS DISTINCT FROM OLD.provider_version
        OR NEW.estimated_flag IS DISTINCT FROM OLD.estimated_flag
        OR NEW.rule_code IS DISTINCT FROM OLD.rule_code
        OR NEW.stop_count IS DISTINCT FROM OLD.stop_count
        OR NEW.total_distance IS DISTINCT FROM OLD.total_distance
        OR NEW.created_at IS DISTINCT FROM OLD.created_at
        OR NEW.created_by IS DISTINCT FROM OLD.created_by THEN
        RAISE EXCEPTION 'delivery_plan_proposal snapshots are immutable';
    END IF;
    RETURN NEW;
END $$;
