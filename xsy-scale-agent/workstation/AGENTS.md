# 秤端工作台

- 0.3.6按用户最新要求：首页只留按商品/按客户两个入口，移除三张统计卡，覆盖0.3.5旧布局。两条路径选择页、最终TaskList及失效分组共用页头唯一返回上级，分别固定回/tasks及/tasks/{mode}；Weigh正常/失效页也统一返回上级，沿用returnTo。新增task-heading-actions复用全局按钮token，至少48px；不可改成router.back()，直接进入/刷新后无可靠历史。
- Tasks.vue的两级路由共用TaskList.vue与sorting.ts。只用skuId/customerId字符串分组，缺失时用job/item隔离；相关身份缺失计任务组，不猜测同名身份。称重query.returnTo只接受本地任务路由，刷新/侧栏切换/最近作业保留fullPath。两个入口仍用相同job/task/item和员工范围，不能新增第二套累计。
- test/sorting.test.mjs需独立-GroupingFixture及XSY_SORT_TEST_URL/XSY_SORT_FIXTURE_URL/XSY_SORT_ARTIFACTS；默认演练/硬件仍三个任务。覆盖跨客户SKU、同名不同ID、缺ID、搜索、8.84+8.86共享17.70/完成、API拒绝新筐及离线。0.3.6补充两条逐级返回链、刷新/直接进入/失效页和两分辨率按钮可触达检查，返回按钮显式aria-label=返回上级，避免Ant图标参与名称。完成后采集按钮被面板替换，断言不存在且后端拒绝，而非假设disabled。
- settings只预留公开HTTPS模板，默认空，维护保护/重启生效；records预览显示冻结链接状态，旧票没有traceReference则无新查询区，不用当前配置回填历史。70/70及三组本地浏览器、1024/1366截图通过；无外部请求，未宣称真实平台/实机通过。


- 0.3.4接入全局scaleControl状态，由status初始化、SSE持续更新；失联不误清未确认状态以放行。control按钮只POST一次，随后GET状态，ZERO_OBSERVED只提示收到稳定零值，超时/发送异常不自动重发。去皮/置零要求新鲜稳定采样和授权，但允许零值/毛重，不套用商品1kg门槛。待核对保存/完成时不能操作设备；新记录需控制未阻断，已有pending保存只核对原操作。后端Freeze同样校验，不只禁用UI。
- 新浏览器入口test/scale-control.test.mjs需独立全新-EnableScaleControl演练，设置XSY_CONTROL_TEST_URL/XSY_CONTROL_FIXTURE_URL/XSY_CONTROL_ARTIFACTS；普通多筐测试仍用原XSY_UI_*变量。缺环境会跳过，不能声称通过。控制后的500ms reading=null为预期，门禁测试先等新帧再取sampleId。两组均使用发布EXE承载产物；1024/1366截图、超时/重复/响应丢失与原数17.70验证通过。未知控制状态保守禁止新采集，不用设备指令自动重试。

- 0.3.3按用户要求取消称重页mod(divisionKg)过滤，与后台Freeze一致；1–150kg内的稳定净重按原始值保存/打印，设置页显示“可记录范围（保留原始读数）”，越界提示不再混称分度错误。不是修改成一位/两位小数精度，不舍入；8.84＋8.86在隔离自包含浏览器验证累计17.70和两张原数票，0.95/150.01与不稳定仍禁用。现有本地浏览器测试断言版本0.3.3及新范围文案，不能为绕过失败删掉越界验证。

- 0.3.1的bootstrap新增hardwareTest只读标记；localTest=true不再单独等同于无实纸，必须结合hardwareTest。硬件联动模式的横幅/设置/凭条预览明确真实串口和手动发测试标签；记录预览按冻结templateVersion判断原票标记，不能只看当前模式。普通演练仍显示未出纸。只改文案与已有组件，未新增页面或改变操作流程。

- 属于西安独立秤端模块，平台参考副本只读。沿用根DESIGN：浅色、蓝主色、触屏大按钮；统一主题入口为src/theme.ts和src/styles.css。
- Vue3/TypeScript/Vue Router/Vite/Ant Design Vue；不引入平台管理端路由、登录底座或其他业务状态机。
- 使用同源HttpOnly工作台会话和X-Workstation-Request头，不向页面交付X-Agent-Key；员工授权引用来自平台，不伪造正式登录。
- JSON大ID与重量保持字符串；差额使用decimal.js。一次人工动作固定operationId，未知结果保留请求以便刷新/重启后重试；补打不生成新筐。
- 本地累计与平台缓存量分开；WAITING_CONTRACT展示等待平台同步，TEST_ONLY展示演练未出纸，禁止显示真实上传/出纸成功。
- 演练由独立mock进程和独立数据目录提供；工作台不内置随机任务、模拟重量或正式采样注入入口。
- npm build先typecheck；测试通过后由脚本复制dist至Agent wwwroot。node_modules、dist、wwwroot不提交；临时浏览器文件放项目.codex-tmp。
- 0.3.0四页本地浏览器验证通过。端到端测试需全新隔离演练，XSY_UI_TEST_URL/XSY_UI_CONTROL_URL/XSY_UI_ARTIFACTS必须显式设置；没有配置则跳过，不能把跳过当通过。运行既有业务库会被初始数据断言拒绝，不为测试清空记录。
- 刷新已有商品先要求人工确认换筐；当前员工切换时清空旧batch与记录视图，迟到请求使用代次隔离。打印图标属于按钮名称的一部分，自动化可使用语义文字正则定位；不要靠组件内部DOM写样式。
- 本机不加载外部字体、图片或统计；analytics入口仅在明确配置的生产域名启用，localhost/回环/演练永久关闭。
