# Windows 秤端 Agent

- 2026-10-20用户明确授权创建分支、提交并交付深圳：使用已存在本地feat/sohe-scale-agent创建远程同名分支，仅提交本模块，不改main、不合并、不自动pull。源码包含当前DESIGN.md快照及FIRST-GENERATION-HANDOFF.md接入清单；忽略delete取消方案与所有运行状态/编译输出，发布测试包单独交付。后续提交推送仍按用户具体指令处理，不将本次授权扩展成自动持续推送。

- 2026-10-20用户明确要求首页只留两个分拣入口，0.3.6删除三张统计卡，覆盖0.3.5保留统计卡的旧要求。Tasks.vue在所有mode下的普通/空态/失效分组页头提供唯一返回上级，确定父级为/tasks或/tasks/{mode}；Weigh正常/失效页统一返回上级，沿用returnTo。按钮48px、无浏览器历史依赖；业务/API/数据库不变，仅版本号更新。已通过构建、发布EXE的HTTP冒烟及分拣浏览器逐级/刷新/离线/失效与跨入口两筐17.70回归，1024/1366截图已检查；不把前版70/70或软件控制专项称为本轮重跑。未操作办公COM1/发纸，正式同步仍WAITING_CONTRACT。记录项目.codex-log/20261020172718_任务入口精简与逐级返回按钮.md。

- 2026-10-20用户确认两个分拣入口及网址未定先预留，0.3.5完成。workstation/src/sorting.ts只组织导航，TaskList.vue复用原列表，/tasks/products|customers/:group保留面包屑/刷新/返回。TaskItem追加可空字符串SkuId/CustomerId；名称不作为身份，缺ID按job/item隔离，客户身份待补时统计用任务组而非伪报客户数。称重侧栏限当前分组并显示客户，仍用原业务范围，不重复累计。
- TraceLink.cs校验一个{eventKey}占位符的固定主机HTTPS模板；配置默认空，不访问查询页或签发unitCode。新快照冻结TraceReference=EventKey和可空TraceUrl，V2标签含15.5mm查询区，QRCoder1.8.1/M纠错/含4模块静区/最多49模块（>=0.30mm）；长网址拒绝。旧V1票保留旧版式，补打保持原快照，不将后来配置补写历史。模板字段保存在原JSON，无表结构/迁移变化；维护配置缺字段/null保留，空串清空，重启只影响新票。
- 0.3.5本地70/70回归、构建、自包含HTTP冒烟、原多筐/软件控制浏览器及新增分组浏览器通过；203dpi配置/补打QR由独立ZXing-C++解码验证，待配置图无二维码。新分组验证8.84+8.86两入口两筐17.70、完成/API门禁/离线/失效路径，1024/1366截图已检查。测试脚本两次假设错误已纠正（图标参与按钮名称、完成面板替换按钮），保留失败证据。新版Win10标签/软件T/Z仍需实机核验，多筐同步WAITING_CONTRACT；平台受控源码不改。记录项目.codex-log/20261020164618_商品客户分拣入口与标签溯源预留.md。


- 2026-10-20用户“好的继续”批准软件去皮/置零，0.3.4已实现，覆盖下方“禁用/无新版”的历史状态。ScaleControl.cs管理SENDING/WAITING/ZERO_OBSERVED/UNCONFIRMED；真实T/Z单字节、不带CR/LF，清串口半帧和旧样本，写后flush，500ms隔离，5秒未见稳定零则未确认。去皮需NET零，置零可NET/GROSS零；不本地造零/减重/补皮重、不自动重发。ScaleReader串口读/组帧/Set与控制共锁，HTTP读/写互斥，避免旧响应污染；Fail/发送异常不能永久卡住MaxValue隔离。Freeze在控制待定或未确认时阻止新采集，已有记录幂等核对不造新事实。
- 0.3.4的status/controlHistory与SSE/control、POST/control是增量投影；历史只存最近20次操作当前状态，重启清空，非永久审计。硬件测试模式允许软件控制且启动脚本显式true；其他TEST-编码、回环、RS232、60×40和数据目录互斥约束保留。普通演练默认关闭，可用-EnableScaleControl对Fixture验证；正式配置仍false。
- 0.3.4共66/66本地回归、工作台构建、自包含HTTP冒烟、原多筐浏览器及新控制浏览器通过。覆盖双击一次、无授权/不稳定拒绝、无零值超时的UI/API门禁、刷新不重发、后续零恢复、响应丢失不重发及8.84＋8.86＝17.70原值票。软件T/Z尚未在秤Win10验收，不复用实体键证据；办公电脑未开COM1/发纸。唯一接口文档已更新，无SQL/平台源码变更。日志项目.codex-log/20261020152916_软件去皮置零控制与实机测试包实现.md。

