-- 独立照片记录不依赖采购、收货、批次或包装码；原批次报告保留原发布规则。
ALTER TABLE xsy_trace_inspection_report ALTER COLUMN lot_id DROP NOT NULL;
ALTER TABLE xsy_trace_inspection_report ALTER COLUMN inspection_date DROP NOT NULL;
ALTER TABLE xsy_trace_inspection_report ALTER COLUMN inspection_org DROP NOT NULL;
ALTER TABLE xsy_trace_inspection_report ADD COLUMN removed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE xsy_trace_inspection_report ADD CONSTRAINT ck_inspection_report_metadata CHECK (
    (lot_id IS NOT NULL AND inspection_date IS NOT NULL AND inspection_org IS NOT NULL AND NOT removed)
    OR (lot_id IS NULL AND status = 'DRAFT' AND conclusion = 'UNDETERMINED'
        AND inspection_date IS NULL AND inspection_org IS NULL AND expires_at IS NULL)
);
CREATE INDEX idx_inspection_photos ON xsy_trace_inspection_report(id DESC) WHERE lot_id IS NULL AND NOT removed;
CREATE SEQUENCE inspection_photo_no_seq;
COMMENT ON COLUMN xsy_trace_inspection_report.removed IS '独立照片移除标记；保留原始记录及操作历史';

INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component,
    perms_type, api_perms, web_perms, context_menu_id, visible_flag, create_user_id)
VALUES (2140, '检测报告', 2, 700, 710, '/purchase/inspection-photos',
    '/business/scm/trace/inspection-photos.vue', 1, 'scm:trace:photo:query', 'scm:trace:photo:query', 2140, TRUE, 1),
    (2141, '上传报告照片', 3, 2140, 1, NULL, NULL, 1, 'scm:trace:photo:upload', 'scm:trace:photo:upload', 2140, TRUE, 1),
    (2142, '移除报告照片', 3, 2140, 2, NULL, NULL, 1, 'scm:trace:photo:remove', 'scm:trace:photo:remove', 2140, TRUE, 1)
ON CONFLICT (menu_id) DO NOTHING;
INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id FROM t_role r CROSS JOIN t_menu m
WHERE (r.role_id = 1 OR r.role_code IN ('SCM_PURCHASER', 'SCM_FINANCE', 'SCM_STOREKEEPER', 'SCM_STOREKEEPER_LEAD'))
    AND m.menu_id IN (2140, 2141, 2142)
    AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);
SELECT setval(pg_get_serial_sequence('t_menu', 'menu_id'), (SELECT MAX(menu_id) + 1 FROM t_menu), false);
