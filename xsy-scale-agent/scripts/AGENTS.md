# 秤端启动与设备检查脚本

- 2026-10-29 0.4.1：用户报告称重恢复，明确只改按钮业务。称重主按钮“记录上传并打印”，下面“记录上传”；手工弹窗同选。CaptureRequest/ManualBasketRequest追加可空PrintRequested，null序列化忽略以保留0.4.0原请求摘要；false参与摘要并落NOT_REQUESTED，不建票/不调用票工厂，与打印记录共用累计和完成。未知结果冻结原号及选择，禁用另一路；无票记录不补打。正式队列仍WAITING_CONTRACT，不称上传成功。保留0.4.0市斤状态目录，打印预检仅警告、不清队列。85/85、构建/发布EXE HTTP、五组浏览器通过；未做新版真实出纸或正式平台联调，无提交推送。日志项目.codex-log/20261029162651_称重记录可选打印.md。

- 2026-10-29用户已确认本轮厂家输出方式为“稳定后发送一次”，与20秒一帧及SCALE_TIMEOUT吻合；实机应配置连续发送/连续输出（X2市斤、9600/8N1保持），再用现有0.4.0验证。不能靠延长ReadingMaxAgeSeconds保留旧重量代替实时采样。当前仅确认配置原因，连续读数/NET/实纸恢复待用户实测反馈。

- 2026-10-29实机0.4.0归档20261029153051-fd2897de：COM1/9600、SCALE_TIMEOUT、reading=null；日志10次AgentFault，没有本轮原帧，不能断言单位或端口占用。新增独立Collect-ScaleRaw.ps1只读采集20秒/最多64KiB，固定8N1无流控、不发T/Z/AD/任何串口命令、不写业务、不打印；只能停止称重服务后在秤Win10手动运行。每轮serial-raw-check新目录保留原始hex/ASCII分块和摘要，未知13字节行只展示原文单位位置，不解释为业务事实。tests/Raw-Serial-Diagnostic.ps1七项离线/COM4095拒绝验证通过，未打开办公真实端口。此独立诊断不改0.4.0主程序包或宣称恢复。

- 0.4.0的Start/Stop默认使用ScaleAgent-Exercise-0.4.0及ScaleAgent-HardwareWorkflow-0.4.0，防止同ID测试任务混合新斤与旧kg记录；不自动搬库或换单位，旧包先Stop。12个PS1通过Windows PowerShell5.1解析及UTF-8 BOM检查。发布EXE用显式临时目录跑普通演练验证，不在办公机运行-HardwareTest。

- 2026-10-29用户确认0.4.0：统一市斤（sj/斤/市斤同单位别名，数值不变），取消固定软件重量上下限；零负数仍拒绝，设备实称仍需可信稳定NET。手工净重独立manual-baskets请求，必须确认已扣皮，最多14位整数/4位小数；每次新增记录，与实称共用累计、事务和完成集合。MANUAL来源/说明/员工/时间持久冻结，不伪造原帧、皮重或稳定状态。未知结果原号重试，补打不新增重量。旧公斤事实及旧票不改。正式多筐仍WAITING_CONTRACT；本轮不提交推送、不更新平台或执行迁移。此条优先于下文旧1–150kg门槛及仅真实采样流程。

- 0.3.6仅更新随包硬件说明：从两个分拣入口选择商品/客户后再领取，所有下级可返回；发布PS1/CMD逻辑保持不变。旧版先Stop，新包独立目录解压，保留原数据和0.3.5包。本轮构建/HTTP/分拣浏览器通过，不代表真实串口、软件T/Z或出纸通过。

- 0.3.5任务入口变为按商品/按客户两级，随包HardwareTest-README已更新。Start-LocalTest新增-GroupingFixture仅扩展隔离mock自动验证，不改变默认/实机三个任务；显式清空演练TraceUrlTemplate，避免误用未知正式网址。Publish随包携带QRCoder的MIT声明，版本0.3.5；只更新本模块脚本，无平台脚本/CI改动。新票查询区待配置，软件T/Z和新标签实机仍待验证。


- 0.3.4实机Start-LocalTest -HardwareTest显式AllowScaleControl=true；普通演练仍默认false，仅-EnableScaleControl可显式模拟控制。硬件预检查与TEST-编码/回环/RS232/指定队列/60×40及状态目录互斥保持，不自动清理旧筐或补打。HardwareTest-README改为空秤软件置零、空筐软件去皮、等稳定零后放货；未确认先核对，不反复点。Export原有status只读导出自动包含最近20次controlHistory，进程内历史须重启前Stop导出。本版PS1继续UTF-8 BOM、PS5.1语法；不在办公机执行-HardwareTest或向真实打印队列发纸。

