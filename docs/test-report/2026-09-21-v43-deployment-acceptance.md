# V43 部署后验收测试报告（2026-09-21）

- 验收对象：`V40 → V43` 升级后的运行实例
- 测试窗口：2026-09-21 16:22 – 17:45（Flyway 应用时间 → 配送数据回收完成）
- 执行方式：真实浏览器（Playwright Chromium + browser-use 真实 Chrome 会话）+ 真实 HTTP 调用 + PostgreSQL `SELECT` 交叉验证 + 后端日志扫描
- 本轮性质：**只做验收，不改业务代码，不修 Bug**。下列缺陷全部只记录、不修复
- 结论速览：**P0 = 0，P1 = 0，P2 = 0，P3 = 6**；共执行 80 项，PASS 67 / FAIL 5 / BLOCKED 2 / NOT TESTED 6

---

## 1. 测试环境

| 组件 | 实际形态 | 证据 |
| --- | --- | --- |
| 被测构建 | 后端 fat jar，构建自当时的 main `35e0fa4`；前端为 HEAD 工作区的 Vite dev | 构建产物时间戳与后端进程启动时间先后一致 → 可确认进程加载的就是该 commit |
| 数据库 | 本地开发栈的 PostgreSQL，schema `xsy_v2`（V43 由 Flyway 在 16:22 应用） | `flyway_schema_history` |
| 缓存 | 本地开发栈的 Redis | 登录态可用 |
| 文件存储 | **LOCAL 模式**（落盘目录 + `/upload/**` 静态映射） | 上传后的磁盘文件与匿名 GET 结果 |
| 账号 | `admin`（SUPER_ADMIN，`administratorFlag` 绕过权限校验）——沿用既有登录态，未重置任何密码 | 浏览器会话复用 |
| 地图 Key | **未配置**：三份前端环境配置文件中 `VITE_AMAP_KEY` 命中数均为 0，`mapConfigured()` 返回 false | 页面告警 + 前端代码分支 |

**环境范围勘误（重要）**：任务书指定的入口 `https://xsy.leyingiot.com` 实际可达（`GET /` → 200，`<title>鲜蔬源智链 V2</title>`，`/api/login/getTwoFactorLoginFlag` → `code:0`），但按用户中途指示，本轮全部验收改在**本地开发栈**完成。远端实例只做了匿名存活探测，**未在其上跑任何写流程**，也未确认其 schema 是否已升到 V43。见 §11、§12。

---

## 2. Git / Flyway 状态

```text
branch = main
测试基线 commit = 35e0fa4  fix(product): IMAGE_NOT_PUBLIC 换号 40030 → 40038
测试结束时 HEAD = 110173e  merge: bring dev-map formatting into main（其间 8c0ab90 style: format source files）
工作区 = clean（git status --porcelain 为空）
```

`35e0fa4 → 110173e` 为**前后端代码格式化**（用户确认）。已用去注释 + 去空白后的字符流比对配送服务实现：`DeliveryRouteService.java`、`DeliveryEligibilityPolicy.java` 与 `35e0fa4` **完全一致**，因此本报告的配送 L0–L2 结论对 HEAD 同样成立；其余未逐文件比对，但无任何行为差异迹象（测试期前端一直是 HEAD 工作区，后端运行的是 `35e0fa4` 构建，两轮结果自洽）。

Flyway（`xsy_v2.flyway_schema_history`，`SELECT` 原样输出）：

| installed_rank | version | description | installed_on | success |
| --- | --- | --- | --- | --- |
| 40 | 40 | scm geo region and master location | 2026-09-21 04:02:46.912408 | **t** |
| 41 | 41 | scm product image drop file url | 2026-09-21 10:48:58.314517 | **t** |
| 42 | 42 | scm delivery static route | 2026-09-21 16:22:49.266118 | **t** |
| 43 | 43 | scm delivery permissions | 2026-09-21 16:22:53.649447 | **t** |

V41 / V42 / V43 全部 `success = true`，版本连续且无重复，末次应用后再无失败记录。

存活检查：后端 `/api/login/getTwoFactorLoginFlag` → 200；前端首页 → 200。

后端以独立进程运行（不是 compose 服务），因此任务书里的 `docker compose logs backend` 在本环境对应后端进程日志。同机其他项目容器未触碰，未执行任何 prune / down -v / reset / clean。

---

## 3. 测试数据

全部经系统页面或正式 API 生成（未直接写任何业务表），标识统一带 `AI_TEST_` / `AI验收`。

