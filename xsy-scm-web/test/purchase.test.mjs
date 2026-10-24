/**
 * 采购模块契约测试。
 *
 * 由 6 个独立测试文件按模块合并：每个 `{ … }` 块对应原来一份文件，
 * 块内 helper / 常量各自作用域隔离；只有 import 提到顶部并去重。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {
  SCM_DEMAND_SUMMARY_STATUS_COLOR,
  SCM_DEMAND_SUMMARY_STATUS_ENUM,
  SCM_PURCHASE_EXPORT_COLUMNS,
  SCM_DEMAND_STATUS_ENUM,
  SCM_WAREHOUSE_STATUS_ENUM,
  SCM_PURCHASE_TABLE_ID,
  SCM_RECEIPT_STATUS_ENUM,
  SCM_PUTAWAY_STATUS_ENUM,
  SCM_PURCHASE_STATUS_ENUM,
  SCM_RECEIPT_MODE_ENUM,
  SCM_PURCHASE_OPERATION_ENUM,
} from '../src/constants/business/scm/purchase-const.ts';
import {
  quantity,
  validateOrder,
  unitMismatch,
  amount,
  price,
  allocatedOnItem,
  validateConfirm,
  hasAllocation,
  progress,
  fixed,
  allocationCapacity,
  newConfirmLines,
  newOrder,
  newAllocation,
  confirmPayload,
  beyondRemaining,
  payload,
  toleranceHint,
  newOrderItem,
  isNonStandard,
} from '../src/views/business/scm/purchase/purchase-form-model.ts';
import {purchaseError} from '../src/views/business/scm/purchase/purchase-errors.ts';
import {toWarehousePayload} from '../src/views/business/scm/purchase/warehouse-form-model.ts';

// ---------------------------------------------------------------- 来源：purchase-list-contract.test.mjs
{
  /**
   * 采购需求、收货、日志、仓库的前端契约测试。
   *
   * 这四个页面此前只做过「操作列居中」（commit ffa95370 是 8 文件 16 行的 align 改动），
   * 列精简与动作分组都没有做。本文件钉住三件容易回退的事：
   *
   * 1. 越界的操作列宽度：普通操作列 120～160px，
   *    而采购收货此前是 250px。
   * 2. 日志页的时间不能隐藏，裸 ID 不能上列：日志是审计凭据，
   *    「采购单 id」这种内部主键对排查没有价值，且会被误读成单号。
   * 3. 危险动作不能与普通动作同排常驻：删除 / 停用必须进「更多」，
   *    否则操作列里最显眼的永远是那排低价值或红色的文字。
   */

  /** 读源码并剥掉注释（先剥 HTML 注释再剥块注释，顺序不能反）。 */
  function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  const BASE = '../src/views/business/scm/';
  const demand = code(`${BASE}purchase/purchase-demand-list.vue`);
  const receipt = code(`${BASE}purchase/purchase-receipt-list.vue`);
  const purchaseLog = code(`${BASE}purchase/purchase-log-list.vue`);
  const warehouse = code(`${BASE}purchase/warehouse-list.vue`);

  /** 取出「操作」列声明里的 width；取不到就断言失败，不返回默认值。 */
  function actionWidth(source, name) {
    const matched = /dataIndex: 'action'[\s\S]{0,80}?width: (\d+)/.exec(source);
    assert.ok(matched, `${name}：找不到操作列的 width`);
    return Number(matched[1]);
  }

  test('采购需求：来源、批次、商品和编码分列，常用列前置', () => {
    assert.match(demand, /title: '来源单号', dataIndex: 'salesOrderNoSnapshot'/);
    assert.match(demand, /title: '需求来源 \/ 批次', dataIndex: 'calculationBatchId'/);
    assert.match(demand, /title: '商品名称', dataIndex: 'productName'/);
    assert.match(demand, /title: '商品规格编码', dataIndex: 'skuCode'[\s\S]{0,80}showFlag: false/);
    assert.ok(!demand.includes(`title: '需求单位'`), '采购需求仍有独立的需求单位列');
    // 单位跟在每个数量后面（量纲注脚），三个数量列都要有
    assert.match(demand, /dataIndex === 'requiredQuantity'[\s\S]{0,200}record\.demandUnit/);
    assert.match(demand, /dataIndex === 'unallocatedQuantity'[\s\S]{0,200}record\.demandUnit/);
    assert.match(demand, /dataIndex === 'skuCode'[\s\S]{0,100}record\.skuCode/);
    assert.match(demand, /const columns = ref<DemandListColumn\[\]>\(/);
    // scrollX 必须由列宽派生，不能写死数字；列宽与铺满统一由 useScmTableFill 计算。
    assert.match(demand, /useScmTableFill\(columns\)/);
    assert.match(demand, /:scroll="\{\s*x:\s*scrollX\s*\}"/);
    assert.match(demand, /dataIndex: 'requiredQuantity'[\s\S]{0,300}dataIndex: 'unallocatedQuantity'[\s\S]{0,300}dataIndex: 'status'/);
    assert.match(demand, /<TableOperator v-model="columns"/);
  });

  test('采购收货：操作列收到 160px 以内，编辑与删除进「更多」', () => {
    const width = actionWidth(receipt, '采购收货');
    assert.ok(width <= 160, `采购收货操作列 ${width}px 超出 160px`);
    // 当前状态唯一的推进动作仍常驻行内
    assert.match(receipt, /record\.status === 'DRAFT'/);
    assert.match(receipt, /record\.putawayStatus === 'PENDING'/);
    // 低频 / 危险动作收进「更多」，并走 hasPermission 裁剪（v-privilege 对菜单项不生效）
    assert.match(receipt, /ScmActionMore/);
    assert.match(receipt, /hasPermission\('scm:purchase:receipt:update'\)/);
    assert.match(receipt, /hasPermission\('scm:purchase:receipt:delete'\)/);
  });

  test('采购日志：时间必须保留，裸主键列必须移除或改名', () => {
    // 时间字段例外：日志页时间不能被隐藏
    assert.match(purchaseLog, /title: '时间'/);
    // 内部主键不是业务标识，上列只会被误读成单号
    assert.ok(!purchaseLog.includes(`title: '采购单 id'`), '采购日志仍有裸的「采购单 id」列');
    // 内部主键不进入日志列表；若未来恢复关联列，必须点明它是内部编号
    assert.doesNotMatch(purchaseLog, /title: '(采购单 id|关联收货单（内部编号）)'/);
    assert.match(purchaseLog, /title: '操作人'/);
    assert.match(purchaseLog, /title: '原因'/);
  });

  test('仓库：创建时间下沉，定位状态可见，启停进「更多」', () => {
    assert.ok(!warehouse.includes(`title: '创建时间'`), '仓库列表仍默认展示创建时间');
    const width = actionWidth(warehouse, '仓库');
    assert.ok(width <= 160, `仓库操作列 ${width}px 超出 160px`);
    // 仓库编码是运营配置识别字段，保留
    assert.match(warehouse, /title: '仓库编码'/);
    // 所在地区与详细地址各自成列：地址是库管实际找货的凭据
    assert.match(warehouse, /title: '所在地区', dataIndex: 'areaText'/);
    assert.match(warehouse, /title: '详细地址', dataIndex: 'address'/);
    // 定位状态是「一眼要挑出来」的信号，用图标 + Tooltip
    assert.match(warehouse, /title: '定位'/);
    assert.match(warehouse, /isLocated\(record\)/);
    // 启用 / 停用是状态机动作，不与普通动作同排常驻
    assert.match(warehouse, /ScmActionMore/);
    assert.match(warehouse, /hasPermission\('scm:warehouse:disable'\)/);
    assert.match(warehouse, /hasPermission\('scm:warehouse:enable'\)/);
  });

  test('四个采购页面的操作列全部居中', () => {
    for (const [name, source] of [
      ['采购需求', demand],
      ['采购收货', receipt],
      ['采购日志', purchaseLog],
      ['仓库', warehouse],
    ]) {
      // 列声明有单行与多行两种写法，不能假设 align 紧跟在 dataIndex 后面
      assert.match(source, /dataIndex: 'action'[\s\S]{0,80}?align: 'center'/, `${name}的操作列未居中`);
    }
  });
}

