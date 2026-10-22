-- 包装码称重与公开扫码令牌。
--
-- 边界：
--   * 重量是包装的固有属性（贴上标签就不再变），写在主体侧，不为它单建关联表；
--     一个称重筐最多对应一个包装码，由 weighing_event_id 上的部分唯一索引保证。
--   * 关联方向是 unit → 称重事件：xsy_sorting_scale_event 是设备上报的追加事实表，
--     不塞业务绑定列，否则作废/解绑都要改写事实表。
--   * public_token 与 unit_code 分开：unit_code 由自增序列发号，相邻码可推算，
--     不能当公开访问的凭据。token 只出现在标签二维码里，unit_code 仍是员工侧主键。
--   * 不新增数据库外键（V109 已确立）。

ALTER TABLE xsy_trace_unit
    -- 标签二维码里携带的凭据；与 unit_code 解耦，泄露 unit_code 不等于泄露扫码入口。
    ADD COLUMN public_token      VARCHAR(32),
    -- 净重与业务计量单位必须成对出现：只有净重没有单位，无法判断这是 3 公斤还是 3 斤。
    ADD COLUMN net_weight        NUMERIC(18, 4),
    ADD COLUMN weight_unit       VARCHAR(32),
    -- SCALE 表示来自电子秤；MANUAL 表示后台补录，两者在溯源页上不混称。
    ADD COLUMN weight_source     VARCHAR(16),
    -- 称重事实来源；为空表示这枚码没有经过电子秤。
    ADD COLUMN weighing_event_id BIGINT,
    ADD COLUMN measured_at       TIMESTAMPTZ;

-- 存量码补发令牌：md5 取前 16 位，够标签容量也够抗枚举（64 bit）。
UPDATE xsy_trace_unit
   SET public_token = substr(md5(random()::text || id::text || clock_timestamp()::text), 1, 16)
 WHERE public_token IS NULL;

ALTER TABLE xsy_trace_unit
    ALTER COLUMN public_token SET NOT NULL,
    ADD CONSTRAINT uk_trace_unit_public_token UNIQUE (public_token),
    ADD CONSTRAINT ck_trace_unit_weight CHECK (
        (net_weight IS NULL AND weight_unit IS NULL)
        OR (net_weight IS NOT NULL AND weight_unit IS NOT NULL AND net_weight > 0)),
    ADD CONSTRAINT ck_trace_unit_weight_source CHECK (
        weight_source IS NULL OR weight_source IN ('SCALE', 'MANUAL'));

-- 一个称重筐最多对应一个包装码：设备重复提交换筐不能造出第二个身份。
CREATE UNIQUE INDEX uk_trace_unit_weighing_event
    ON xsy_trace_unit (weighing_event_id) WHERE weighing_event_id IS NOT NULL;

COMMENT ON COLUMN xsy_trace_unit.public_token IS '标签二维码携带的公开凭据；与 unit_code 解耦，永不复用';
COMMENT ON COLUMN xsy_trace_unit.net_weight IS '净重；weight_source 说明它来自电子秤还是人工补录';
COMMENT ON COLUMN xsy_trace_unit.weighing_event_id IS '来源称重筐（xsy_sorting_scale_event.id）；为空表示未经电子秤';

-- 事件白名单补 WEIGH：人工补录与设备称重都要留下可追溯的事件，而不是静默改列。
ALTER TABLE xsy_trace_event DROP CONSTRAINT ck_trace_event_type;
ALTER TABLE xsy_trace_event ADD CONSTRAINT ck_trace_event_type
    CHECK (event_type IN ('ISSUE', 'VOID', 'BIND', 'UNBIND', 'WEIGH'));

-- 称重补录是仓库岗的现场操作：单独一个权限，不与发码/作废混授。
INSERT INTO t_menu(menu_id, menu_name, menu_type, parent_id, sort, path, component,
                   perms_type, api_perms, web_perms, context_menu_id, visible_flag, create_user_id)
VALUES (2125, '包装码称重补录', 3, 2110, 2125, NULL, NULL, 1,
        'scm:inventory:unit:weigh', 'scm:inventory:unit:weigh', 2110, TRUE, 1)
ON CONFLICT(menu_id) DO NOTHING;

INSERT INTO t_role_menu(role_id, menu_id)
SELECT r.role_id, m.menu_id FROM t_role r CROSS JOIN t_menu m
WHERE m.menu_id = 2125
  AND (r.role_id = 1 OR r.role_code IN ('SCM_STOREKEEPER', 'SCM_STOREKEEPER_LEAD'))
  AND NOT EXISTS (SELECT 1 FROM t_role_menu p WHERE p.role_id = r.role_id AND p.menu_id = m.menu_id);
