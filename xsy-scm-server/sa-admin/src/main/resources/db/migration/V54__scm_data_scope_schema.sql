-- ============================================================================
-- P0-F 数据范围地基：员工-仓库授权表 + 司机与员工绑定
--
-- 口径来自 docs/decisions.md「P0 基线收口裁决」第 1、8、9 条：
--   角色只表达「能做什么」，仓库可见范围是独立授权，不编进角色名、也不借用部门。
--   本表是仓库维度的唯一事实来源：没有授权行就看不到该仓数据，
--   「看全部」同样必须是显式授权（scm:inventory:scope:all:query），不由代码写死。
--
-- 不使用数据库外键（AGENTS §8 项目级约定），关系完整性由服务层与唯一索引保证。
-- ============================================================================

CREATE TABLE employee_warehouse_scope (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_employee_warehouse_scope_positive CHECK (employee_id > 0 AND warehouse_id > 0),
    CONSTRAINT ck_employee_warehouse_scope_flag CHECK (deleted_flag IN (TRUE, FALSE))
);

-- 同一员工同一仓库只允许一条活动授权；重复绑定在写入侧即被拒绝，而不是靠 Java 去重
CREATE UNIQUE INDEX uk_employee_warehouse_scope_active
    ON employee_warehouse_scope (employee_id, warehouse_id)
    WHERE deleted_flag = FALSE;

-- 反向查询：这个仓有哪些授权人（停用仓库前的占用检查、按仓维度的范围解析）
CREATE INDEX idx_employee_warehouse_scope_warehouse
    ON employee_warehouse_scope (warehouse_id)
    WHERE deleted_flag = FALSE;

COMMENT ON TABLE employee_warehouse_scope IS '员工可操作的仓库授权（SCM 数据范围的仓库维度事实来源，一行=一人一仓）';
COMMENT ON COLUMN employee_warehouse_scope.id IS '主键';
COMMENT ON COLUMN employee_warehouse_scope.employee_id IS '员工 id，对应 t_employee.employee_id（无外键，服务层校验）';
COMMENT ON COLUMN employee_warehouse_scope.warehouse_id IS '仓库 id，对应 warehouse.id（无外键，服务层校验）';
COMMENT ON COLUMN employee_warehouse_scope.deleted_flag IS '授权回收标识；活动授权受部分唯一索引约束，回收后同一组合可重新授权';
COMMENT ON COLUMN employee_warehouse_scope.create_time IS '授权建立时间';
COMMENT ON COLUMN employee_warehouse_scope.update_time IS '最后变更时间';

-- ----------------------------------------------------------------------------
-- 司机绑定员工：普通司机只能看到自己名下的线路，必须能把 delivery_driver 映射回登录人
--
-- 历史行允许为 NULL（迁移前建的司机、外部司机），但正式启用前要求绑定，
-- 该约束落在服务层（启用状态校验），不在库里做半吊子 CHECK。
-- ----------------------------------------------------------------------------

ALTER TABLE delivery_driver ADD COLUMN IF NOT EXISTS employee_id BIGINT;
ALTER TABLE delivery_driver
    ADD CONSTRAINT ck_delivery_driver_employee_positive CHECK (employee_id IS NULL OR employee_id > 0);

-- 一个员工最多绑一个活动司机；一个司机记录只有一个 employee_id 列值，天然一人一司机
CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_driver_active_employee
    ON delivery_driver (employee_id)
    WHERE deleted = FALSE AND employee_id IS NOT NULL;

COMMENT ON COLUMN delivery_driver.employee_id IS '绑定的系统员工 id；用于把登录人映射回司机档案，NULL 表示未绑定（不可启用为正式司机）';
