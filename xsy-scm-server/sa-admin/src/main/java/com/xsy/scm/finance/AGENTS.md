# 财务域

- FinanceSettlementRuleService在正常应收/应付追加前冻结账期，关联红字继承原单快照；到期日按Asia/Shanghai业务日期计算，不能依赖数据库触发器。
- 事实Mapper继承AppendOnlyMapper；金额、方式、来源配对及核销守恒由后端服务校验，数据库CHECK不再作为业务兜底。

- 业务规则以根目录 `docs/decisions.md` 为准；过账入口与查询分别在 `FinancePostingService`、`FinancePostingQueryService`。
- 调整幂等流程时保留当前对象授权；版本、作废等可变状态不得挡住原成功请求的同键回放。
- `FinancePostingIT` 覆盖修改/作废重试、一次事实与一次审计。接口时间按秒展示，回放测试需按该展示精度比较业务时刻。
- 应收自动抵扣的候选余额复用列表的客户级FIFO抵扣，按实际客户与结算主体分别归集，在计算有效余额后才筛选负责人和限制数量；不能把结算主体当客户共用抵扣池。
- 应收/扣减详情的负责人通过FinanceReceivableDao.selectSellerId读取，与列表的订单负责人优先、客户负责人兜底一致；独立退货没有订单，不得因此误拒绝其客户负责人。
- 被商城资金Mapper跨命名空间复用的应收SQL片段，其嵌套include必须写完整命名空间；相对引用会在调用方命名空间解析并导致应用启动失败。