| 类别 | 主键 / 编码 | 说明 |
| --- | --- | --- |
| 仓库（复用） | `warehouse.id = 2` `WH002` 冷库备用仓 | 本轮定位：`113.216, 23.27, GCJ02`，`status = ENABLED` |
| 客户 A | `customer.id = 60` `AI_TEST_A` | 天河区天河路100号AI验收点，`113.329, 23.135, GCJ02` |
| 客户 B | `customer.id = 61` `AI_TEST_B` | 白云区机场路200号AI验收点，`113.273, 23.169, GCJ02` |
| 客户 C | `customer.id = 62` `AI_TEST_C` | 海珠区新港西路300号AI验收点，`113.298, 23.099, GCJ02` |
| 司机 | `delivery_driver.id = 1` `AI_TEST_DRIVER`（ENABLED）；`id = 3` `AI_TEST_DRIVER_OFF`（DISABLED，用于停用排除） | 未触碰任何既有真实司机 |
| 车辆 | `delivery_vehicle.id = 1` `AI-TEST-01`，4.2米厢式冷藏车，载重 5000，体积 20 | ENABLED |
| 销售订单 | `35 / 36`（A，**地址完全相同**）、`37`（B）、`38`（C）= `CONFIRMED`；`39` = `DRAFT`（候选池排除用）；`40` = `CONFIRMED` 且**地址快照无坐标**（覆盖率门禁用） | 经 `/scm/order/create-and-progress`、`/scm/order/create` 生成，带 `Idempotency-Key` |
| 线路 | `1` `DR20260921000001`（主流程）、`2` `DR20260921000002`（跨线路重复分配）、`3` `DR20260921000003`（空线路门禁） | 三条均已 `CANCELLED` |

**回收状态**：3 条线路 `CANCELLED`、`delivery_route_order` 中 `ACTIVE = 0`，AI 订单全部重新出现在候选池；司机 / 车辆 / 客户 / 订单作为可复用测试主档保留（`remark` 标注「V43 配送验收测试数据，可删除」）。库存、出库、预留、订单状态零变化（见 §9）。

---

## 4. 基础回归结果

扫描工具：Playwright Chromium 脚本（真实登录 + 逐路由等待表格出数 + 截图），结果与 38 张截图留痕在本机被 `.gitignore` 的运行目录，未入库。

覆盖 35 条路由：首页、商品（列表 / 分类 / 辅助资料）、客户（列表 / 类型 / 可见性）、供应商（列表 / SKU）、定价（协议价 / 类型价 / 历史 / 预览）、订单（列表 / 退货 / 退款 / 日志）、采购（需求 / 订单 / 收货 / 日志 / 仓库）、库存（余额 / 流水 / 出库 / 预留 / 盘点 / 报损报溢 / 调拨 / 预警 / 阈值 / 规格转换）、**物流配送（线路 / 司机 / 车辆）**、数据大屏。

| 指标 | 结果 |
| --- | --- |
| console error | **0**（35/35 页） |
| pageerror | **0** |
| HTTP ≥ 400 | **0** |
| 无 401 / 403 / 404 / 500 | 是 |
| 侧栏菜单与权限渲染 | 正常，V43 三条新菜单可见 |
| 刷新后保持 | 抽查商品 / 库存 / 配送列表刷新后正常重查 |

任务书要求的 10 个重点区域（登录、首页、商品、客户、供应商、销售订单、采购、库存、数据大屏、物流配送）全部为 PASS，且额外覆盖了定价、退货退款、库存 9 页与配送 3 页。

---

## 5. V41 商品图片结果

对象：`product_spu.id = 57`。

| # | 步骤 | 实际结果 | 状态 |
| --- | --- | --- | --- |
| 5.1 | 查看商品 / 打开编辑抽屉 | 既有图片正常显示 | PASS |
| 5.2 | 上传新测试图片 | 成功，返回 `fileKey` 前缀 `public/image/`（`FileFolderTypeEnum.PUBLIC_IMAGE` 生效） | PASS |
| 5.3 | 保存商品 | `16:43:29` 落库 SQL：先 `UPDATE product_image SET is_primary=FALSE WHERE spu_id=57 ...`，再更新原主图 `version=1`，最后 `INSERT INTO product_image (... file_key='public/image/7e4895c0...164320.png', file_name='ai-test-image.png', is_primary=false ...)` | PASS |
| 5.4 | 重新打开商品 | 两张图片均在，主图切换正确 | PASS |
| 5.5 | 刷新页面 | 图片仍显示，URL 无过期参数、无签名串 | PASS |
| 5.6 | 匿名静态可读性 | 无 token 直接 GET `/upload/public/image/7e4895c0…164320.png` → **200 `image/png`** | PASS |
| 5.7 | 列结构 | `information_schema` 确认 `product_image` **已无 `file_url`**，只剩 `file_key` 等；URL 一律派生 | PASS |
| 5.8 | 写侧收口负向 | 用 `private/common/...png` 绑到 SPU 57 → `{"code":40038,"msg":"商品图片只能引用公开图片目录的文件，请重新上传"}`；事后图片列表仍是两条 `public/image/`，**未被数据提升** | PASS |
| 5.9 | 其他字段回归 | 主档标量（含 PCO-1 字段）、SKU 列表、标签编辑不受影响；商品列表 / 详情控制台无报错 | PASS |

