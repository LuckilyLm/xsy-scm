-- 鲜蔬源：trace模块最终结构与默认数据（截至原V244）。
-- 仅用于空库初始化；已有库先通过显式基线切换工具核对并接管。
CREATE SCHEMA IF NOT EXISTS xsy;
SET search_path TO xsy, public;
SET check_function_bodies = false;

CREATE SEQUENCE xsy.inspection_photo_no_seq
  START WITH 1
  INCREMENT BY 1
  NO MINVALUE
  NO MAXVALUE
  CACHE 1;

CREATE SEQUENCE xsy.trace_lot_no_seq
  START WITH 1
  INCREMENT BY 1
  NO MINVALUE
  NO MAXVALUE
  CACHE 1;

CREATE SEQUENCE xsy.trace_unit_seq
  START WITH 1
  INCREMENT BY 1
  NO MINVALUE
  NO MAXVALUE
  CACHE 1;

CREATE FUNCTION xsy.xsy_assert_unit_bindable()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    unit_status VARCHAR(16);
    unit_lot_id BIGINT;
    active_bindings INT;
BEGIN
    SELECT status, lot_id INTO unit_status, unit_lot_id
    FROM xsy_trace_unit WHERE id = NEW.unit_id FOR UPDATE;
    IF unit_status IS DISTINCT FROM 'ACTIVE' OR unit_lot_id IS DISTINCT FROM NEW.lot_id THEN
        RAISE EXCEPTION 'cannot bind a void trace unit' USING ERRCODE = '23514';
    END IF;
    IF NEW.sales_order_item_id IS NULL THEN
        RAISE EXCEPTION 'binding must identify a sales order item' USING ERRCODE = '23514';
    END IF;
    -- 与正式出库 INSERT 共用订单行锁；发车后不能再补贴/改绑包装码。
    PERFORM 1 FROM xsy_sales_order_item WHERE id = NEW.sales_order_item_id FOR UPDATE;
    IF NOT FOUND OR NOT EXISTS (
        SELECT 1 FROM xsy_sorting_task_item si
        WHERE si.id = NEW.sorting_task_item_id
          AND si.sales_order_item_id = NEW.sales_order_item_id
          AND si.task_id = NEW.sorting_task_id
    ) THEN
        RAISE EXCEPTION 'binding source does not match a sorting order item'
            USING ERRCODE = '23514';
    END IF;
    IF EXISTS (
        SELECT 1 FROM xsy_inventory_outbound_item oi
        JOIN xsy_inventory_outbound o ON o.id = oi.outbound_id
        WHERE oi.sales_order_item_id = NEW.sales_order_item_id
          AND (o.status = 'CONFIRMED' OR o.confirmed_at IS NOT NULL)
    ) THEN
        RAISE EXCEPTION 'cannot bind trace unit after sales order item shipped'
            USING ERRCODE = '23514';
    END IF;
    SELECT count(*) INTO active_bindings
    FROM xsy_trace_unit_binding b
    WHERE b.unit_id = NEW.unit_id
      AND NOT EXISTS (SELECT 1 FROM xsy_trace_unit_unbind u WHERE u.binding_id = b.id);
    IF active_bindings > 0 THEN
        RAISE EXCEPTION 'trace unit already has an active binding' USING ERRCODE = '23505';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION xsy.xsy_guard_trace_unit_unbind_outbound()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    order_item_id BIGINT;
    already_shipped BOOLEAN;
BEGIN
    PERFORM 1 FROM xsy_trace_unit WHERE id = NEW.unit_id AND status = 'ACTIVE' FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'only an active trace unit can be unbound' USING ERRCODE = '23514';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM xsy_trace_unit_correction c
        WHERE c.id = NEW.correction_id AND c.unit_id = NEW.unit_id
          AND c.binding_id = NEW.binding_id AND c.status = 'PENDING'
          AND c.requested_by IS DISTINCT FROM NEW.unbound_by
    ) THEN
        RAISE EXCEPTION 'unbind requires matching pending independent correction'
            USING ERRCODE = '23514';
    END IF;
    SELECT sales_order_item_id INTO order_item_id
    FROM xsy_trace_unit_binding
    WHERE id = NEW.binding_id AND unit_id = NEW.unit_id;
    IF NOT FOUND OR order_item_id IS NULL THEN
        RAISE EXCEPTION 'trace unit unbind has no valid sales order item'
            USING ERRCODE = '23514';
    END IF;
    PERFORM 1 FROM xsy_sales_order_item WHERE id = order_item_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'trace unit unbind source order item missing' USING ERRCODE = '23514';
    END IF;
    SELECT EXISTS (
        SELECT 1 FROM xsy_inventory_outbound_item oi
        JOIN xsy_inventory_outbound o ON o.id = oi.outbound_id
        WHERE oi.sales_order_item_id = order_item_id
          AND (o.status = 'CONFIRMED' OR o.confirmed_at IS NOT NULL)
    ) INTO already_shipped;
    IF already_shipped THEN
        RAISE EXCEPTION 'cannot unbind a unit after its sales order item shipped'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION xsy.xsy_reject_trace_event_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'trace events are append-only' USING ERRCODE='23514';
