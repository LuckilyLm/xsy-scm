# 财务域

- 业务规则以根目录 `docs/decisions.md` 为准；过账入口与查询分别在 `FinancePostingService`、`FinancePostingQueryService`。
- 调整幂等流程时保留当前对象授权；版本、作废等可变状态不得挡住原成功请求的同键回放。
- `FinancePostingIT` 覆盖修改/作废重试、一次事实与一次审计。接口时间按秒展示，回放测试需按该展示精度比较业务时刻。
