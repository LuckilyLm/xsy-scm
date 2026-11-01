-- 草稿状态下线后，销售单 / 采购单的建单默认状态必须从已删除的 DRAFT 改为首个合法状态。
-- V221 / V222 只改了 CHECK 约束，漏了列默认值，导致不显式传 status 的插入违反约束。
ALTER TABLE xsy_sales_order ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE xsy_purchase_order ALTER COLUMN status SET DEFAULT 'SUBMITTED';
