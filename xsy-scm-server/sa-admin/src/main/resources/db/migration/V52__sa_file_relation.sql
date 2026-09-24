-- ============================================================================
-- FA-2（F0-DEBT-01 读侧收口的第二步）：业务对象 ↔ 文件的关系授权
--
-- 要解决的问题：私有附件「谁能读」此前只能按目录前缀判断
-- （private/notice/、private/help-doc/ 直接对全体登录员工放行），
-- 前缀表达不了「哪一张单据」——同前缀下所有人的附件互相可读。
-- 规则改为：有权看这个业务对象 ⇒ 能看它绑定的文件（见 docs/decisions.md
-- 「P0 基线收口裁决」第 12–15 条）。
--
-- 顺序是硬性的：**本迁移先建表再回填存量关系**，Java 侧的 relation 判定
-- 只能在回填之后打开；若在没有关系行时就按 relation fail-closed，
-- 所有历史附件会一起变成不可读（裁决明令禁止的那种上线顺序）。
--
-- 不建数据库外键（AGENTS §8）：file_key 指向 t_file，biz_id 指向各业务表，
-- 完整性由服务层与唯一约束保证。列宽与 t_file.file_key 一致（VARCHAR(200)），
-- 不用附件规划稿里的 250 —— 存一个比来源列更宽的值会让「能存但关联不上」成为可能。
-- ============================================================================

CREATE TABLE t_file_relation
(
    relation_id        BIGSERIAL   NOT NULL,
    file_key           VARCHAR(200) NOT NULL,
    biz_type           VARCHAR(50)  NOT NULL,
    biz_id             BIGINT       NOT NULL,
    create_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag       BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT t_file_relation_pkey PRIMARY KEY (relation_id),
    -- biz_type 是白名单而不是自由字符串：注册表按它派发放行规则，
    -- 写错的值既不会被任何策略读到，也不会报错，只会静默不可读。
    CONSTRAINT ck_t_file_relation_biz_type CHECK (biz_type IN
        ('ENTERPRISE', 'NOTICE', 'HELP_DOC', 'FEEDBACK', 'PRODUCT')),
    CONSTRAINT ck_t_file_relation_key_not_blank CHECK (length(btrim(file_key)) > 0)
);

COMMENT ON TABLE t_file_relation IS '业务对象与文件附件的授权关系（有权看对象即可看其文件）';
COMMENT ON COLUMN t_file_relation.file_key IS 't_file.file_key 的值，不建外键';
COMMENT ON COLUMN t_file_relation.biz_type IS '业务对象类型白名单：ENTERPRISE 企业档案 / NOTICE 公告 / HELP_DOC 帮助文档 / FEEDBACK 反馈 / PRODUCT 商品';
COMMENT ON COLUMN t_file_relation.biz_id IS '该业务类型下的对象主键';
COMMENT ON COLUMN t_file_relation.deleted_flag IS '软删除；业务对象删除时同步置位，物理文件不删';

-- 同一份文件可以挂多个业务对象（一张回单属于多个结算单），所以唯一约束是三元组而不是 file_key。
CREATE UNIQUE INDEX uk_t_file_relation_active
    ON t_file_relation (file_key, biz_type, biz_id)
    WHERE deleted_flag = FALSE;

-- 读路径的两种访问方向都要有索引：按 key 判放行、按对象列附件。
CREATE INDEX idx_t_file_relation_key ON t_file_relation (file_key) WHERE deleted_flag = FALSE;
CREATE INDEX idx_t_file_relation_biz ON t_file_relation (biz_type, biz_id) WHERE deleted_flag = FALSE;

-- ----------------------------------------------------------------------------
-- 存量关系回填：按各业务表**已有的**附件列扫描，不猜、不新建第二份事实。
-- 附件列是逗号分隔的多 key 形态（FileKeyVoSerializer 的存储形式），逐个拆开。
-- ----------------------------------------------------------------------------

INSERT INTO t_file_relation (file_key, biz_type, biz_id)
SELECT btrim(part), 'ENTERPRISE', e.enterprise_id
FROM t_oa_enterprise e
         CROSS JOIN LATERAL regexp_split_to_table(
        concat_ws(',', e.enterprise_logo, e.business_license), ',') AS part
WHERE e.deleted_flag = FALSE
  AND btrim(part) <> ''
ON CONFLICT DO NOTHING;

INSERT INTO t_file_relation (file_key, biz_type, biz_id)
SELECT btrim(part), 'NOTICE', n.notice_id
FROM t_notice n
         CROSS JOIN LATERAL regexp_split_to_table(n.attachment, ',') AS part
WHERE n.deleted_flag = FALSE
  AND coalesce(n.attachment, '') <> ''
  AND btrim(part) <> ''
ON CONFLICT DO NOTHING;

-- t_help_doc / t_feedback 没有软删除列，整表即活动集合。
INSERT INTO t_file_relation (file_key, biz_type, biz_id)
SELECT btrim(part), 'HELP_DOC', h.help_doc_id
FROM t_help_doc h
         CROSS JOIN LATERAL regexp_split_to_table(h.attachment, ',') AS part
WHERE coalesce(h.attachment, '') <> ''
  AND btrim(part) <> ''
ON CONFLICT DO NOTHING;

INSERT INTO t_file_relation (file_key, biz_type, biz_id)
SELECT btrim(part), 'FEEDBACK', f.feedback_id
FROM t_feedback f
         CROSS JOIN LATERAL regexp_split_to_table(f.feedback_attachment, ',') AS part
WHERE coalesce(f.feedback_attachment, '') <> ''
  AND btrim(part) <> ''
ON CONFLICT DO NOTHING;

-- 商品图：只有仍落在私有前缀的存量行需要关系行。已经公开的行由前缀规则放行，
-- 给它们再建一行关系只是把「公开」这件事复制成两份事实。
INSERT INTO t_file_relation (file_key, biz_type, biz_id)
SELECT p.file_key, 'PRODUCT', p.spu_id
FROM product_image p
WHERE p.deleted = FALSE
  AND p.file_key NOT LIKE 'public/%'
  AND length(btrim(p.file_key)) > 0
ON CONFLICT DO NOTHING;