现库存活 `product_image` 2 行，前缀均为 `public`（历史 `private/common` 行已随编辑软删）。

限制：当前为 LOCAL 存储模式，`/upload/**` 静态映射无守卫，因此「private 目录不对外可读」**未在本环境验证**（见 §11）。

---

## 6. 物流配送 L0–L2 测试矩阵

主表（状态口径：PASS = 有浏览器 / HTTP / DB 至少一种实证；BLOCKED / NOT TESTED 见 §11）：

| # | 用例 | 预期 | 实测 | 状态 | 关键证据 |
| --- | --- | --- | --- | --- | --- |
| 7.1 | 准备数据（1 仓 + 3 客户 + 司机 + 车辆 + ≥4 单，A 两单同址） | 可满足 4 单→3 停靠点 | 建成并全部经正式接口 | PASS | §3 |
| 7.2 | 仓库定位（省市区 / 详细地址 / 经纬度 / 坐标系成组） | 保存并回显 | `WH002` = `113.216, 23.27, GCJ02`，编辑重开不丢 | PASS | DB + 表单截图 |
| 7.2b | 高德地图定位 / 地址检索 | — | Key 未配置，弹窗提示未配置 | **BLOCKED / NOT CONFIGURED** | `l0_amap_not_configured.png` |
| 7.2c | 手工经纬度入口（不依赖高德） | 可保存 | `scm-map-picker` 手工输入 + CRS 下拉可用，后续用例据此完成 | PASS | 交互记录 |
| 7.3 | 客户 A/B/C 统一定位 GCJ02 | 刷新不丢 | 三行 `longitude/latitude/geom_crs` 完整且同 CRS | PASS | DB |
| 7.4 | 候选资格 = `CONFIRMED` + 未删除 + 无 ACTIVE 分配 | DRAFT 不出现 | `39`（DRAFT）在候选池不可见；强制提交草稿单 → `41102` | PASS | API + `t_operate_log` |
| 7.5 | 司机 CRUD + 启停 | 停用后不入选项 | 新增 / 查询 / 编辑 / 停用确认 / 重开正常；`options/drivers` 仅返回 `[1]`，停用的 `3` 被排除 | PASS | HTTP `optionIds:[1]` |
| 7.6 | 车辆 CRUD + 启停 | 停用后不入选项 | 同上，`options/vehicles` 只含 ENABLED | PASS | HTTP |
| 7.7 | 新建线路 | `DRAFT` + 自动 `routeNo` | `DR20260921000001`，`status=DRAFT`，`version=0`，并复制仓库名 / 地址 / 坐标快照 | PASS | `delivery_route` |
| 7.8 | 候选筛选（配送日期 / 关键字 / 省市区 / 预计送达 / 定位 / 商品种类） | 只出合规订单 | 各条件在 UI 与 API 双层验证：`deliveryDate` 命中 / 不命中，`locatedOnly` 过滤，无 `expect_delivery_time` 的订单需先「重置」 | PASS（含 P3-6） | `DeliveryQueryDao.xml` 口径 + HTTP |
| 7.8b | 组单 4 单（A×2 同址）+ 原因「AI 自动化配送验收」 | 订单数 4 / 停靠点数 3 | `delivery_route_order` 4 行；`delivery_route_stop` 3 行，`35`+`36` 同 `stop_id=1`，`customer_id` 与 `address_snapshot` 完全一致 | PASS | DB dump |
| 7.9 | 重复分配（UI 隐藏 + 强制 API + DB 兜底） | 不可重复 | 二次加入 → `41103`；跨线路抢单 → `41103`；同请求重复 ID 去重后成功（`orderIds` distinct）；`SELECT` 双 ACTIVE = **0 行** | PASS | HTTP + `uk_delivery_order_active` |
| 7.10 | 停靠点顺序编排（拖拽 / 上移 / 下移）+ 刷新持久化 | 顺序保持 | `1 A/2 B/3 C` → `1 C/2 A/3 B`，`stop_seq` 落库 `1=客户C, 2=客户A, 3=客户B`，整页刷新后一致；`/routes/1/map` 停靠序列同步 | PASS（视觉 Polyline 顺序 BLOCKED） | DB + map 接口 |
| 7.11 | 定位覆盖率门禁 | 未齐不能规划 | 告警「尚有 1 个停靠点未定位。补齐后才能确认规划。」，覆盖率 `3/4`，强制规划 → `41104` | PASS | UI alert + HTTP |
| 7.12 | 坐标系不一致门禁 | 拒绝规划 | 仅用测试停靠点（未改真实客户数据）造 `GCJ02` 起点 + `WGS84` 停靠点，全 `4/4` 定位仍 → `41104` | PASS | `t_operate_log` 1197–1198 |
| 7.13 | 确认规划 | `DRAFT → PLANNED`，且**不扣库存 / 不生成出库单** | `1200` 成功，`status=PLANNED`；库存 / 出库 / 预留 / 订单状态与执行前基线**逐值相同**（§9） | PASS | 基线快照对比 |
| 7.13b | PLANNED 后写路径锁定 | 六类写全拒 | `update` / `add` / `remove` / `reorder` / `locate` / `plan` 全部 `41101`，线路字段零变化 | PASS | HTTP 六连 + `t_operate_log` 1201–1206 |
| 7.14 | 打印预览（线路号 / 名称 / 仓库 / 司机 / 车辆 / 停靠顺序 / 客户 / 订单 / 商品 / 数量 / 金额） | 内容完整、可预览 | `/routes/1/print` → `code:0`，`detail` + `items`；预览页含全部字段，缺失实重显示「—」不推导；`@OperateLog` 记录 `1207`、`1208` | PASS（物理输出 NOT TESTED） | `l2_print_preview.png` |
| 7.15 | 取消线路 → 释放订单 | `CANCELLED` + `RELEASED` + 回候选池 | 填原因取消，`assignment_status=RELEASED`（`deleted=false` 保留历史与金额快照），订单重新出现在候选池并可再次组单 | PASS | DB + 二次组单成功 |
| N-1 | 停用主档挂线路 | 拒绝 | 建线路 / 编辑时用停用司机 → `41105` | PASS | HTTP |
| N-2 | 编码 / 车牌重复 | 拒绝 | 司机编码、车牌重复均 `41106` | PASS | HTTP |
| N-3 | 停靠顺序非法（缺号 / 重复 / 错集合） | 拒绝 | 两种构造均 `41107` | PASS | HTTP |
| N-4 | 空线路规划 | 拒绝 | 无订单线路规划 → `41108`（非 `30001`） | PASS | HTTP |
| N-5 | 取消不填原因 / 空白原因 | 拒绝 | `40000 请求参数不正确`（`@Valid` 边界），线路状态不变 | PASS | HTTP |
| N-6 | 乐观锁（过期 `version`） | 拒绝 | `40921 数据已被其他操作修改，请刷新后重试`，且失败后线路 `version` 不变 | PASS | HTTP + DB |
| N-7 | 不存在的订单 / 已删订单 | 拒绝 | `41102` | PASS | HTTP |
| N-8 | 地理成组校验（坐标有值 CRS 空、CRS 非法） | 拒绝 | `30001 经纬度和坐标系必须同时填写或同时清空` / `需要匹配正则表达式"GCJ02|WGS84"` | PASS | HTTP |
| N-9 | `routeNo` 全局递增 | 不按日重置 | 同日三条 `000001 / 000002 / 000003` | PASS | DB |
| N-10 | 500 单上限 `41109` | — | 未实测（需造 501 单） | **NOT TESTED** | — |

