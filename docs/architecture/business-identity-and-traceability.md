# XSY-SCM 业务编号、主数据标识与一物一码溯源设计（仓库核对修订版）

> 核对日期：2026-10-18
> 本地仓库：D:\Browser Download\xsy-scm / main / cb2e7338（读取时工作区无未提交变更）
> 状态：**当前工作区已修改 SPU/SKU/分类/标签发号并新增 V147（尚未迁移数据库/生产部署）；追溯部分仍为目标架构建议**
> 依据优先级：现有代码/SQL + docs/decisions.md + docs/requirements/产品功能需求基线.md；参考工程仅作对照，不能当作现行能力或已确认产品决定。

## 0. 撤回前几版不准确的建议

1. **撤回“所有单据每天重置六位流水”**：实际 ScmDocumentNumbers.format 使用北京时间日期和至少六位 PG 序列，序列全局递增、不按日重置。6 位只是最小展示宽度，不是容量硬上限；跳号正常。无充分理由不得改成每日重置。
2. **撤回随意定义的前缀表**：如 PR=采购退货、TR=调拨、ST=盘点、DL=配送等都不符合当前代码。实际 PR 是采购收货，TRF 是调拨，STK 是盘点，DR 是配送线路。禁止用空想字典覆盖在用编号。
3. **撤回把 P00001234、SKU00005678 或 FRU-APL-00128 当作行业统一标准**：SPU/SKU 的身份边界、供应商货号、包装、计量与追溯颗粒度应先确定。编码格式是次级选择；不能把可变分类/产地/价格/批次塞进稳定身份码。
4. **撤回“参考工程已有一物一码就能直接迁移”的暗示**：project-reference-examples 中 TraceCodeService 为一码一批，traceCode=batchNo，**并不是一箱一码/一件一码**。产品需求明确提出一物一码，二者存在差异。
5. **撤回“当前已有统一 BusinessNumberService 管理所有编号”的说法**：现有 ScmBusinessNoService 只服务 CUSTOMER/SUPPLIER/WAREHOUSE 主数据；各业务域各取 PG Sequence，并共享格式化工具。统一规范 ≠ 立即重写所有生成器。
6. **不使用编号推断数据可信性**：某日的日期段说明取号/业务日期策略，不证明采收时间、发货时间；一个订单号不是批次或唯一实物码；一张二维码不等于有可审计的追溯事实。

## 1. 当前代码核对：哪些已实现

### 1.1 单据编号（均为“前缀 + yyyyMMdd + 至少六位序列”，除明确列出的特殊情况）

| 领域 | 已存在的编号 | 真实前缀 | 生成位置/说明 |
| --- | --- | --- | --- |
| 销售订单 | 订单号 | SO | order/service/OrderNumberGenerator.java；独立 sales_order_no_seq |
| 销售退货 | 退货单号 | RT | 同上；order_return_no_seq |
| 订单退款 | 订单侧退款单号 | RF | 同上；order_refund_no_seq，不能混同支付网关退款 |
| 采购 | 采购订单号 | PO | purchase/service/PurchaseNumberGenerator.java；purchase_order_no_seq |
| 采购收货 | 收货单号 | PR | 同上；purchase_receipt_no_seq；PR **不是采购退货** |
| 库存出库 | 出库单号 | OUT | inventory/service/InventoryOutboundNumberGenerator.java |
| 库存调拨 | 调拨单号 | TRF | inventory/service/InventoryTransferNumberGenerator.java |
| 库存盘点 | 盘点单号 | STK | inventory/service/InventoryStocktakeNumberGenerator.java |
| 报损/报溢 | 损溢单号 | LGR | inventory/service/InventoryLossGainNumberGenerator.java |
| 规格转换 | 转换单号 | CVT | inventory/service/InventoryConversionNumberGenerator.java |
| 分拣 | 分拣任务号 | SRT | sorting/service/SortingTaskService.java；SortingConstant.TASK_NO_PREFIX |
| 配送 | 配送线路号 | DR | delivery/service/DeliveryRouteService.java；**日期来自指定 deliveryDate**，不是简单的当前日 |
| 财务应收 | 应收单号 | AR | finance/constant/FinanceConstant.java；各业务 Service 调用共享 formatter |
| 财务应付 | 应付单号 | AP | 同上 |
| 财务收款 | 收款单号 | RC | 同上 |
| 财务付款 | 付款单号 | PM | 同上 |
| 财务核销 | 核销单号 | WO | 同上 |
| 支付意图 | 内部意图编号 | PYI | payment/service/PaymentNumberGenerator.java |
| 支付交易 | 内部交易编号 | PYT | 同上 |
| 支付退款 | 内部退款编号 | PYR | 同上；与订单 RF 分层 |
| 支付对账 | 对账记录编号 | PYC | 同上；四种类型共用 payment_document_seq |