END $$;

CREATE FUNCTION xsy.xsy_reject_trace_unit_binding_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'trace unit bindings are append-only' USING ERRCODE='23514';
END $$;

CREATE FUNCTION xsy.xsy_reject_trace_unit_unbind_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'trace unit unbinds are append-only' USING ERRCODE='23514';
END $$;

CREATE FUNCTION xsy.xsy_reject_void_of_bound_unit()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status = 'VOID' AND OLD.status IS DISTINCT FROM 'VOID'
        AND EXISTS (
            SELECT 1 FROM xsy_trace_unit_binding b
            WHERE b.unit_id = NEW.id
              AND NOT EXISTS (SELECT 1 FROM xsy_trace_unit_unbind ub WHERE ub.binding_id = b.id)
        ) THEN
        RAISE EXCEPTION 'cannot void a trace unit with an active binding'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;

CREATE TABLE xsy.xsy_trace_event (
  id bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY (SEQUENCE NAME xsy.xsy_trace_event_id_seq
                                                       START WITH 1
                                                       INCREMENT BY 1
                                                       NO MINVALUE
                                                       NO MAXVALUE
                                                       CACHE 1),
  unit_id bigint,
  lot_id bigint NOT NULL,
  event_type varchar(24) NOT NULL,
  source_document_type varchar(32),
  source_document_id bigint,
  source_document_item_id bigint,
  occurred_at timestamp with time zone NOT NULL,
  operator varchar(64) NOT NULL,
  remark varchar(200),
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  created_by varchar(64) NOT NULL,
  CONSTRAINT ck_trace_event_type CHECK (((event_type)::text = ANY ((ARRAY['ISSUE'::character varying, 'VOID'::character varying, 'BIND'::character varying, 'UNBIND'::character varying, 'WEIGH'::character varying])::text[]))),
  CONSTRAINT xsy_trace_event_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE xsy.xsy_trace_event
  IS '包装码事件：只追加，作废也追加一条 VOID 而不是改写原行';

COMMENT ON COLUMN xsy.xsy_trace_event.id
  IS '主键 ID';

COMMENT ON COLUMN xsy.xsy_trace_event.unit_id
  IS '追溯单元 ID';

COMMENT ON COLUMN xsy.xsy_trace_event.lot_id
  IS '批次 ID';

COMMENT ON COLUMN xsy.xsy_trace_event.event_type
  IS '事件类型';

COMMENT ON COLUMN xsy.xsy_trace_event.source_document_type
  IS '来源单据类型';

COMMENT ON COLUMN xsy.xsy_trace_event.source_document_id
  IS '来源单据 ID';

COMMENT ON COLUMN xsy.xsy_trace_event.source_document_item_id
  IS '来源单据明细 ID';

COMMENT ON COLUMN xsy.xsy_trace_event.occurred_at
  IS '业务发生时间';

COMMENT ON COLUMN xsy.xsy_trace_event.operator
  IS '操作人（用户类型:用户 ID）';

COMMENT ON COLUMN xsy.xsy_trace_event.remark
  IS '备注';

COMMENT ON COLUMN xsy.xsy_trace_event.created_at
  IS '创建时间';

COMMENT ON COLUMN xsy.xsy_trace_event.created_by
  IS '创建人（用户类型:用户 ID）';

CREATE TABLE xsy.xsy_trace_inspection_report (
  id bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY (SEQUENCE NAME xsy.xsy_trace_inspection_report_id_seq
                                                       START WITH 1
                                                       INCREMENT BY 1
                                                       NO MINVALUE
                                                       NO MAXVALUE
                                                       CACHE 1),
  report_no varchar(100) NOT NULL,
  report_name varchar(200) NOT NULL,
  lot_id bigint,
  inspection_date date,
  inspection_org varchar(200),
  conclusion varchar(24) NOT NULL,
  expires_at timestamp with time zone,
  status varchar(16) DEFAULT CAST('DRAFT' AS varchar) NOT NULL,
  published_by varchar(100),
  published_at timestamp with time zone,
  withdrawn_by varchar(100),
  withdrawn_at timestamp with time zone,
  created_by varchar(100) NOT NULL,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_by varchar(100) NOT NULL,
  updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  version integer DEFAULT 0 NOT NULL,
  operation_history jsonb DEFAULT CAST('[]' AS jsonb) NOT NULL,
  removed boolean DEFAULT FALSE NOT NULL,
  CONSTRAINT ck_inspection_report_metadata CHECK ((lot_id IS NOT NULL
                                               AND inspection_date IS NOT NULL
                                               AND inspection_org IS NOT NULL
                                               AND NOT removed)
                                               OR (lot_id IS NULL
                                               AND CAST(status AS text) = CAST('DRAFT' AS text)
                                               AND CAST(conclusion AS text) = CAST('UNDETERMINED' AS text)
                                               AND inspection_date IS NULL
                                               AND inspection_org IS NULL
                                               AND expires_at IS NULL)),
  CONSTRAINT xsy_trace_inspection_report_check CHECK (((expires_at IS NULL) OR (((expires_at AT TIME ZONE 'Asia/Shanghai'::text))::date >= inspection_date))),
  CONSTRAINT xsy_trace_inspection_report_check1 CHECK (CAST(status AS text) = CAST('DRAFT' AS text)
                                                    OR (published_by IS NOT NULL
                                                    AND published_at IS NOT NULL)),
  CONSTRAINT xsy_trace_inspection_report_check2 CHECK (CAST(status AS text) <> CAST('WITHDRAWN' AS text)
                                                    OR (withdrawn_by IS NOT NULL
                                                    AND withdrawn_at IS NOT NULL)),
  CONSTRAINT xsy_trace_inspection_report_conclusion_check CHECK (((conclusion)::text = ANY ((ARRAY['QUALIFIED'::character varying, 'UNQUALIFIED'::character varying, 'UNDETERMINED'::character varying])::text[]))),
  CONSTRAINT xsy_trace_inspection_report_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying, 'WITHDRAWN'::character varying])::text[]))),
  CONSTRAINT xsy_trace_inspection_report_version_check CHECK (version >= 0),
  CONSTRAINT xsy_trace_inspection_report_pkey PRIMARY KEY (id),
  CONSTRAINT xsy_trace_inspection_report_report_no_key UNIQUE (report_no)
);

