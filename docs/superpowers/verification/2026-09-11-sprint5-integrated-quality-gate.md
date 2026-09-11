# Sprint 5 集成质量门禁验证报告

## 1. 验证范围与结论

- 目标分支：`feature/sprint5-quality-gate`
- 集成基线：`feature/sprint5`
- 验证提交树：`78666e69cdde6c02a46f142dfacdcc3c714a9054`（`merge: integrate Sprint 5 mall client`）
- 验证日期：2026-09-11
- 执行时间：约 17:49–17:55（UTC+08:00）
- 结论：**静态检查、单元/集成测试和构建门禁通过；浏览器 E2E 因后端 `127.0.0.1:8080` 未启动而阻塞。因此 Sprint 5 完整质量门禁未全部达到，不能将 E2E 标记为通过。**

## 2. 分支与工作区前置检查

目标分支原 `HEAD` 为 `0f2e7fd73c44d4e6ea8b68ffc5dfc543b7ef2c02`，`feature/sprint5` 为 `78666e69cdde6c02a46f142dfacdcc3c714a9054`。祖先检查成功，目标分支可安全快进。

目标 worktree 的暂存区已完整重现 `78666e6` 的树（`git diff --cached --quiet 78666e6` 退出码 0）；没有强推或历史重写。本次验证在独立 worktree `D:\DevCaches\Codex\2026-09-11\xsy-scm-sprint5-quality-gate` 中执行，未切换主工作区的 `feature/sprint5`。

开始前已阅读：

- `AGENTS.md`
- `docs/superpowers/plans/2026-09-11-next-step-reuse-and-sprint5-plan.md`
- `docs/superpowers/plans/2026-09-11-sprint5-price-center-spec-plan.md`
- `docs/superpowers/specs/2026-09-11-sprint5-price-center.md`

## 3. 环境

| 项目 | 版本/状态 |
| --- | --- |
| OS | Windows 11 Home China 10.0.26100, amd64 |
| JDK 21 | Microsoft OpenJDK `21.0.12.1` |
| Maven | Apache Maven `3.9.16` |
| Node.js | `v22.21.1` |
| npm | `10.9.4` |
| 后端地址 | `http://127.0.0.1:8080` 不可访问 |

说明：系统默认 `java` 为 Oracle JDK 17；后端 Maven 命令显式使用 `D:/Java/JDK21`。编译日志确认使用 `release 21`。

## 4. 后端验证（xsy-scm-server）

| 命令 | 退出码 | 结果 | 数量/证据 |
| --- | ---: | --- | --- |
| `JAVA_HOME=D:/Java/JDK21 PATH=D:/Java/JDK21/bin:$PATH mvn.cmd test` | 0 | 成功 | Tests run: **370**, Failures: **0**, Errors: **0**, Skipped: **0**；BUILD SUCCESS；2:09 min |
| `JAVA_HOME=D:/Java/JDK21 PATH=D:/Java/JDK21/bin:$PATH mvn.cmd -DskipTests package` | 0 | 成功 | Tests skipped；生成 `target/xsy-scm-server-0.1.0-SNAPSHOT.jar`；BUILD SUCCESS；10.195 s |

### FlywayMigrationIT

`mvn test` 实际执行了 `com.xianshuyuan.scm.migration.FlywayMigrationIT`：

- Tests run: **29**
- Failures: **0**
- Errors: **0**
- Skipped: **0**
- 结果：**通过**

### 后端警告

测试日志包含非阻塞警告，包括：

- Spring Security 检测到显式 `AuthenticationProvider`，不会自动使用 `UserDetailsService`；
- SpringDoc API/Swagger UI 默认启用的生产配置提醒；
- 部分测试刻意触发的会话失效失败、数据库约束拒绝等 WARN；对应测试均通过。

## 5. 后台 Web 验证（xsy-scm-web）

| 命令 | 退出码 | 结果 | 数量/证据 |
| --- | ---: | --- | --- |
| `npm run lint` | 0 | 成功 | ESLint 无错误 |
| `npm run typecheck` | 0 | 成功 | `tsc -b` 无错误 |
| `npm run test` | 0 | 成功 | Test Files: **14 passed (14)**；Tests: **39 passed (39)**；Duration: **604.64 s** |
| `npm run build` | 0 | 成功（有警告） | Vite 8.2.2；9992 modules；built in 1m 1s |

