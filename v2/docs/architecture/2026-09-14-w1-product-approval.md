# W1 Product Pilot 批准记录

批准来源：2026-09-14 当前用户指令。仅执行 W1，按 T1 → T15 顺序，不自动进入 W2。

## 冻结基线唯一批准差异

冻结 HEAD：`95a54233586aa3c652e55cd833bb346d51f5a952`；分支：`feature/sprint5`。
原始清单：`D:\DevCaches\Codex\reviews\xsy-scm-legacy-freeze-2026-09-14\evidence\original-sha256.manifest`。

唯一批准差异是根 `.gitignore` 新增 `.workbuddy-ai` 与 `v2/.runtime` 两条规则，保留、不还原。`.pw-output/` 已在冻结快照内，并非新增豁免。
复验不得笼统跳过 `.gitignore`：应确认相对快照只增加这两条规则，其余内容不变。
`xsy-scm-server/**`、`xsy-scm-web/**`、`xsy-scm-miniapp/**`、根 `docs/**` 和其它冻结文件不允许变化。
预检：原始清单 900 个文件逐项 SHA-256 比较，仅 `.gitignore` 变化；HEAD 相符。不得将已有未提交改动误判为本次新增变化或还原它们。

## Q1–Q6

1. 冲突使用 HTTP 200 + ResponseDTO `code=40921`，保留业务语义。
2. 分类更新携带 version，启用乐观锁。
3. 分类筛选包含全部子分类，明确列为 **V2 Enhancement**，不是 legacy 事实。
4. 图片仅为 SPU 图集，不实现 SKU 图片。
5. 不保留“基础商品 / 加工品”Tab。
6. 详情使用独立隐藏路由，支持深链。

## 强制约束

- ScmExceptionHandler 只处理 ScmBusinessException；其它异常继续交给 SmartAdmin GlobalExceptionHandler。
- product_image 仅保存 SmartAdmin File 返回的引用和元数据；复用 COMMON 上传目录与文件能力。
- G1 的真实 @Version 自动测试先通过，再开始 Product Service。
- G2 仅 SCM Entity 显式使用 `@TableLogic(value="false", delval="true")`；不改系统表删除规则。
- V6/V7 在正式 W1 数据库成功应用后固定，只可新增后续 migration；该约束覆盖设计中“无第三个迁移”的旧措辞。
- 完成需 24 条 legacy 不变量与新增图片约束、Unit/Web/PG IT、Vue typecheck/lint/build、Playwright、冻结复验全部通过，出具验收报告及 Go/No-Go。

## 执行进度

T1–T15 全部完成（2026-09-15 收尾）。验收结论见 `2026-09-14-w1-product-验收报告.md`：**Go（附 1 项 typecheck 基线债说明）**。
不进入 W2。
