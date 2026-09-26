# Q1 迁包就绪（Quality Q0.1）

> 阶段：Quality Q0.1 — Package Migration Readiness
> 前置：QUALITY Q0 已完成（`b30d879b` 门禁 / `94bd00b2` 审计与整改基线）
> 本轮性质：只加固门禁，**不迁任何一个正式文件**
> 业务状态：F1-3B COMPLETE；F1-3C / F1-4 / F1-5 / F1-6 / F1-8 继续暂停

Q1 会把 `net.lab1024.sa.admin.module.scm.*` 迁到 `com.xsy.scm.*`。
迁包真正的风险不是门禁太严，而是**门禁静默停止看代码**：扫描范围只钉在旧包，迁完就报
「0 findings」；baseline 的 identity 全部指向已消失的路径，`check` 就报「全部改善」；
ArchUnit 的包名写错时在空类集上没有任何规则会失败。三者都显示绿色，而实际什么都没检查。

所以 Q0.1 的唯一目标，是让 Quality Guard / Checkstyle / ArchUnit / baseline 棘轮
在**新旧双包并存**的整个迁移过程中都仍然有效，并提供确定性的 baseline 路径迁移能力。

---

## 1. 双包扫描策略

### 1.1 Quality Guard

`tools/quality/quality_guard.py` 的扫描范围由单一路径常量改成包路径表：

```python
LEGACY_SCM_PACKAGE_PATH = Path("net/lab1024/sa/admin/module/scm")
NEW_SCM_PACKAGE_PATH    = Path("com/xsy/scm")
SCM_PACKAGE_PATHS = (LEGACY_SCM_PACKAGE_PATH, NEW_SCM_PACKAGE_PATH)
```

`package_roots(source_root)` 对 main / test 两个源根各展开两条路径，`load_sources(roots)`
按**解析后的绝对路径去重**，所以两条根路径将来即使嵌套也不会把同一文件计两次。

关键点不只是「两边都扫」，还有 **enum 词汇表必须由两侧生产源码一起构建**：
magic-string 规则判定的是「这个字面量是否等于某个已有 enum 常量」。如果词汇表只从旧包构建，
那么在 Q1 中途，一个已经迁进 `com.xsy.scm` 的 enum 就不在词汇表里，
所有仍写它的 Service 会**从 A 类（有 enum 却硬编码）掉到不报**——
门禁会在迁移进行中静默松掉，而这正是它最该盯住的时候。

`scm_metrics.py` 的度量范围同步改为两侧并集，域归属解析同时认
`/module/scm/` 与 `/com/xsy/scm/` 两种前缀。

### 1.2 防「空源码集 = PASS」

`collect()` 在生产源码集为空时抛 `ScanConfigurationError`，`main()` 把它变成
exit 2 并打印：

```text
SCM production source set is empty; quality scan is likely misconfigured.
```

`scan` / `check` / `capture` 三个模式一视同仁 —— `capture` 尤其重要，
否则一次配置错误就能把 baseline 写成空文件，之后所有 `check` 永久全绿。

这条不是「代码里存在一个 if」，而是被执行验证的：
`package_migration_readiness.py` 把 `SCM_PACKAGE_PATHS` 临时换成**只有新包**
（今天它是空的），然后要求 `collect()` 抛出该异常。见 §5。

### 1.3 Checkstyle

父 pom 的 includes 由一条变成两条模式，且**只**覆盖 SCM 自己的两个包：

```xml
<includes>**/module/scm/**/*.java,**/com/xsy/scm/**/*.java</includes>
```

`sourceDirectories` 已限定为 `sa-admin/src/main/java`，所以这里不能用
`src/main/java/**` 这种全树通配 —— 那会把 SmartAdmin 底座的债务一并灌进 SCM baseline，
让「871」这个数不再代表我们的账。

Checkstyle 仍然 `failOnViolation=false`：它是**报告器**，判定权在 guard。
把 Maven 侧改成 `true` 会让历史 871 条直接红掉构建，等于用另一种方式废掉棘轮。
新包里的新违规仍然会被阻断，路径是
`checkstyle-result.xml → quality_guard(checkstyle family) → NEW/GROWN 判定`，
这条链由 §5 的坏探针实证。

### 1.4 ArchUnit

`@AnalyzeClasses` 的 `packages` 改成引用两个常量（注解里写常量、常量里写值，
是为了让「分析了两边」这件事既可断言又可复用）：

```java
@AnalyzeClasses(
        packages = {ScmArchitectureTest.LEGACY_SCM_PACKAGE, ScmArchitectureTest.XSY_SCM_PACKAGE},
        importOptions = ImportOption.DoNotIncludeTests.class)
```

