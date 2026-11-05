# 采购域

- 业务规则以根目录 `docs/decisions.md` 为准；补价和改价集中在 `PurchasePriceService`，共享防重复处理封装为 `PurchaseIdempotencyService`。
- 修改补改价入口时同时检查返回原结果前的当前归属校验，以及返回原结果后的价格窗口、锁单、收货/记账状态校验。
- `PurchasePriceReplayTest` 覆盖授权撤回后的拒绝与合法重复请求返回原结果；数据库价格窗口与审计回归在 `PurchasePriceWindowIT`。