矩阵小结：配送域执行 **26 项 PASS、1 项 PASS 带提示缺陷、2 项 BLOCKED/NOT CONFIGURED 关联项、1 项 NOT TESTED**，另有 6 条数据库约束 / 索引作为 §9 的一致性证据单独计数。

---

## 7. 浏览器 console / 网络错误

加载级（35 页自动扫描）：`console error = 0`、`pageerror = 0`、`HTTP ≥ 400 = 0`，无 401 / 403 / 500。

交互级（真实 Chrome 会话，配送 / 仓库 / 商品表单）捕获到两类噪音，均不影响功能：

1. `[Vue warn]: Invalid prop: type check failed for prop "maxlength". Expected Number with value 64, got String with value "64"` —— 来自 `<a-input maxlength="64">` 静态写法，`xsy-scm-web/src/views/business/scm/purchase/warehouse-list.vue:111`；同类静态写法全库 **63 处**（含本轮新增的配送页面），另有 59 处已用 `:maxlength`。
2. `[log] Proxy(Array) 2` —— `xsy-scm-web/src/store/modules/system/dict.ts:115` 遗留 `console.log`。仅 dev 模式可见：`vite.config.ts` 配了 `drop_console: true`，生产构建会剔除。

早期一次「仓库下拉为空」和一次「确定按钮点不动」的快照，经复核是**自动化脚本命中了 Ant Design 关闭后仍留在 DOM 的历史节点**（选择器未按可见性过滤），换用文本匹配 + 尺寸过滤后复测正常，**不计为产品缺陷**。

---

## 8. 后端日志异常

本次运行的后端日志（16:22 起，7067 行）：`migration failed` 0、`Bean creation failed` 0、SQL error 0、HTTP 500 0、Flyway / PostgreSQL 版本类 warning 0（已知项，非失败）。

`[ERROR]` 共 **3 条**，逐条定位：