只改成 `com.xsy.scm` 是错的：Q1 期间两边并存，只分析新包意味着所有未迁的域不再受约束。

新增一条**防空扫描**的断言 `importedSourceSetIsNotEmpty(JavaClasses)`：
抓到 0 个 SCM 类就失败。它的价值在于把「注解写对了」从假设变成被检查的事实 ——
包名一旦拼错，其余 7 条规则会在空集上全体通过。
旧包归零后它仍然成立（只看两边之和），所以不需要在 Q1 结束时删掉。

`financeDoesNotDependOnOtherDomains` 的历史例外**继续精确到类**
（`belongToAnyOf(OrderIdempotencyService.class)`）。改成排除 `..finance..` 整域
等于让这条规则对财务域永久失效，而它是这条规则唯一需要例外的地方。

### 1.5 迁移进度可见指标

`legacy-scm-package` 仍是唯一被门禁的包数规则（旧包文件数只降不升）。
新包一侧**不设门禁**，改为 scan/check 末尾的报表：

```text
--- Q1 package migration progress (report only) ---
  main  legacy    692
  main  new         0
  test  legacy    155
  test  new         0
```

不设门禁的理由：「新包必须增长」不是长期质量规则 —— Q1 结束后新包数就等于总数、旧包数为 0，
把它永久钉成断言只会在之后每次改动里误报。真正需要的是迁移期间能同时看到
OLD 降、NEW 升，因为「只改了一边」或「复制而非移动」在其他任何规则眼里都是干净的。

---

## 2. Baseline 路径迁移策略

### 2.1 问题

baseline identity 是 `rule<TAB>path<TAB>locator`。`git mv` 之后 defect 本身没变，变的只是地址：
旧 identity 消失 → 报 IMPROVEMENT；新路径上同一条 → 报 NEW DEFECT。
一次纯改名就会同时「改善」和「新增」，把 Q1 卡在门禁上。

### 2.2 解法：只做前缀 1:1 替换

`tools/quality/migrate_baseline_paths.py` 只重写 path 字段，**绝不重扫源码**。

这个限制就是它的意义：`capture` 在迁包后重扫会把所有「当前 findings」写进 baseline，
其中也包括迁移过程中真正新写进来的 magic string、坏命名、裸权限、阶段注释 ——
一次重扫会把它们全洗进账本。确定性的路径重写做不到这件事：那些 identity 是新出现的，
`check` 照样会拦。

安全性靠断言而不是靠小心：

| 属性 | 处理 |
| --- | --- |
| rule / locator / occurrence / family 变动 | 视为非法行，直接 FAIL |
| 两条旧 identity 折叠成同一条新 identity | FAIL（会静默抹掉记录在案的债务） |
| 目标 path 已存在于 baseline | FAIL（collision） |
| occurrence 总数迁移前后不等 | FAIL |
| identity 总数迁移前后不等 | FAIL |
| 格式非法的行 | 收集并 FAIL，且不丢弃原文 |
| 默认模式 | dry-run，`--apply` 才写；不 ok 时拒绝写盘 |

最典型的误配（把 main 和 test 两个前缀指向同一个目标）会被折叠检测抓到 —— 这是唯一
能真正造成静默丢债的情形，所以专门写成一条测试。

### 2.3 Q1 的正确用法

```bash
python tools/quality/migrate_baseline_paths.py --dry-run     # 先看
python tools/quality/migrate_baseline_paths.py --apply       # 迁包那次 commit 里执行
python tools/quality/quality_guard.py check --checkstyle     # 必须仍然 PASS 且 +0
```

禁止用 `capture --allow-growth` 作为迁包手段，也禁止「迁完重建 baseline」：
那等于放弃 Q0 的全部账本。

---

## 3. Q1 执行顺序建议

1. 先落 `AdminApplication.COMPONENT_SCAN` → `{net.lab1024.sa, com.xsy}` 双根
   （`@ComponentScan` 与 `@MapperScan` **都要**改，只改前者 Mapper 会静默注不进），
   验证一次启动与全量后端测试。此时新包还不存在，双根无副作用。
2. 按域整体移动，一域一次 commit，顺序建议 `common → warehouse → product → supplier →
   customer → pricing → order → purchase → inventory → sorting → delivery → finance →
   report → screen → dashboard`。每域结束后跑：`verify.py quality` + 该域定向 IT。
3. 每次移动的同时，对 baseline 做一次 `migrate_baseline_paths.py --apply`，
   然后 `check` 必须 `+0`。
