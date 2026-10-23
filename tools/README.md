# 工具入口

从仓库根目录调用工具。开发、测试、构建、迁移和服务启动仍按 `AGENTS.md` 的验证授权规则执行；本页只说明入口，不表示已经运行。

| 功能 | 入口 | 职责 |
| --- | --- | --- |
| 项目验证 | `verify.ps1`、`verify.sh` → `verify.py` | 统一编排质量、后端、管理后台、微信小程序和浏览器验证，报告通过、失败与未覆盖 |
| E2E 夹具 | `e2e_fixtures.py account-setup\|account-cleanup\|report\|product-create\|product-update\|stocktake` | 统一入口：创建/清理指定波次的隔离临时账号、准备报表日期与采购单、填充三类导入模板；各子命令只作用于自己的数据 |
| 迁移校验 | `migration_checksum_guard.py`、`migration_checksum_snapshot.json` | 校验已应用 Flyway 文件的字节和版本清单 |
| 前端类型基线 | `ts_baseline_ratchet.py` | 区分历史诊断与新增错误，SCM 源码错误要求为零 |
| 质量守卫 | `quality/quality_guard.py`、`quality/scm_*_guard.py`、`quality/scm_error_code_registry.py` | 按不同规则检查 Java 质量、领域边界与错误码；门禁自测合集见 `quality/test_quality_tools.py` |

`quality/` 内的基线迁移与就绪校验面向明确的维护任务，不能当作日常验证入口。需要跑完整门禁时使用 `verify.py`，避免分别调用脚本后把局部结果当成全量通过。

E2E 账号示例：

```powershell
$env:XSY_V2_PG_DB = '独立测试库名'
$env:W1_E2E_NAME = 'w1_e2e_唯一后缀'
$env:W1_E2E_PASSWORD = '仅测试环境使用的密码'
python tools/e2e_fixtures.py account-setup --wave w1
python tools/e2e_fixtures.py account-cleanup --wave w1
```

脚本只接受明确指定的数据库与带波次前缀的临时账号；实际测试库、容器和凭据由执行环境提供。
