-- 主数据内部编码改由后端统一生成：客户 CUS / 供应商 SUP / 仓库 WH 各一条 PG 序列。
-- 存量数据的 CUS0001 / SUP0001 / WH001 与六位补零格式不相同，新号从 1 起不会与它们重号。
CREATE SEQUENCE customer_code_seq;
CREATE SEQUENCE supplier_code_seq;
CREATE SEQUENCE warehouse_code_seq;