- 2026-10-20用户在0.3.3后反馈“现在正常了”，继续询问软件去皮/置零。已只读核对厂家RS232原文第4节：T/0x54去皮、Z/0x5A置零，单字节无回车；ScaleReader.Control已有发送入口。当前按钮灰色来自AllowScaleControl=false及HardwareTest模式显式禁止控制，并非厂商文档缺失；后续若启用须同步调整模式约束，不能只取消前端disabled。软件指令真实执行尚未实机验证，应以后续稳定设备帧确认；不复用实体去皮通过作为软件控制通过，不用本地减重代替硬件去皮。本轮无新版本或控制命令。记录项目.codex-log/20261020152322_软件去皮置零现状与下一步核对.md。

- 2026-10-20用户明确要求“去掉限制，称多少就是多少”，0.3.3据此取消ScaleState.Freeze及称重页的分度倍数过滤，优先于下方历史“50g倍数必须合格”的约束。范围仍默认1–150kg，稳定NET、单位、时效和任务授权检查保持；DivisionKg仅为铭牌资料，不自动舍入或改设备标定。8.55/8.84/8.86/1.01kg真实形状帧已本地验证精确保存、重启读取和标签渲染，新增贯穿链路回归而非仅放开按钮；57/57及自包含HTTP冒烟通过。隔离浏览器验证8.84＋8.86＝17.70，票快照保持原值、TEST_ONLY不发纸；1/150kg可采，0.95/150.01kg拒绝，1024/1366触屏及原多筐离线流程通过、无浏览器异常。0.3.3现场小数标签仍待实机，不把旧0.3.2两张实纸扩大为新版通过；平台仍WAITING_CONTRACT。日志项目.codex-log/20261020150331_取消分度倍数限制并按原重打印.md。

- 2026-10-20最新现场核验（20261020144845-f963de99）：0.3.2上海青9.10kg、西红柿10.60kg均已真实采集保存，Windows驱动返回SUBMITTED_TO_SPOOLER且无错误，用户确认“两张都已出纸”；仅本轮两张HARDWARE_TEST标签联动通过，不扩展为正式平台同步或完整验收。原导出physicalOutputVerified=false不改写，派生analysis单独记录用户证据。当前稳定NET原始帧sn0008.84kg与解析8.84一致，按现场1–150kg/0.05kg配置应拒绝；8.55符合分度但本包未抓到8.55状态。秤0.01kg输出与铭牌50g分度差异待核对，不因打印已通就放宽阈值、自动舍入或现场标定。两张14.10kg/TICKET_TOO_LONG来自0.3.1，非新版失败；保留历史，可在称重记录补打原票而不重建筐。平台队列仍WAITING_CONTRACT。本轮只诊断留痕，无版本或源码逻辑变更。日志项目.codex-log/20261020145237_实机新版出纸与分度差异核验.md。

- 2026-10-20当前0.3.2仅修复60×40真实记录号溢出：事件键是设备前缀加32位GUID（TEST-HW-01时43字符），旧渲染用例省略前缀导致漏检。TicketLayout紧凑标题7.5pt、净重12pt、正文仍7pt、行距0.05mm，给完整编号预留两行，仍先预检、不裁切、不自动重打；回归Sample必须保留完整前缀。56/56本地通过及实际生成票预览通过，不等于Win10/X7已实纸联动。
- 用户重启后COM1读数恢复，并已将秤实体设置为KG，随后取消单位转换开发。短暂的MassUnits及调用改动已撤销（新文件移delete保留为.txt），源码仍仅IDENTITY且原始单位必须与销售单位一致，不继续换算方案。软件去皮/置零仍禁用，使用实体键。
- 用户反馈8.50可记录、8.55提示计量范围或分度不合格；不能认定为只支持一位小数。0.3.2自包含包隔离浏览器/API核验8.50/8.55/1.05均可记录，8.56/0.95拒绝，8.55精确持久化并进入演练票TEST_ONLY。现场这一项仍需在异常读数保持时导出status/workstation核对，未放宽1kg/0.05kg。记录.codex-log/20261020142136_KG模式记录门槛及打印故障排查.md，证据.codex-tmp/kg-20261020142136。旧0.3.1包保留。