COMMENT ON TABLE xsy.xsy_trace_inspection_report
  IS '批次检测报告；发布后内容冻结，撤回保留记录';

COMMENT ON COLUMN xsy.xsy_trace_inspection_report.id
  IS '主键 ID';

COMMENT ON COLUMN xsy.xsy_trace_inspection_report.report_no
  IS '检测报告编号';

COMMENT ON COLUMN xsy.xsy_trace_inspection_report.report_name
  IS '检测报告名称';

COMMENT ON COLUMN xsy.xsy_trace_inspection_report.lot_id
  IS '批次 ID';

COMMENT ON COLUMN xsy.xsy_trace_inspection_report.inspection_date
  IS '检测日期';

COMMENT ON COLUMN xsy.xsy_trace_inspection_report.inspection_org
  IS '检测机构';

COMMENT ON COLUMN xsy.xsy_trace_inspection_report.conclusion
  IS '检测结论：QUALIFIED 合格 / UNQUALIFIED 不合格 / UNDETERMINED 未判定';

COMMENT ON COLUMN xsy.xsy_trace_inspection_report.expires_at
  IS '过期时间';

COMMENT ON COLUMN xsy.xsy_trace_inspection_report.status
  IS '报告状态：DRAFT 草稿 / PUBLISHED 已发布 / WITHDRAWN 已撤回';

COMMENT ON COLUMN xsy.xsy_trace_inspection_report.published_by
  IS '发布人（用户类型:用户 ID）';

COMMENT ON COLUMN xsy.xsy_trace_inspection_report.published_at
  IS '发布时间';