| 时间 | URL | 定性 |
| --- | --- | --- |
| 16:35:54 | `/scm/order/query` | `AsyncRequestNotUsableException` / `ClientAbortException`（客户端中止连接）——页面跳转取消在途请求，非业务缺陷 |
| 16:55:25 | `/scm/customer/add` | `HttpMessageNotReadableException: Invalid UTF-8 middle byte 0xf8`——**测试脚本侧** GBK 编码载荷所致，浏览器路径无此问题；属工具产物 |
| 17:56:45 | `/api/upload/public/image/...` | 我在验收窗口结束后自建的错误 URL 探测，非系统缺陷 |

`[WARN]` 36 条均为启动期既有噪音：`BeanPostProcessorChecker`（MyBatis-Plus / Druid Bean 提前初始化）、`SelectById` 覆盖告警等，与 V41–V43 无关。

---

## 9. 数据库一致性检查

仅执行 `SELECT` / `pg_constraint` / `information_schema` 查询，未写任何业务表。

线路（`delivery_route`）：

```text
id 1 DR20260921000001  CANCELLED  version=8  outbound_id=NULL  cancel_reason='AI 自动化配送验收结束回收'   deleted=f
id 2 DR20260921000002  CANCELLED  version=2  outbound_id=NULL  cancel_reason='AI 验收清理：跨线路测试线路取消' deleted=f
id 3 DR20260921000003  CANCELLED  version=1  outbound_id=NULL  cancel_reason='AI 验收清理：空线路门禁测试取消' deleted=f
```

三条线路 `outbound_id` 全为 `NULL` —— 规划与打印**没有**生成任何出库单。

停靠点 / 分配：

```text
delivery_route_stop  id3 route1 seq1 C(GCJ02) | id1 seq2 A(GCJ02) | id2 seq3 B(GCJ02)   ← 7.10 重排结果持久化
                     id4 route1 seq4 B'  WGS84 deleted=t                                ← 7.12 测试停靠点，随移除订单软删
                     id5 route2 seq1 B'  坐标 NULL deleted=f                              ← 7.11 未定位停靠点
delivery_route_order 35/36→stop1  37→stop2  38→stop3  RELEASED deleted=f
                     40→stop4 RELEASED deleted=t   40→stop5 RELEASED deleted=f
ACTIVE 计数 = 0     双 ACTIVE 查询（GROUP BY order_id HAVING count(*)>1 WHERE ACTIVE）= 0 行
```

约束与索引（`pg_constraint` / `pg_indexes` 实测存在）：

```text
ck_delivery_route_location / ck_delivery_route_stop_location   经纬度+CRS 三元成组、±180/±90 范围、CRS ∈ {GCJ02,WGS84}
delivery_route_status_check                                    DRAFT/PLANNED/CANCELLED（+预留态）
assignment_status ∈ {ACTIVE,RELEASED}   stop_seq > 0   version >= 0   order_amount_snapshot >= 0
delivery_route_route_no_key UNIQUE
uk_delivery_order_active   UNIQUE (order_id) WHERE deleted=false AND assignment_status='ACTIVE'
uk_delivery_stop_seq       UNIQUE (route_id, stop_seq) WHERE deleted=false
uk_delivery_stop_address   UNIQUE (route_id, customer_id, address_snapshot) WHERE deleted=false
地理 CHECK 覆盖：customer / warehouse / order_address_snapshot / delivery_route / delivery_route_stop
```

地理归属一致性：`order_address_snapshot` 中 `35–39` 完整带 `GCJ02` 坐标，`40` 三列全空（覆盖率用例所需，非缺陷）；客户 A/B/C 与仓库 `WH002` 统一 `GCJ02`，7.12 之后**未残留混合坐标系**。

库存 / 订单副作用核对（配送全流程前后同一条 SQL）：

| 指标 | 执行前基线 | 执行后 |
| --- | --- | --- |
| `inventory_balance` 行数 / 总数量 / 预留量 | 49 / 5357.5000 / 5.0000 | **完全相同** |
| `inventory_movement` 行数 | 131 | **131** |
| `inventory_outbound` / `_item` / `inventory_reservation` | 9 / 10 / 1 | **9 / 10 / 1** |
| 订单状态变更数（`status` 相对基线） | — | **0** |

操作日志：`t_operate_log` 中 `%delivery%` 共 55 条（成功 23 / 失败 32，失败全部为上述**故意的**负向用例），线路号、URL、方法名、`success_flag`、`fail_reason` 齐备；打印接口有独立审计行。

---

## 10. Bug 清单

**P0 = 0 P1 = 0 P2 = 0；验收轮 P3 = 6，修复验证阶段追加 P3-7（共 7）。** 无核心业务流程失败，无数据破坏。P3-1 已修复，其余按 §12 建议分级处理。

### P3-1 订单定点小数字符串契约不一致，且错误文案误导

