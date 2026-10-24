/**
 * 销售订单模块契约测试。
 *
 * 由 9 个独立测试文件按模块合并：每个 `{ … }` 块对应原来一份文件，
 * 块内 helper / 常量各自作用域隔离；只有 import 提到顶部并去重。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {transformSync} from 'esbuild';
import {
  draftKey,
  nextOrderForCustomer,
  newOrder,
  serializeDraft,
  fromHistory,
  applyDraft,
  createRecentPriceCache,
  isLoadingRecentPrice,
  loadRecentPrices,
  recentPriceKey,
  hasRecentPrices,
  recentPriceRows,
  payload,
  validateOrder,
  fixed,
  amount,
} from '../src/views/business/scm/order/order-form-model.ts';
import {orderError} from '../src/views/business/scm/order/order-errors.ts';

// ---------------------------------------------------------------- 来源：order-batch-price-check.test.mjs
{
  const require = createRequire(import.meta.url);
  const source = readFileSync(new URL('../src/views/business/scm/order/order-batch-price-check.ts', import.meta.url), 'utf8');
  const compiled = transformSync(source, {loader: 'ts', format: 'cjs', target: 'es2022'}).code;
  const module = {exports: {}};
  new Function('require', 'module', 'exports', compiled)(require, module, module.exports);
  const {compareOrderPrices, canApplyPriceCheck} = module.exports;

  const order = (items) => ({
    orderId: '9007199254740993',
    orderNo: 'SO-123',
    version: 3,
    status: 'DRAFT',
    customerId: '9',
    customerNameSnapshot: '甲客户',
    items,
  });
  const item = (skuId, price, manual = false) => ({
    itemId: String(skuId),
    skuId: String(skuId),
    orderedQuantity: '2.0000',
    draftUnitPrice: price,
    manualPriceOverride: manual,
    productNameSnapshot: '商品' + skuId,
    skuCodeSnapshot: 'A' + skuId,
  });
  const priced = (skuId, unitPrice, sellable = true) => ({
    skuId: String(skuId),
    unitPrice,
    sellable,
    priceStatus: unitPrice == null ? 'UNPRICED' : 'PRICED',
    priceSource: unitPrice == null ? null : 'MARKET',
  });

  test('订单核价使用定点十进制比较，不写回历史草稿价或订单版本', () => {
    const input = order([item(1, '1.1000'), item(2, '3.0000'), item(3, '0.0000')]);
    const before = JSON.stringify(input);
    const result = compareOrderPrices(input, [priced(1, '1.1000'), priced(2, '2.9000'), priced(3, '0.0000')]);
    assert.deepEqual(
      result.lines.map((row) => row.state),
      ['SAME', 'DOWN', 'SAME']
    );
    assert.equal(result.changed, 1);
    assert.equal(result.version, 3);
    assert.equal(JSON.stringify(input), before);
    assert.equal(canApplyPriceCheck(result), true);
  });

  test('人工改价不参与重算；缺失价格与不可售均阻止人工应用', () => {
    const input = order([item(1, '12.0000', true), item(2, '1.0000'), item(3, '2.0000')]);
    const result = compareOrderPrices(input, [priced(1, '15.0000'), priced(2, null), priced(3, '5.0000', false)]);
    assert.deepEqual(
      result.lines.map((row) => row.state),
      ['MANUAL', 'BLOCKED', 'BLOCKED']
    );
    assert.equal(result.lines[0].currentPrice, '12.0000');
    assert.equal(result.blocked, 2);
    assert.equal(canApplyPriceCheck(result), false);
  });

  test('原草稿未定价但已有现价时标记为新定价，涨价与降价分别展示', () => {
    const result = compareOrderPrices(order([item(1, null), item(2, '5.0000'), item(3, '5.0000')]), [
      priced(1, '0.0000'),
      priced(2, '6.0000'),
      priced(3, '4.0000'),
    ]);
    assert.deepEqual(
      result.lines.map((row) => row.state),
      ['NEW', 'UP', 'DOWN']
    );
    assert.equal(result.changed, 3);
  });

  test('拒绝对已确认订单生成草稿核价报告', () => {
    assert.throws(() => compareOrderPrices({...order([]), status: 'CONFIRMED'}, []), /草稿订单/);
    assert.throws(() => compareOrderPrices({...order([]), version: undefined}, []), /草稿订单/);
  });

  test('UI 禁止直接批量改价：每次操作基于可见订单详情和版本核对', () => {
    const modal = readFileSync(new URL('../src/views/business/scm/order/components/order-batch-price-check-modal.vue', import.meta.url), 'utf8');
    const list = readFileSync(new URL('../src/views/business/scm/order/order-list.vue', import.meta.url), 'utf8');
    assert.match(list, /hasPermission\('scm:order:query'\)/);
    assert.match(list, /hasPermission\('scm:pricing:resolve:query'\)/);
    assert.match(list, /selected\.length > 8/);
    assert.match(modal, /orderApi\.detail\(entry\.id/);
    assert.match(modal, /orderApi\.preview\(/);
    assert.match(modal, /latest\.version !== previewVersion/);
    assert.match(modal, /orderApi\.reprice\(\{orderId: entry\.id, version: previewVersion, reason: actionReason\}\)/);
    assert.doesNotMatch(modal, /orderApi\.createAndProgress\(|orderApi\.confirm\(|orderApi\.update\(/);
  });
}

// ---------------------------------------------------------------- 来源：order-continuous-entry-contract.test.mjs
{
  const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');
  const drawer = read('../src/views/business/scm/order/components/order-form-drawer.vue');
  const model = read('../src/views/business/scm/order/order-form-model.ts');

  test('连续录单复用原 create-and-progress 命令，不新增隐蔽的自动确认、复制或批量写入端点', () => {
    assert.match(drawer, /创建并继续录单/);
    assert.match(drawer, /save\(false, true\)/);
    assert.match(drawer, /orderApi\.createAndProgress\(f\)/);
    assert.match(drawer, /isNew && !exchangeReturnId\.value && !draft && continueCreating && visible\.value/);
    assert.doesNotMatch(drawer, /orderApi\.copy\(|orderApi\.clone\(|orderApi\.batchCreate\(/);
  });

  test('只有创建成功才清草稿和继续；后续客户地址从服务端重取且失败时清空客户', () => {
    assert.match(drawer, /if \(isNew && !exchangeReturnId\.value\) clearDraft\(\)/);
    assert.match(drawer, /await continueWithNextOrder\(f\)/);
    assert.match(drawer, /nextOrderForCustomer\(previous\)/);
    assert.match(drawer, /customerApi\.detail\(form\.value\.customerId/);
    assert.match(drawer, /form\.value\.customerId = undefined/);
    assert.match(drawer, /if \(token !== requestId \|\| !visible\.value\) return/);
    assert.match(drawer, /if \(saving\.value \|\| loading\.value\) return/);
    assert.match(model, /nextOrderForCustomer/);
    assert.doesNotMatch(
      model.match(/export function nextOrderForCustomer[\s\S]+?\n\}/)?.[0] || '',
      /items\s*=|lockedUnitPrice|manualPriceOverride|originalOrderId|orderNo|status/
    );
  });
}

// ---------------------------------------------------------------- 来源：order-form-model.test.mjs
{
  /**
   * 订单录单效率纯模型测试：草稿压缩 / 还原 / 历史复用。
   *
   * 这三件事都涉及「什么能存、什么绝不能存」的安全边界，违背后不报错但会造成
   * 草稿互串、把历史价 / version 当新单事实等隐患，因此对纯函数逐条钉死：
   * 1. 草稿只存用户录入，绝不落 PriceResolver 解析价、lockedUnitPrice、version、status、快照；
   * 2. 恢复只回填用户字段，解析价留空由调用方重新请求；
   * 3. 历史复用不复制 orderId / orderNo / version / status / 历史价 / 人工改价，来源回落后台录单。
   */

  function itemOf(over) {
    return {orderedQuantity: '2.0000', manualPriceOverride: false, ...over};
  }

  test('连续录单只保留客户与仓库，清空来源快照、旧地址、商品、价款及补单关联', () => {
    const previous = {
      ...newOrder(),
      orderId: '9007199254740993',
      orderNo: 'SO-900',
      version: 8,
      status: 'CONFIRMED',
      customerId: '12',
      mallDeliveryWarehouseId: '31',
      orderSource: 'SUPPLEMENT',
      originalOrderId: '9',
      supplementReason: '上一单原因',
      expectDeliveryTime: '2026-10-17 19:00:00',
      remark: '仅上一单的备注',
      address: {receiverName: '旧收货人', receiverPhone: '123456', address: '旧地址'},
      items: [
        itemOf({
          skuId: '4',
          itemId: '11',
          version: 2,
          orderedQuantity: '5.0000',
          draftUnitPrice: '9.9900',
          lockedUnitPrice: '9.9900',
          manualPriceOverride: true,
          unitPrice: '9.9900',
          overrideReason: '上一单改价',
        }),
      ],
    };
    const next = nextOrderForCustomer(previous);
    assert.deepEqual(next, {
      ...newOrder(),
      customerId: '12',
      deliveryWarehouseId: '31',
    });
    assert.equal(previous.items.length, 1, '准备下一单不能修改上一单快照');
    assert.notEqual(next.address, previous.address);
    const chosen = nextOrderForCustomer({...previous, deliveryWarehouseId: '42'});
    assert.equal(chosen.deliveryWarehouseId, '42', '显式选仓优先于历史商城履约仓');
  });

  test('草稿键按登录用户隔离，不同操作员互不串', () => {
    assert.equal(draftKey(44), 'xsy-scm:order-draft:44');
    assert.notEqual(draftKey('44'), draftKey('45'));
  });

  test('serializeDraft 丢弃一切服务端解析 / 快照 / version / 状态字段', () => {
    const form = {
      ...newOrder(),
      orderId: 99,
      orderNo: 'SO-1',
      version: 7,
      status: 'DRAFT',
      customerId: 12,
      orderSource: 'ADMIN',
      expectDeliveryTime: '2026-09-22T00:00:00Z',
      remark: 'r',
      items: [
        itemOf({
          skuId: 5,
          itemId: 3,
          version: 2,
          draftUnitPrice: '8.0000',
          draftPriceSource: 'MARKET',
          lockedUnitPrice: '8.0000',
          productNameSnapshot: 'P',
          saleUnitSnapshot: 'kg',
        }),
      ],
    };
    const draft = serializeDraft(form);
    assert.equal(draft.orderId, undefined);
    assert.equal(draft.orderNo, undefined);
    assert.equal(draft.version, undefined);
    assert.equal(draft.status, undefined);
    const line = draft.items[0];
    // 解析价 / 锁定价 / 快照 / 行 version 一律不进草稿
    assert.equal(line.draftUnitPrice, undefined);
    assert.equal(line.lockedUnitPrice, undefined);
    assert.equal(line.productNameSnapshot, undefined);
    assert.equal(line.version, undefined);
    assert.equal(line.itemId, undefined);
    // 保留用户录入
    assert.equal(line.skuId, 5);
    assert.equal(line.orderedQuantity, '2.0000');
  });

  test('serializeDraft 只在补单来源下保留补单上下文，并过滤空行', () => {
    const base = {
      ...newOrder(),
      customerId: 12,
      items: [
        {orderedQuantity: '', manualPriceOverride: false},
        {orderedQuantity: '1.0000', manualPriceOverride: false, skuId: 8},
        {orderedQuantity: '', manualPriceOverride: false, skuId: 9},
      ],
    };
    const admin = serializeDraft({...base, orderSource: 'ADMIN', supplementReason: 'x', originalOrderId: 3});
    assert.equal(admin.supplementReason, null);
    assert.equal(admin.originalOrderId, null);
    // 只丢弃「无 SKU 且无数量」的占位行；有 SKU 或有数量都算用户在录的半行
    assert.equal(admin.items.length, 2);

    const sup = serializeDraft({...base, orderSource: 'SUPPLEMENT', supplementReason: 'x', originalOrderId: 3});
    assert.equal(sup.supplementReason, 'x');
    assert.equal(sup.originalOrderId, 3);
  });

  test('serializeDraft 仅在人工改价开启时保留单价与原因', () => {
    const form = {...newOrder(), items: [itemOf({skuId: 5, manualPriceOverride: true, unitPrice: '6.6000', overrideReason: '促销'})]};
    assert.deepEqual(serializeDraft(form).items[0], {
      skuId: 5,
      orderedQuantity: '2.0000',
      manualPriceOverride: true,
      unitPrice: '6.6000',
      overrideReason: '促销',
    });
    const off = {...newOrder(), items: [itemOf({skuId: 5, manualPriceOverride: false, unitPrice: '6.6000'})]};
    assert.equal(serializeDraft(off).items[0].unitPrice, null);
  });

  test('applyDraft 还原为新建草稿：保留用户字段，来源缺省回落 ADMIN', () => {
    const draft = {
      customerId: 12,
      orderSource: 'ADMIN',
      expectDeliveryTime: '2026-09-22T00:00:00Z',
      remark: 'r',
      supplementReason: null,
      originalOrderId: null,
      items: [{skuId: 5, orderedQuantity: '3.0000', manualPriceOverride: false}],
    };
    const form = applyDraft(draft);
    assert.equal(form.orderId, undefined);
    assert.equal(form.customerId, 12);
    assert.equal(form.items.length, 1);
    assert.equal(form.items[0].orderedQuantity, '3.0000');
    // 解析价不在草稿里，恢复后由 preview 重填
    assert.equal(form.items[0].draftUnitPrice, undefined);
  });

  test('fromHistory 复制允许字段但绝不带历史价 / 状态 / version / 人工改价', () => {
    const detail = {
      ...newOrder(),
      orderId: 55,
      orderNo: 'SO-55',
      version: 9,
      status: 'CONFIRMED',
      customerId: 12,
      orderSource: 'MALL',
      expectDeliveryTime: '2026-09-22T00:00:00Z',
      remark: 'r',
      address: {receiverName: '张三', receiverPhone: '139', address: '路 1 号'},
      items: [
        itemOf({
          skuId: 5,
          itemId: 7,
          version: 4,
          orderedQuantity: '3.0000',
          manualPriceOverride: true,
          unitPrice: '1.1000',
          draftUnitPrice: '9.9000',
          lockedUnitPrice: '8.8000',
          lockedPriceSource: 'OVERRIDE',
        }),
      ],
    };
    const reuse = fromHistory(detail);
    assert.equal(reuse.orderId, undefined);
    assert.equal(reuse.orderNo, undefined);
    assert.equal(reuse.version, undefined);
    assert.equal(reuse.status, undefined);
    // 作为后台录单的新草稿重新提交
    assert.equal(reuse.orderSource, 'ADMIN');
    assert.equal(reuse.customerId, 12);
    assert.equal(reuse.expectDeliveryTime, '2026-09-22T00:00:00Z');
    assert.equal(reuse.remark, 'r');
    assert.deepEqual(reuse.address, {receiverName: '张三', receiverPhone: '139', address: '路 1 号'});
    const line = reuse.items[0];
    assert.equal(line.skuId, 5);
    assert.equal(line.orderedQuantity, '3.0000');
    // 历史价 / 人工改价 / 行身份绝不作为新单事实
    assert.equal(line.manualPriceOverride, false);
    assert.equal(line.unitPrice, null);
    assert.equal(line.itemId, undefined);
    assert.equal(line.version, undefined);
    assert.equal(line.draftUnitPrice, undefined);
    assert.equal(line.lockedUnitPrice, undefined);
  });

  test('fromHistory 在无地址的历史单上回退到空白地址而非 undefined', () => {
    const reuse = fromHistory({...newOrder(), address: null, customerId: 12, items: []});
    assert.deepEqual(reuse.address, {receiverName: '', receiverPhone: '', address: ''});
  });
}

