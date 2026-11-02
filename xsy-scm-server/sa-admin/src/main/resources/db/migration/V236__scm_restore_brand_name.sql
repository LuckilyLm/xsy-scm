-- 品牌名恢复为鲜蔬源。
-- V170 已在 2026-10-21 把默认部门与文号示例改为“鲜蔬园”；V170 已执行，不能回改，
-- 这里用新迁移把默认部门改回，并同步文号列注释。管理员自定义过的部门名不回写。
UPDATE xsy_v2.t_department
SET department_name = '鲜蔬源',
    update_time = now()
WHERE department_id = 1
  AND department_name = '鲜蔬园';

COMMENT ON COLUMN xsy_v2.t_notice.document_number IS '文号，如：鲜蔬源〔2026〕字第36号';
