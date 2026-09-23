# 代码评审与贡献约定

> 面向**人**的评审约定。面向 AI 编码代理的仓库规则见 [`AGENTS.md`](./AGENTS.md)，
> 技术栈边界见 [`SMARTADMIN_REFERENCE_RULES.md`](./SMARTADMIN_REFERENCE_RULES.md)。
> 本文只讲一件事：**怎么评审，才能让这个项目的真实风险被挡住。**

---

## 一、为什么清单要这样写

通用评审清单（命名规范、代码风格、注释是否完整）对本项目帮助有限——这些 eslint 和编译器已经在管了。

本项目的真实风险集中在另一类问题上，而且**它们全部是「代码看起来对、跑起来也过、但语义已经错了」的类型**：

- 锁序写反 → 两个方向相反的加锁顺序形成环 → 生产上偶发死锁
- `ON CONFLICT` 冲突目标与部分唯一索引不匹配 → 约束静默失效
- 需求侧漏算旧分配 → 数量没变的一次编辑就误报 40082
- 漏掉「旧 ∪ 新」并集 → 需求永久卡在 `ALLOCATED`，再也分不出去
- `BigDecimal` 用 `equals` 比较 → 精度不同的相等数值被判不等

这些都无法靠读代码风格发现，只能靠**知道要问什么问题**。所以下面的清单直接对着本项目已有的不变量写。

---

## 二、评审清单

### 写入路径

- [ ] 新增写命令是否走 `IdempotencyService.claim` / `complete` / `replay`？
- [ ] 幂等 scope 是否唯一且含实体 id（如 `"PURCHASE_ORDER_SUBMIT:" + form.getId()`）？
- [ ] 乐观锁：是否校验 version，且 DAO 返回行数 `!= 1` 时抛 `VERSION_CONFLICT`？

### 并发与锁序（P12）

- [ ] 是否遵守 P12 锁序？本次改动有没有让两个方向相反的加锁顺序形成环？
- [ ] 批量操作是否**按 id 升序**处理（如 `batchDelete` 的 `sorted(comparing(...::getId))`）？
- [ ] 涉及 `FOR UPDATE` 的查询是否一次性按升序锁完，而不是循环内逐条锁？
- [ ] 锁序说明是否保留在类注释里？（锁序是**跨类不变量**，抽类时最容易丢）

### 数据库

- [ ] 是否新增了外键？**项目禁止**——关系完整性由服务层 + 唯一约束 + CHECK 保证。
- [ ] 是否修改了 V1–V18？**不可变**。新迁移一律新增版本号。
- [ ] 新迁移版本号是否**唯一且连续**？选号前是否 `git fetch` 并确认远端 main 是本地 HEAD 的祖先？
- [ ] `ON CONFLICT (...) WHERE ...` 的冲突目标是否与部分唯一索引**逐字一致**（Q11）？
- [ ] 金额 / 数量字段精度是否足够（`NUMERIC(18,4)` 等）？比较是否用 `compareTo` 而非 `equals`？

### 领域不变量

- [ ] 状态迁移是否走状态机（`PurchaseOrderStateMachine` 等），而不是裸写 `setStatus`？
- [ ] 库存 Q13：同一 `(warehouse_id, sku_id)` 是否只锁一个记账单位？不一致时是否**整笔回滚**并抛 `INVENTORY_UNIT_MISMATCH(41001)`？
- [ ] 库存 Q7：流水是否只增不改？DAO 是否只声明 insert + select？冲销是否用**新的反向流水**而不是 UPDATE？
- [ ] 需求侧 `allocated_quantity` / `status` 是否在**旧 ∪ 新并集**上重算？
      （只在旧集合出现的 demand 也必须重算，否则需求永久卡在 `ALLOCATED`）
- [ ] `demand.allocated_quantity` 是**全库**合计，校验时是否扣除了本单旧值？
      （不扣会导致「原样保存」都误报 40082）

### 架构与安全

- [ ] `@Transactional` 是否只在编排层（`*Service`）？
      被抽出的 `*Factory` / `*Calculator` / `*Assembler` **不应**带事务注解。
- [ ] 是否触碰 F0-DEBT-01 未解决的路径？
      （`FileKeyVoSerializer` 旁路已临时收口为逐 key 过 `FileAccessGuard.filterReadable`，
      但 `FileService.getFileList(keys)` 仍是**无身份批量入口**、代码生成模板未改；
      新代码不得直接调用无身份入口，`scm_file_relation`（FA-2）落地前不视为已关闭）
- [ ] 业务附件是否走了正确的 fileKey 前缀策略与读取守卫？

### 测试

- [ ] 新增行为是否有对应 IT？
- [ ] 本次是否**改了测试来让重构通过**？纯提取应保留既有断言；发现独立缺陷时先补能复现的回归用例，再修实现，不能削弱断言来获得通过。
- [ ] **IT 是否真的跑了？** 看 `mvn test` 的输出里有没有 `*IT` 类；
      只有 `*Test` 类说明集成测试又被静默跳过了。

---

## 三、坏味道对照表

