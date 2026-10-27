-- 抹零单号序列：与应收/应付/收款/付款/核销同一套「全局非重置」口径，
-- 单号 = WV + 业务日(Asia/Shanghai) + %06d，由 ScmDocumentNumbers.format 拼接。
--
-- 单独补一个迁移而不是改 V194：V194 只建表，序列属于单号规则；
-- 已应用迁移的校验和必须保持稳定。

CREATE SEQUENCE finance_waiver_no_seq;