- 严重级别：P3（当前 UI 不触发；对外接口调用方会踩）
- 页面 / 接口：`POST /scm/order/create`、`/scm/order/create-and-progress`
- 前置条件：以固定点小数字符串提交 `orderedQuantity`
- 复现步骤：请求体 `items[0].orderedQuantity = "10"`（整数串、无小数位）
- 预期结果：接受（文档口径 `ScmDecimalStrings.PATTERN = ^\d{1,14}(\.\d{1,4})?$` 允许 0–4 位小数），或至少给出「格式不符」类提示
- 实际结果：`{"code":40063,"msg":"数量必须大于零"}` —— 值本身合法，文案指向完全无关的原因；改成 `"10.0000"` 即成功
- 证据：5 张测试订单首轮全部 `40063`（A1/A2/B/C/A3），改用 4 位小数后全部成功
- 初步定位：`OrderValidator.decimal`（`.../order/manager/OrderValidator.java:25`）用 `[0-9]{1,14}\.[0-9]{4}` 要求**恰好** 4 位小数，与 `ScmDecimalStrings.PATTERN`（`.../common/util/ScmDecimalStrings.java:50`）不一致；两分支共用 `ORDER_QUANTITY_INVALID` 文案
- 是否稳定复现：是（100%）
- **修复状态（2026-09-21 验收后，两条建议同时落地）**：`OrderValidator.decimal` 改为委托 `ScmDecimalStrings.parseScale4Required`，因此 `10` / `1.5` / `1.5000` 均接受并统一归一化为 4 位小数，负数、科学计数法、超 4 位小数仍拒绝；新增 `ORDER_QUANTITY_FORMAT_INVALID(40076)` 承担「形态不合法」，`40063` 回归「数量必须大于零」单一语义，`40066` 文案改为「非负、至多 4 位小数的定点数」（编号不动）。验证：`OrderRulesTest` 32/32、`SalesOrderServiceIT` 12/12、`OrderUnpricedIT` 1/1（一次性临时库，V1→V43 全量迁移后删除）。采购域的同类严格形态（`40080` / `40081`）有类头明确设计依据，**未一并改动**。

### P3-2 `supplier` 缺「坐标与 CRS 必须成组」的表级 CHECK

- 严重级别：P3（潜在；当前 0 行受影响）
- 页面 / 表：`supplier`（M0 地理归属）
- 前置条件：直接比较三张主档的 CHECK
- 复现步骤：`pg_constraint` 列 `customer` / `warehouse` / `supplier` 的 CHECK
- 预期结果：三表同等强度的地理不变量
- 实际结果：`customer` / `warehouse` 有 `ck_*_location_complete`（三元成组 + 范围 + CRS 域），`supplier` 只有 `ck_supplier_coordinate_pair` + `ck_supplier_crs_with_coordinate` + `ck_supplier_geom_crs` + 经纬度范围 —— 即**允许「有经纬度但 `geom_crs` 为空」**
- 证据：约束清单实测（§9）；`SELECT count(*) FILTER (WHERE longitude IS NOT NULL AND geom_crs IS NULL) FROM supplier WHERE deleted=FALSE` = 0（22 行供应商全部无坐标）
- 初步定位：V40 建列时对 supplier 少建一个 complete-check
- 是否稳定复现：结构层面稳定；当前无脏数据

### P3-3 静态 `maxlength` 字符串触发 Vue prop 类型告警

- 严重级别：P3（控制台噪音）
- 页面：仓库编辑表单（`purchase/warehouse-list.vue:111`）及同类 63 处（含 `delivery/components/*`）
- 复现步骤：打开任一含 `<a-input maxlength="64">` 的表单
- 预期结果：无 console error/warn
- 实际结果：`[Vue warn]: Invalid prop: type check failed for prop "maxlength". Expected Number with value 64, got String with value "64"`
- 证据：交互期 console 捕获原文（§7）
- 初步定位：应写 `:maxlength="64"`；项目内两种写法并存（63 静态 / 59 绑定）
- 是否稳定复现：是

### P3-4 字典 store 遗留 `console.log`

- 严重级别：P3（仅 dev）
- 位置：`xsy-scm-web/src/store/modules/system/dict.ts:115` `console.log(this.dictList, 2)`
- 预期 / 实际：字典初始化不应向控制台倾倒对象；实际每次初始化打印 `Proxy(Array) 2`
- 证据：§7 捕获；`vite.config.ts` 的 `drop_console: true` 使生产构建无影响
- 是否稳定复现：是（仅 dev）

### P3-5 配送选项接口返回持久层实体

- 严重级别：P3（契约 / 信息最小化）
- 接口：`GET /scm/delivery/options/drivers`、`/options/vehicles`（`DeliveryRouteController.java:126/132`）
- 实际结果：返回 `List<DeliveryDriverEntity>` / `List<DeliveryVehicleEntity>`，含 `version`、`deleted`、`createdAt`、`createdBy` 等内部字段（SmartAdmin 其余选项接口返回精简 VO）
- 影响：下拉只需要 `id / 名称 / 编码`；实体形状成为隐式契约，后续加字段即泄漏到前端
- 是否稳定复现：是

### P3-6 候选订单弹窗预填配送日期，导致「看起来是空的」

