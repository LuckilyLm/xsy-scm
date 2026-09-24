-- V59: 订单操作日志类型白名单加 RETURN / REFUND，2026-09-23。
--
-- 背景：AGENTS.md §7.3 规定订单状态变更必须显式可审计，并把 cancellation / refund / return
-- 三类并列；`CANCEL` 早在建表白名单里，但退货与退款一直没有对应的 operation_type，
-- 于是 OrderReturnService 的建单/审批/驳回/撤销与 OrderRefundService 的退款完成
-- 全部没有留下任何操作日志记录 —— 持订单查看权的人从 /scm/order/log/query 看不到这些动作。
--
-- 沿用 V27 的口径：新增业务动作要顺着查一遍五处硬编码（业务枚举、DB CHECK、前端 SmartEnum、
-- 查询表单 @Pattern、本白名单）。本表单的 operationType 只有 @Size(max=40) 没有 @Pattern，
-- 所以这一处不受影响；其余三处随本迁移同步更新（ScmOrderOperationTypeEnum、
-- order-const.ts 的 SCM_ORDER_OPERATION_ENUM）。
--
-- 类型刻意保持粗粒度（RETURN / REFUND 各一条），与 SalesOrderService 既有的
-- log(UPDATE, "删除草稿") 同风格：具体是建单还是审批，看 before/after 里的单据状态迁移，
-- 不为每一步动作再扩一次白名单。
--
-- 纯 DDL（重建一条 CHECK），无数据变更。
ALTER TABLE order_operation_log DROP CONSTRAINT ck_order_operation_log_type;
ALTER TABLE order_operation_log ADD CONSTRAINT ck_order_operation_log_type
    CHECK (operation_type IN ('CREATE', 'UPDATE', 'SUBMIT', 'ACTUAL_QUANTITY',
                              'CONFIRM', 'CANCEL', 'RESERVE_STOCK',
                              'RETURN', 'REFUND'));