备注：余额充值及余额流水也已使用单独序列 + ScmDocumentNumbers，需在实施时补充其常量与全量注册表；此表不假装已经穷尽 146 个 Flyway 文件的所有字段。不同业务的共享序列/独立序列以及唯一键均以实际 SQL 为准。

### 1.2 主数据与商品：现状不一致

- 已有 ScmBusinessNoService / ScmBusinessNoType（CUSTOMER/CUS、SUPPLIER/SUP、WAREHOUSE/WH）；对应 V111 及 V113/V114 的序列补位逻辑。docs/decisions.md 已明确“内部编码后端生成，外部真实标识人工输入”。
- product_spu.spu_code、product_sku.sku_code 已存在；V6__scm_product.sql 对未删除记录设置唯一索引。
- **原 HEAD 存在的不一致**：ProductSpuAddForm.spuCode、ProductSkuForm.skuCode 原先使用 @NotBlank，Service 直接采信客户端输入。**2026-10-18 当前未提交工作区已更改**：后台新增 SPU/SKU 使用 ScmBusinessNoService（V147 新序列），编辑保持原编码，前端移除编码输入；实际 DB 迁移与业务回归尚未执行。
- 商品分类和标签由当前工作区新增后端发号，客户端输入不再参与创建/编辑；**计量单位码有标准单位语义，不自动换成无语义流水号**，创建时人工按约定录入、编辑时锁定。客户类型 GROUP 也是集团结算语义键，不参与普通流水发号。
- product_sku.barcode 与 sku_code 是不同字段；当前 barcode 的活跃唯一索引只是数据库约束，**不代表合法 GS1 GTIN**，也不支持一个 SKU 多条外部识别码的映射结构。
- 现有商品含标品/非标品、spec_values、sale_unit、mallMinQuantity、mallQuantityStep、多供应商等业务；后续码制不能破坏现有盘点 Excel 用 skuCode 匹配、销售导入、订单行 SPU/SKU 快照和打印模板。
- 当前活跃唯一索引允许软删除后编码被复用。对已流转的商品/对外打印的 SKU，**不建议复用历史码**；是否增加跨全历史唯一约束必须先检查存量重复和合规迁移。

### 1.3 一物一码真实进度

- 当前正式工程 xsy-scm-server/sa-admin 下，尚未在本次核对范围找到已落地的 trace_batch/trace_code、实物单元流水与 EPCIS 事件模型，**不能将参考工程当成正式模块已上线**。
- docs/requirements/产品功能需求基线.md 明确列“一物一码扫码溯源”；docs/decisions.md 的“后续产品工作边界”已明确 **以批次及包装码建立可追溯关系、公开查询使用批准字段白名单**。这已经确定 L1 批次与 L2 包装两个维度，但具体每袋/箱/筐的发码和收货现场操作仍要细化。
- 参考工程 project-reference-examples/xsy-scm/.../trace：TraceBatchEntity 包含外部生产批号、供应商、产地、生产日期；TraceCodeEntity / TraceCodeService 采用**一码一批**。可以参考字段与权限交互，不得直接复制其颗粒度来冒充“一物一码”。
- 已有库存/采购/分拣/配送/订单存在业务明细、实重及来源关联；应在这些确认事实中补充批次、实际单元、转换、出入库的追溯关联，而不是凭扫码页面反推不存在的真实物流事实。

## 2. 编号与身份的正式分层（统一的是语义，不是字符串模板）

### 2.0 编码字段归属决定表（实施准则）

**A. 系统发号：不进入前端编辑控件，也不展示“由系统生成”提示。** 使用数据库序列/安全唯一生成器及唯一约束；创建时生成，编辑/删除后不重编或复用：

