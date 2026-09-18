## 改了什么

<!-- 一句话说清行为变化。重构类改动请注明「行为等价」以及用什么证明。 -->

## 涉及的不变量

<!-- 勾选本次触碰到的，并在下方说明如何保证。未触碰的项不必勾选。 -->

- [ ] 幂等（`IdempotencyService.claim` / `complete` / `replay`）
- [ ] 锁序 P12（`FOR UPDATE` 加锁顺序、批量按 id 升序）
- [ ] 迁移不可变性 / 版本号唯一连续
- [ ] 无外键原则（关系完整性由服务层 + 唯一约束 + CHECK 保证）
- [ ] 库存 Q13 单位不变量 / Q7 只增账本
- [ ] 事务边界（`@Transactional` 只在编排层）
- [ ] 状态机（`PurchaseOrderStateMachine` 等，而非裸写 `setStatus`）
- [ ] F0 文件访问权限（含 F0-DEBT-01）
- [ ] 以上均不涉及

**如何保证**：

## 验证证据

<!--
  要求：粘贴实际命令与实际输出。不接受「已验证」「测试通过」「本地跑过」这类结论性描述。
  目的不是追责，而是让「没跑」或「没跑全」在评审阶段就被看见。

  `mvn test` 已显式包含 *IT.java；请记录 Surefire 实际执行和跳过数量。
-->

| 验证项 | 命令 | 实际结果 |
| --- | --- | --- |
| 后端（单测 + 集成测试） | `mvn -pl sa-admin -am test` | `Tests run: ___ , Failures: ___ , Errors: ___ , Skipped: ___` |
| 前端类型门禁（含 vue-tsc） | `python tools/ts_baseline_ratchet.py check` | SCM 零错误、无新增错误；不代表上游全量 typecheck 为零 |
| 前端 lint | `npm run lint` | |
| 前端单测 | `npm run test` | |
| 前端构建 | `npm run build` | |
| 端到端 | `npx playwright test` | |

<!--
  提示：`mvn test` 会同时执行 *Test.java 与 *IT.java。若输出里只有 *Test 类，
  说明集成测试又被静默跳过了 —— 那是本次改动未验证，不是验证通过。
  也可以直接跑 ./verify.sh（Windows：.\verify.ps1）。
-->

**未覆盖到的部分**（必填；确实没有就写「无」）：

## 风险与回滚

- 风险：
- 回滚方式：
- 是否需要数据修复或新迁移：

## 评审人确认

- [ ] 我能在不看代码的情况下，复述出本次改动的业务意图
- [ ] 我确认上方「验证证据」是实际输出，而非结论性描述
