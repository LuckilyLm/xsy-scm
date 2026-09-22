-- V27: 订单操作日志类型白名单加 RESERVE_STOCK，2026-09-19。
--
-- 背景：V26 引入「预留库存」操作后，SalesOrderService.reserveStock 会写一条
-- operation_type = 'RESERVE_STOCK' 的操作日志，但 ck_order_operation_log_type 的
-- 白名单还是 W4 建表时的 6 个值，插入直接失败（联调实测 10001 + DataIntegrityViolation）。
--
-- 这是**第五处硬编码白名单**（前四处：枚举类、DB CHECK、前端常量、查询表单 @Pattern）。
-- 教训：新增业务动作时，凡是「动作类型」都要顺着查一遍 —— 至少包括
--   * 业务枚举
--   * 该枚举对应的 DB CHECK 白名单
--   * 前端 SmartEnum 常量
--   * 查询表单的 @Pattern 校验
--   * **操作日志的 operation_type 白名单**（最容易漏，因为它不在业务枚举旁边）
--
-- 纯 DDL（重建一条 CHECK），无数据变更。
ALTER TABLE order_operation_log DROP CONSTRAINT ck_order_operation_log_type;
ALTER TABLE order_operation_log ADD CONSTRAINT ck_order_operation_log_type
    CHECK (operation_type IN ('CREATE', 'UPDATE', 'SUBMIT', 'ACTUAL_QUANTITY',
                              'CONFIRM', 'CANCEL', 'RESERVE_STOCK'));