| 编码 | 现有/目标规则 | 用户真实可维护的信息 |
| --- | --- | --- |
| 内部 ID | PG identity/主键，全部后端维护 | 无 |
| 客户/供应商/仓库 | 现行 CUS/SUP/WH + PG 序列 | 名称、联系人、地点、结算关系 |
| SPU/SKU | 本轮后端 SPU/SKU + PG 序列；存量保持不变 | 商品名称、销售属性、规格组合、销售单位、定价 |
| 商品分类/标签 | 本轮后端 CAT/TAG + PG 序列；存量保持不变 | 分类名称与父节点、标签名称 |
| 分拣和其他内部任务流水 | 现有业务域发号，不能重用工单业务语义 | 作业配置、确认人、来源单明细 |
| 追溯内部批次/逐包装实物码 | **目标方案，尚未开发**；批次身份与包装实物身份必须独立 | 来源批号、包装计量、质检/交接事实，不自行设计随机实物码 |

**B. 系统拼接：由用户输入业务事实，后端拼接最终展示号，不让前端自由拼字符串。**

| 已有对象 | 现行规则 | 决定性输入 |
| --- | --- | --- |
| 销售 SO、退货 RT、退款 RF | 已有前缀 + 北京时间业务日 + PG 全局单调序号 | 正式下单/退货/退款业务命令 |
| 采购订单 PO、收货 PR | 同上（PR 是收货，不是采购退货） | 下单/实际收货动作 |
| 库存 OUT/TRF/STK/LGR/CVT | 同上 | 出库/调拨/盘点/损溢/规格转换动作 |
| 分拣任务 SRT | 同上 | 正式创建分拣任务 |
| 配送线路 DR | 前缀 + **选择的配送日期** + 不重置序列 | 操作员选择配送日期和停靠信息 |
| 财务 AR/AP/RC/PM/WO | 领域前缀 + 日期 + 各自序列 | 已确认业务/应收/应付/收付款事实 |
| 支付 PYI/PYT/PYR/PYC | 类型化前缀 + 日期 + 共享支付序列 | 支付域正式状态操作；渠道交易号另存 |

**C. 用户可输入/选择的代码：它们有外部或业务语义，不能替换为无意义的系统流水。**

| 代码 | 输入口径 | 约束 |
| --- | --- | --- |
| 商品条码/正规 GTIN、供应商货号、第三方平台商品码 | 用户录入或外部同步 | 分配主体、格式、校验、唯一范围分别维护；内部 SKU ≠ GTIN |
| 单位 UOM（kg、箱等）编码 | 受权人员根据已有单位字典维护 | 已关联交易后锁定编码和名称；规格换算另管理 |
| 客户类型 GROUP 及其他受业务代码直接引用的语义类型 | 受权管理员创建时配置，日常不要求普通用户编写 | 编码固定后不在普通编辑中更改；更名不影响集团结算条件 |
| 系统角色/权限/字典等技术配置键 | 配置/开发人员维护 | 不作为普通业务主数据流水，保持与代码鉴权协议一致 |
| 外部生产批号、原产地证明号、检验报告号 | 上游提供或现场登记 | 原样保存与来源方绑定，不把它冒充 XSY 发的 lot_id |
| 车牌、税号、税收分类编码 | 有权人员按真实资料填写 | 按外部规则校验；不能自动编造 |

**交互要求**：内部发号字段从新增/编辑的前端 payload 与校验逻辑移除；列表/详情/打印/导出仍提供只读查询。用户维护的语义配置/外部真实编码才有输入控件；不得以“自动生成”提示替代后端发号。旧编号/外部对账/存量 Excel 必须兼容，不得通过编辑或导入偷偷修改已有内部码。

**演示数据与正式规则的边界**：正式发号规则只由 Java 服务与 Flyway 序列实现，本文件是唯一规则来源。开发/演示环境的初始化脚本可以重建演示数据并按发号形态写入编码，但那是**环境数据修复**，不是系统规则：正式库已发放的内部编码不重编、不复用，也不允许用“整体重编号”作为上线或升级手段。演示脚本必须可重复执行且带破坏性护栏，不得把它的实现细节当成编码规范。