- 严重级别：P3（引导 / 文案）
- 页面：线路详情 → 「加入订单」（`delivery/components/candidate-order-modal.vue:162`）
- 前置条件：线路配送日为 T，候选订单未填 `expect_delivery_time`
- 复现步骤：打开弹窗 → 列表为空 → 点「重置」→ 4 张合规订单全部出现
- 预期结果：默认口径与提示一致（明示「按线路配送日筛选，未填预计送达时间的订单不显示」）
- 实际结果：默认静默带日期条件，用户会误判为「没有候选订单」
- 证据：`open()` 内 `query.deliveryDate = value.deliveryDate`；本轮 7.8 即依赖「重置」才拿到数据
- 是否稳定复现：是

### P3-7 `ScmOrderMigrationIT` 的模式断言自 V42 起失效（修复验证阶段新发现）

- 严重级别：P3（测试资产失真，非产品缺陷；不影响任何运行时行为）
- 用例：`ScmOrderMigrationIT.approvedSchemaHasEightTablesNoDeadFieldsAndNullableDraftPrices:23`
- 复现步骤：对**任意**已应用 V42 的库跑该用例（一次性临时库与日常开发库结果一致）
- 预期结果：订单 8 表内所有 `numeric` 列均为 `NUMERIC(18,4)`
- 实际结果：`expected: 0 but was: 2` —— V42 给 `order_address_snapshot` 增加了 `longitude NUMERIC(11,8)` / `latitude NUMERIC(10,8)`，而该表在断言的 8 表清单内
- 证据：`information_schema.columns` 实测两行（一次性临时库与日常开发库相同）；V42 迁移起始处的列定义
- 影响：断言原意是「金额 / 数量精度统一」，写成「全部 numeric 列」后被 V42 的坐标列正当突破；此前 L0–L2 只跑了 `DeliveryRouteServiceIT`，因此未暴露
- 建议：把该断言的谓词收窄到金额 / 数量列（或显式排除坐标列）。属测试修正，待确认后再改，本次未改动

### 观察项（**不计为缺陷**）

- `scm:delivery:driver:query` / `vehicle:query`（菜单 1021 / 1031）在前端无 `v-privilege` 消费方——项目多数列表页（如供应商列表）同样不给「查询」按钮挂权限，属既有范式；后端 `@SaCheckPermission` 已生效。
- 7.13 之前一轮负向返回 `40921`、一次 `plan` 返回 `30001`，均为**我的脚本参数过期 / URL 拼错**（`routes/undefined/plan`），修正后得到 `40921` / `41108` / `40000`，不记产品问题。
- L3 缺失项（确认发货、自动出库、DISPATCHED / COMPLETED 实操、GPS 轨迹、司机端、签收、运费、自动排线 / OR-Tools、实时 ETA）按当前设计边界处理，页面**未暴露**任何坏按钮或占位入口，不记 Bug。

---

## 11. BLOCKED / NOT TESTED 项

| 项 | 状态 | 原因 |
| --- | --- | --- |
| 高德地图底图 / Marker / Polyline 视觉呈现、地址地理编码检索 | **BLOCKED / NOT CONFIGURED** | `VITE_AMAP_KEY` 三个环境文件均未配置；核心配送业务已改走手工经纬度入口完成验收，等价规则（成组、同 CRS、覆盖率门禁）已实证 |
| 地图可视化下「停靠顺序变化 → 连线顺序同步」的**肉眼**确认 | BLOCKED | 同上；顺序同步改由 `/routes/{id}/map` 返回序列 + `stop_seq` 落库证明 |
| 打印机物理输出 | **NOT TESTED** | 环境无真实打印机。浏览器打印预览为**实际测试**并通过（内容项、金额 / 数量 / 实重列、缺失实重显示「—」） |
| 非管理员角色的配送权限负向用例（无 `scm:delivery:*` 权限的账号访问接口 / 按钮隐藏） | **NOT TESTED** | 环境仅有 SUPER_ADMIN（`administratorFlag` 绕过校验），不新建角色以免污染测试库权限数据 |
| 重复分配 / 乐观锁**并发**压力（多线程同时抢单） | **NOT TESTED** | 未做压测；单线程强制提交下 `41103` + `uk_delivery_order_active` 双层拦截已验 |
| `LIMIT_EXCEEDED 41109`（501 单） | **NOT TESTED** | 需造 501 张订单，代价与收益不匹配；`@Size(max=500)` 与分支代码在位 |
| 对象存储（S3 / MinIO）模式下的 `private/*` 读权限行为 | **NOT TESTED** | 本环境 LOCAL，`/upload/**` 静态无守卫；F0 的权限语义须在对象存储模式单独验 |
| 远端 `https://xsy.leyingiot.com` 侧的 V43 验收 | **NOT TESTED** | 本轮按用户指示在本地栈执行；远端仅做匿名存活探测，未确认其 schema 版本，未写入任何数据 |