COMMENT ON COLUMN xsy.xsy_trace_inspection_report.withdrawn_by
  IS '撤回人（用户类型:用户 ID）';

COMMENT ON COLUMN xsy.xsy_trace_inspection_report.withdrawn_at
  IS '撤回时间';

COMMENT ON COLUMN xsy.xsy_trace_inspection_report.created_by
  IS '创建人（用户类型:用户 ID）';

COMMENT ON COLUMN xsy.xsy_trace_inspection_report.created_at
  IS '创建时间';

COMMENT ON COLUMN xsy.xsy_trace_inspection_report.updated_by
  IS '更新人（用户类型:用户 ID）';

COMMENT ON COLUMN xsy.xsy_trace_inspection_report.updated_at
  IS '更新时间';

COMMENT ON COLUMN xsy.xsy_trace_inspection_report.version
  IS '乐观锁版本号';

COMMENT ON COLUMN xsy.xsy_trace_inspection_report.operation_history
  IS '保存、发布与撤回的操作人和时间，随业务事务追加';

COMMENT ON COLUMN xsy.xsy_trace_inspection_report.removed
  IS '独立照片移除标记；保留原始记录及操作历史';

CREATE TABLE xsy.xsy_trace_lot (
  id bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY (SEQUENCE NAME xsy.xsy_trace_lot_id_seq
                                                       START WITH 1
                                                       INCREMENT BY 1
                                                       NO MINVALUE
                                                       NO MAXVALUE
                                                       CACHE 1),
  lot_no varchar(32) NOT NULL,
  source_lot_no varchar(64),
  supplier_id bigint,
  sku_id bigint NOT NULL,
  origin_type varchar(24) DEFAULT CAST('PURCHASE_RECEIPT' AS varchar) NOT NULL,
  first_receipt_item_id bigint,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  created_by varchar(64) NOT NULL,
  CONSTRAINT ck_trace_lot_origin CHECK (CAST(origin_type AS text) = CAST('PURCHASE_RECEIPT' AS text)),
  CONSTRAINT uk_trace_lot_no UNIQUE (lot_no),
  CONSTRAINT xsy_trace_lot_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE xsy.xsy_trace_lot
  IS '追溯批次档案：内部批次号由系统发号，来源生产批号原样保存且允许为空';

COMMENT ON COLUMN xsy.xsy_trace_lot.id
  IS '主键 ID';

COMMENT ON COLUMN xsy.xsy_trace_lot.lot_no
  IS '批次号';

COMMENT ON COLUMN xsy.xsy_trace_lot.source_lot_no
  IS '供应商或现场提供的来源批号，不参与唯一约束，不冒充内部批次号';

COMMENT ON COLUMN xsy.xsy_trace_lot.supplier_id
  IS '供应商 ID';

COMMENT ON COLUMN xsy.xsy_trace_lot.sku_id
  IS '商品规格 ID';

COMMENT ON COLUMN xsy.xsy_trace_lot.origin_type
  IS '批次来源类型';

COMMENT ON COLUMN xsy.xsy_trace_lot.first_receipt_item_id
  IS '首次收货明细 ID';

COMMENT ON COLUMN xsy.xsy_trace_lot.created_at
  IS '创建时间';

COMMENT ON COLUMN xsy.xsy_trace_lot.created_by
  IS '创建人（用户类型:用户 ID）';

CREATE TABLE xsy.xsy_trace_public_query (
  id bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY (SEQUENCE NAME xsy.xsy_trace_public_query_id_seq
                                                       START WITH 1
                                                       INCREMENT BY 1
                                                       NO MINVALUE
                                                       NO MAXVALUE
                                                       CACHE 1),
  unit_id bigint NOT NULL,
  public_token varchar(32) NOT NULL,
  client_ip varchar(64),
  user_agent varchar(200),
  queried_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT ck_trace_public_query_token_not_blank CHECK (length(btrim(CAST(public_token AS text))) > 0),
  CONSTRAINT xsy_trace_public_query_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE xsy.xsy_trace_public_query
  IS '公开扫码查询审计：只追加，用于发现枚举爬取与举证';

COMMENT ON COLUMN xsy.xsy_trace_public_query.id
  IS '主键 ID';

COMMENT ON COLUMN xsy.xsy_trace_public_query.unit_id
  IS '追溯单元 ID';

COMMENT ON COLUMN xsy.xsy_trace_public_query.public_token
  IS '公开查询令牌';

COMMENT ON COLUMN xsy.xsy_trace_public_query.client_ip
  IS '取 X-Forwarded-For 首个地址；为空表示无法判定来源';

COMMENT ON COLUMN xsy.xsy_trace_public_query.user_agent
  IS '只留前 200 字符，用于粗分设备类型';

COMMENT ON COLUMN xsy.xsy_trace_public_query.queried_at
  IS '查询时间';

CREATE TABLE xsy.xsy_trace_unit (
  id bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY (SEQUENCE NAME xsy.xsy_trace_unit_id_seq
                                                       START WITH 1
                                                       INCREMENT BY 1
                                                       NO MINVALUE
                                                       NO MAXVALUE
                                                       CACHE 1),
  unit_code varchar(32) NOT NULL,
  lot_id bigint NOT NULL,
  package_type varchar(16) DEFAULT CAST('BAG' AS varchar) NOT NULL,
  quantity numeric(18, 4) NOT NULL,
  unit varchar(32) NOT NULL,
  status varchar(16) DEFAULT CAST('ACTIVE' AS varchar) NOT NULL,
  remark varchar(200),
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  created_by varchar(64) NOT NULL,
  public_token varchar(32) NOT NULL,
  net_weight numeric(18, 4),
  weight_unit varchar(32),
  weight_source varchar(16),
  weighing_event_id bigint,
  measured_at timestamp with time zone,
  CONSTRAINT ck_trace_unit_package_type CHECK (((package_type)::text = ANY ((ARRAY['BAG'::character varying, 'BOX'::character varying, 'CRATE'::character varying])::text[]))),
  CONSTRAINT ck_trace_unit_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'VOID'::character varying])::text[]))),
  CONSTRAINT ck_trace_unit_weight CHECK ((net_weight IS NULL
                                      AND weight_unit IS NULL)
                                      OR (net_weight IS NOT NULL
                                      AND weight_unit IS NOT NULL
                                      AND net_weight > CAST(0 AS numeric))),
  CONSTRAINT ck_trace_unit_weight_source CHECK (((weight_source IS NULL) OR ((weight_source)::text = ANY ((ARRAY['SCALE'::character varying, 'MANUAL'::character varying])::text[])))),
  CONSTRAINT xsy_trace_unit_quantity_check CHECK (quantity > CAST(0 AS numeric)),
  CONSTRAINT uk_trace_unit_code UNIQUE (unit_code),
  CONSTRAINT uk_trace_unit_public_token UNIQUE (public_token),
  CONSTRAINT xsy_trace_unit_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE xsy.xsy_trace_unit
  IS '追溯包装单元：一码一批，code 不透明且永不复用，可作废但不可删除';