| 身份类型 | 内部主键 | 对外码 / 字段 | 允许包含日期 | 是否允许重编或复用 | 与追溯的关系 |
| --- | --- | --- | --- | --- | --- |
| 商贸主体、客户、供应商、仓库 | 稳定 id | customer_code / supplier_code / warehouse_code | 否 | 发生外部引用后原则上不复用 | 参与方与事件位置 |
| 商品族 SPU | spu_id | spu_code | 不推荐 | 已交易后不可静默修改，禁止复用历史身份 | 型号/商品族，不代表实物 |
| 可销售库存规格 SKU | sku_id | sku_code | 不推荐 | 同上 | 表示可销售/可计价/可备货的规格 |
| 外部商品码 | 映射行 id | barcode / GTIN / 供应商货号 / 平台商品 id | 按外部标准 | 保留原持有者与生效记录 | 跨系统识别，不替代 sku_id |
| 业务单据 | doc_id | order_no / receipt_no / task_no 等 | 可 | 已发号不复用 | 关联业务事实 |
| 来源批次 | source_lot_id | 外部生产/采收批号 + 主体 | 依来源约定 | 原样保留 | 来自供应商/基地的输入证据 |
| 本系统追溯批次 | lot_id | 内部 lot_no | 可 | 已收货/创建后不复用 | 共同来源/处理履历 |
| 被贴码实物单元 | trace_unit_id | 不透明且全局唯一 unit_code | **不嵌入日期或敏感信息更适宜** | 永不复用 | 真正的“一物一码”扫描对象 |
| 物流/周转单元 | handling_unit_id | 内部 HU 码 / 正规 SSCC（如有资质） | 各自约定 | 永不复用 | 装箱/托盘/车辆交接 |
| 物理事件 | event_id | event_id + source_system + idempotency_key | 时间在独立字段 | 仅追加/补偿更正 | 谁在何时何地对什么做了什么 |

**不应把商品名称、分类树路径、当前供货商、时价、产地、任意重量直接塞进永久 SPU/SKU 编号**：分类与供应关系可改变，产地往往是批次事实，重量可能是本次称重结果。人需要识别的是“名称+规格+别名+查询码”，不是从永久编号解码全部业务事实。

### 2.1 SPU / SKU 身份判定

- SPU：业务上认定的同一商品主体；允许多个 SKU；身份主属性变化要走新建/替代关系审议。
- SKU：独立报价、履约、库存管理、包装/销售规格所需的变体。红富士散称与固定 5kg 箱装，若库存与履约单元不同，可以独立 SKU。
- 同一散称 SKU 的本次 0.8kg、1.3kg，不因为称量不同产生两个 SKU；实际重量保存在称重/库存/追溯单元事实中。
- 同一 SKU 由不同供应商交货通常不新建 SKU；通过供应商商品映射、价格、来源批次关联。若品牌承诺/质量等级/产地保证本就是交易规格，则另行判定 SKU 身份。
- 多包装规格与采购单位/销售单位换算独立建模；“斤、kg 转换”不自动变成两个 SKU。
- 生成策略：**先稳定身份边界与现有存量映射，再确定不可变、可配置的系统内部编码策略**。可以保留历史人工码，新增改成后端发号；不要贸然批量改造成凭名字/分类生成的半语义码。对商家/供应商熟悉的编码保留 alias/external_code。

## 3. 全业务引用关系：编号不能代替源单明细 ID