// ---------------------------------------------------------------- 来源：purchase-inquiry-contract.test.mjs
{
  const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');
  const api = read('../src/api/business/scm/purchase-inquiry-api.ts');
  const page = read('../src/views/business/scm/purchase/purchase-inquiry-list.vue');
  const batchDrawer = read('../src/views/business/scm/purchase/components/purchase-demand-batch-detail-drawer.vue');

  test('询价与报价写请求复用采购幂等封装，确认需明确版本', () => {
    assert.match(api, /purchaseCommand<PurchaseInquiryDetail>\(prefix \+ '\/create', data(?:, options)?\)/);
    assert.match(api, /purchaseCommand<PurchaseInquiryDetail>\(prefix \+ '\/quote', data(?:, options)?\)/);
    assert.match(api, /purchaseCommand<PurchaseInquiryDetail>\(prefix \+ '\/accept', data(?:, options)?\)/);
    assert.match(page, /quoteId:\s*quote\.id,\s*version:\s*current\.version/);
    assert.match(page, /采购单仍需单独创建和提交/);
  });

  test('询价批次分页和报价修订历史都使用服务端分页', () => {
    assert.match(api, /postRequest(?:<[^']*>)?\(prefix \+ '\/batch', \{batchId, page\}(?:, options)?\)/);
    assert.match(api, /postRequest(?:<[^']*>)?\(prefix \+ '\/history', \{inquiryId, beforeId\}(?:, options)?\)/);
    assert.match(page, /quoteHistoryMore/);
    assert.match(page, /onPageChange/);
    assert.match(page, /c\.existingInquiryId/);
  });

  test('询价从冻结批次进入，当前报价与历史版本分别展示', () => {
    assert.match(batchDrawer, /\/purchase\/purchase-inquiry-list/);
    assert.match(page, /detail\.currentOffers/);
    assert.match(page, /:data-source="quoteHistory"/);
    assert.match(page, /nextQuoteBeforeId/);
    assert.match(api, /\/history'/);
    assert.match(page, /scmDrawerWidth\('xl'\)/);
    assert.match(page, /v-privilege="'scm:purchase:inquiry:accept'"/);
  });
}

// ---------------------------------------------------------------- 来源：demand-summary-preview-contract.test.mjs
{
  /**
   * 订单汇总 / 库存缺口预览的前端契约测试。
   *
   * 保护这一「只读辅助决策」最容易被悄悄违背、且违背后不报错的几条线：
   * 1. 计算状态枚举与后端 `PurchaseDemandSummaryVO.calculationStatus` 逐字对齐（5 值），颜色映射全覆盖；
   * 2. 预览端点走只读 `postRequest`（无幂等键），且不得复用/改写 `generate`；
   * 3. 前端绝不重算 `availableQuantity` / `shortageAgainstAvailable`，也不引入浮点 / Decimal 运算；
   * 4. 在途采购是否抵扣缺口尚无结论，`openPurchaseQuantity` 不得出现在前端契约里。
   */

  /** 读源码并剥掉注释 —— 门禁只针对代码，头注释里提到被剪枝的名字是合规的。 */
  function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  test('summary calculation status enum matches the backend 5 states and every value has a color', () => {
    assert.deepEqual(Object.keys(SCM_DEMAND_SUMMARY_STATUS_ENUM), ['STOCK_ENOUGH', 'SHORTAGE', 'ZERO_STOCK', 'UNIT_MISMATCH', 'NO_BALANCE']);
    assert.deepEqual(
      Object.values(SCM_DEMAND_SUMMARY_STATUS_ENUM).map((e) => e.value),
      Object.keys(SCM_DEMAND_SUMMARY_STATUS_ENUM)
    );
    for (const key of Object.keys(SCM_DEMAND_SUMMARY_STATUS_ENUM)) {
      assert.ok(SCM_DEMAND_SUMMARY_STATUS_COLOR[key], `${key} 缺少 tag 颜色映射`);
    }
  });

  test('summary-preview is a read-only postRequest and does not touch generate', () => {
    const api = code('../src/api/business/scm/purchase-demand-api.ts');
    assert.match(
      api,
      /summaryPreview:\s*\(data:\s*DemandSummaryPreviewQuery(,\s*options\?:\s*RequestOptions)?\)\s*=>\s*postRequest(?:<[^']*>)?\('\/scm\/purchase\/demand\/summary-preview'/
    );
    // 只读：预览自身不得走带 Idempotency-Key 的命令式封装（只看 summaryPreview 到其 postRequest 之间）
    assert.doesNotMatch(api, /summaryPreview:[^\n]*purchaseCommand/);
    // generate 端点仍是原样（未被预览改写语义）
    assert.match(api, /purchaseCommand<GenerateResult>\('\/scm\/purchase\/demand\/generate'/);
  });

  test('preview component renders backend-derived quantities and never recomputes available or gap', () => {
    const src = code('../src/views/business/scm/purchase/components/purchase-demand-summary-preview.vue');
    assert.match(src, /scm-purchase-demand-summary-preview-table/);
    assert.match(src, /purchaseDemandApi\.summaryPreview/);
    assert.match(src, /SCM_DEMAND_SUMMARY_STATUS_ENUM\[record\.calculationStatus\]/);
    // 数量全部后端算好，前端只渲染，绝不自己算可用量 / 差额
    assert.doesNotMatch(src, /Decimal/);
    assert.doesNotMatch(src, /availableQuantity\s*[-+]/);
    assert.doesNotMatch(src, /onHandQuantity\s*[-+]\s*reservedQuantity/);
    assert.doesNotMatch(src, /orderDemandQuantity\s*[-+]/);
    // 审计修复：预留必须分「本批自身 / 其他业务」两段，且旧的 shortageAgainstAvailable 语义已废弃
    assert.match(src, /selectedOrderReservedQuantity/);
    assert.match(src, /otherReservedQuantity/);
    assert.match(src, /stockAvailableForSelectedOrders/);
    assert.doesNotMatch(src, /shortageAgainstAvailable/);
    // 差额不是采购建议：文案必须显性说明
    assert.match(src, /不是最终净采购建议/);
  });

  test('undecided in-transit deduction stays out of the frontend contract', () => {
    const types = code('../src/views/business/scm/purchase/purchase-types.ts');
    assert.doesNotMatch(types, /openPurchaseQuantity/);
    assert.match(types, /interface DemandSummaryRow/);
    assert.match(types, /interface DemandSummaryPreviewQuery/);
  });

  test('the preview is a tab inside the existing demand page with no new route or menu wiring', () => {
    const list = code('../src/views/business/scm/purchase/purchase-demand-list.vue');
    assert.match(list, /PurchaseDemandSummaryPreview/);
    assert.match(list, /a-tab-pane\s+key="preview"/);
    // 既有需求列表的写入口仍在本页：ADM-05 之后是「冻结批次 → 从批次生成需求」，
    // 直接 generate 端点保留给既有集成，UI 写入口是 batch:create（+ 逐行 allocate）。
    assert.match(list, /scm:purchase:demand:batch:create/);
    assert.match(list, /scm:purchase:demand:allocate/);
    assert.match(list, /scm:purchase:demand:allocate/);
  });
}

// ---------------------------------------------------------------- 来源：purchase-efficiency-contract.test.mjs
{
  /**
   * 采购效率（批量 / 导出 / 打印 / 按商品收货工作台）的前端契约测试。
   *
   * 保护这一批「不新增迁移、复用既有权限」的效率能力最容易被悄悄违背、且违背后不报错的线：
   * 1. 导出走只读 `postDownload('/scm/purchase/export')`，绝不落写接口；
   * 2. 批量少收关单 / 按商品工作台走只读 `postRequest`，且不得复用带 Idempotency-Key 的 `purchaseCommand`
   *    （后端这两个端点都不接幂等头，误用会让契约与后端不一致）；
   * 3. 按商品工作台只渲染后端逐行裁剪后聚合的四位定点字符串，前端绝不重算欠收 / 超收；
   * 4. 打印是纯客户端动作，`purchase-order-print.ts` 里不得出现任何网络 / 写调用；
   * 5. 导出列目录与后端 `PurchaseOrderExportSupport` 的 15 列逐字对齐（顺序敏感）。
   */

  /** 读源码并剥掉注释 —— 门禁只针对代码，头注释里提到被剪枝的名字是合规的。 */
  function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  test('采购单导出走只读 postDownload，不落任何写接口', () => {
    const api = code('../src/api/business/scm/purchase-order-api.ts');
    assert.match(api, /export:\s*\(data:\s*OrderExportPayload\)\s*=>\s*postDownload\('\/scm\/purchase\/export'/);
    // 只读：导出自身不得使用命令式幂等封装
    assert.doesNotMatch(api, /export:[^\n]*purchaseCommand/);
  });

  test('批量少收关单走只读 postRequest，不复用带幂等键的 purchaseCommand', () => {
    const api = code('../src/api/business/scm/purchase-order-api.ts');
    assert.match(
      api,
      /batchShortClose:\s*\(data:\s*OrderBatchShortClosePayload\)\s*=>\s*postRequest(?:<[^']*>)?\('\/scm\/purchase\/batch\/short-close'/
    );
    assert.doesNotMatch(api, /batchShortClose:[^\n]*purchaseCommand/);
  });

  test('按商品收货工作台走只读 postRequest 的 item-workbench 端点', () => {
    const api = code('../src/api/business/scm/purchase-receipt-api.ts');
    assert.match(
      api,
      /itemWorkbench:\s*\(data:\s*ReceiptItemWorkbenchQuery(,\s*options\?:\s*RequestOptions)?\)\s*=>\s*postRequest(?:<[^']*>)?\('\/scm\/purchase\/receipt\/item-workbench\/query'/
    );
    assert.doesNotMatch(api, /itemWorkbench:[^\n]*purchaseCommand/);
  });

  test('工作台组件只渲染后端聚合量，绝不重算欠收 / 超收', () => {
    const src = code('../src/views/business/scm/purchase/components/purchase-receipt-item-workbench.vue');
    assert.match(src, /scm-purchase-receipt-item-workbench-table/);
    assert.match(src, /purchaseReceiptApi\.itemWorkbench/);
    assert.doesNotMatch(src, /Decimal/);
    // 欠收 / 超收来自后端逐行裁剪求和，前端不得自行做 planned-received 之类减法
    assert.doesNotMatch(src, /plannedQuantity\s*[-+]/);
    assert.doesNotMatch(src, /receivedQuantity\s*[-+]/);
    assert.doesNotMatch(src, /plannedQuantity\s*-\s*receivedQuantity/);
  });

  test('采购单打印走 ADM-07 打印中心，且不会改写采购业务事实', () => {
    // ADM-07 用服务端模板渲染（冻结模板版本 / 业务模型 / 版面）取代了早期的纯客户端打印工具，
    // 因此这里守的是同一件事的另一面：打印入口不携带任何采购写操作。
    const list = code('../src/views/business/scm/purchase/purchase-order-list.vue');
    assert.match(list, /PrintDocumentModal/);
    assert.match(list, /document-type="PURCHASE_ORDER"/);
    assert.doesNotMatch(list, /purchase-order-print/);

    const modal = code('../src/views/business/scm/print/print-document-modal.vue');
    assert.match(modal, /printApi\.preview\(/);
    assert.match(modal, /printApi\.print\(/);
    // 打印与预览只经 print-api：不得出现采购命令（否则打印会改状态 / 重复生成业务事实）
    assert.doesNotMatch(modal, /purchaseOrderApi|purchaseCommand/);

    const api = code('../src/api/business/scm/print-api.ts');
    assert.doesNotMatch(api, /putRequest|deleteRequest/);
  });

  test('导出列目录与后端 15 列逐字对齐且顺序敏感', () => {
    assert.deepEqual(
      SCM_PURCHASE_EXPORT_COLUMNS.map((c) => c.key),
      [
        'orderNo',
        'supplierName',
        'supplierCode',
        'purchaserName',
        'warehouseName',
        'warehouseCode',
        'plannedArrivalDate',
        'status',
        'totalAmount',
        'receivedProgress',
        'remark',
        'cancelReason',
        'shortCloseReason',
        'submittedAt',
        'createdAt',
      ]
    );
  });

  test('收货页把工作台作为「按商品」Tab 内联，无新增路由 / 菜单', () => {
    const list = code('../src/views/business/scm/purchase/purchase-receipt-list.vue');
    assert.match(list, /PurchaseReceiptItemWorkbench/);
    assert.match(list, /a-tab-pane\s+key="by-item"/);
    // 既有按单据写入口（确认 / 入库 / 删除）仍在，未被工作台改写
    assert.match(list, /scm:purchase:receipt:confirm/);
    assert.match(list, /scm:purchase:receipt:putaway/);
  });

  test('采购单页把批量 / 导出 / 打印入口接在既有工具栏，权限沿用既有码', () => {
    const list = code('../src/views/business/scm/purchase/purchase-order-list.vue');
    assert.match(list, /batchShortClose/);
    assert.match(list, /onExport/);
    assert.match(list, /printRow/);
    assert.match(list, /scm:purchase:short-close/);
    assert.match(list, /scm:purchase:query/);
  });
}

// ---------------------------------------------------------------- 来源：purchase-contract.test.mjs
{
  /**
   * 采购域契约测试。
   *
   * 保护定点数、分配集合、单位一致性、收货完整性等跨文件约束 ——
   * 这些是前端最容易悄悄违背、且违背后不会报错的几条：
   * 1. 三态定点数（`null` / `"0.0000"` / 有值）在渲染层不得被合并；
   * 2. 分配是集合，改一条不得牵动同行其它条；
   * 3. 单位不一致必须拒绝，不得猜换算系数；
   * 4. 提交前必须覆盖全部收货行（40998）、非标品必须给实重（40083）；
   * 5. 已剪枝的尺寸调整管线与旧枚举不得回流。
   */

  /** 一张最小的合法采购单。 */
  function form() {
    const f = newOrder();
    f.supplierId = '1';
    f.warehouseId = '2';
    f.items = [{skuId: '3', plannedQuantity: '1.0000', purchasePrice: '2.0000', allocations: []}];
    return f;
  }

  /** 一张含标准品 + 非标品两行的收货单。 */
  function receipt() {
    return {
      id: 1,
      version: 0,
      items: [
        {id: 11, version: 0, productType: 'STANDARD', remainingQuantity: '5.0000'},
        {id: 12, version: 0, productType: 'NON_STANDARD', remainingQuantity: '2.0000'},
      ],
    };
  }

  /** 一条待分配的需求：required 10 = allocated 4 + unallocated 6。 */
  function demand(over = {}) {
    return {
      id: 7,
      skuId: '3',
      demandUnit: 'kg',
      status: 'PENDING',
      requiredQuantity: '10.0000',
      allocatedQuantity: '4.0000',
      unallocatedQuantity: '6.0000',
      version: 5,
      ...over,
    };
  }

  /**
   * 读源码并剥掉注释 —— 门禁只针对代码，Provenance 里提到被剪枝的名字是合规的。
   *
   * 剥离顺序不能反：Provenance 头自己会写出「斜杠 + 双星号」的路径通配写法，
   * 若先跑块注释正则，那个双星号会被当成块注释开头、一路吃到下一个「星号 + 斜杠」，
   * 把 HTML 注释的收尾标记一起吞掉，于是头注释反而留在了「代码」里。
   */
  function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  // ------------------------------------------------------------------
  // 定点数与三态渲染
  // ------------------------------------------------------------------

  test('null, zero and missing amounts stay distinguishable in three states', () => {
    assert.equal(amount(null, true), '未定价');
    assert.equal(amount(null), '—');
    assert.equal(amount('0.0000', true), '¥ 0.00');
    assert.equal(amount('0.0000'), '¥ 0.00');
    assert.equal(price('12.3456'), '¥ 12.3456');
    assert.equal(quantity(null), '—');
    assert.equal(quantity('0.0000'), '0.0000');
    assert.equal(quantity(''), '—');
  });

  test('fixed rounds half-up to four decimals and never fabricates zero', () => {
    assert.equal(fixed('1.00005'), '1.0001');
    assert.equal(fixed('1'), '1.0000');
    assert.equal(fixed(2.5), '2.5000');
    assert.equal(fixed(null), '');
    assert.equal(fixed(undefined), '');
    assert.equal(fixed(''), '');
  });

  test('received progress is a ratio rendered as a percentage and stays blank when absent', () => {
    assert.equal(progress(null), '—');
    assert.equal(progress('0.0000'), '0.00%');
    assert.equal(progress('0.5000'), '50.00%');
    assert.equal(progress('1.0000'), '100.00%');
    assert.equal(progress('0.33335'), '33.34%');
  });

  // ------------------------------------------------------------------
  // 采购单校验与请求体
  // ------------------------------------------------------------------

  test('order validation requires supplier, warehouse and at least one line', () => {
    const f = newOrder();
    assert.match(validateOrder(f), /供应商/);
    f.supplierId = '1';
    assert.match(validateOrder(f), /仓库/);
    f.warehouseId = '2';
    // `newOrder()` 自带一行空白行，先清空才能验到「至少一行」这条
    assert.match(validateOrder(f), /采购商品/);
    f.items = [];
    assert.match(validateOrder(f), /至少添加一行/);
  });

  test('order validation rejects duplicate SKU, non-positive quantity and bad price shape', () => {
    const f = form();
    f.items.push({...f.items[0]});
    assert.match(validateOrder(f), /重复/);
    f.items.pop();
    f.items[0].plannedQuantity = '0.0000';
    assert.match(validateOrder(f), /采购数量/);
    // 用户键入期间 antd 的 `precision` 不生效（见 purchase-form-model 的 typedFixed），
    // 模型里就是裸数：这里断言「校验时机与提交口径一致」——
    // 形状合法的短小数按 4 位定点归一后放行，而 `payload` 归一出的正是同一个值。
    f.items[0].plannedQuantity = '1.00';
    assert.equal(validateOrder(f), undefined);
    assert.equal(payload(f).items[0].quantity, '1.0000');
    assert.equal(validateOrder(f), undefined);
    // 真正不合法的形状仍然要被拒（不放宽定点纪律）。
    for (const bad of ['', '0.0000', '-1', '2.', '.5', 'abc', '1e5', '12345678901234567890']) {
      f.items[0].plannedQuantity = bad;
      assert.match(validateOrder(f), /采购数量/, `应收紧 ${JSON.stringify(bad)}`);
    }
    f.items[0].plannedQuantity = '1.0000';
    f.items[0].purchasePrice = '-1.0000';
    assert.match(validateOrder(f), /采购单价/);
    // 单价可以留空（未定价）；填了就必须是非负定点数。
    f.items[0].purchasePrice = '';
    assert.equal(validateOrder(f), undefined);
    f.items[0].purchasePrice = '0.0000';
    assert.equal(validateOrder(f), undefined);
  });

  test('allocation validation requires demand, positive quantity and demand version', () => {
    const f = form();
    f.items[0].allocations = [{quantity: '1.0000', demandVersion: 0}];
    assert.match(validateOrder(f), /分配缺少采购需求/);
    f.items[0].allocations = [{demandId: '7', quantity: '0.0000', demandVersion: 0}];
    assert.match(validateOrder(f), /分配数量/);
    // 分配数量同样按「输入框键入中的裸数」口径归一后再判定形状。
    f.items[0].allocations = [{demandId: '7', quantity: '1.00', demandVersion: 0}];
    assert.equal(validateOrder(f), undefined);
    assert.equal(payload(f).items[0].allocations[0].quantity, '1.0000');
    f.items[0].allocations = [{demandId: '7', quantity: '', demandVersion: 0}];
    assert.match(validateOrder(f), /分配数量/);
    f.items[0].allocations = [{demandId: '7', quantity: '1.0000'}];
    assert.match(validateOrder(f), /需求版本/);
    f.items[0].allocations = [{demandId: '7', quantity: '1.0000', demandVersion: 0}];
    assert.equal(validateOrder(f), undefined);
  });

  test('payload normalizes to four-decimal strings and keeps nulls as null', () => {
    const f = form();
    f.items[0].plannedQuantity = '3';
    f.items[0].purchasePrice = '2.5';
    const body = payload(f);
    assert.deepEqual(body.items[0], {
      id: undefined,
      version: undefined,
      skuId: '3',
      quantity: '3.0000',
      price: '2.5000',
      allocations: [],
    });
    assert.equal(body.purchaserId, null);
    assert.equal(body.plannedArrivalDate, null);
    assert.equal(body.remark, null);
    assert.equal('id' in body, false);
    assert.equal('version' in body, false);
  });

  test('payload keeps id and version only for retained lines', () => {
    const f = form();
    f.id = '9';
    f.version = 3;
    f.items = [
      {
        id: '11',
        version: 2,
        skuId: '3',
        plannedQuantity: '1.0000',
        purchasePrice: '2.0000',
        allocations: [{demandId: '7', quantity: '0.5000', demandVersion: 5}],
      },
      {skuId: '4', plannedQuantity: '1.0000', purchasePrice: '1.0000', allocations: []},
    ];
    const body = payload(f);
    assert.equal(body.id, '9');
    assert.equal(body.version, 3);
    assert.deepEqual(body.items[0], {
      id: '11',
      version: 2,
      skuId: '3',
      quantity: '1.0000',
      price: '2.0000',
      allocations: [{demandId: '7', quantity: '0.5000', demandVersion: 5}],
    });
    assert.deepEqual(body.items[1], {
      id: undefined,
      version: undefined,
      skuId: '4',
      quantity: '1.0000',
      price: '1.0000',
      allocations: [],
    });
  });

  // ------------------------------------------------------------------
  // 分配集合与单位一致性
  // ------------------------------------------------------------------

  test('new allocation carries the demand version and its unallocated remainder', () => {
    assert.deepEqual(newAllocation(demand()), {
      demandId: 7,
      skuId: '3',
      quantity: '6.0000',
      demandUnit: 'kg',
      demandVersion: 5,
      demandStatus: 'PENDING',
    });
    const blank = newAllocation({id: 8});
    assert.equal(blank.quantity, '0.0000');
    assert.equal(blank.demandVersion, 0);
    assert.deepEqual(newOrderItem(), {plannedQuantity: '1.0000', purchasePrice: '0.0000', allocations: []});
  });

  test('item totals and per-demand capacity follow the server allocation identity', () => {
    const item = {
      ...newOrderItem(),
      allocations: [
        {demandId: '7', quantity: '3.0000', demandVersion: 0},
        {demandId: '8', quantity: '2.0000', demandVersion: 0},
      ],
    };
    assert.equal(allocatedOnItem(item), '5.0000');
    assert.equal(allocatedOnItem(newOrderItem()), '0.0000');
    // 全库剩余 6 已扣掉本行旧分配 3 → 本行本次上限 6 + 3 = 9
    assert.equal(allocationCapacity(demand(), '3.0000').toFixed(4), '9.0000');
    assert.equal(allocationCapacity(demand(), null).toFixed(4), '6.0000');
    assert.equal(allocationCapacity({}, '').toFixed(4), '0.0000');
  });

  test('editing one allocation leaves the sibling allocation untouched', () => {
    const item = {
      ...newOrderItem(),
      allocations: [
        {demandId: '7', quantity: '1.0000', demandVersion: 0},
        {demandId: '8', quantity: '2.0000', demandVersion: 0},
      ],
    };
    const next = item.allocations.map((row) => (String(row.demandId) === '7' ? {...row, quantity: '3.0000'} : row));
    assert.equal(next[1].quantity, '2.0000');
    assert.equal(allocatedOnItem({...item, allocations: next}), '5.0000');
    const removed = next.filter((row) => String(row.demandId) !== '8');
    assert.equal(removed.length, 1);
    assert.equal(hasAllocation({...item, allocations: removed}, '8'), false);
    assert.equal(hasAllocation({...item, allocations: removed}, '7'), true);
  });

  test('allocation identity compares numerically so id types never diverge', () => {
    const item = {...newOrderItem(), allocations: [{demandId: 7, quantity: '1.0000', demandVersion: 0}]};
    assert.equal(hasAllocation(item, '7'), true);
    assert.equal(hasAllocation(item, 7), true);
    assert.equal(hasAllocation(item, '8'), false);
    assert.equal(hasAllocation(newOrderItem(), '7'), false);
  });

  test('unit mismatch blocks allocation instead of guessing a conversion', () => {
    assert.equal(unitMismatch({purchaseUnit: 'kg'}, demand({demandUnit: '箱'})), true);
    assert.equal(unitMismatch({purchaseUnit: 'kg'}, demand({demandUnit: 'kg'})), false);
    assert.equal(unitMismatch({}, demand()), false);
    assert.equal(unitMismatch({purchaseUnit: 'kg'}, {}), false);
  });

  // ------------------------------------------------------------------
  // 收货确认
  // ------------------------------------------------------------------

  test('confirm lines default to the remaining quantity of every receipt line', () => {
    assert.deepEqual(newConfirmLines(receipt()), [
      {receiptItemId: 11, version: 0, receivedQuantity: '5.0000', actualWeight: null, weightSource: null, correctionReason: null, lots: []},
      {receiptItemId: 12, version: 0, receivedQuantity: '2.0000', actualWeight: null, weightSource: null, correctionReason: null, lots: []},
    ]);
    assert.equal(newConfirmLines({items: []}).length, 0);
  });

  test('only NON_STANDARD products are treated as weighed goods', () => {
    assert.equal(isNonStandard({productType: 'NON_STANDARD'}), true);
    assert.equal(isNonStandard({productType: 'STANDARD'}), false);
    assert.equal(isNonStandard(undefined), false);
  });

  test('confirm must cover every receipt line', () => {
    const r = receipt();
    assert.match(validateConfirm(r, []), /全部收货明细/);
    assert.match(validateConfirm(r, newConfirmLines(r).slice(0, 1)), /全部收货明细/);
    assert.match(validateConfirm({items: []}, []), /没有明细/);
  });

  test('non-standard lines demand a manual actual weight', () => {
    const r = receipt();
    const lines = newConfirmLines(r);
    assert.match(validateConfirm(r, lines), /非标品必须录入实重/);
    lines[1].actualWeight = '2.0000';
    assert.match(validateConfirm(r, lines), /实重来源/);
    lines[1].weightSource = 'MANUAL';
    assert.equal(validateConfirm(r, lines), undefined);
  });

  test('standard lines may omit the weight but a supplied weight must be fixed-point', () => {
    const r = receipt();
    const lines = newConfirmLines(r);
    lines[1].actualWeight = '2.0000';
    lines[1].weightSource = 'MANUAL';
    lines[0].actualWeight = '5.00';
    assert.match(validateConfirm(r, lines), /实重必须为四位定点数/);
    lines[0].actualWeight = '5.0000';
    assert.equal(validateConfirm(r, lines), undefined);
    lines[0].actualWeight = null;
    assert.equal(validateConfirm(r, lines), undefined);
  });

  test('declared quantity shape and line identity are validated before submit', () => {
    const r = receipt();
    const lines = newConfirmLines(r);
    lines[1].actualWeight = '2.0000';
    lines[1].weightSource = 'MANUAL';
    lines[0].receivedQuantity = '1.00';
    assert.match(validateConfirm(r, lines), /声明数量/);
    lines[0].receivedQuantity = '-1.0000';
    assert.match(validateConfirm(r, lines), /声明数量/);
    lines[0].receivedQuantity = '0.0000';
    assert.equal(validateConfirm(r, lines), undefined);
    lines[0].receiptItemId = 99;
    assert.match(validateConfirm(r, lines), /不匹配/);
  });

  test('confirm payload normalizes quantities and derives the manual weight source', () => {
    const r = receipt();
    const lines = newConfirmLines(r);
    lines[0].receivedQuantity = '1';
    lines[1].actualWeight = '2.0000';
    lines[1].weightSource = 'MANUAL';
    const body = confirmPayload(r, lines);
    assert.equal(body.id, 1);
    assert.equal(body.version, 0);
    assert.deepEqual(body.items[0], {
      receiptItemId: 11,
      version: 0,
      receivedQuantity: '1.0000',
      actualWeight: null,
      weightSource: null,
      correctionReason: null,
      lots: [],
    });
    assert.deepEqual(body.items[1], {
      receiptItemId: 12,
      version: 0,
      receivedQuantity: '2.0000',
      actualWeight: '2.0000',
      weightSource: 'MANUAL',
      correctionReason: null,
      lots: [],
    });
  });

  test('tolerance stays server-side while the client only warns on the remainder', () => {
    assert.match(toleranceHint({remainingQuantity: '5.0000'}), /剩余可收 5\.0000/);
    assert.equal(beyondRemaining({remainingQuantity: '5.0000'}, '6.0000'), true);
    assert.equal(beyondRemaining({remainingQuantity: '5.0000'}, '5.0000'), false);
    assert.equal(beyondRemaining({remainingQuantity: '5.0000'}, '1.00'), false);
    assert.equal(beyondRemaining({remainingQuantity: null}, '9.0000'), false);
  });

  // ------------------------------------------------------------------
  // 错误码与枚举门禁
  // ------------------------------------------------------------------

  test('purchase errors resolve from body, data and response shapes', () => {
    assert.match(purchaseError({code: 40921}), /刷新/);
    // 保护的是「文案必须说明不会自动换算」，不是具体措辞；两种写法都算达标。
    assert.match(purchaseError({code: 40971}), /不(做|支持)自动换算/);
    assert.match(purchaseError({code: 40082}), /可分配余量/);
    assert.match(purchaseError({code: 40989}), /超收容差/);
    assert.match(purchaseError({code: 40998}), /全部明细/);
    assert.match(purchaseError({code: 41005}), /库存余额/);
    assert.match(purchaseError({code: 41007}), /待入库收货单/);
    assert.match(purchaseError({code: 41008}), /已入库/);
    assert.match(purchaseError({data: {code: 40982}}), /状态/);
    assert.equal(purchaseError({msg: '业务提示'}), '业务提示');
    assert.equal(purchaseError({response: {data: {msg: '业务提示'}}}), '业务提示');
    assert.equal(purchaseError({code: 99999}), '操作失败，请重试');
    assert.equal(purchaseError({}), '操作失败，请重试');
  });

  test('purchase enums expose exactly the states the backend state machine allows', () => {
    assert.deepEqual(Object.keys(SCM_PURCHASE_STATUS_ENUM), ['DRAFT', 'SUBMITTED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'SHORT_CLOSED', 'CANCELLED']);
    assert.deepEqual(
      Object.values(SCM_PURCHASE_STATUS_ENUM).map((e) => e.value),
      Object.keys(SCM_PURCHASE_STATUS_ENUM)
    );
    assert.deepEqual(Object.keys(SCM_RECEIPT_STATUS_ENUM), ['DRAFT', 'CONFIRMED']);
    assert.deepEqual(Object.keys(SCM_DEMAND_STATUS_ENUM), ['PENDING', 'PARTIALLY_ALLOCATED', 'ALLOCATED']);
    assert.deepEqual(Object.keys(SCM_WAREHOUSE_STATUS_ENUM), ['ENABLED', 'DISABLED']);
    assert.deepEqual(Object.keys(SCM_RECEIPT_MODE_ENUM), ['DIRECT', 'WAREHOUSE_CONFIRM']);
    assert.deepEqual(Object.keys(SCM_PUTAWAY_STATUS_ENUM), ['PENDING', 'COMPLETED']);
    assert.deepEqual(Object.keys(SCM_PURCHASE_OPERATION_ENUM), [
      'DEMAND_GENERATE',
      'DEMAND_ALLOCATE',
      'CREATE',
      'UPDATE',
      'SUBMIT',
      'CANCEL',
      'SHORT_CLOSE',
      'DELETE',
      'RECEIPT_CREATE',
      'RECEIPT_UPDATE',
      'RECEIPT_CONFIRM',
      'RECEIPT_DELETE',
      'RECEIPT_PUTAWAY',
    ]);
    assert.deepEqual(Object.keys(SCM_PURCHASE_TABLE_ID), ['ORDER', 'RECEIPT', 'DEMAND', 'LOG', 'WAREHOUSE']);
    assert.equal(SCM_PURCHASE_TABLE_ID.ORDER, 'scm-purchase-order-table');
  });

  test('pruned legacy enums, resize plumbing and inventory wiring never reach the purchase sources', () => {
    const constants = code('../src/constants/business/scm/purchase-const.ts');
    assert.doesNotMatch(constants, /RECEIVE_FLAG_ENUM|SUPPLIER_STATUS_ENUM|INQUIRY_STATUS_ENUM|PURCHASE_ITEM_STATUS_ENUM/);
    assert.doesNotMatch(constants, /resizable|resizeColumn/);

    const pages = ['purchase-order-list.vue', 'purchase-receipt-list.vue', 'purchase-demand-list.vue', 'purchase-log-list.vue', 'warehouse-list.vue'];
    for (const page of pages) {
      const source = code('../src/views/business/scm/purchase/' + page);
      assert.doesNotMatch(source, /resizable|resizeColumn|handleResizeColumn/, page + ' 残留 resize 管线');
      assert.match(source, /TableOperator/, page + ' 未使用 TableOperator');
      assert.match(source, /SCM_PURCHASE_TABLE_ID/, page + ' 未绑定表格 DOM id');
    }

    const editable = code('../src/views/business/scm/purchase/components/purchase-order-item-editable-table.vue');
    assert.doesNotMatch(editable, /resizable|resizeColumn|handleResizeColumn/);

    // 采购域不实现库存：不得出现库存读写入口（`OrderInventoryContract` 只有定义、零调用点）。
    const inventory = code('../src/api/business/scm/purchase-order-api.ts');
    assert.doesNotMatch(inventory, /inventory|stock/i);
  });

  test('commands and action guards stay explicit in the frontend contract', () => {
    const receiptApi = code('../src/api/business/scm/purchase-receipt-api.ts');
    assert.match(receiptApi, /purchaseCommand<Receipt>\('\/scm\/purchase\/receipt\/putaway'/);

    const warehouseApi = code('../src/api/business/scm/warehouse-api.ts');
    assert.match(warehouseApi, /postRequest(?:<[^']*>)?\('\/scm\/warehouse\/enable'/);
    assert.match(warehouseApi, /postRequest(?:<[^']*>)?\('\/scm\/warehouse\/disable'/);

    const receiptList = code('../src/views/business/scm/purchase/purchase-receipt-list.vue');
    assert.match(
      receiptList,
      /record\.status === 'CONFIRMED'\s*&&\s*record\.receiptMode === 'WAREHOUSE_CONFIRM'\s*&&\s*record\.putawayStatus === 'PENDING'/
    );
    assert.match(receiptList, /scm:purchase:receipt:putaway/);

    const warehouseList = code('../src/views/business/scm/purchase/warehouse-list.vue');
    // 启用 / 停用已从行内按钮移入「更多」菜单（状态机动作不与普通动作同排常驻），
    // 因此条件与权限码不再彼此相邻。断言改成分别检查两者的存在与绑定关系：
    // 菜单项由 `rowActions` 按当前状态二选一生成，权限码由 `hasPermission` 显式裁剪
    //（`v-privilege` 指令对「更多」里的菜单项不生效，这是必须换写法的原因）。
    assert.match(warehouseList, /row\.status === 'ENABLED'/);
    assert.match(warehouseList, /hasPermission\('scm:warehouse:disable'\)/);
    assert.match(warehouseList, /hasPermission\('scm:warehouse:enable'\)/);
    // 状态机没有被改写：启用/停用的两个接口调用仍在同一分支里按状态二选一
    assert.match(warehouseList, /onRowAction[\s\S]{0,400}disable\(row\)/);
    assert.match(warehouseList, /onRowAction[\s\S]{0,400}else\s*\{\s*enable\(row\)/);
  });
}

// ---------------------------------------------------------------- 来源：warehouse-form-model.test.mjs
{
  /*
   * 仓库写请求体单测
   *
   * 运行方式与断言风格与 `test/customer-form-model.test.mjs` 一致
   * （`node --experimental-strip-types --test`，直接 import `.ts` 源码）。
   *
   * 只覆盖「写请求体里不该有什么」：仓库编码由服务端生成，后端 `WarehouseAddForm` /
   * `WarehouseUpdateForm` 都没有这个字段，客户端带上它只是噪音。
   */

  test('写请求体不含仓库编码：编码由服务端生成', () => {
    const payload = toWarehousePayload({warehouseCode: 'WH000001', name: '主仓'});
    // 编码只在 UI 表单模型（WarehouseFormModel）上做只读回显。
    assert.equal('warehouseCode' in payload, false);
    assert.equal(payload.name, '主仓');
  });

  test('写请求体保留编辑所需的 id 与 version', () => {
    const payload = toWarehousePayload({id: 3, version: 7, warehouseCode: 'WH000003', name: '分仓'});
    assert.equal(payload.id, 3);
    assert.equal(payload.version, 7);
    assert.equal('warehouseCode' in payload, false);
  });
}