COMMENT ON COLUMN xsy.xsy_trace_unit.id
  IS '主键 ID';

COMMENT ON COLUMN xsy.xsy_trace_unit.unit_code
  IS '系统发号的不透明码，不嵌日期与业务含义；作废后仍保留原码，不得复用';

COMMENT ON COLUMN xsy.xsy_trace_unit.lot_id
  IS '批次 ID';

COMMENT ON COLUMN xsy.xsy_trace_unit.package_type
  IS '包装类型';

COMMENT ON COLUMN xsy.xsy_trace_unit.quantity
  IS '数量';

COMMENT ON COLUMN xsy.xsy_trace_unit.unit
  IS '计量单位';

COMMENT ON COLUMN xsy.xsy_trace_unit.status
  IS '追溯单元状态';

COMMENT ON COLUMN xsy.xsy_trace_unit.remark
  IS '备注';

COMMENT ON COLUMN xsy.xsy_trace_unit.created_at
  IS '创建时间';

COMMENT ON COLUMN xsy.xsy_trace_unit.created_by
  IS '创建人（用户类型:用户 ID）';

COMMENT ON COLUMN xsy.xsy_trace_unit.public_token
  IS '标签二维码携带的公开凭据；与 unit_code 解耦，永不复用';

COMMENT ON COLUMN xsy.xsy_trace_unit.net_weight
  IS '净重；weight_source 说明它来自电子秤还是人工补录';

COMMENT ON COLUMN xsy.xsy_trace_unit.weight_unit
  IS '重量单位';

COMMENT ON COLUMN xsy.xsy_trace_unit.weight_source
  IS '重量来源';