| 业务流程 | 系统必须保留的身份和关联 | 对未来追溯的最小要求 |
| --- | --- | --- |
| 商品建档与上下架 | SPU、SKU、外部码、规格、计量单位 | 配置追溯级别及可变重量规则，不赋予假“原产地证明” |
| 供应商、基地、农场 | 供应商/生产主体、基地/地块、供应商货号 | 来源方及资质/证明的版本信息 |
| 采购订单、采购需求 | PO + 明细行 id、sku_id、计划量 | 计划与实际到货事实分离 |
| 多次采购收货 | PR + 明细行 id、供应商批次、每次实际收货量 | 一笔 PO 可分多次 PR；每次可涉及多个批次 |
| 质检、抽检 | 检验任务、检验结果、取样对象、附件 | 哪个批次/实物被检、状态如何变化 |
| 入库与库存 | 仓库/库位、sku、lot、质量/权属状态、账本流水、单位 | 库存余额与库存账本可按批次追查；不能只存 sku+总量 |
| 分拣称重 | SRT + 任务明细、称重记录及确认时间 | 记录货源 lot / 单元与实际净重；仅确认后的事实参与流转 |
| 规格转换/分装/混装 | CVT + 输入明细/批次 + 输出明细/批次 + 损耗 | 输入→输出多对多谱系与数量守恒规则 |
| 库存调拨、出库 | TRF/OUT + 明细、源/目标位置、批次 | 批次实物的出入方向、占用/释放/确认区别 |
| 配送线路、装车、签收 | DR + 装车/交接/签收事件、箱/筐/订单行关联 | 同一个箱的父子包装关系随时点可重建 |
| 商城订单与售后 | SO/RT/RF + 订单行快照、来源出库/收货记录 | 能从客户订单行追到真实来源批次/单元；退货不能覆盖原轨迹 |
| 财务收付与支付 | AR/AP/RC/PM/WO、PYI/PYT/PYR/PYC | 资金单据引用交易事实，不当作食品实物流转事件 |
| 召回、异常与销毁 | 召回任务、冻结/召回/销毁事件、责任人 | 根据批次/单元查上下游，执行后的结果可对账 |
| 消费者查询 | 公共码解析、授权披露的履历摘要 | 不直接暴露采购价、客户资料、司机隐私、内部审计信息 |

特别强调：**没有采购退货/生产工单/装箱实体的现行实现证据时，不凭空为它们宣布某个“已实施单号前缀”**；确需新增时先审批业务对象、唯一域、生命周期和追溯粒度，再登记号段。

## 4. “一物一码”必须区分三种级别

| 级别 | 实际贴码/识别对象 | 示例 | 能承诺的能力 |
| --- | --- | --- | --- |
| L1：批次级（一码一批） | 批次公共展示码 | 散装同批蔬菜一张批次标签 | 查批次来源、检测和整体出入库；**不能知道消费者拿到哪一颗** |
| L2：包装/实物单元级 | 每一袋/箱/筐独立码 | 一箱固定包装苹果一个不可重复码 | 某个实际包装单元的装箱、仓储、运输、签收轨迹 |
| L3：单件级 | 物理每一个单件独立码 | 高价值可逐个标记货品 | 每件唯一身份、单件生命周期、精确召回 |

**已确定的产品方向（docs/decisions.md）：同时覆盖 L1 批次及 L2 包装码，消费者只查批准字段白名单。** 散装、无法逐件贴码的商品在批次级追踪；实际可交接的袋、箱等以独立包装码追踪；L3 单件级暂不能声称已确定或已实现。后续应进一步确认包装颗粒度、打印/贴码设备和现场扫码流程，而不是退回到“一码一批”代替包装码。

### 4.1 码的物理载体和标识规范

- unit_code 建议是随机/不可枚举的稳定标识，至少保证全局唯一；打印或 QR URL 本身不承载个人、价格等敏感信息。
- 动态解析入口可以使用系统授权域名与随机 token；token 不等于实物主键，可撤销/换域/保留历史。扫码展示记录“证据来源、时间、是否经审核”，不能仅凭印了一张码就宣称真实产地。
- 正式 GS1 GTIN、SSCC、GLN 有规则和分配资格；没有合法分配就使用明确标注的 XSY 内部码，不要伪造。
- GS1 Digital Link 的 01 为 GTIN、10 为批次、21 为序列；只有拥有相应合法标识且满足标准格式时才生成兼容 URI。不能把内部 SKU 字符串塞进 01 伪装标准 GTIN。
- 每次称重的实际净重/皮重、计量单位、称重设备、校准/操作人、称量时间独立存储；可变重量不靠 SKU 末尾拼数字解决。

## 5. 目标数据模型（供后续 Flyway 新版本实施；目前**未建表**）