2026-10-20，执行者：Codex。

- 0.3.3启动提示及随包HardwareTest-README改为1–150kg稳定净重按原数记录，不要求50g倍数；仍使用原HardwareWorkflow数据目录，不清空旧筐或自动补打。旧程序先Stop导出，新包在新目录启动；第一个商品可能已本地完成，应选择仍未完成的商品测试小数。Start脚本保留PS5.1兼容UTF-8 BOM，硬件铭牌分度配置0.05仍为资料，不等于采集过滤。

- 0.3.1新增显式硬件工作流：Start-LocalTest.ps1 -HardwareTest用独立HardwareWorkflow目录、33630/33631、COM1/9600/真实RS232及指定X7，LocalTest与HardwareTest同时开启，保持测试身份与非业务票模板。串口存在/指定队列非脱机且无旧任务才启动，不打开串口做预探测，不回退默认打印机。普通演练显式HardwareTest=false，仍只模拟重量和打印。
- Stop-LocalTest.ps1 -HardwareTest先运行Export-HardwareTest.ps1，经现有只读API收集3个测试任务范围的筐/累计/命令/票，不复制local-api-key/凭证/数据库。导出目录默认程序旁hardware-workflow；本地验证用OutputDirectory指向项目.codex-tmp。PS5.1顶层数组原样保存HTTP内容，不经ConvertTo-Json引入Count/value包装。导出有误保留错误和现场，仍仅按PID/启动时刻/路径回收自有进程。
- Publish-Agent.ps1生成Start-HardwareTest.cmd和Stop-HardwareTest.cmd并随包带说明。新0.3.1与旧0.3.0包分开，不原位覆盖旧zip。现场两筐保存打印需实机证据，本机仅用不存在队列/COM4095验证拒绝与禁用，不可盲开办公COM1或发纸。

- PowerShell 脚本使用 UTF-8 BOM，兼容 Windows PowerShell 5.1；cmd 启动器使用 ASCII。
- `Start-ScaleCheck.ps1` 复用发布版 Agent 0.3.0，只读已确认的 COM1/9600/8N1 连续 RS232。通过命令行覆盖配置，不写 appsettings.Local.json，不修改计量阈值，不发送去皮、置零或 AD 轮询命令。
- 每次使用全新的 `%LOCALAPPDATA%/XSY/ScaleAgent-HardwareCheck/<runId>` 状态，FIELD 模式且无平台凭证；禁用控制、员工初始会话和本地凭条。只调用根页面、health 和 status GET，不配对、不取任务、不保存筐或打印。
- 结果写程序旁 `hardware-check/<runId>`；readings.jsonl 为真实 Agent 状态的定时抽样，不是逐帧串口抓包。result.json 的 READINGS_RECEIVED 只说明收到数据，不代表计量、净重、归零、物理打印或平台同步验收。
- 毛净重、稳定性按 Agent 原样显示；sj 除以 2 换算公斤，未知单位拒绝，不把毛重改成净重。
- 正常结束或按 Q 时核对 PID、启动时刻、完整路径后仅停止自有进程。窗口被强制关闭可运行 `Stop-ScaleCheck.ps1`，按相同身份检查回收遗留进程，保留数据。
- 本机开发验证只用不存在的端口名或函数级假数据，不盲开办公电脑上的 COM1。禁止将本地验证称为 Win10 实机通过。
- 现场检查包以独立 overlay 交付，与已交付 0.3.0 主程序 zip 和校验值分开；不静默覆盖原包。
- `Test-Label60x40.ps1` 是X7间隙标签的独立硬件测试。默认仅用System.Drawing生成预览；只有显式-Print（Print-TestLabel60x40.cmd中已指定）才向指定队列提交一次、一页、一份。不存在的队列不回退默认打印机，旧任务未结束或脱机时保留现场并停止，不清队列、不自动重试、不修改全局驱动配置。
- 测试样张固定60×40mm，包含明确测试标识、示例重量、时间、编号和四边框。它不调用Agent、不读秤、不写业务库、不访问平台，不能冒充正式分拣票。预览与物理请求共享毫米坐标绘制方法；Windows报告非60×40纸型时拒绝继续绘制。
- 打印前先落SUBMITTING，驱动返回后只记SUBMITTED_TO_SPOOLER，不称物理成功。驱动执行期间异常为UNKNOWN，先核对实纸再决定重试。报告在printer-check，不包含密钥。
- 本机只做无-Print预览、明确不存在队列的拒绝测试或不调用Print的渲染验证，不能向办公电脑真实队列发送测试纸。现场照片与结果带回后再核验物理出纸和间隙停纸。