COMMENT ON COLUMN xsy.xsy_trace_unit.weighing_event_id
  IS '来源称重筐（xsy_sorting_scale_event.id）；为空表示未经电子秤';

COMMENT ON COLUMN xsy.xsy_trace_unit.measured_at
  IS '称重时间';

CREATE TABLE xsy.xsy_trace_unit_binding (
  id bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY (SEQUENCE NAME xsy.xsy_trace_unit_binding_id_seq
                                                       START WITH 1
                                                       INCREMENT BY 1
                                                       NO MINVALUE
                                                       NO MAXVALUE
                                                       CACHE 1),
  unit_id bigint NOT NULL,
  lot_id bigint NOT NULL,
  sorting_task_id bigint NOT NULL,
  sorting_task_item_id bigint NOT NULL,
  sales_order_id bigint,
  sales_order_item_id bigint,
  customer_id bigint,
  customer_name_snapshot varchar(150),
  order_no_snapshot varchar(64),
  bound_at timestamp with time zone NOT NULL,
  bound_by varchar(64) NOT NULL,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  created_by varchar(64) NOT NULL,
  CONSTRAINT xsy_trace_unit_binding_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE xsy.xsy_trace_unit_binding
  IS '包装码与分拣任务行的绑定：一码一物，只能绑一次，绑错只能作废该码';

COMMENT ON COLUMN xsy.xsy_trace_unit_binding.id
  IS '主键 ID';

COMMENT ON COLUMN xsy.xsy_trace_unit_binding.unit_id
  IS '追溯单元 ID';

COMMENT ON COLUMN xsy.xsy_trace_unit_binding.lot_id
  IS '批次 ID';

COMMENT ON COLUMN xsy.xsy_trace_unit_binding.sorting_task_id
  IS '分拣任务 ID';

COMMENT ON COLUMN xsy.xsy_trace_unit_binding.sorting_task_item_id
  IS '分拣任务明细 ID';

COMMENT ON COLUMN xsy.xsy_trace_unit_binding.sales_order_id
  IS '销售订单 ID';

COMMENT ON COLUMN xsy.xsy_trace_unit_binding.sales_order_item_id
  IS '销售订单明细 ID';

COMMENT ON COLUMN xsy.xsy_trace_unit_binding.customer_id
  IS '客户 ID';

COMMENT ON COLUMN xsy.xsy_trace_unit_binding.customer_name_snapshot
  IS '绑定时刻的客户名称快照：客户改名后仍能回答「当时发给了谁」';

COMMENT ON COLUMN xsy.xsy_trace_unit_binding.order_no_snapshot
  IS '订单号快照';

COMMENT ON COLUMN xsy.xsy_trace_unit_binding.bound_at
  IS '绑定时间';

COMMENT ON COLUMN xsy.xsy_trace_unit_binding.bound_by
  IS '绑定人（用户类型:用户 ID）';

COMMENT ON COLUMN xsy.xsy_trace_unit_binding.created_at
  IS '创建时间';

COMMENT ON COLUMN xsy.xsy_trace_unit_binding.created_by
  IS '创建人（用户类型:用户 ID）';

CREATE TABLE xsy.xsy_trace_unit_correction (
  id bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY (SEQUENCE NAME xsy.xsy_trace_unit_correction_id_seq
                                                       START WITH 1
                                                       INCREMENT BY 1
                                                       NO MINVALUE
                                                       NO MAXVALUE
                                                       CACHE 1),
  unit_id bigint NOT NULL,
  binding_id bigint NOT NULL,
  reason varchar(200) NOT NULL,
  status varchar(16) DEFAULT CAST('PENDING' AS varchar) NOT NULL,
  requested_by varchar(64) NOT NULL,
  requested_at timestamp with time zone NOT NULL,
  decided_by varchar(64),
  decided_at timestamp with time zone,
  decision_reason varchar(200),
  version integer DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  created_by varchar(64) NOT NULL,
  updated_at timestamp with time zone,
  updated_by varchar(64),
  CONSTRAINT ck_trace_unit_correction_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))),
  CONSTRAINT xsy_trace_unit_correction_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE xsy.xsy_trace_unit_correction
  IS '包装码更正申请：解绑必须经他人审批，申请与审批不得是同一人';

COMMENT ON COLUMN xsy.xsy_trace_unit_correction.id
  IS '主键 ID';

