-- 库存预警阈值通知：状态跃迁记忆 + 手动检查权限。
--
-- 为什么需要这张表：ADR-007 要求「异常持续期间不重复发送，恢复为 NORMAL 后再次进入异常可再次提醒」。
-- 这两条语义不可能只靠 scm_notification_event 的唯一键表达 —— 唯一的 event_key 只能回答
-- 「发过没有」，回答不了「上一次是什么状态、中间恢复过没有」。因此按 (仓库, SKU) 记住最近一次
-- 观察到的状态和一个**纪元号**：回到 NORMAL 时纪元 +1，下一次异常于是拿到一个新的 event_key。
--
-- 状态本身仍然不落库（ScmInventoryWarningStatusEnum 是唯一判定处）；这里存的是
-- 「上次通知时的状态」，是通知去重的依据，不是库存状态本身。

CREATE TABLE scm_inventory_warning_state (
    warehouse_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    epoch INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    PRIMARY KEY (warehouse_id, sku_id),
    CONSTRAINT ck_scm_inventory_warning_state_status CHECK (status IN ('NORMAL','LOW','HIGH')),
    CONSTRAINT ck_scm_inventory_warning_state_epoch CHECK (epoch >= 0)
);
CREATE INDEX idx_scm_inventory_warning_state_status ON scm_inventory_warning_state (status, updated_at DESC);

-- menu_id 已核对：857-899 空闲（900/901 属数据大屏），867 紧邻 V32 的 860-866 库存预警块。
-- 权限与 861（scm:inventory:warning:query）分开：查看预警是日常只读，
-- 主动检查并投递通知会给别人发站内信，不是同一量级的操作。
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component, perms_type,
                   api_perms, web_perms, icon, context_menu_id, visible_flag, create_user_id)
VALUES (867, '发送预警通知', 3, 860, 867, NULL, NULL, 1,
        'scm:inventory:warning:scan', 'scm:inventory:warning:scan', NULL, NULL, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id FROM t_role r
CROSS JOIN LATERAL unnest(ARRAY[867::bigint]) AS m(menu_id)
WHERE r.role_code IN ('SCM_STOREKEEPER','SCM_STOREKEEPER_LEAD','SUPER_ADMIN')
  AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);

SELECT setval(pg_get_serial_sequence('t_menu','menu_id'), (SELECT MAX(menu_id)+1 FROM t_menu), FALSE);

-- 扫描任务注册。默认每 15 分钟：阈值预警不是秒级指标，而每轮都要读全部阈值配置，
-- 过密只会重复得出同一个结论。检测与记账解耦（扫描而不是挂在余额写路径上）的取舍见 ADR-007。
-- 任务与手动检查共用同一段逻辑与同一个 event_key，因此两条路径不会各发一条通知。
INSERT INTO t_smart_job (job_name, job_class, trigger_type, trigger_value, enabled_flag, param, sort, remark,
                         deleted_flag, update_name, create_time, update_time)
SELECT '库存预警通知扫描', 'com.xsy.scm.inventory.job.InventoryWarningScanJob', 'cron', '0 */15 * * * ?',
       TRUE, '', 92,
       '默认每15分钟检查一次阈值跃迁并投递站内信；只在状态发生跃迁时发送，恢复为正常后再次异常可再提醒。留空参数。',
       FALSE, 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM t_smart_job
                  WHERE job_class = 'com.xsy.scm.inventory.job.InventoryWarningScanJob' AND deleted_flag = FALSE);
