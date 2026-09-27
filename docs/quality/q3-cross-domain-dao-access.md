# Q3 跨域 DAO 访问边界

跨域读取只有两种入口：正式领域 API，或经人工审议并登记的只读 DAO 访问。控制器不得直接访问 DAO；跨域 DAO 不能用于写入来源域事实。

## 现存只读访问

[`cross-domain-dao-allowlist.tsv`](../../tools/quality/cross-domain-dao-allowlist.tsv) 逐条记录调用文件、目标 DAO、允许的方法和业务原因。白名单按文件与 DAO 类型匹配，方法列表只包含查询或行锁读取。新增 DAO 引用、未登记方法或通配 DAO 导入会使 `python tools/verify.py quality` 失败。

## 幂等写入

采购幂等服务已停止直接调用 `order.dao.IdempotencyRecordDao`，改经幂等服务接口提交 claim、读回和完成结果，继续共用 `idempotency_record` 表并保持调用方事务。

Q1 审计提出将 `OrderIdempotencyService` 的通用实现迁入 common。此项仍需在同一事务和既有错误码语义下完成；现存订单、配送、库存、分拣和财务调用保留在 API 侧，不能改回直接 DAO 访问。