COMMENT ON COLUMN xsy.xsy_trace_unit_correction.unit_id
  IS '追溯单元 ID';

COMMENT ON COLUMN xsy.xsy_trace_unit_correction.binding_id
  IS '绑定记录 ID';

COMMENT ON COLUMN xsy.xsy_trace_unit_correction.reason
  IS '原因';

COMMENT ON COLUMN xsy.xsy_trace_unit_correction.status
  IS '更正状态';

COMMENT ON COLUMN xsy.xsy_trace_unit_correction.requested_by
  IS '申请人（用户类型:用户 ID）';

COMMENT ON COLUMN xsy.xsy_trace_unit_correction.requested_at
  IS '申请时间';

COMMENT ON COLUMN xsy.xsy_trace_unit_correction.decided_by
  IS '审批人（用户类型:用户 ID）';

COMMENT ON COLUMN xsy.xsy_trace_unit_correction.decided_at
  IS '审批时间';

COMMENT ON COLUMN xsy.xsy_trace_unit_correction.decision_reason
  IS '审批原因';

COMMENT ON COLUMN xsy.xsy_trace_unit_correction.version
  IS '乐观锁版本号';

COMMENT ON COLUMN xsy.xsy_trace_unit_correction.created_at
  IS '创建时间';

COMMENT ON COLUMN xsy.xsy_trace_unit_correction.created_by
  IS '创建人（用户类型:用户 ID）';

COMMENT ON COLUMN xsy.xsy_trace_unit_correction.updated_at
  IS '更新时间';

COMMENT ON COLUMN xsy.xsy_trace_unit_correction.updated_by
  IS '更新人（用户类型:用户 ID）';

