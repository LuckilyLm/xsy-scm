# 鲜蔬源 Windows 秤端

更新：2026-11-03，Codex。当前源码版本 `0.4.1`（见 `Xsy.Scale.Agent.csproj`）；本文说明当前源码的工程与接入方式。交付/验收边界见 [项目状态](../docs/status.md)。

## 工程与职责

- `src/Xsy.Scale.Agent/`：.NET 10 本机服务、设备适配、SQLite、同步与打印执行。
- `workstation/`：Vue 3 / TypeScript 触屏工作台，由 Agent 同源承载；界面约束见本模块 [DESIGN.md](DESIGN.md)。
- `mock-platform/`：独立演练替身，不是正式平台；`scripts/` 提供构建、发布、启动、导出与设备检查。
- 正式分拣、库存与财务仍由平台维护，设备入口为 `/scm/device/scale/v1`（V171/V172）。本地保存、平台受理、商品完成与物理打印是不同结果。

## 作业规则

1. 使用平台签发的员工授权引用获取任务并领取作业，不收集员工 Cookie，不自行建立平台身份。
2. 首页只有按商品分拣、按客户分拣两个入口，复用同一任务明细、每筐累计和完成记录；每个下级可明确返回所属父级。
3. 新业务按市斤（1 斤 = 500 克），`sj / 斤 / 市斤` 是数值不变的同单位别名。旧公斤记录与旧标签保留；不同单位不能只改标签混算。
4. 设备采集要求新鲜、可信、稳定的正 NET。不按固定 1–150kg 或 50g 倍数筛选，不对原值舍入，也不改变秤体承重或标定。
5. 手工录入填写其他大秤已扣皮净重，正数、最多 14 位整数/4 位小数；不依赖本机秤连接，仍需有效员工与领取授权，不伪造原始帧、稳定结论或独立皮重。
6. “记录上传并打印”和“记录上传”逐条选择；先本地原子落盘，再同步/打印。只上传不生成票或打印任务，显示“未要求打印”，不能补打。
7. 响应未知时保持原操作号与打印选择重试。完成商品保存两种来源的完整有序集合，不另采重量、不重复计重；补打只能新增打印尝试。

## 同步与标签

当前 `PlatformClient` 已调用 `/measurements` 和 `/items/{taskItemId}/complete`；`SyncWorker` 按持久命令派发，先每筐后集合完成，完成摘要取有序 `eventKeys`。服务端按每筐记录求和。

`WAITING_CONTRACT` 是现有本地命令的初始待发送状态；随后可为 `SENT / SYNC_RETRY / NEEDS_REVIEW`，表示待处理命令。只有正式平台受理回执才能说明同步成功。手工来源 `entrySource/entryNote` 的平台字段映射和语义仍须核对，不把本地字段存在当作已联调。

本地待同步凭条与平台正式包装标签分开：本地 eventKey 查询区不等于正式包装码；正式标签必须使用人工选来源批次后平台返回的包装码、批次与公开链接。补打保持保存内容，不用新配置回填旧票。

通过 Windows 指定打印驱动提交。`SUBMITTED_TO_SPOOLER` 仅表示系统受理，`UNKNOWN` 必须先核对实纸再人工补打；普通演练 `TEST_ONLY` 不出纸。

## 本地启动模式

| 模式 | 入口与地址 | 隔离边界 |
| --- | --- | --- |
| FIELD | 发布包 `Open-Workstation.cmd`，默认 `127.0.0.1:33600` | 正式配置与数据目录，设备/员工授权由平台提供 |
| LOCAL_TEST | `Start-LocalTest.cmd`，工作台 33610、控制页 33611 | 模拟平台/重量，不读真实串口、不出纸 |
| HARDWARE_TEST | `Start-HardwareTest.cmd`，工作台 33630、回环平台 33631 | 真实 RS232 与指定测试打印机，任务和标签明确非业务，不上传正式平台 |

三类数据目录禁止混用。状态目录由启动脚本按模式隔离，历史公斤测试记录保留在原目录，不与新市斤数据混算。结束使用对应 Stop 脚本，硬件结果按随包 `HardwareTest-README.txt` 导出。

现场复制 `appsettings.Local.example.json` 为程序旁的 `appsettings.Local.json`，设置平台地址、设备/工作台、协议、单位、端口与指定打印机，维护码由现场设置。配置优先级：`appsettings.json → appsettings.Local.json → XSY_环境变量 → 命令行`；设置落盘后重启统一生效。

串口应连续输出；不通过延长旧读数有效期掩盖单帧模式。软件 T/Z 需显式启用，发送后隔离旧帧 500ms，最多等 5 秒稳定零值，不自动重发、不软件造零。计量标定不属于此控制。

## 构建与发布入口

开发机需 .NET 10 SDK、Node.js 20+。以下命令仅在用户授权构建/验证时执行：

```powershell
./scripts/Build-Workstation.ps1
./verify.ps1
./scripts/Publish-Agent.ps1 -Runtime win-x64 -SkipWorkstationBuild
```

发布目录包含自包含程序、工作台、启动器和 [交接清单](FIRST-GENERATION-HANDOFF.md)。独立演练由 `scripts/Start-LocalTest.ps1` 启动；浏览器用例需明确配置隔离地址和产物目录，不对已有业务数据库重跑初始流程。

升级先在旧包执行 Stop 并导出，将新包完整解压至新目录；保留原 `DataDirectory`、`appsettings.Local.json`、未同步记录与旧包。保持同一 Windows 用户，避免 DPAPI/打印机权限变化；不同时运行新旧实例。

## 接口、交接与验收

- 唯一本地接口文档：[秤端本地接口文档](.codex-api-spec/秤端本地接口文档.md)；SQLite 增量说明保留在 `.codex-sql/`，不因历史日期移除。
- 现场步骤：[硬件联动说明](scripts/HardwareTest-README.txt)、[只读读秤说明](scripts/ScaleCheck-README.txt)、[独立标签说明](scripts/Label60x40-README.txt)。
- 平台组合联调与真实设备/打印尚需当前版本证据，见 [交接清单](FIRST-GENERATION-HANDOFF.md)。旧版读重或单张出纸不等于当前多筐、手输、连续打印及正式平台闭环通过。
- 默认不测试、构建或访问真实硬件。数据库、密钥、个人配置、构建产物 不入 Git。