1. trace_source_lot：保留外部批号及发码主体/供应商、工厂、基地、采收/生产/到货资料、证明附件与版本。外部批号不能假设全局唯一；联合来源主体、货品及来源类型识别。
2. trace_lot：内部批次 identity、sku_id、状态、生产/失效/接收信息；供应商送货、多次收货和内部库存批次不能强行一一对应。
3. trace_lot_origin（或等价来源关系）：内部批次→多个上游批次，关联数量、单位、来源收货/转换明细；混批后仍能回溯。
4. trace_unit：真正逐包装/逐件唯一的 unit_code、sku_id、lot_id（仅单批次包装的快捷关系）、数量单位、净重、状态、发行/作废信息；混批包装由内容表明细表达。
5. trace_handling_unit 和 trace_handling_unit_content：物流箱/托盘/周转筐与内容物的**时效性**父子关系；支持装入/取出/拆箱/合箱，禁止递归循环与重复占用。
6. trace_event：不可变 event_id、event_type、occurred_at、recorded_at、actor、organization、location、biz_step、status、source_system、source_doc_type/id/line_id、request_id/idempotency_key、凭证与更正指向。事件可以被补偿/更正，但不原地抹除历史。
7. trace_event_subject：事件与批次、实物单元、物流单元等多对多关联，以及相应数量/单位。
8. trace_transformation_input / output：加工、分选、重新包装的投入批次/单元与产出批次/单元、数量、损耗及单位换算；审核数量守恒或说明损耗。
9. trace_identifier_mapping：内部对象与供应商标签、内部 QR token、授权 GTIN/SSCC 等外部标识的分配者、生效状态、关联关系。
10. trace_recall_case、trace_recall_scope、trace_recall_action：召回范围、冻结数量、通知对象、处理结论；需要与库存可用量和真实客户去向核对。

原则：**不要为了现在的规格转换再凭空造一套与库存账本平行的“假库存”**；追溯引用正式已确认单据和库存流水作为事实来源，记录更多维度与证据，避免二次扣库存。

### 5.1 三个核心场景必须能走通

- **同批到货两次**：供应商批号 A，同一 PO 分两次 PR 到货；对应两个收货事实及可能不同质检状态，可保留同一上游来源或拆内部批次，不能只用采购单号替代 lot。
- **散称称重后装箱**：原 lot A 的 3.42kg 苹果装成 unit X，另一个 4.15kg 装成 unit Y；X/Y 是两个实物码，SKU 不变，真实重量在装箱/称重事件中。
- **两批混装并退货**：lot A 和 lot B 混装产生输出批次 C，分箱后单箱 unit Z 被客户退回；通过转换投入/产出、装箱、出库、签收、退货事件双向追到 A/B 与受影响客户。

## 6. 变更策略与先后次序（防止直接改坏现网）

**阶段 A：只做数据与代码审计，不迁移**

- 扫描全部后台/商城/采购/库存/分拣/配送/财务使用业务码的 DTO、SQL、打印、导入模板和对外 API，登记字段、唯一域、前缀、取号序列、业务日期、长度、软删除策略。
- 查 SPU/SKU 现有前缀/长度/重复/空白/手工编码来源；检查前后端商品创建、批量导入、盘点 Excel、客户映射与历史订单快照。对“自动生成”作出兼容方案。
- 检查实际数据库版本与迁移集合；Git 有 V140 等迁移文件，**不能用历史部署资料的 V40 充当前开发分支版本，也不能推断生产已经迁移到 V140**。
- 制定“编号不可重用、创建后是否允许修正”的数据库验收策略；不同业务域已有唯一键，先核对再加约束。

**阶段 B：商品身份治理**

- 维持既有 id、已有编码和所有外键/历史快照；新增商品 SPU/SKU 后端发号能力，在表单创建与 Excel 导入路径统一使用；保存历史编码映射。
- 商品规格身份判定规则、外部条码/供应商货号与内部码分离；更新涉及身份实质变化时新建 SKU 和替代关系，已交易 SKU 禁止静默改码。
- 将统一编号注册表、库表唯一约束、错误码及前端只读展示落实，而不是在前端拼码。
- 当存量数据与新规则冲突时制定数据修复清单，不直接批量重编号，更不修改已经发给客户的外部单号。

**阶段 C：批次级底座**