4. 旧包文件数归零后：删除 `legacy-scm-package` family 及其 baseline、
   收缩 `SCM_PACKAGE_PATHS` 为单条、去掉 pom 的旧 includes、
   启用 ArchUnit 的 `com.xsy.scm..` 正向规则，并同步 readiness 脚本的断言。
5. 迁包 commit 与 formatter commit 必须分开（计划 §17 Q1）。

---

## 4. Spotless ratchet 的实测与选择

`ratchetFrom` 的语义是「只看相对该 ref 发生变更的文件」。本轮实测（不是推断）：

| 条件 | Spotless 实际处理数 |
| --- | --- |
| ref = `HEAD`，工作树有 3 个已修改文件 | **3 个**（`keeping 3 files clean`） |
| ref = `HEAD~2` | 同上，且日志显示 `3 were skipped because caching determined they were already clean` |
| ref = `origin/main`，且 `origin/main == HEAD`、工作树干净 | **0 个** —— 此时才是 vacuous PASS |

两个先前设想都不成立，必须按实测修正：

1. **`ref == HEAD` 并不等于「什么都不检查」。** ratchet 比较的是 ref 与**工作树**，
   所以本地未提交的改动照样在范围内。真正空转的只有「工作树干净 + ref 就是当前 commit」这一种，
   而它恰好就是 **merge 之后的 CI 状态** —— 那才是需要防的假绿。
2. **Spotless 自己的汇总行不能当覆盖率信号。** 它有增量缓存（`target/` 下的 index），
   第二次跑同一批文件会报「skipped because caching determined they were already clean」。
   也就是说「keeping N files」的 N 会受缓存影响，不保证等于本轮真正被判定过的文件数。

因此 `verify.py quality` 的 sanity check **自己用 `git diff --name-only <ref>` 数文件**，
不看 Spotless 的输出：

```text
[spotless-coverage] ref=origin/main changed-files=3 ref_equals_HEAD=False
```

N 为 0 时写入 `incomplete`，退出码变 2（verify.py 里 2 的既有语义正是「验证不完整」）。

**选择：warning + 记 INCOMPLETE，不 fail-fast。** 理由：干净树上增量格式化器确实无事可做，
判失败是错的；但也不能让它被读成「质量已完整检查」。
覆盖率损失的范围有限且明确：Checkstyle、quality guard、ArchUnit 都是**全树扫描 + baseline 比对**，
与 git ref 无关，失去的只有 Spotless 那条行尾空白/末尾换行的重写检查。

未来 CI 更稳的做法是显式传 PR 的 merge-base / 目标分支 SHA
（`-Dquality.ratchet.from=<sha>`，属性已在父 pom 里留出），
而不是硬编码一个永不更新的历史 commit。当前仓库还没有 CI 工作流配置，所以本轮只落地
「可见 + 不假装完整」这一层，并把 CI 传参写进待办。

---

## 5. 机器验证的就绪契约

`tools/quality/package_migration_readiness.py`（已接入 `verify.py quality`）不是文档清单，
而是逐条执行：

| # | 检查 | 方式 |
| --- | --- | --- |
| 1 | Guard 双包 | 断言 main/test 各展开两条根、且两侧都在表内 |
| 2 | 空集必须失败 | **执行**：临时把范围收成只有新包，要求 `collect()` 抛 `ScanConfigurationError` |
| 3 | Checkstyle 双包 | 解析父 pom includes，要求含 `com/xsy/scm`，且**拒绝**全树通配写法 |
| 4 | ArchUnit 双包 + 非空 | 注解引用两个常量、常量值正确、存在空扫描断言 |
| 5 | Finance 例外未扩大 | 必须仍是 `belongToAnyOf(OrderIdempotencyService.class)`；出现按包排除即失败 |
| 6 | baseline 迁移工具 | 工具与自测都在，两条映射各自命中且只命中一个源根 |
| 7 | `.editorconfig` 对 md 安全 | **按 editorconfig 语义求值** `*.md` 的有效值（后段覆盖前段），要求 trim=false 且 charset/EOL/final-newline 仍在，同时确认 Java 侧没被顺手放宽 |
| 8 | 盘点 IT 已脱离共享仓库 | IT 内不得再出现 `seedWarehouseId()`，必须有 `fixtureWarehouseId` 覆盖 + `newWarehouse(`，基类仍 `@Transactional` |
| 9 | 探针证据 | 本文存在；`--with-probe` 可复现 |

