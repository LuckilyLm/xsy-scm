# 部署期配置说明

各环境的 Spring 配置（`sa-admin/src/main/resources/<profile>/application.yaml`）只声明取值来源，
生产密钥一律不写进代码仓库。本文件登记**必须由环境变量注入**的配置项。

## 生产 / 预发必填

| 环境变量 | 配置键 | 用途 | 缺失后果 |
| --- | --- | --- | --- |
| `SCM_INVENTORY_STOCKTAKE_SNAPSHOT_SECRET` | `scm.inventory.stocktake.snapshot.secret` | 盘点快照导入凭证的 HMAC-SHA256 签名密钥（`/scm/inventory/stocktake/import/template` 签发的实盘快照） | 应用启动期直接失败：`StocktakeSnapshotSigner` 在 `pre` / `prod` profile 下拒绝空值，也拒绝沿用代码仓库内公开可知的开发默认值 |

取值要求：高熵随机串（建议不少于 32 字节随机值），`pre` 与 `prod` 使用不同值，各环境独立保管。
轮换该值只会让**尚未提交**的快照凭证失效（凭证有效期以分钟计），不影响已入库的盘点单。

之所以按环境拒绝而不是靠注释提醒：静默退回公开密钥会让「快照与当前账面一致」这一校验变成可伪造的断言，
盘点差异写库前的最后一道防线随之失效。

## 开发 / 测试

`dev` 与 `test` 配置显式写入仓库内的开发默认密钥，便于本地与自动化测试直接跑；
该默认值出现在 `pre` / `prod` 时按上表启动失败，不会降级运行。