---

## 12. 最终结论

```text
共执行 80 项
PASS       67
FAIL        5      （全部 P3：P3-1 / P3-2 / P3-3+P3-4 合并的「控制台洁净」项 / P3-5 / P3-6）
BLOCKED     2      （高德可视化、高德地理编码检索 → NOT CONFIGURED）
NOT TESTED  6      （物理打印、非管理员授权、并发压测、41109 上限、对象存储权限语义、远端实例验收）

P0 = 0    P1 = 0    P2 = 0    P3 = 6
```

**验收后更新（同日）**：上面 80 项计数描述**验收执行时点**，不回填。其后 P3-1 已修复并通过单测 + 订单 IT 验证（见 §10 P3-1 修复状态），并在修复验证阶段新增 1 条测试资产类问题 P3-7（`ScmOrderMigrationIT` 模式断言自 V42 起失效）。当前开放项：P3-2 / P3-3 / P3-4 / P3-5 / P3-6 / P3-7。

判定：**V40 → V43 升级通过验收**。原有核心链路（商品 / 客户 / 供应商 / 定价 / 订单 / 采购 / 库存 9 页 / 大屏）在 35 条路由上零 console error、零 pageerror、零 HTTP≥400，库存与订单侧零副作用；V41 商品图片在「上传—保存—重开—刷新—匿名可读」全链路可用，写侧收口按预期返回 `40038` 且不产生数据提升；V42/V43 物流配送 L0–L2 的 26 条主用例与 10 条负向用例全部按预期收敛，数据库快照、约束、索引、乐观锁、审计日志与业务口径一致。

### 建议

**必须现在处理（阻断项）**：无。

**进入 L3 之前建议先处理：**

1. ~~**P3-1**（定点小数契约）~~ **已处理（2026-09-21）** —— `OrderValidator.decimal` 现复用 `ScmDecimalStrings` 的唯一规则，并把「形态不合法」拆到新码 `40076`，`40063` 只表达「必须大于零」。仍待单独裁决的是**采购域**：`PurchaseOrderValidator.decimal` 与 `PurchaseReceiptQuantityCalculator` 同样要求恰好 4 位小数（`40080` / `40081` 也共用文案），但类头写明了「请求/响应均为 4 位小数字符串」的设计依据，因此本次刻意未动；若要统一到同一口径，需要连 W5 的接口契约与 `PurchaseOrderValidatorTest` 一起改。
2. **远端实例一致性** —— 确认 `xsy.leyingiot.com` 的 Flyway 是否已到 V43、后端构建是否与本地一致；若不是，本轮结论不能直接外推到该实例（本地与远端是两套部署）。
3. **非管理员授权回归** —— V43 权限点只以 SUPER_ADMIN 验过。任何业务员 / 调度 / 司机角色上线前，必须补一轮「无权限账号 → 接口 403 / 按钮隐藏」的负向用例，并同时收口 F0-DEBT-01 读侧（`FileKeyVoSerializer` → `FileService.getFileList()` 无按人过滤）。

**可以延后（不阻断）**：P3-3 / P3-4 控制台清理（生产构建已 `drop_console`）；P3-5 选项接口收敛为 VO；P3-6 候选弹窗文案与默认口径；P3-2 给 `supplier` 补 `location_complete`（下次 schema 迁移顺带）。P3-7 不影响产品行为，但会让每次全量 `*IT` 常红，建议下次触碰订单测试时顺手收窄断言谓词。

**属于当前功能边界、不需要「修」**：确认发货 / 出库扣减 / DISPATCHED / COMPLETED 实操 / GPS 轨迹 / 司机端 / 签收 / 运费 / 自动最优排线 / 实时 ETA。L0–L2 只承诺静态规划，且已明确「规划不写库存」并在数据上证实。

**是否进入 L3：可以进入。** 判断依据只看 L0–L2 是否存在阻断性工程问题——不存在（0 P0/P1/P2，交互 / 接口 / 权限 / 数据四条线均闭环）。前置条件是上面第 2–3 条，尤其第 3 条：L3 会引入司机与仓库角色，非管理员授权与附件读侧收口必须在有真实业务角色之前完成，否则 L3 一上线就带越权面。另注意 L3 落地前需先裁决实发数量来源（分拣规则），`DeliveryEligibilityPolicy` 已是既定扩展点。

### 复现与证据留存

四类证据支撑上文结论：路由扫描（真实浏览器登录态逐路由等待表格出数，含 38 张截图与逐路由控制台 / 网络记录）、接口响应原文（正向与负向用例逐条）、数据库 `SELECT` 快照（Flyway 历史、线路 / 停靠点 / 分配、约束与索引、库存前后对照）、后端进程日志关键字扫描。原始件留在本机被 `.gitignore` 忽略的运行目录，不入库；复现方式是按本报告各表的接口路径与 SQL 口径在当前部署上重跑。