`--with-probe` 额外做两件事，因为「坏探针触发了规则」并不等于「扫描器看见了新路径」：

- **GoodProbe**：在 `com.xsy.scm.qualityprobe` 放一个**完全合规**的类，
  要求新包 main 文件数 `before → before+1 → before`，且该文件不产生任何 finding；
- **BadQualityProbe**：同目录放一个含裸角色名字段 `dao`、`"ENABLED"` 硬编码、
  wildcard import 与阶段注释的类，跑 Checkstyle 后要求 guard 报告该路径上有规则命中。

两个探针都在 `finally` 里删除，并回收自己创建的空目录 —— 留下空的 `com/xsy/scm`
会让后续 `git status` 看起来像迁移开了一半头。探针与其 baseline 一律不进 commit。

---

## 6. 盘点 IT 的夹具隔离

`ScmStocktakeImportPgIT` 曾在 Q0 全量中出现一次 `expected SNAPSHOT_STALE / actual CREDENTIAL_INVALID`，
单跑通过、之后难以复现。本轮不复述根因结论，只消除那条**共享状态耦合**。

耦合的物理原因在链路本身：快照凭证要为**仓库里每条活跃余额**带上
skuCode / 单位 / 账面量，并且逐行写进模板单元格，而 POI 单元格上限 32,767 字符
（`StocktakeSnapshotSigner#sign` 正是为此加了一层 DEFLATE）。
夹具用的是 V15 播种的 `WH001`，整张表规模由别的测试往同一仓库里塞了多少余额行决定 ——
签名长度因而不归本用例管。

改法是给基类一个可覆盖点，而不是削弱校验：

```java
// ScmW5PgITBase：默认仍是播种仓库，对既有测试行为不变
protected Long fixtureWarehouseId() { return seedWarehouseId(); }

// ScmStocktakeImportPgIT：本类独占
@Override protected Long fixtureWarehouseId() {
    if (exclusiveWarehouseId == null) {
        exclusiveWarehouseId = newWarehouse("stocktake-import");
    }
    return exclusiveWarehouseId;
}
```

`fixtureWarehouseId()` 替换的是**夹具方法内部**的四处分仓库点（需求生成、采购单、
分拣任务、订单表单），测试类自己直接按播种仓库断言的地方一律不动，
所以除本类以外所有 IT 的数据完全不变。

满足的边界：独占仓库是真实数据库行（走 `WarehouseService#create`）；
生产代码一行未改；凭证校验、`SNAPSHOT_STALE` 断言、签名与压缩路径全部原样；
没有 mock、没有 skip、没有 truncate；基类 `@Transactional` 逐方法回滚，
JUnit 5 逐方法新建实例，因此 `exclusiveWarehouseId` 每个用例重新求值。

顺带修正了该类 Javadoc 里「共享种子仓库还带着其它历史余额行」这段**已经成为历史**的原因说明。

---

## 7. 本轮实测证据

全部在 `main @ 94bd00b2` + 本轮工作区改动上执行。

### 7.1 探针（`package_migration_readiness.py --with-probe`）

| 探针 | 结果 |
| --- | --- |
| GoodProbe（新包内的合规类） | 新包 main 文件数 `0 → 1 → 0`，且该类**不产生任何 finding** → 扫描器确实在枚举 `com/xsy/scm` |
| BadQualityProbe（新包内） | 触发规则族 `['checkstyle', 'generic-dependency-field', 'magic-string-domain-literal', 'stage-comment']` |
| BadLayerProbe（`com.xsy.scm.common.probe` 依赖 `product.dao`） | `ScmArchitectureTest` **exit=1**，命中 `commonDoesNotDependOnConcreteDomains` → ArchUnit 也在分析新包 |
| 探针残留 | 源码 `com/` 文件 0、`target/classes/com` 文件 0、`git status` 无 Probe → 探针与其 class 都不进 commit |
| 探针后复跑 ArchUnit | 8 tests / 0 failures / 0 errors → 没有陈旧 class 污染 |

BadProbe 覆盖的是 guard + Checkstyle + ArchUnit 三个门禁；权限字面量那一条按 §22-B 的许可
用「另一条可被 guard 检测的新 defect」（阶段注释 + magic string）替代，
因为为探针专门建一个 Controller 会引入本轮不该有的 HTTP 端点。

清理时**必须连 `target/classes/com` 一起删**：ArchUnit 读编译产物，
只删源码会把 `BadLayerProbe.class` 留在 classpath 上，
下一次跑 `ScmArchitectureTest` 会因为这条陈旧 class 继续失败，看起来像规则不稳定。

### 7.2 门禁与后端