// ---------------------------------------------------------------- 来源：order-tag-contract.test.mjs
{
  const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');
  const sql = read('../../xsy-scm-server/sa-admin/src/main/resources/db/migration/V145__scm_order_tags.sql');
  const mapper = read('../../xsy-scm-server/sa-admin/src/main/resources/mapper/scm/order/SalesOrderMapper.xml');
  const service = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/order/service/OrderTagService.java');
  const readService = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/order/service/SalesOrderQueryService.java');
  const api = read('../src/api/business/scm/order-tag-api.ts');
  const list = read('../src/views/business/scm/order/order-list.vue');
  const modal = read('../src/views/business/scm/order/components/order-tag-modal.vue');

  test('标签独立存储并按订单与标签对唯一约束，不能改变订单交易事实', () => {
    assert.match(sql, /CREATE TABLE xsy_order_tag\b/);
    assert.match(sql, /CREATE TABLE xsy_order_tag_link\b/);
    assert.match(sql, /PRIMARY KEY \(order_id, tag_id\)/);
    assert.doesNotMatch(sql, /ALTER TABLE xsy_sales_order|FOREIGN KEY/i);
    assert.match(service, /orderDao\.lock\(id\)/);
    assert.match(service, /scope\.allows\(order\.getSellerId\(\)\)/);
    assert.match(service, /@Transactional[\s\S]*?void apply\(/);
  });

  test('订单列表的原负责人范围先于标签过滤；非草稿可打标，但不可批量删除或核价', () => {
    assert.match(mapper, /scope\.all == false/);
    assert.match(mapper, /AND EXISTS \(SELECT 1 FROM xsy_order_tag_link/);
    assert.match(readService, /orderTagDao\.tagsByOrders\(/);
    assert.match(list, /queryForm\.tagId/);
    assert.match(list, /record\.tags/);
    assert.doesNotMatch(list, /getCheckboxProps/);
    assert.match(list, /selectedAllDraft/);
    assert.match(list, /!selectedAllDraft/);
    assert.match(list, /key: 'tag', label: '订单标签'/);
  });

  test('批量打标最多20单；只有订单更新与查询权限才可操作', () => {
    assert.match(api, /'\/scm\/order\/tags\/apply'/);
    assert.match(list, /hasPermission\('scm:order:query'\)/);
    assert.match(list, /hasPermission\('scm:order:update'\)/);
    assert.match(list, /selected\.length > 20/);
    assert.match(modal, /orderTagApi\.apply\(/);
    assert.match(service, /form\.getOrderIds\(\)\.stream\(\)\.distinct\(\)\.sorted\(\)/);
  });
}

// ---------------------------------------------------------------- 来源：order-log-return-refund-contract.test.mjs
{
  /**
   * 订单日志、退货、退款的前端契约测试。
   *
   * 这三个页面此前只做过「操作列居中」（commit ffa95370 是 8 文件 16 行的 align 改动），
   * 列精简与动作分组都没有做。本文件钉住两件容易回退的事：
   *
   * 1. 越界的操作列宽度：普通操作列 120～160px，
   *    而这三个页面此前一律是 240px。
   * 2. 危险动作不能与普通动作同排常驻：驳回 / 取消必须进「更多」，
   *    否则 240px 的操作列里最显眼的永远是那排红色文字。
   *
   * 日志页的时间是时间字段的例外，单独断言，防止「统一隐藏时间」被误用到审计页。
   */

  /** 读源码并剥掉注释（先剥 HTML 注释再剥块注释，顺序不能反）。 */
  function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  const BASE = '../src/views/business/scm/order/';
  const orderLog = code(`${BASE}order-log-list.vue`);
  const orderReturn = code(`${BASE}order-return-list.vue`);
  const orderRefund = code(`${BASE}order-refund-list.vue`);

  /** 取出「操作」列声明里的 width；取不到就断言失败，不返回默认值。 */
  function actionWidth(source, name) {
    const matched = /dataIndex: 'action'[\s\S]{0,120}?width: (\d+)/.exec(source);
    assert.ok(matched, `${name}：找不到操作列的 width`);
    return Number(matched[1]);
  }

  test('订单日志：操作列收到 120px 以内，时间必须保留', () => {
    const width = actionWidth(orderLog, '订单日志');
    assert.ok(width <= 120, `订单日志操作列 ${width}px 超出 120px`);
    // 日志页是时间例外：隐藏时间会让「谁在什么时候改的」这条审计链断掉
    assert.match(orderLog, /title: '时间'/);
    assert.match(orderLog, /title: '操作人'/);
    assert.match(orderLog, /title: '原因'/);
  });

  test('退货：操作列收到 160px 以内，驳回与取消进「更多」', () => {
    const width = actionWidth(orderReturn, '退货');
    assert.ok(width <= 160, `退货操作列 ${width}px 超出 160px`);
    // 常驻的仍是详情与当前状态唯一的推进动作
    assert.match(orderReturn, /showDetail\(record\.returnId\)/);
    assert.match(orderReturn, /record\.status\s*===\s*'PENDING'/);
    assert.match(orderReturn, /record\.status\s*===\s*'APPROVED'/);
    // 审批 / 接收 / 拒绝 / 取消 不能全部常驻，低频与危险动作按状态进「更多」
    assert.match(orderReturn, /ScmActionMore/);
    assert.match(orderReturn, /hasPermission\('scm:order:return:reject'\)/);
    assert.match(orderReturn, /hasPermission\('scm:order:return:cancel'\)/);
    // 原因可能很长，必须 ellipsis 而不是把行撑成两行
    assert.match(orderReturn, /title: '退货原因'[\s\S]{0,80}?ellipsis: true/);
    assert.match(orderReturn, /title: '退货单号'/);
    assert.match(orderReturn, /title: '批准金额'/);
  });

  test('退款：操作列收到 160px 以内，外部凭证不常驻长文本', () => {
    const width = actionWidth(orderRefund, '退款');
    assert.ok(width <= 160, `退款操作列 ${width}px 超出 160px`);
    assert.match(orderRefund, /title: '退款单号'/);
    assert.match(orderRefund, /title: '已返还钱包'/);
    // 外部凭证是渠道返回的长字符串，要求 ellipsis + Tooltip
    assert.match(orderRefund, /title: '外部凭证'[\s\S]{0,120}?ellipsis: true/);
  });

  test('三个订单页面的操作列全部居中', () => {
    for (const [name, source] of [
      ['订单日志', orderLog],
      ['退货', orderReturn],
      ['退款', orderRefund],
    ]) {
      // 列声明有单行与多行两种写法，不能假设 align 紧跟在 dataIndex 后面
      assert.match(source, /dataIndex: 'action'[\s\S]{0,120}?align: 'center'/, `${name}的操作列未居中`);
    }
  });
}

// ---------------------------------------------------------------- 来源：draft-off-page.test.mjs
{
  /**
   * 录单草稿「离页不丢」的前端契约测试。
   *
   * 草稿只在主动点「关闭」时落本地，曾是这个能力最容易被误改回去的地方：刷新、切换路由、
   * 直接离开页面都不经过 `closeDrawer()`，一旦有人把它退回成单一关闭路径，页面上看不出任何异常，
   * 用户却会整单丢失。这里锁三条：
   * 1. 三类离页通道（beforeunload / 路由离开 / 组件卸载）都挂到同一个 `persistUnsavedDraft`；
   * 2. 写草稿只有 `persistUnsavedDraft` 一处出口，且抽屉已关闭时不写（创建成功后不得把已保存的单写回草稿）；
   * 3. `beforeunload` 监听必须被摘除，不给 keep-alive 页面留重复监听。
   */

  /** 剥掉模板 / 块 / 行注释，门禁只针对代码。 */
  const src = readFileSync(new URL('../src/views/business/scm/order/components/order-form-drawer.vue', import.meta.url), 'utf8')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');

  test('all three off-page channels persist the draft through the same entry point', () => {
    assert.match(src, /window\.addEventListener\('beforeunload',\s*persistUnsavedDraft\)/);
    assert.match(src, /onBeforeRouteLeave\(\(\)\s*=>\s*persistUnsavedDraft\(\)\)/);
    assert.match(src, /onBeforeUnmount\(\(\)\s*=>\s*\{[\s\S]*?persistUnsavedDraft\(\);/);
  });

  test('draft writing has a single exit that refuses while the drawer is closed', () => {
    const writeCalls = src.match(/writeDraft\(serializeDraft/g) ?? [];
    assert.equal(writeCalls.length, 1, '落草稿只应有 persistUnsavedDraft 一个出口');
    assert.match(src, /function persistUnsavedDraft\(\)\s*\{\s*if\s*\(!visible\.value\)\s*return;/);
    assert.match(src, /function closeDrawer\(\)\s*\{\s*persistUnsavedDraft\(\);/);
  });

  test('the beforeunload listener is removed on both close and unmount', () => {
    const removeCalls = src.match(/window\.removeEventListener\('beforeunload',\s*persistUnsavedDraft\)/g) ?? [];
    assert.equal(removeCalls.length, 2, '关闭抽屉与组件卸载都要摘掉 beforeunload 监听');
  });
}

// ---------------------------------------------------------------- 来源：order-entry-contract.test.mjs
{
  /**
   * 订单录单效率（草稿恢复 / 历史复用 / 最近已确认订单价）的前端契约测试。
   *
   * 钉死这一波最容易被日后改动悄悄破坏、且破坏后不报错的前端边界（后端读路径 / 权限由 Java 测试覆盖）：
   * 1. 最近已确认订单价是只读查询：走 getRequest，绝不复用带 Idempotency-Key 的 orderCommand；
   * 2. 最近已确认订单价仅旁证：不写回解析单价、不前端重算，preview / 定价路径保持不变；
   * 3. 草稿恢复 / 历史复用接在既有 Drawer，无新增路由 / 菜单；创建成功清草稿；
   * 4. 历史复用只读 GET detail，不引入后端「复制订单」命令；
   * 5. 「复用为新单」沿用 scm:order:add，明细「历史价」按客户 + SKU 现查。
   */

  /** 读源码并剥掉注释 —— 门禁只针对代码，头注释里提到被禁用的名字是合规的。 */
  function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  test('最近已确认订单价走只读 getRequest 的 reference 端点，不复用幂等命令封装', () => {
    const api = code('../src/api/business/scm/order-api.ts');
    assert.match(api, /recentPrices:[\s\S]*?getRequest(?:<[^']*>)?\('\/scm\/order\/reference\/recent-prices'/);
    assert.doesNotMatch(api, /recentPrices:[^\n]*orderCommand/);
    // 既有写命令与价格预览路径保持不变
    assert.match(api, /preview:[\s\S]*?postRequest(?:<[^']*>)?\('\/scm\/order\/price\/preview'/);
  });

  test('录单页把草稿恢复 / 历史复用接在既有 Drawer，创建成功清草稿', () => {
    const drawer = code('../src/views/business/scm/order/components/order-form-drawer.vue');
    assert.match(drawer, /promptRestoreDraft/);
    assert.match(drawer, /closeDrawer/);
    assert.match(drawer, /openFromHistory/);
    assert.match(drawer, /defineExpose\(\{open,\s*openFromHistory,\s*openForExchange\}\)/);
    assert.match(drawer, /if \(isNew && !exchangeReturnId\.value\) clearDraft\(\)/);
    // 关闭 / 保存动作走 closeDrawer，不再直接置 visible=false
    assert.doesNotMatch(drawer, /@close="visible=false"/);
  });

  test('历史复用只读 GET detail，不引入后端复制命令', () => {
    const drawer = code('../src/views/business/scm/order/components/order-form-drawer.vue');
    assert.match(drawer, /orderApi\.detail\(id\b/);
    assert.match(drawer, /fromHistory/);
    const api = code('../src/api/business/scm/order-api.ts');
    assert.doesNotMatch(api, /\bcopy\b|\bduplicate\b|\bclone\b/i);
  });

  test('明细表按客户 + SKU 现查最近已确认订单价，且不回写解析单价、不重算', () => {
    const table = code('../src/views/business/scm/order/components/order-item-editable-table.vue');
    // 缓存化后调用点带 `as Id` 收窄（模板已保证客户与本行 SKU 都存在）；容许类型断言，
    // 但两个键的来源必须仍是「当前客户 + 本行 SKU」，跨行/跨客户取值会在这里变红。
    assert.match(table, /orderApi\.recentPrices\(props\.customerId(?:\s+as\s+Id)?,\s*record\.skuId(?:\s+as\s+Id)?/);
    // 只渲染后端字符串价，绝不前端 Decimal 重算或写回 draftUnitPrice
    assert.doesNotMatch(table, /Decimal/);
    assert.doesNotMatch(table, /record\.draftUnitPrice\s*=/);
  });

  test('订单列表把「复用为新单」接在操作列的「更多」里，沿用 scm:order:add 权限', () => {
    const list = code('../src/views/business/scm/order/order-list.vue');
    // 已确认订单行内只留「详情 + 预留库存」，复用为新单属于低频动作，收进「更多」。
    // 菜单项挂不上 v-privilege 指令，改用同一口径的 hasPermission 裁剪，权限码不变。
    assert.match(list, /key: 'reuse', label: '复用为新单'/);
    assert.match(list, /openFromHistory\(row\.orderId\)/);
    assert.match(list, /hasPermission\('scm:order:add'\)/);
  });
}

// ---------------------------------------------------------------- 来源：recent-price-cache.test.mjs
{
  /**
   * 「历史价」缓存测试。
   *
   * 钉死的是串数据而不是显示：缓存键一旦退化成行序号，换商品、删行上移、换客户都会
   * 静默把上一个 SKU / 上一个客户的历史成交价显示成当前行的参考价，而参考价看着完全合理。
   */

  const APPLE = 101,
    BANANA = 102,
    CUSTOMER_A = 7,
    CUSTOMER_B = 8;
  const priceOf = (skuId, unitPrice) => [{itemId: skuId, skuId, orderNo: `SO${skuId}`, unitPrice}];

  /** 记录调用次数的假接口：断言「该请求时必须请求」只能靠计数。 */
  function spy(rows) {
    const calls = [];
    return {
      calls,
      fetcher: async (skuId) => {
        calls.push(skuId);
        return rows;
      },
    };
  }

  test('同一行换商品（Apple → Banana）：必须按新 SKU 重新查询，不能复用旧缓存', async () => {
    const cache = createRecentPriceCache();
    const apple = spy(priceOf(APPLE, '6.50'));
    const banana = spy(priceOf(BANANA, '3.20'));
    await loadRecentPrices(cache, CUSTOMER_A, APPLE, () => apple.fetcher(APPLE));
    // 行号没变、只有 skuId 变了——按行号缓存正是在这里串数据
    const ok = await loadRecentPrices(cache, CUSTOMER_A, BANANA, () => banana.fetcher(BANANA));
    assert.ok(ok);
    assert.deepEqual(banana.calls, [BANANA], '换商品后没有重新请求历史价');
    assert.equal(recentPriceRows(cache, CUSTOMER_A, BANANA)[0].unitPrice, '3.20');
    assert.equal(recentPriceRows(cache, CUSTOMER_A, APPLE)[0].unitPrice, '6.50', '旧 SKU 缓存被覆盖');
  });

  test('删除第一行后第二行上移：上移行仍取自身 SKU 的历史价', async () => {
    const cache = createRecentPriceCache();
    await loadRecentPrices(cache, CUSTOMER_A, APPLE, async () => priceOf(APPLE, '6.50'));
    const rows = [{skuId: APPLE}, {skuId: BANANA}];
    rows.shift(); // 移除第一行，BANANA 从 index 1 变成 index 0
    const bananaCalls = [];
    await loadRecentPrices(cache, CUSTOMER_A, rows[0].skuId, async () => {
      bananaCalls.push(rows[0].skuId);
      return priceOf(BANANA, '3.20');
    });
    assert.deepEqual(bananaCalls, [BANANA], '上移后的行拿到了前一行的缓存（没重新请求）');
    assert.equal(recentPriceRows(cache, CUSTOMER_A, BANANA)[0].unitPrice, '3.20');
  });

  test('切客户（A → B）同 SKU：必须重新查询，不把 A 的成交价显示给 B', async () => {
    const cache = createRecentPriceCache();
    const calls = [];
    const fetcher = async () => {
      calls.push(calls.length + 1);
      return priceOf(APPLE, '6.50');
    };
    await loadRecentPrices(cache, CUSTOMER_A, APPLE, fetcher);
    await loadRecentPrices(cache, CUSTOMER_B, APPLE, fetcher);
    assert.equal(calls.length, 2, '换客户后复用了上一个客户的历史价缓存');
    assert.notEqual(recentPriceKey(CUSTOMER_A, APPLE), recentPriceKey(CUSTOMER_B, APPLE));
  });

  test('同客户同 SKU 复用缓存：第二次点击不再请求', async () => {
    const cache = createRecentPriceCache();
    let calls = 0;
    const fetcher = async () => {
      calls += 1;
      return priceOf(APPLE, '6.50');
    };
    assert.equal(await loadRecentPrices(cache, CUSTOMER_A, APPLE, fetcher), true);
    assert.equal(await loadRecentPrices(cache, CUSTOMER_A, APPLE, fetcher), true);
    assert.equal(calls, 1, '同一客户同一 SKU 重复请求');
  });

  test('请求失败：加载态必须落回、缓存不落，下次点击可重试', async () => {
    const cache = createRecentPriceCache();
    let calls = 0;
    await assert.rejects(
      loadRecentPrices(cache, CUSTOMER_A, APPLE, async () => {
        calls += 1;
        throw new Error('network down');
      }),
      /network down/
    );
    assert.equal(isLoadingRecentPrice(cache, CUSTOMER_A, APPLE), false, '失败后该行永久处于加载态');
    assert.equal(hasRecentPrices(cache, CUSTOMER_A, APPLE), false, '失败结果被当成缓存写入');
    await loadRecentPrices(cache, CUSTOMER_A, APPLE, async () => {
      calls += 1;
      return priceOf(APPLE, '6.50');
    });
    assert.equal(calls, 2, '失败后无法重试');
  });

  test('查过但确无历史价：区分「空结果」与「还没查」，且客户/SKU 缺失时不请求', async () => {
    const cache = createRecentPriceCache();
    let calls = 0;
    await loadRecentPrices(cache, CUSTOMER_A, APPLE, async () => {
      calls += 1;
      return [];
    });
    assert.equal(hasRecentPrices(cache, CUSTOMER_A, APPLE), true);
    assert.deepEqual(recentPriceRows(cache, CUSTOMER_A, APPLE), []);
    assert.equal(
      await loadRecentPrices(cache, null, APPLE, async () => {
        calls += 1;
        return [];
      }),
      false
    );
    assert.equal(
      await loadRecentPrices(cache, CUSTOMER_A, undefined, async () => {
        calls += 1;
        return [];
      }),
      false
    );
    assert.equal(calls, 1, '缺客户或缺 SKU 时仍然发起了请求');
    assert.equal(recentPriceRows(cache, null, APPLE).length, 0);
  });
}

// ---------------------------------------------------------------- 来源：order-contract.test.mjs
{
  function form() {
    const f = newOrder();
    f.customerId = '1';
    f.address = {receiverName: '客户', receiverPhone: '123', address: '地址'};
    f.items = [{skuId: '3', orderedQuantity: '1.0000', manualPriceOverride: false}];
    return f;
  }
  test('null, zero and not-yet-settled amounts remain distinguishable', () => {
    assert.equal(amount(null, true), '未定价');
    assert.equal(amount(null), '—');
    assert.equal(amount('0.0000', true), '¥ 0.00');
    assert.equal(fixed('1.00005'), '1.0001');
  });
  test('valid draft and supplement validation', () => {
    const f = form();
    assert.equal(validateOrder(f), undefined);
    f.orderSource = 'SUPPLEMENT';
    assert.match(validateOrder(f), /补单原因/);
    f.supplementReason = '补录';
    assert.equal(validateOrder(f), undefined);
  });
  test('duplicate SKU and missing override reason rejected', () => {
    const f = form();
    f.items.push({...f.items[0]});
    assert.match(validateOrder(f), /重复/);
    f.items.pop();
    f.items[0].manualPriceOverride = true;
    f.items[0].unitPrice = '0.0000';
    assert.match(validateOrder(f), /原因/);
    f.items[0].overrideReason = '免费';
    assert.equal(validateOrder(f), undefined);
  });
  test('payload preserves item identity and removes inactive override', () => {
    const f = form();
    Object.assign(f.items[0], {itemId: '9', version: 3, unitPrice: '2.0000', overrideReason: 'old'});
    assert.deepEqual(payload(f).items[0], {
      itemId: '9',
      version: 3,
      skuId: '3',
      orderedQuantity: '1.0000',
      manualPriceOverride: false,
      unitPrice: null,
      overrideReason: null,
      sortOrder: 0,
    });
  });
  test('stale data and availability errors are actionable', () => {
    assert.match(orderError({code: 40921}), /刷新/);
    assert.match(orderError({code: 40949}), /未定价/);
    assert.equal(orderError({msg: '业务提示'}), '业务提示');
    assert.match(orderError({data: {code: 40921}}), /刷新/);
    assert.equal(orderError({response: {data: {msg: '业务提示'}}}), '业务提示');
  });
  test('frontend enum excludes fulfillment and unpriced source values', () => {
    const source = readFileSync(new URL('../src/constants/business/scm/order-const.ts', import.meta.url), 'utf8');
    assert.match(source, /DRAFT[\s\S]*PENDING[\s\S]*CONFIRMED[\s\S]*CANCELLED/);
    assert.doesNotMatch(source, /UNPRICED|DELIVERING|PURCHASING|SIGNED/);
  });
  test('order operation enum matches backend ScmOrderOperationTypeEnum', () => {
    const ts = readFileSync(new URL('../src/constants/business/scm/order-const.ts', import.meta.url), 'utf8');
    const block = ts.match(/SCM_ORDER_OPERATION_ENUM[^{]*\{([\s\S]*?)\n\};/)[1];
    const front = [...block.matchAll(/^\s+([A-Z_]+):/gm)].map((m) => m[1]);
    const java = readFileSync(
      new URL('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/order/constant/ScmOrderOperationTypeEnum.java', import.meta.url),
      'utf8'
    );
    const back = [...java.match(/enum ScmOrderOperationTypeEnum \{([^}]*)\}/)[1].matchAll(/([A-Z_]+)\("/g)].map((m) => m[1]);
    assert.ok(back.length > 0, '后端枚举解析失败');
    assert.deepEqual(front.slice().sort(), back.slice().sort(), '前端 SCM_ORDER_OPERATION_ENUM 与后端 ScmOrderOperationTypeEnum 取值集合不一致');
    assert.ok(
      front.every((k) => ts.includes(`${k}: {value: '${k}'`)),
      '每个取值都要以自身作为 value，不能用别名'
    );
  });
}
