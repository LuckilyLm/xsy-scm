# 订单域

- 业务规则以根目录 `docs/decisions.md` 为准；本目录的 `manager` 负责明细差异、快照和金额规则，`service` 负责事务编排。
- 修改明细身份与重锁金额时，同步检查 `OrderRulesTest`、`OrderLockPgIT` 和 Web 的 `order-form-model.ts`；商品替换必须经过既有分拣/出库保护。
- `SalesOrderItemChangeSet` 的新增行不再携带旧主键，移除行仍保留原版本供软删除校验。
- 非标未称重的暂计与实际称重应分别覆盖；不能靠过滤空金额掩盖重新构造明细时遗漏的结算额。
- 订单Excel导入的合计/签字结束标记只结束明细解析，仍要检查尾部是否存在遗漏单据；回归入口为SalesOrderImportServiceTest。