### Web 警告

- Vite 报告部分压缩后 chunk 超过 500 kB；最大列出的 `index-*.js` 为 **541.49 kB**（gzip **171.75 kB**）。
- 该警告不影响构建退出码，但应作为后续按需拆包的性能风险跟踪。

### Vitest 持续时间

Vitest 最终正常输出并退出 0，未发生无最终输出的挂起。完整运行约 10 分钟，主要时间记录为 import 2754.56 s（跨 worker 累计）、environment 532.72 s、tests 51.14 s。

## 6. 小程序验证（xsy-scm-miniapp）

| 命令 | 退出码 | 结果 | 数量/证据 |
| --- | ---: | --- | --- |
| `npm run lint` | 0 | 成功 | ESLint 无错误 |
| `npm run type-check` | 0 | 成功 | `tsc --noEmit` 无错误 |
| `npm run build:weapp` | 0 | 成功 | Taro 4.2.1；Webpack compiled successfully in 1.03m |
| `npm run build:h5` | 0 | 成功（有警告） | Taro 4.2.1；Webpack compiled successfully in 1.35m |

### 小程序 H5 警告和包体积

- Webpack deprecation warning：模板路径中的 `[hash]` 已弃用，建议使用 `[fullhash]`/`[chunkhash]`/`[contenthash]`。
- `EntrypointsOverSizeLimitWarning`：推荐上限 244 KiB，H5 `app` entrypoint 为 **323 KiB**：
  - `js/623.b4a0cfaa.js`: 103 KiB
  - `css/app.css`: 2.5 KiB
  - `js/app.b4a0cfaa.js`: 217 KiB
- H5 JavaScript assets 总计 **1.46 MiB**，CSS assets 总计 **26.6 KiB**。
- 上述均为非阻塞警告；构建退出码为 0。

## 7. 浏览器/E2E

### 后端可达性检查

命令：

```text
curl.exe -sS -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/actuator/health
```

- 退出码：**7**
- HTTP code：`000`
- 错误：`Failed to connect to 127.0.0.1:8080 ... Could not connect to server`

### E2E 执行结论

- 状态：**未执行，环境阻塞**
- 原因：项目 Playwright 配置只启动 Vite 前端，要求 PostgreSQL 与后端预先运行；本机 `127.0.0.1:8080` 不可访问。
- 未擅自启动或修改数据库、服务配置、测试环境或生产数据。
- 现有主链路规格文件：
  - `e2e/product-flow.spec.ts`
  - `e2e/sprint2-order-pricing.spec.ts`
  - `e2e/sprint2-after-sales.spec.ts`
- 未发现已检入的 Sprint 5 商城关键主链路 Playwright spec；因此即使环境恢复，现有 E2E 也不能自动等同于 Sprint 5 商城全链路覆盖。

## 8. 成功、失败、警告与阻塞汇总

### 成功

- 后端完整 Maven 测试：370/370 通过。
- `FlywayMigrationIT`：29/29 通过。
- 后端跳过测试打包成功。
- Web lint、typecheck、39 个 Vitest 测试及生产构建成功。
- 小程序 lint、type-check、微信小程序构建和 H5 构建成功。

### 失败

- 无代码检查、测试或构建失败。

### 警告

- Web 存在超过 500 kB 的压缩 chunk。
- 小程序 H5 app entrypoint 323 KiB，超过推荐 244 KiB。
- 小程序 H5 构建存在 Webpack `[hash]` 弃用警告。
- 后端测试日志存在 Spring Security/SpringDoc 和测试场景相关 WARN，但测试均通过。

### 阻塞/未执行

- Playwright E2E 未执行：后端 `127.0.0.1:8080` 连接拒绝（curl 退出码 7）。
- Sprint 5 商城关键主链路 Playwright spec 未在当前仓库中发现。

## 9. 质量门禁判定

**判定：部分达到，不满足“完整 Sprint 5 质量门禁通过”。**

理由：编译、静态检查、后端完整测试、Flyway 迁移测试、Web 测试和所有构建均通过，代码级门禁结果良好；但浏览器 E2E 因依赖服务未启动而未执行，且当前仓库没有明确的 Sprint 5 商城 Playwright 主链路规格。按照“不虚报测试通过”的要求，必须将其保留为环境与覆盖阻塞项。