- 2026-10-20首次0.3.1硬件联动日志20261020135233-27b572c5已核验：COM1/9600/RS232配置生效，但53次UnauthorizedAccessException，状态SCALE_DISCONNECTED且reading=null，三任务零筐/零票。已定位串口访问被拒绝，尚未确定占用进程；优先关闭串口助手、用旧Stop-ScaleCheck释放自有检查，或秤Win10重启后仅启动一次联动程序。不能当作协议或环境覆盖错误，也不能宣称恢复或联动通过。本轮无程序改动，证据项目.codex-tmp/hardware-workflow-20261020135233，记录.codex-log/20261020135630_实机联动COM1访问拒绝日志排查.md。

- 2026-10-20当前源码0.3.1：TicketLayout提供60×40紧凑模板与旧长票，预览/实纸共享毫米绘制，全部字段预检后才进入Windows打印；超长不裁切。WindowsTicketOutput选择实际尺寸匹配纸型、补偿硬边距，驱动调用后异常仍UNKNOWN。
- 新增显式HardwareTest策略，须同时LocalTest、TEST-设备、回环平台、SOHE_RS232_V101、禁用软件秤控制、指定队列及60×40纸张；状态模式HARDWARE_TEST与FIELD/LOCAL_TEST互斥。测试快照仍TestOnly=true并冻结HARDWARE_TEST_6040_V1。HardwareTestTicketPrinter只接受该标记，正式DriverTicketPrinter仍拒绝TestOnly，ExerciseTicketPrinter仍不发纸。不能把硬件测试当正式员工授权或上传成功。
- Start-HardwareTest/Stop-HardwareTest由发布脚本生成cmd，复用Start/Stop-LocalTest的显式开关；结果导出使用现有只读API，不复制密钥、Token或数据库，PS5.1顶层数组需保留原始响应避免Count/value包装。默认60×40，仅新策略默认值改变，不改已冻结旧票的纸张。
- 0.3.1回归56/56与HTTP冒烟、Vue构建、普通演练浏览器通过；硬件模式仅在不存在COM4095下验证页面提示、无读数禁用和PS5.1导出，未开办公COM1/发纸。现场两筐真实保存打印尚未通过。过程见项目.codex-log/20261020120711_六十乘四十业务标签与实机联动入口研发.md，旧0.3.0发布包保留。