```text
python tools/verify.py quality                     RESULT: PASS (exit 0)
  checkstyle-report      exit 0
  spotless-check         exit 0
  quality-guard check    exit 0（6 个 family 全部 +0）
  baseline-migration-selftest  exit 0（10 tests）
  package-migration-readiness  exit 0（9 项）

python tools/quality/quality_guard.py check --checkstyle
  generic-dependency-field    current 71   baseline 71   (+0)
  magic-string-domain-literal current 191  baseline 191  (+0)
  raw-permission-literal      current 285  baseline 285  (+0)
  stage-comment               current 695  baseline 695  (+0)
  legacy-scm-package          current 847  baseline 847  (+0)
  checkstyle                  current 871  baseline 871  (+0)
  Q1 progress: main legacy 692 / main new 0 / test legacy 155 / test new 0

python tools/migration_checksum_guard.py check     PASS（missing 0 / renamed 0 / unbaked 0）
```

**本轮没有触碰 `db/migration` 与 baseline 内容**：六个 family 的 current 与 baseline 全部相等，
双包改造只是把「同一批债务」换个扫描范围重新数了一遍，没有任何一条被洗掉或放宽。

### 7.3 baseline 路径迁移 dry-run 演练（§30）

```text
python tools/quality/migrate_baseline_paths.py --dry-run
  files                : 6
  identities before    : 1184
  identities after     : 1184
  occurrences before   : 2960
  occurrences after    : 2960
  rewritten records    : 1182
  unchanged records    : 2
  collisions           : 0
  collapsed identities : 0
  invalid lines        : 0
RESULT: PASS
```

`unchanged records = 2` 是 `legacy-scm-package` 的两条**源根聚合记录**：
它们的 path 是一个目录地址、不是被迁移的源文件，Q1 结束后整个 family 退役，
所以它们本来就不该被前缀重写。这个数字同时也是「工具没有误伤别的记录」的证据。

自测 10 条覆盖：只改 path 字段、occurrence 不被重算、注释行原样透传、
前缀边界精确匹配、两条旧 identity 折叠必须 FAIL、目标已存在必须 FAIL、
非法行必须报出且不丢行。

### 7.4 盘点 IT 稳定性

```text
ScmStocktakeImportPgIT 连续 20 次（每次独立 mvn 调用、独立 Spring 上下文、独立库事务）
  run 1..20  全部 OK   Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
  ===== repeats: 20 ok / 0 failed =====
```

### 7.5 一次「全量偶发红」的定位过程与结论

第一次全量后端（含本轮改动）：1193 tests / 0 failures / **1 error**，
`PurchaseDemandSummaryPreviewIT.aggregatesMultipleOrdersOfSameSku` 抛
`INVENTORY_RESERVATION_INVALID`。

取证顺序（没有靠猜，也没有动测试）：

1. **单跑该类** → 11 tests / 0 failures / 0 errors。与 §7.4 之前那次盘点用例的表现同形。
2. **读抛点** → `InventoryReservationService.reserve` 第 102 行
   `if (reservationDao.insertOnConflictDoNothing(entity) != 1) throw ...`，
   冲突目标是部分唯一索引 `uk_inventory_reservation_source_active`，
   语义是「这条来源订单行已经有一条 ACTIVE 预留」。
3. **查开发库** → `inventory_reservation` 里有 **142 条 ACTIVE / 115 条 RELEASED**。
   长跑共享开发库累积了此前中断运行留下的、已提交的预留行。
4. **决定性验证**：新建一次性干净库（Flyway 自 V1 应用整条链），
   把 `XSY_V2_DB_URL` 指过去重跑全量 →
   **1201 tests / 0 failures / 0 errors / 5 skipped（既有云端门控）**，
   随后 `DROP DATABASE`。

结论：那是**环境数据残留**，不是本轮改动引入的回归，也不是 `PurchaseDemandSummaryPreviewIT`
自身的顺序耦合。本轮没有修改该测试、没有扩大 skip、没有弱化任何断言。
这条排查配方（以及「脚本改文件会静默改行尾、被 Spotless 抓到」那次）已单独记入项目记忆。

> 顺带被这次运行纠正的一处推断错误：`verify.py` 早先那次「1200 tests / 1 failure」的
> 盘点用例失败，当时我按「凭证触顶」假说写进了审计报告 §11.2。本轮把夹具改成独占仓库后
> 20 次全绿，但那**不构成对原根因的证明** —— 它只证明该用例不再受共享数据影响。
> §11.2 已按这个口径改写，不再把假说写成结论。