CREATE TABLE xsy.xsy_trace_unit_unbind (
  id bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY (SEQUENCE NAME xsy.xsy_trace_unit_unbind_id_seq
                                                       START WITH 1
                                                       INCREMENT BY 1
                                                       NO MINVALUE
                                                       NO MAXVALUE
                                                       CACHE 1),
  unit_id bigint NOT NULL,
  binding_id bigint NOT NULL,
  correction_id bigint NOT NULL,
  reason varchar(200) NOT NULL,
  unbound_at timestamp with time zone NOT NULL,
  unbound_by varchar(64) NOT NULL,
  created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
  created_by varchar(64) NOT NULL,
  CONSTRAINT uk_trace_unit_unbind_binding UNIQUE (binding_id),
  CONSTRAINT xsy_trace_unit_unbind_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE xsy.xsy_trace_unit_unbind
  IS '包装码解绑事实：只追加；一条绑定只能被撤销一次';

COMMENT ON COLUMN xsy.xsy_trace_unit_unbind.id
  IS '主键 ID';

COMMENT ON COLUMN xsy.xsy_trace_unit_unbind.unit_id
  IS '追溯单元 ID';

COMMENT ON COLUMN xsy.xsy_trace_unit_unbind.binding_id
  IS '绑定记录 ID';

COMMENT ON COLUMN xsy.xsy_trace_unit_unbind.correction_id
  IS '追溯更正单 ID';

COMMENT ON COLUMN xsy.xsy_trace_unit_unbind.reason
  IS '原因';

COMMENT ON COLUMN xsy.xsy_trace_unit_unbind.unbound_at
  IS '解绑时间';

COMMENT ON COLUMN xsy.xsy_trace_unit_unbind.unbound_by
  IS '解绑人（用户类型:用户 ID）';

COMMENT ON COLUMN xsy.xsy_trace_unit_unbind.created_at
  IS '创建时间';

COMMENT ON COLUMN xsy.xsy_trace_unit_unbind.created_by
  IS '创建人（用户类型:用户 ID）';

CREATE INDEX idx_inspection_photos
  ON xsy.xsy_trace_inspection_report (id DESC)
  WHERE lot_id IS NULL
    AND NOT removed;

CREATE INDEX idx_trace_event_lot
  ON xsy.xsy_trace_event (lot_id,
                             id);

CREATE INDEX idx_trace_event_unit
  ON xsy.xsy_trace_event (unit_id,
                             id);

CREATE INDEX idx_trace_inspection_report_lot
  ON xsy.xsy_trace_inspection_report (lot_id,
                                         id DESC);

CREATE INDEX idx_trace_lot_sku
  ON xsy.xsy_trace_lot (sku_id,
                           id);

CREATE INDEX idx_trace_lot_source
  ON xsy.xsy_trace_lot (supplier_id,
                           source_lot_no);

CREATE INDEX idx_trace_public_query_time
  ON xsy.xsy_trace_public_query (queried_at DESC);

CREATE INDEX idx_trace_public_query_token
  ON xsy.xsy_trace_public_query (public_token,
                                    queried_at DESC);

CREATE INDEX idx_trace_unit_binding_task_item
  ON xsy.xsy_trace_unit_binding (sorting_task_item_id);

CREATE INDEX idx_trace_unit_correction_status
  ON xsy.xsy_trace_unit_correction (status,
                                       id);

CREATE INDEX idx_trace_unit_lot
  ON xsy.xsy_trace_unit (lot_id,
                            id);

CREATE UNIQUE INDEX uk_trace_unit_correction_binding
  ON xsy.xsy_trace_unit_correction (binding_id)
  WHERE CAST(status AS text) = CAST('APPROVED' AS text);

CREATE UNIQUE INDEX uk_trace_unit_correction_pending
  ON xsy.xsy_trace_unit_correction (unit_id)
  WHERE CAST(status AS text) = CAST('PENDING' AS text);

CREATE UNIQUE INDEX uk_trace_unit_weighing_event
  ON xsy.xsy_trace_unit (weighing_event_id)
  WHERE weighing_event_id IS NOT NULL;

SELECT pg_catalog.setval('xsy.inspection_photo_no_seq', 1, FALSE);

SELECT pg_catalog.setval('xsy.trace_lot_no_seq', 1, FALSE);

SELECT pg_catalog.setval('xsy.trace_unit_seq', 1, FALSE);

SELECT pg_catalog.setval('xsy.xsy_trace_event_id_seq', 1, FALSE);

SELECT pg_catalog.setval('xsy.xsy_trace_inspection_report_id_seq', 1, FALSE);

SELECT pg_catalog.setval('xsy.xsy_trace_lot_id_seq', 1, FALSE);

SELECT pg_catalog.setval('xsy.xsy_trace_public_query_id_seq', 1, FALSE);

SELECT pg_catalog.setval('xsy.xsy_trace_unit_binding_id_seq', 1, FALSE);

SELECT pg_catalog.setval('xsy.xsy_trace_unit_correction_id_seq', 1, FALSE);

SELECT pg_catalog.setval('xsy.xsy_trace_unit_id_seq', 1, FALSE);

SELECT pg_catalog.setval('xsy.xsy_trace_unit_unbind_id_seq', 1, FALSE);

CREATE TRIGGER trg_trace_event_append_only
  BEFORE DELETE OR UPDATE OR TRUNCATE
  ON xsy.xsy_trace_event
  FOR EACH STATEMENT
    EXECUTE PROCEDURE xsy.xsy_reject_trace_event_mutation();

CREATE TRIGGER trg_trace_unbind_outbound_guard
  BEFORE INSERT
  ON xsy.xsy_trace_unit_unbind
  FOR EACH ROW
    EXECUTE PROCEDURE xsy.xsy_guard_trace_unit_unbind_outbound();

CREATE TRIGGER trg_trace_unit_binding_active
  BEFORE INSERT
  ON xsy.xsy_trace_unit_binding
  FOR EACH ROW
    EXECUTE PROCEDURE xsy.xsy_assert_unit_bindable();

CREATE TRIGGER trg_trace_unit_binding_append_only
  BEFORE DELETE OR UPDATE OR TRUNCATE
  ON xsy.xsy_trace_unit_binding
  FOR EACH STATEMENT
    EXECUTE PROCEDURE xsy.xsy_reject_trace_unit_binding_mutation();

CREATE TRIGGER trg_trace_unit_unbind_append_only
  BEFORE DELETE OR UPDATE OR TRUNCATE
  ON xsy.xsy_trace_unit_unbind
  FOR EACH STATEMENT
    EXECUTE PROCEDURE xsy.xsy_reject_trace_unit_unbind_mutation();

CREATE TRIGGER trg_trace_unit_void_active_guard
  BEFORE UPDATE OF status
  ON xsy.xsy_trace_unit
  FOR EACH ROW
    EXECUTE PROCEDURE xsy.xsy_reject_void_of_bound_unit();

SET check_function_bodies = true;
