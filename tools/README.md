# 工具入口

从仓库根目录调用工具。开发、测试、构建、迁移和服务启动仍按 `AGENTS.md` 的验证授权规则执行；本页只说明入口，不表示已经运行。

| 功能 | 入口 | 职责 |
| --- | --- | --- |
| 项目验证 | `verify.ps1`、`verify.sh` → `verify.py` | 统一编排质量、后端、管理后台、微信小程序和浏览器验证，报告通过、失败与未覆盖 |
| E2E 账号 | `e2e_accounts.py setup\|cleanup --wave w1…w9\|f0\|p1` | 创建/清理指定波次的隔离临时账号；原各波次薄封装已收敛至这一入口 |
| E2E 报表夹具 | `e2e_report_fixture.py <action> --wave w6` | 查找空闲报表日期、受限回填采购单时间、清理该夹具；与账号生命周期分离 |
| E2E Excel 夹具 | `patch_product_create_xlsx.py`、`patch_product_update_xlsx.py`、`fill_stocktake_template.py` | 对不同正式模板填值；独立保留，因为列契约与签名保护要求不同 |
| 迁移校验 | `migration_checksum_guard.py`、`migration_checksum_snapshot.json` | 校验已应用 Flyway 文件的字节和版本清单 |
| 前端类型基线 | `ts_baseline_ratchet.py` | 区分历史诊断与新增错误，SCM 源码错误要求为零 |
| 质量守卫 | `quality/quality_guard.py`、`quality/scm_*_guard.py`、`quality/scm_error_code_registry.py` | 按不同规则检查 Java 质量、领域边界与错误码；基线和契约用例位于 `quality/` |

`quality/` 内的包迁移、基线迁移与审计脚本面向明确的维护任务，不能当作日常验证入口。需要跑完整门禁时使用 `verify.py`，避免分别调用脚本后把局部结果当成全量通过。

E2E 账号示例：

```powershell
$env:XSY_V2_PG_DB = '独立测试库名'
$env:W1_E2E_NAME = 'w1_e2e_唯一后缀'
$env:W1_E2E_PASSWORD = '仅测试环境使用的密码'
python tools/e2e_accounts.py setup --wave w1
python tools/e2e_accounts.py cleanup --wave w1
```

脚本只接受明确指定的数据库与带波次前缀的临时账号；实际测试库、容器和凭据由执行环境提供。