| 坏味道 | 本仓库实例 | 判定阈值 |
| --- | --- | --- |
| 上帝类 | `PurchaseOrderService` 805 行 / 28 方法 | 单类 > 400 行需说明理由 |
| 长方法 | `PurchaseReceiptService.confirm()` 163 行 | 单方法 > 60 行需拆分或说明 |
| 复制粘贴 | `stamp(...)` 三个重载（Entity / Item / Allocation） | 出现第 2 份相似实现即提取 |
| 职责混杂 | 审计快照序列化与事务编排同处一类 | 一个类只应有一个变更原因 |
| 门禁数据混入文档目录 | `docs/quality/ts-baseline.json` 被当文档删除 | 数据文件与文档分目录存放 |
| 大文件混入源码 | `province-city-district.ts` 17,546 行 | 数据文件与逻辑分离 |

> 这张表是**活的**：每周回溯评审发现的新模式追加进来，不要另建文档。

---

## 四、评审轮值与第二责任人

仓库历史上 97 次提交全部来自单一作者。这意味着所有设计判断（锁序、幂等、精度口径、Q11 匹配、三态可回落）都只存在于一个人的脑子里——**这是当前最大的单点风险，比任何代码问题都严重**。

机制建议：

1. **每次改动必须有第二人评审**，评审人轮值，不固定为同一人。
2. **评审人要在 PR 里复述一遍改动的业务意图**。
   复述不出来说明还没理解，**不能点通过**。这一条是本文最重要的规则。
3. **每周抽一个已合并的 PR 做回溯评审**，把发现的新问题模式沉淀进第三节的表。
4. **关键不变量指定「第二责任人」**——锁序、幂等、库存账本、迁移策略各指定一人。
   第二责任人的职责不是分担工作量，而是**能在原负责人缺席时解释并维护它**。

---

## 五、验证证据的写法

PR 模板里的「验证证据」要求粘贴**实际命令与实际输出**，不接受结论性描述。

这不是形式主义。`docs/progress.md` 里出现过这样的记录：

> W6-1 后续修复（含 V21）提交后尚未重新运行测试、构建、迁移或浏览器验证。

以及 `AGENTS.md` 里：

> V21 has NOT been executed or tested in this review.

代码已经交付，但没有任何机制能证明它是好的。要求粘贴输出，是为了让「没跑」在评审阶段就被看见，而不是等到下一个波次才发现。

**必须如实填写「未覆盖到的部分」**——写「无」也是一种声明，同样要能兑现。

---

## 六、当前验证约定

- 已应用迁移不能编辑注释。本次 V15/V19/V20 是恢复到 `d7dbea3` 之前的原始字节；已与本机 Flyway 校验和核对。其他环境若应用了不同版本，先核对历史，不自动执行 repair。
- `mvn -pl sa-admin -am test` 同时发现单元测试和 `*IT`。仅单测可加 `-Dtest='!*IT'`；这种执行不能作为集成测试证据。
- PostgreSQL、Redis 使用本地测试配置。F0 云测试仅在 `XSY_FILE_STORAGE_MODE=cloud` 时启用，还需按 `deploy/minio/README.md` 提供服务及完整云配置；仅启动 MinIO 不会取消条件跳过。
- 类型基线位于 `xsy-scm-web/quality/ts-baseline.json`，从历史基线恢复，保留其错误上限。`tools/ts_baseline_ratchet.py` 使用 PATH 中的 Node，拒绝新增错误、同类错误数量增长及任何 SCM 错误。编译器异常和无定位配置错误直接失败。基线通过不等于全量 `npm run typecheck` 零错误。
- 仅放行验证入口、类型门禁及其测试进入 Git；其他 `tools/` 本机脚本仍忽略。E2E 仍依赖 `tools/w1_e2e_accounts.py` 至 `w6_e2e_accounts.py`，干净克隆缺少这些文件时会明确报告未完成。

## 七、一键验证

前置工具：Python 3.10+、Java 21、Maven、Node 22（既有前端测试使用 strip-types）及已安装的前端依赖。运行前按部署目录配置本地依赖服务，脚本不会自动启动或清理容器。

Python 依赖需显式安装一次（上传类 E2E 用 `openpyxl` 生成 xlsx 夹具，缺它会在用例中途报 `ModuleNotFoundError`，看起来像用例坏了）：

```bash
python -m pip install -r tools/requirements-dev.txt
```

`tools/verify.py` 的 E2E 就绪检查会在 `openpyxl` 不可导入或 xlsx 夹具脚本缺失时记为未覆盖（退出码 `2`），不会静默跳过。

```powershell
.\verify.ps1           # 后端 + 类型门禁 + lint + 前端单测/构建 + E2E 就绪检查/执行
.\verify.ps1 backend   # 后端单测与 IT
.\verify.ps1 frontend  # 前端检查、构建与 E2E
.\verify.ps1 e2e       # 仅 E2E
python tools/test_verification.py
```

macOS / Linux 对应使用 `bash verify.sh [scope]`。两种入口共享 `tools/verify.py`。

E2E 需先启动既有 Playwright 配置对应的服务（默认前端 18081、后端 18080），提供本机账号脚本；F0 云场景还需临时管理员与普通员工 token。前置条件不足时明确列出未覆盖项，不自动供给正式角色。

退出码：`0` 表示所选范围完成且通过；`1` 表示执行失败；`2` 表示存在跳过或未覆盖。日志与 `summary.json` 写入 `.runtime/verify/<本次目录>/`；后端跳过数量读取本次 Surefire XML，E2E 读取本次 Playwright JSON，不复用旧报告。将实际输出和未覆盖项填写到 PR 模板。