- 2026-10-16，Agent 0.3.0已完成含触屏工作台的第一代本地程序；西安交付源码/程序/本地验证给深圳，不能以等待深圳提供前端或评审接口为研发前置。2026-10-20用户已恢复实机读重和打印检查，最新核验见文末；正式平台联调尚未完成。
- 本模块对接 `docs/sohe-scale-integration-handoff.md` 的设备侧草案；设备 API 尚未部署时使用模块内的回环模拟平台，不调用员工 Cookie 接口。
- 设备采集只形成待人工接受事件，不提供 accept、订单、库存、金额或财务写入能力。
- `src/Xsy.Scale.Agent` 是本机HTTP服务，默认仅监听127.0.0.1:33600。`workstation/`为Vue3/TS/Router/Vite/Ant Design Vue四页工作台，构建复制至wwwroot由同源Agent承载；员工身份/权限复用平台来源，不自创正式平台账户或把设备凭证当员工登录。
- `tests/Xsy.Scale.Agent.Tests` 是本地可执行回归，使用临时 SQLite、假 HTTP 及假打印机，不代表实机验收；入口 `verify.ps1`。
- 所有正式采集先写 SQLite 再上传，重试保持同一 eventKey；实时帧不逐帧上报业务事件。
- `BasketModels.cs` 定义范围/筐/累计/完成/命令；`BasketService.cs` 复用 CaptureService 校验和冻结；`BasketStore.cs` 是 StateStore 的部分实现，独立增量表保存多筐事实与意图。
- 多筐范围按员工会话、作业、任务、明细隔离；不跨会话接班。净重用 decimal 精确累计所有筐，不能从旧200条诊断或当前分页计算，也不能直接加平台缓存 sortedQuantity。
- 保存本筐、CAPTURE_BASKET 命令及打印意图必须同事务；完成事务校验最新 manifestHash，持久完整有序事件集合并锁定该范围，不读取第三次重量，不额外打印。
- 多筐命令仍WAITING_CONTRACT，不接入旧NextEvent/measurements/accept，不虚构远程成功。未显式启用本地凭条时WAITING_AUTHORIZATION；启用Workstation.LocalTicketsEnabled后保存独立LOCAL_PENDING_V1待同步凭条，不代表正式平台单据。旧queueDepth和events不含新队列，使用basket-queue诊断。
- 同 operationId 同请求复用记录；不同操作不能复用同sampleId，但相同重量的新采样可形成两筐。真实连续帧仍可能来自同一实物，不能宣称已自动识别换筐。
- 同范围旧 captures 与多筐接口双向互斥，不自动迁移。完成后没有本机重开/作废接口；零筐缺货仍走平台业务入口。
- 新多筐POST需员工头/体一致；过期会话仍可查询其缓存可见的历史，但不允许新采集/完成或通过幂等绕过当前权限。
- 未确认稳定来源、毛重、失联、未知单位或计量范围不合格时拒绝采集。HTTP Code=1 不是稳定标志。
- 采集必须引用平台作业领取结果及 jobVersion/taskItemVersion，租约到期不得离线扩权。jobs 接口与本机 tasks 投影不是两个业务状态机。
- 正式RAW打印按平台printAttemptId持久执行，平台新授权尝试才重印。本地凭条由LocalTicketModels/Store/Printing管理：首次与筐/命令同事务，补打保留snapshot并追加attemptId与原因，可使用当前队列/纸张，绝不新增筐。先落SUBMITTING再调驱动；异常可能已出纸则UNKNOWN，明确拒绝FAILED，重启未决UNKNOWN，不自动重打；Windows接受只记SUBMITTED_TO_SPOOLER。
- 本地票与筐/完成/旧采集共用逻辑操作号命名空间。旧回执不得覆盖新补打状态；读取print_state须取独立列，不从冻结payload推断当前状态。补打幂等仍须当前员工授权/作业租约有效。
- WorkstationService提供有限配置/设备枚举/安全投影；Cookie HttpOnly+Strict及X-Workstation-Request同源认证，不交前端X-Agent-Key；维护码仅用于设备配对/配置，员工授权引用DPAPI保存。维护保存重启生效，网页没有标定/平台数据库/模式切换入口。
- 演练仅Start-LocalTest显式启动：独立进程/目录、TEST-设备、回环平台、ExerciseTicketPrinter。真实Agent没有模拟重量注入API，重量/断网控制只存在mock的Fixture模式；FIELD/LOCAL_TEST数据目录不可混用。演练TEST_ONLY从不调用Windows打印队列。
- 程序目录是配置根目录，发布时不打包开发者的 appsettings.Local.json；PS1 用 UTF-8 BOM 兼容 Windows PowerShell 5.1。
- 所有参数和接口都是版本化适配点；与深圳冻结接口后修改 DTO 和契约测试，不复制平台分拣状态机。
- 优先从给定平台源码提取实际契约；未实现的平台设备/多筐能力使用明确的适配边界及隔离本地测试，交付差异清单。不因缺接口停下界面、设备、本地业务或安装打包研发，不声称替身测试已完成真实同步。
- 本模块验证按用户会话中已授权的本地自动验证执行，不运行远程 CI。提交、推送和发布仍需用户指令。
- 回归入口包含BasketTests和LocalTicketTests；51/51及HTTP冒烟通过。workstation/test在显式指定新隔离演练URL时执行浏览器完整流程，输出到项目.codex-tmp；1366×768与1024×768主操作首屏可达，离线两筐5＋4＝9、响应丢失核对、补打不重复累计。没有实机/真实同步验收证据，不能声称生产就绪。
- 发布脚本构建工作台并打包自包含Agent及local-test替身；Open-Workstation/Start-LocalTest/Stop-LocalTest提供cmd入口。旧输出目录移至项目.codex-tmp，旧zip保留。源码包不得包含bin/obj/node_modules/dist/wwwroot/publish、个人配置、密钥或数据库。
- 2026-10-20串口助手确认COM1/9600/8N1连续RS232后，用户已在秤的Win10运行Start-ScaleCheck，带回hardware-check.rar与截图。59次status抽样的原始帧和解析字段逐项一致，52次稳定、7次不稳定，零重→稳定0.80市斤/0.40kg→稳定归零，无读取错误，结果报告自有进程已退出。全部GROSS，皮重未知；不能据此声称净重、计量、物理打印、正式平台同步通过。用户有实体去皮键，下一步用原只读检查入口配合实体键验证空筐去皮及NET标志，不修改阈值或将GROSS改成NET。证据与分析在项目.codex-tmp/hardware-20261020112119，记录在.codex-log/20261020112119_秤端真实COM1读重结果核验.md。scripts/Start-ScaleCheck.ps1复用0.3.0，独立数据目录和同源status认证；约束见scripts/AGENTS.md，检查包单独交付。
- 2026-10-20第二轮实机去皮已核验：59次status抽样（12 GROSS、47 NET；52稳定、7不稳定），毛重0.28kg→去皮后NET 0→稳定NET 0.08kg。最后NET -0.28kg，用户确认筐也拿走了，符合仍保留皮重；不改符号或把负数夹成零。tareWeight仍为null，去皮前后重量不能冒充独立皮重字段。仅移除商品保留筐后的净重回零仍待补测，计量阈值保持不变。日志.codex-log/20261020112705_实秤去皮净重及负值结果核验.md；证据.codex-tmp/hardware-20261020112705。
- 2026-10-20第三轮44次实机抽样（7 GROSS、37 NET；40稳定、4不稳定）已验证NET 0→稳定NET 0.10kg→稳定NET 0，无读取错误，补齐上一条净重回零待验项。归档无result.json/启动日志，进程退出未知；不伪造结束摘要，不因文件不全要求重称，用Stop-ScaleCheck回收自有检查进程。下一阶段X7打印测试，用户确认间隙标签纸，标签宽×高待补；旧长凭条版面不能视为已适配短标签，不盲打A4测试页，不更改平台源码。日志.codex-log/20261020113418_保留空筐净重回零核验与打印准备.md，证据.codex-tmp/hardware-20261020113418。
- 2026-10-20用户补齐X7间隙标签尺寸60×40mm。已提供scripts/Test-Label60x40.ps1及Print-TestLabel60x40.cmd单张硬件检查，默认仅预览，cmd显式-Print。使用Windows驱动和System.Drawing，与现有Agent同类打印路径，未改变现有长凭条模板或增加业务打印入口；该硬件样张通过也不代表正式凭条已适配60×40。具体约束见scripts/AGENTS.md，操作与本地验证见项目.codex-log/20261020114526_X7六十乘四十标签打印检查包实现.md。实纸中文、位置、间隙走纸与正式模板仍待验证。
- 2026-10-20首次X7实纸已核验，覆盖上一条的单张样张待验项：用户照片编号T115223-4d48与日志一致，中文/数字/示例重量清楚，边框完整；X7/USB001单份任务返回SUBMITTED_TO_SPOOLER且无错误。照片确认本次实纸，不改原始physicalOutputVerified=false，也不把单张证据扩大为连续走纸、业务标签、称重保存打印联动或真实同步通过。正式DriverTicketPrinter仍为长凭条模板，需适配60×40后再测试；保持演练不调用真实打印队列及业务授权边界。证据项目.codex-tmp/printer-20261020115359，记录.codex-log/20261020115359_X7标签实纸打印结果核验.md。
