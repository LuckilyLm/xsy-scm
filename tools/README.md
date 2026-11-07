# 工具入口

从仓库根目录调用工具。开发、测试、构建、迁移和服务启动仍按 `AGENTS.md` 的验证授权规则执行；本页只说明入口，不表示已经运行。

| 功能 | 入口 | 职责 |
| --- | --- | --- |
| 分类图标配置 | `configure_category_icons.py` | 显式指定库与存储参数；默认输出计划，apply才发布原图、注册公开文件并补齐空分类，保留已有图标及商品归属 |
| 项目验证 | `verify.ps1`、`verify.sh` → `verify.py` | 统一协调质量、后端、管理后台、微信小程序和浏览器验证，报告通过、失败与未覆盖 |
| E2E 夹具 | `e2e_fixtures.py account-setup\|account-cleanup\|report\|product-create\|product-update\|stocktake` | 统一入口：创建/清理指定波次的隔离临时账号、准备报表日期与采购单、填充三类导入模板；各子命令只作用于自己的数据 |
| Java 格式 | Maven Spotless、`quality/eclipse-formatter.xml` | 按 4 空格缩进、120 列、续行 4 空格和 LF 格式化 SCM 生产源码 |
| 前端格式 | `xsy-scm-web` 的 `npm run format` / `format:check`、`.prettierrc.cjs` | 按 2 空格、150 列、单引号、LF 格式化源码、E2E、单测和配置 |
| 迁移校验 | `migration_checksum_guard.py`、`migration_checksum_snapshot.json` | 校验已应用 Flyway 文件的字节和版本清单 |
| 模块SQL清单 | `build_install_baseline.py check`、`install_baseline_manifest.json` | 校验24份最终模块SQL及旧库切换元数据，不重放历史V |
| 当前库结构核对 | `cutover_schema_baseline.py`、`schema_catalog.py` | 默认只读核验当前已知历史与结构；资料迁移采用新库导入路线，详见部署说明 |
| 前端类型基线 | `ts_baseline_ratchet.py` | 区分历史诊断与新增错误，SCM 源码错误要求为零 |
| 集成测试库重置 | `tools/reset-it-db.sh [库名]` | 只允许名字以 `_it` 结尾的库；清空前打印目标并等待 5 秒，开发库/演示库直接拒绝 |
| 质量守卫 | `quality/quality_guard.py`、`quality/scm_*_guard.py`、`quality/scm_error_code_registry.py` | 按不同规则检查 Java 质量、领域边界与错误码；门禁自测合集见 `quality/test_quality_tools.py` |

`quality/` 内的基线迁移与就绪校验面向明确的维护任务，不能当作日常验证入口。需要跑完整门禁时使用 `verify.py`，避免分别调用脚本后把局部结果当成全量通过。

E2E 账号示例：

```powershell
$env:XSY_PG_DB = '独立测试库名'
$env:W1_E2E_NAME = 'w1_e2e_唯一后缀'
$env:W1_E2E_PASSWORD = '仅测试环境使用的密码'
python tools/e2e_fixtures.py account-setup --wave w1
python tools/e2e_fixtures.py account-cleanup --wave w1
```

脚本只接受明确指定的数据库与带波次前缀的临时账号；实际测试库、容器和凭据由执行环境提供。

验证结果以入口生成的汇总报告与各测试报告为准，保留执行范围、失败与未覆盖项；项目文档不另行汇编逐次测试数字或本机运行记录。

`migration_checksum_guard.py`只扫描当前`db/migration/modules`，模块初始化清单随本次批准的基线保存。Flyway数值版本规范化保持一致（1000.01与1000.1相同），同号B/V可以共存，同类重复拒绝；后续已应用文件不能改写或改号。静态checksum一致不代表数据库已经安装。

旧历史保留在`legacy_migration_manifest.json`（仅版本、原文件名及checksum）与Git中。`schema_baseline_catalog.json`保存最终结构对象签名，包含约束、索引、函数与触发器，忽略业务行与序列当前位置；其完整性由模块manifest校验。普通静态check只依赖标准库，旧库目录检查和显式切换才需要安装`requirements-baseline.txt`。连接通过`SCM_MIGRATION_DSN`提供，不打印连接串；`--apply`还要求准确目标库名和已完成的备份文件。

`verify.py quality/backend`保留模块清单检查，quality协调对应工具回归；测试执行仍遵循仓库授权约定。历史SQL重新执行测试通过`LegacyMigrationSql`读取固定Git提交原文，需要本地Git及该历史对象，不将两百多份旧SQL重新复制为测试资源。