- 从“采购收货确认→批次入库→库存流水→分拣称重→销售出库/发车→签收/退货”打通 lot 引用。空缺的质检、基地证明应标识为“未采集”，不得生成虚假事实。
- 同 SKU 多批次、同来源多次收货、FEFO/隔离库存/失效批次校验；新增业务前先有隔离和权限。

**阶段 D：独立实物码与包装关系**

- 实际发码/补码/作废、离线扫码重放防重、首次绑定防串码、拆包合包、称重附件、失效和召回；批次公共码与逐箱唯一 token 同时支持。
- 标签模板和客户查询只显示被授权且有来源的资料；扫码记录不等于单据业务状态变更。

**阶段 E：跨系统交换与 GS1 适配**

- 先稳定内部事件契约和字段，按需要输出 EPCIS 2.0 Object/Aggregation/Transformation 等事件与其主数据映射，不以 EPCIS 标准名称替代实现验证。
- 若正式对外供应链使用 GS1 键，核查分配权和合法 GTIN/SSCC，完成与内部主键、客户订单的双向映射。

## 7. 必须通过的验收条件

1. 后端并发创建同类单据编号不重复；测试跨北京时间零点、超过 999999、事务回滚跳号、同幂等键重试。
2. 商品新增和导入不再依赖用户手工填内部 SPU/SKU 编码；历史合法编码不改；创建后不因分类、价格、供应商、称重变化而重编。
3. 商品条码/GTIN/供应商货号分别校验，内部码不会被宣称为有效 GTIN 或 SSCC。
4. 同一个 PO 分两次 PR 收货，能按收货明细、批次和库存台账定位，不混淆采购退货与 PR。
5. 同一 SKU 的两次称重各得不同实物码；该实物码只能绑定一件/一箱当前有效单元。
6. 拆箱/合箱以后按事件时点查询仍能重建原包装关系；混批/重包的输入、输出数量和损耗可审计。
7. 从批次能正向查受影响的在库、在途、已售、退货单元；从客户订单行能反向查各实际来源。
8. 重复扫码/网络重试不会重复入库、扣库存、签收、付款；纠错操作不删除原事件。
9. 无法证明的产地、检测或采收信息明确显示未提供/未核实，不伪造“全程溯源”。
10. 历史打印/客户系统/Excel 查询路径在灰度与回滚期间仍可识别旧号；日志能记录新旧映射。

## 8. 本次仓库参考路径与行业标准

**正式工程**：
- docs/decisions.md（编码后端生成的既有决策）
- docs/requirements/产品功能需求基线.md（包含一物一码）
- xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/common/util/ScmDocumentNumbers.java
- xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/common/no/ScmBusinessNoService.java
- xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/common/no/ScmBusinessNoType.java
- xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/product/service/ProductSpuService.java
- xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/product/manager/ProductSkuSyncManager.java
- xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/product/domain/form/ProductSpuAddForm.java
- xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/product/domain/form/ProductSkuForm.java
- xsy-scm-server/sa-admin/src/main/resources/db/migration/V6__scm_product.sql
- xsy-scm-server/sa-admin/src/main/resources/db/migration/V111__scm_business_no_sequences.sql

**仅供差距对照的参考工程**：
- project-reference-examples/xsy-scm/xsy-scm-server/xsy-scm-server/src/main/java/com/xsy/scm/admin/module/business/trace/service/TraceCodeService.java
- project-reference-examples/xsy-scm/xsy-scm-server/xsy-scm-server/src/main/java/com/xsy/scm/admin/module/business/trace/domain/entity/TraceCodeEntity.java

**外部标准（不等于 XSY 已认证/已实现）**：
- GS1 Global Traceability Standard：https://www.gs1.org/standards/gs1-global-traceability-standard/current-standard
- GS1 EPCIS 2.0.1：https://ref.gs1.org/standards/epcis/2.0.1/
- GS1 Digital Link URI Syntax 1.7.0：https://ref.gs1.org/standards/digital-link/uri-syntax/1.7.0/
- GS1 Digital Link Quick Start：https://ref.gs1.org/docs/2024/digital-link-quick-start-guide

> **实施边界**：本次只纠正架构规范并核对当前仓库代码，没有改动 Flyway、生产数据、正式服务或现有号码。上述“阶段 B–E”是开发工作，不是已经完成的事实。
