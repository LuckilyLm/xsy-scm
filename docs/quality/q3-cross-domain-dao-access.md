# Q3 跨域 DAO 访问边界

跨域读取只有两种入口：正式领域 API，或经人工审议并登记的只读 DAO 访问。控制器不得直接访问 DAO；跨域 DAO 不能用于写入来源域事实。

## 现存只读访问

[`cross-domain-dao-allowlist.tsv`](../../tools/quality/cross-domain-dao-allowlist.tsv) 逐条记录调用文件、目标 DAO、允许的方法和业务原因。白名单按文件与 DAO 类型匹配，方法列表只包含查询或行锁读取。新增 DAO 引用、未登记方法或通配 DAO 导入会使 `python tools/verify.py quality` 失败。

## 幂等写入

幂等记录实体、DAO、哈希和 claim / replay / complete 实现现位于 common，共用 `idempotency_record` 表并参与调用方事务。采购保留错误码语义；订单模块保留 `OrderIdempotencyService` 兼容外观，订单、配送、库存、分拣和财务命令通过公共实现处理幂等。不得再从业务域直接导入该表的 DAO。
