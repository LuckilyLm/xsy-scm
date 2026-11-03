# Windows 秤端协作规则

本模块规则补充根 `AGENTS.md`，不保留历史任务的分支、推送授权或测试统计。工程入口见 [README.md](README.md)，当前交付状态只维护根 `docs/status.md`。

## 结构与边界

- `src/Xsy.Scale.Agent` 为 .NET 10 本机服务；`workstation` 为独立 Vue 3 触屏界面，`mock-platform` 只作隔离演练，`scripts` 负责包装、启动及现场检查。
- 正式任务、权限、实发量、库存与财务由平台维护；本机先 SQLite 落盘，平台设备入口 `/scm/device/scale/v1`。不复制后台业务状态机。
- 触屏设计遵循本目录 `DESIGN.md`，与根目录管理后台规范分开；本机接口唯一文档在 `.codex-api-spec/秤端本地接口文档.md`，SQLite 增量说明在 `.codex-sql/`。

## 稳定约束

- 市斤业务中 `sj / 斤 / 市斤` 同单位数值不变；旧公斤记录与冻结票不重写。设备采集必须新鲜、可信、稳定、正 NET，不恢复固定 1–150kg 或分度倍数过滤，不做隐式单位换算。
- 人工净重独立入口，最多 14 位整数/4 位小数，确认已扣皮；不依赖秤连接，仍受员工与领取租约约束，不伪造原帧、独立皮重或稳定结论。
- 每筐事实、命令、打印选择同事务落盘；未知结果固定原 operationId/eventKey/请求摘要。PrintRequested 为 null 时序列化忽略，保留旧请求摘要；false 不建票、状态 NOT_REQUESTED、不能补打。
- 累计按员工会话、作业、任务与明细隔离，不把平台缓存量再加一次。完成冻结有序事件集合；补打只追加打印尝试，不新增重量。
- 当前 SyncWorker 已派发 measurements 与 item complete。WAITING_CONTRACT 是初始队列态，不等于缺发送器；成功回执、重试、人工复核分开。完成摘要基于有序 eventKeys，不能用完整请求摘要替代。
- 平台正式标签只取平台发码结果，本地 eventKey 不是包装身份。来源与批次是独立字段；手工字段是否完整传到平台须按真实契约核对。
- 本机只回环监听；工作台同源 HttpOnly 会话，不向页面暴露 X-Agent-Key 或串口原始帧。平台员工身份来自已签发引用。
- 软件 T/Z 发送单字节、不附 CRLF；清理旧帧后隔离 500ms，等待最多 5 秒稳定零值。未知不自动重发，不软件造零、不操作标定。
- 打印 SUBMITTED_TO_SPOOLER 不等于实纸，UNKNOWN 先核对再人工补打；普通演练 TEST_ONLY 不发纸。
- FIELD、LOCAL_TEST、HARDWARE_TEST 目录隔离；保留历史数据、凭据和 DPAPI 所属用户。不清队列、不杀无关进程、不为自动验证操作办公真实串口/打印机。

## 交付

- 默认不运行测试、构建、硬件或平台操作，遵循用户明确授权；实际验证范围与未覆盖内容分开报告。
- `FIRST-GENERATION-HANDOFF.md` 为发布脚本依赖，必须保留；升级先停旧包、保留数据与配置，新包完整解压到独立目录。
- 不提交 node_modules、bin/obj、dist/wwwroot、发布包、业务 SQLite、凭据与个人配置。项目文档只维护稳定规则；个人环境与逐次测试结果保留在对应验证记录。
