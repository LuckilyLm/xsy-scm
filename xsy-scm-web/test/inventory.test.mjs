/**
 * 库存模块契约测试。
 *
 * 由 4 个独立测试文件按模块合并：每个 `{ … }` 块对应原来一份文件，
 * 块内 helper / 常量各自作用域隔离；只有 import 提到顶部并去重。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, existsSync, readdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {
  movementTypeText,
  singleWarehouseDefault,
  specText,
  skuMainText,
  quantityText,
  moneyText,
  resolveStocktakeCopyUnits,
  BALANCE_QUERY_PAGE_SIZE,
} from '../src/views/business/scm/inventory/inventory-model.ts';
import {fixed4} from '../src/views/business/scm/common/scm-fixed.ts';
import {inventoryError} from '../src/views/business/scm/inventory/inventory-errors.ts';
import {
  SCM_INVENTORY_TRANSFER_STATUS_ENUM,
  SCM_INVENTORY_WARNING_STATUS_ENUM,
  SCM_INVENTORY_MOVEMENT_INBOUND_TYPES,
  SCM_INVENTORY_TABLE_ID,
  SCM_INVENTORY_CONVERSION_STATUS_ENUM,
  SCM_INVENTORY_LOSS_GAIN_TYPE_ENUM,
  SCM_INVENTORY_SOURCE_TYPE_ENUM,
  SCM_INVENTORY_LOSS_GAIN_STATUS_ENUM,
  SCM_INVENTORY_MOVEMENT_TYPE_ENUM,
  SCM_INVENTORY_CONVERSION_TYPE_ENUM,
} from '../src/constants/business/scm/inventory-const.ts';
import {TABLE_ID_CONST} from '../src/constants/support/table-id-const.ts';

// ---------------------------------------------------------------- 来源：backend-gap-inventory-contract.test.mjs
{
  /**
   * 前端后端字段缺口盘点的登记契约单测。
   *
   * `CONTRIBUTING.md` 要求 UI 不臆造后端尚未提供的业务事实，
   * 后端字段重构不得与 UI 改动同提交。因此执行中遇到的后端字段不足，
   * 一律登记到 `docs/status.md`（编号 B1、B4、B5、B7），
   * 前端只做「不渲染该段 / 不臆造数值」。
   *
   * 本文件钉住登记的两端一致性（钉的是引用完整性，不是文案）：
   * 1. 代码注释里引用的 B 编号，必须在盘点文档里真实存在 —— 防止编号写错或文档删条后注释成孤儿。
   * 2. 盘点文档里列出的编号不得重复 —— 防止两条缺口共用一个编号导致引用歧义。
   * 3. 代码里登记过的缺口文件，仍必须保留指向文档的溯源注释（不得只留「属后端缺口」这种无指向的说法）。
   * 4. 关键证据锚点仍在：文档引用的 VO/类名必须真实存在于后端源码（防止证据漂移）。
   */

  // test/ → xsy-scm-web/ → 仓库根
  const repo = fileURLToPath(new URL('../../', import.meta.url));
  const docPath = `${repo}/docs/status.md`;
  const webRoot = `${repo}/xsy-scm-web`;
  const backendJavaRoot = `${repo}/xsy-scm-server/sa-admin/src/main/java`;

  const doc = readFileSync(docPath, 'utf8');

  /** 后端生产源码里的全部类名（不含 `.java`），用于验证文档引用的证据锚点没有漂移。 */
  function backendClassNames() {
    const names = new Set();
    (function walk(dir) {
      for (const entry of readdirSync(dir, {withFileTypes: true})) {
        if (entry.isDirectory()) walk(`${dir}/${entry.name}`);
        else if (entry.name.endsWith('.java')) names.add(entry.name.replace(/\.java$/, ''));
      }
    })(backendJavaRoot);
    return names;
  }

  /** 文档里声明的所有缺口编号（表格首列的 | B12 | 形式）。 */
  function declaredIds() {
    return [...doc.matchAll(/^\|\s*(B\d+)\s*\|/gm)].map((m) => m[1]);
  }

  test('项目状态存在且声明了当前 B 系列缺口编号', () => {
    assert.ok(existsSync(docPath), '缺少 docs/status.md');
    const ids = declaredIds();
    assert.deepEqual(ids, ['B1', 'B4', 'B5', 'B7'], '缺口清单必须与当前未实现项一致');
  });

  test('缺口编号不重复', () => {
    const ids = declaredIds();
    const seen = new Set();
    for (const id of ids) {
      assert.ok(!seen.has(id), `编号 ${id} 在盘点文档中出现多次`);
      seen.add(id);
    }
  });

  test('代码注释引用的 B 编号必须在盘点文档中存在', () => {
    const registered = [['src/views/business/scm/promotion/promotion-coupon-list.vue', 'B7']];
    for (const [file, id] of registered) {
      const source = readFileSync(`${webRoot}/${file}`, 'utf8');
      // 源码注释和状态表共用 B 编号，保持双向引用。
      const cited =
        new RegExp(`(docs/status\\.md|项目状态)[\\s\\S]{0,80}${id}`).test(source) ||
        new RegExp(`${id}[\\s\\S]{0,80}(docs/status\\.md|项目状态)`).test(source);
      assert.ok(cited, `${file} 未指向项目状态中的 ${id}`);
      assert.ok(declaredIds().includes(id), `项目状态缺少被 ${file} 引用的 ${id}`);
    }
  });

  test('登记过的缺口文件不得退化为无指向的模糊说法', () => {
    for (const file of ['src/views/business/scm/promotion/promotion-coupon-list.vue']) {
      const source = readFileSync(`${webRoot}/${file}`, 'utf8');
      assert.match(source, /docs\/status\.md/, `${file} 丢失了项目状态溯源`);
    }
  });

  test('项目状态引用的后端证据锚点真实存在', () => {
    // 从文档里抽 `` `XxxVO` `` / `` `XxxService` `` / `` `XxxEntity` `` 形式的类名，逐个到后端源码里找。
    // 不写死锚点清单：文档只保留「当前仍成立」的条目，条目一关就会被删，
    // 写死的清单会把「条目已关闭」误报成「证据漂移」。反过来，只要文档还引用着某个类名，
    // 它就必须真实存在 —— 这才是要钉的不变量。
    const named = [...new Set([...doc.matchAll(/`([A-Z][A-Za-z]*(?:VO|Service|Entity))`/g)].map((m) => m[1]))];
    assert.ok(named.length >= 1, '项目状态没有引用任何后端类名，证据链已断（缺口的「当前缺口」列必须指向具体 VO/Service）');

    const existing = backendClassNames();
    for (const name of named) {
      assert.ok(existing.has(name), `盘点文档引用了后端不存在的类 ${name}（证据漂移）`);
    }
  });

  test('项目状态不得把「已具备的字段」误列为缺口', () => {
    // 流水行本身带 operator/createdAt，文档必须显式澄清，防止后续被误开单
    assert.match(doc, /operator[\s\S]{0,80}createdAt/);
    assert.match(doc, /不是缺口/);
  });

  test('已关闭的旧 UI 口径项仍被记录，防止被机械清理', () => {
    // 前端口径分歧随 `.num` 收口一并关闭，仍应留在项目状态中防止重复登记。
    assert.match(doc, /已关闭的旧 UI 口径项/);
    assert.match(doc, /\.num/);
    assert.match(doc, /不再是后端缺口/);
  });
}

// ---------------------------------------------------------------- 来源：inventory-contract.test.mjs
{
  /**
   * 库存域契约测试。
   *
   * 保护前端最容易悄悄违背、且违背后不会报错的几条契约：
   * 1. 默认仓库：恰好 1 个启用仓库才默认带出，否则一个都不选 ——
   *    多仓下自动选一个会让用户误以为在看全部库存；
   * 2. 三态展示：`null` / `undefined` / 空串 → `—`，
   *    `"0.0000"` → `0.0000`；「没有值」与「值是零」不得被合并；
   * 3. append-only：流水前端只有 query，没有新增 / 编辑 / 删除入口；
   * 4. 枚举与 DB 白名单同源：流水类型 `PURCHASE_IN` / `SALES_OUT` / `STOCKTAKE_GAIN` /
   *    `STOCKTAKE_LOSS` / `LOSS_REPORT` / `GAIN_REPORT`，来源 `PURCHASE_RECEIPT_ITEM` /
   *    `SALES_OUTBOUND_ITEM` / `SALES_ORDER_ITEM` / `STOCKTAKE_ITEM` / `LOSS_GAIN_ITEM`；
   * 5. 错误码有可执行文案：库存域全部码（含盘点 41019–41027、报损报溢 41028–41037）
   *    都必须在映射表里，且提示要说「下一步做什么」；
   * 6. 盘点口径不被误读：确认后的账面不一定等于实盘数（差异施加到确认瞬间的账面量上），
   *    页面必须写明，且新增表单不得提交账面量；
   * 7. 审批乐观锁：报损报溢的审批请求必须带上审批人看到的 `version`，
   *    且打开审批弹窗时不得重新拉取单据 —— 否则「审批人必须批准自己读到的内容」这条防线失效。
   */
  // 定点转换住在全域公共层（scm-fixed）：定价 / 营销等模块也要用它，
  // 放在库存域会让别的模块反向依赖库存。

  /**
   * 读源码并剥掉注释后返回。
   *
   * 先剥 HTML 注释再剥块注释（顺序不能反）：`<!--` 里没有星号斜杠，但 HTML 注释正文里
   * 常出现「星号 + 斜杠」的写法，先跑块注释正则会把 HTML 注释的收尾一起吞掉。
   */
  function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  // ------------------------------------------------------------------
  // 默认仓库
  // ------------------------------------------------------------------

  test('default warehouse only applies when exactly one enabled warehouse exists', () => {
    // 恰好 1 个 → 默认带出
    assert.equal(singleWarehouseDefault([{id: 7}]), 7);
    assert.equal(singleWarehouseDefault([{id: '7'}]), '7');

    // 0 个 → 不选（没什么可选，且 0 个仓库本身是异常状态，页面应以空态呈现）
    assert.equal(singleWarehouseDefault([]), undefined);
    assert.equal(singleWarehouseDefault(null), undefined);
    assert.equal(singleWarehouseDefault(undefined), undefined);

    // 2 个及以上 → 不自动选任何一个（多仓下自动选中一个仓会让人误以为在看全部库存）
    assert.equal(singleWarehouseDefault([{id: 1}, {id: 2}]), undefined);
    assert.equal(singleWarehouseDefault([{id: 1}, {id: 2}, {id: 3}]), undefined);
  });

  test('default warehouse wiring: the balance page applies it before the first query, and reset clears it', () => {
    const page = code('../src/views/business/scm/inventory/inventory-balance-list.vue');

    // 默认仓库必须在第一屏查询之前落定，否则首屏会先显示「全部仓库」再跳成「默认仓库」
    assert.match(page, /applySingleWarehouseDefault/);
    assert.match(page, /singleWarehouseDefault/);
    assert.ok(page.indexOf('await applySingleWarehouseDefault()') < page.indexOf('await queryData()'), '默认仓库必须在首次查询之前应用');

    // 重置的语义是「回到无筛选」，不得重新套用默认值
    const reset = page.slice(page.indexOf('function resetQuery'), page.indexOf('onMounted(async'));
    assert.ok(reset.length > 0, '未能定位 resetQuery');
    assert.match(reset, /warehouseId = undefined/);

    // 拉不到仓库列表时静默降级：默认仓库只是便利，不该让只读页打不开
    assert.match(page, /catch\s*\{[\s\S]*?warehouses\.value = \[\]/);
  });

  // ------------------------------------------------------------------
  // 三态展示
  // ------------------------------------------------------------------

  test('three-state quantity display never merges "no value" with "zero"', () => {
    assert.equal(quantityText(null), '—');
    assert.equal(quantityText(undefined), '—');
    assert.equal(quantityText(''), '—');

    assert.equal(quantityText('0.0000'), '0.0000');
    assert.equal(quantityText('10.0000'), '10.0000');
    // 原样返回：后端已保证 4 位定点，前端不做二次格式化（否则会与后端口径分叉）
    assert.equal(quantityText('12345678901234.5678'), '12345678901234.5678');
  });

  test('specValues render as readable text and degrade to a dash', () => {
    assert.equal(specText(null), '—');
    assert.equal(specText(undefined), '—');
    assert.equal(specText({}), '—');
    assert.equal(specText({规格: '散装'}), '规格：散装');
    assert.equal(specText({规格: '散装', 产地: '寿光'}), '规格：散装，产地：寿光');
  });

  test('sku cell prefers spec values and falls back to the spec name', () => {
    assert.equal(skuMainText({规格: '散装'}, '散装 500g'), '规格：散装');
    assert.equal(skuMainText(null, '散装 500g'), '散装 500g');
    assert.equal(skuMainText(undefined, '散装 500g'), '散装 500g');
    assert.equal(skuMainText({}, '散装 500g'), '散装 500g');
    assert.equal(skuMainText(null, null), '—');
    assert.equal(skuMainText(null, ''), '—');

    // 回归：页面曾写 `specText(...) || record.skuName`，而 specText 无值时返回 '—'（truthy），
    // 回落分支永远不会执行 —— 无规格值的商品在列表里只剩一个破折号。
    for (const page of ['inventory-balance-list.vue', 'inventory-movement-list.vue']) {
      const source = code('../src/views/business/scm/inventory/' + page);
      assert.match(source, /skuMainText\(record\.specValues, record\.skuName\)/, page + ' 未使用 skuMainText');
      assert.doesNotMatch(source, /specText\(record\.specValues\)\s*\|\|/, page + ' 仍在用 truthy 回落');
    }
  });

  // ------------------------------------------------------------------
  // 入 / 出方向
  // ------------------------------------------------------------------

  test('the movement page derives 入/出 from the shared inbound set, not from a local list', () => {
    const page = code('../src/views/business/scm/inventory/inventory-movement-list.vue');

    // 方向集合必须来自常量（与后端 `ScmInventoryMovementTypeEnum.getInbound()` 同源）。
    // 本页自己写一份 IN / OUT 名单的话，后端加类型时方向会判反，而数字看起来完全正常。
    assert.match(page, /SCM_INVENTORY_MOVEMENT_INBOUND_TYPES\.includes\(/);
    for (const literal of [
      'PURCHASE_IN',
      'SALES_OUT',
      'STOCKTAKE_GAIN',
      'STOCKTAKE_LOSS',
      'LOSS_REPORT',
      'GAIN_REPORT',
      'TRANSFER_IN',
      'TRANSFER_OUT',
      'CONVERT_IN',
      'CONVERT_OUT',
      'SALES_RETURN_IN',
      'PROMOTION_GIFT_OUT',
    ]) {
      assert.doesNotMatch(page, new RegExp(`'${literal}'`), `流水页硬编码了 ${literal} 来判方向`);
    }

    // 未知类型不渲染方向标签：否则会凭空出现一个橙色的「出」
    assert.match(page, /v-if="directionOf\(record\)"/);

    // 集合与枚举互补：12 个类型里恰好 6 个入、6 个出，没有既不进集合也不在枚举里的值
    assert.deepEqual([...SCM_INVENTORY_MOVEMENT_INBOUND_TYPES].sort(), [
      'CONVERT_IN',
      'GAIN_REPORT',
      'PURCHASE_IN',
      'SALES_RETURN_IN',
      'STOCKTAKE_GAIN',
      'TRANSFER_IN',
    ]);
    for (const type of SCM_INVENTORY_MOVEMENT_INBOUND_TYPES) {
      assert.ok(Object.keys(SCM_INVENTORY_MOVEMENT_TYPE_ENUM).includes(type), type + ' 不在流水类型枚举里');
    }
  });

  // ------------------------------------------------------------------
  // 定点字符串转换（InputNumber → 后端 4 位定点）
  // ------------------------------------------------------------------

  test('fixed4 pads to 4 decimals and turns "not set" into undefined, never an empty string', () => {
    assert.equal(fixed4(10), '10.0000');
    assert.equal(fixed4(0), '0.0000');
    assert.equal(fixed4(0.5), '0.5000');
    assert.equal(fixed4(12.3456), '12.3456');
    // 「不设该边界」是 undefined（提交时收口成 null），不是 "0.0000"，也不是空串 ——
    // 后端 @Pattern 接受 null、拒绝 ''，而 0.0000 是一个真实的阈值。
    assert.equal(fixed4(null), undefined);
    assert.equal(fixed4(undefined), undefined);
    // 转换只在模型里做一次：页面不得各写一份 toFixed
    for (const page of ['inventory-warning-threshold-list.vue', 'inventory-outbound-list.vue']) {
      const source = code('../src/views/business/scm/inventory/' + page);
      assert.doesNotMatch(source, /toFixed\(/, page + ' 自己做了定点转换');
    }
  });

  // ------------------------------------------------------------------
  // 移动加权成本
  // ------------------------------------------------------------------

  test('money text passes the server-formatted value through unchanged', () => {
    // 「没有这个事实」与「真的是零」不得合并 —— 与 quantityText 同一口径
    assert.equal(moneyText(null), '—');
    assert.equal(moneyText(undefined), '—');
    assert.equal(moneyText(''), '—');

    // 均价 4 位、金额 2 位，都由源头定好；前端再加千分位/补零就会与后端口径分叉
    assert.equal(moneyText('7.0000'), '7.0000');
    assert.equal(moneyText('112.00'), '112.00');
    assert.equal(moneyText('0.0000'), '0.0000');
    assert.equal(moneyText('12345678.9000'), '12345678.9000');
  });

  test('the balance page shows the moving-average cost and the derived amount', () => {
    const page = code('../src/views/business/scm/inventory/inventory-balance-list.vue');
    assert.match(page, /dataIndex: 'avgCost'/, '余额页必须有均价列');
    assert.match(page, /dataIndex: 'amount'/, '余额页必须有金额列');
    // 两列都要走 moneyText，不能落到「原样 {{ record[column.dataIndex] }}」的兜底分支
    assert.match(page, /column\.dataIndex === 'avgCost'[\s\S]{0,120}moneyText\(record\.avgCost\)/);
    assert.match(page, /column\.dataIndex === 'amount'[\s\S]{0,120}moneyText\(record\.amount\)/);

    const types = code('../src/views/business/scm/inventory/inventory-types.ts');
    // 均价是 NOT NULL DEFAULT 0，所以类型里不带 `| null`；金额允许为空
    assert.match(types, /avgCost\?:\s*string;/);
    assert.match(types, /amount\?:\s*string \| null;/);
  });

  test('movement type text falls back to the raw value so an unmapped type stays visible', () => {
    const labels = SCM_INVENTORY_MOVEMENT_TYPE_ENUM;
    assert.equal(movementTypeText('PURCHASE_IN', labels), '采购入库');
    assert.equal(movementTypeText('', labels), '—');
    assert.equal(movementTypeText(null, labels), '—');
    assert.equal(movementTypeText(undefined, labels), '—');
    // 未知类型不显示成「—」：一个后端新增而前端未跟上的类型本身就是有用信号。
    // 用 UNKNOWN_IN（明确不存在的名字）：真实类型已全部落地，
    // 再拿「未实现的业务类型」当反例会每落地一个就要改一次。
    assert.equal(movementTypeText('UNKNOWN_IN', labels), 'UNKNOWN_IN');
    // 文案表里缺 desc 时同样回落到原值，而不是显示空白
    assert.equal(movementTypeText('PURCHASE_IN', {}), 'PURCHASE_IN');
  });

  // ------------------------------------------------------------------
  // 枚举 / 表格 id
  // ------------------------------------------------------------------

  test('inventory enums expose exactly the values the backend CHECK whitelist allows', () => {
    assert.deepEqual(Object.keys(SCM_INVENTORY_MOVEMENT_TYPE_ENUM), [
      'PURCHASE_IN',
      'SALES_OUT',
      'SALES_RETURN_IN',
      'STOCKTAKE_GAIN',
      'STOCKTAKE_LOSS',
      'LOSS_REPORT',
      'GAIN_REPORT',
      'TRANSFER_OUT',
      'TRANSFER_IN',
      'CONVERT_OUT',
      'CONVERT_IN',
      'PROMOTION_GIFT_OUT',
    ]);
    assert.deepEqual(Object.keys(SCM_INVENTORY_SOURCE_TYPE_ENUM), [
      'PURCHASE_RECEIPT_ITEM',
      'SALES_OUTBOUND_ITEM',
      'SALES_RETURN_RECEIPT_ITEM',
      'SALES_ORDER_ITEM',
      'STOCKTAKE_ITEM',
      'LOSS_GAIN_ITEM',
      'TRANSFER_OUT_ITEM',
      'TRANSFER_IN_ITEM',
      'CONVERT_OUT_ITEM',
      'CONVERT_IN_ITEM',
      'ORDER_PROMOTION_GIFT',
    ]);

    // 值与键逐字一致（后端 `ScmInventoryMovementTypeEnum.name()` 就是持久化值）
    for (const [key, item] of Object.entries(SCM_INVENTORY_MOVEMENT_TYPE_ENUM)) {
      assert.equal(item.value, key);
      assert.ok(item.desc && item.desc.length > 0, key + ' 缺少中文描述');
    }
    for (const [key, item] of Object.entries(SCM_INVENTORY_SOURCE_TYPE_ENUM)) {
      assert.equal(item.value, key);
      assert.ok(item.desc && item.desc.length > 0, key + ' 缺少中文描述');
    }

    // 盘盈与盘亏必须是两个类型：方向要能从类型本身读出来，否则 DB 的
    // `ck_inventory_movement_snap` 无法判定 after 该加还是该减。报损 / 报溢、转出 / 转入同理。
    assert.doesNotMatch(JSON.stringify(SCM_INVENTORY_MOVEMENT_TYPE_ENUM), /STOCKTAKE_ADJUST/);

    // 全部类型必须恰好分成两个方向组（入 6 / 出 6）——
    // 这是 `ck_inventory_movement_snap`「按方向分组」写法的前提。
    const inbound = ['PURCHASE_IN', 'SALES_RETURN_IN', 'STOCKTAKE_GAIN', 'GAIN_REPORT', 'TRANSFER_IN', 'CONVERT_IN'];
    const outbound = ['SALES_OUT', 'STOCKTAKE_LOSS', 'LOSS_REPORT', 'TRANSFER_OUT', 'CONVERT_OUT', 'PROMOTION_GIFT_OUT'];
    assert.deepEqual([...inbound, ...outbound].sort(), Object.keys(SCM_INVENTORY_MOVEMENT_TYPE_ENUM).sort());

    // 页面方向判定用的是导出的 `SCM_INVENTORY_MOVEMENT_INBOUND_TYPES`，
    // 因此它必须与上面的入方向组逐字一致，否则报表会把退货回库显示成「出」。
    assert.deepEqual([...SCM_INVENTORY_MOVEMENT_INBOUND_TYPES].sort(), inbound.sort());

    // 真实类型已全部落地；这里断言的是「白名单之外一律拒绝」这条性质本身
    assert.doesNotMatch(JSON.stringify(SCM_INVENTORY_MOVEMENT_TYPE_ENUM), /UNKNOWN|ADJUST/);
  });

  test('loss/gain document enums match the backend state machine', () => {
    // 方向是单据级属性：一张单要么全报损、要么全报溢
    assert.deepEqual(Object.keys(SCM_INVENTORY_LOSS_GAIN_TYPE_ENUM), ['LOSS', 'OVERFLOW']);
    // 没有 DRAFT：报损报溢创建即提交待审核（录单与审批应当由不同的人完成）
    assert.deepEqual(Object.keys(SCM_INVENTORY_LOSS_GAIN_STATUS_ENUM), ['PENDING', 'COMPLETED', 'REJECTED']);
    assert.doesNotMatch(JSON.stringify(SCM_INVENTORY_LOSS_GAIN_STATUS_ENUM), /DRAFT/);

    for (const [key, item] of Object.entries(SCM_INVENTORY_LOSS_GAIN_TYPE_ENUM)) {
      assert.equal(item.value, key);
      assert.ok(item.desc && item.desc.length > 0, key + ' 缺少中文描述');
    }
    for (const [key, item] of Object.entries(SCM_INVENTORY_LOSS_GAIN_STATUS_ENUM)) {
      assert.equal(item.value, key);
      assert.ok(item.desc && item.desc.length > 0, key + ' 缺少中文描述');
    }
  });

  test('inventory table DOM ids are distinct, non-empty and registered with numeric table ids', () => {
    assert.deepEqual(Object.keys(SCM_INVENTORY_TABLE_ID), [
      'BALANCE',
      'MOVEMENT',
      'OUTBOUND',
      'RESERVATION',
      'STOCKTAKE',
      'LOSS_GAIN',
      'TRANSFER',
      'WARNING',
      'WARNING_THRESHOLD',
      'CONVERSION',
      'LOT_BALANCE',
      'TRACE_UNIT',
      'TRACE_CORRECTION',
    ]);
    assert.equal(SCM_INVENTORY_TABLE_ID.BALANCE, 'scm-inventory-balance-table');
    assert.equal(SCM_INVENTORY_TABLE_ID.MOVEMENT, 'scm-inventory-movement-table');
    assert.equal(SCM_INVENTORY_TABLE_ID.STOCKTAKE, 'scm-inventory-stocktake-table');
    assert.equal(SCM_INVENTORY_TABLE_ID.LOSS_GAIN, 'scm-inventory-loss-gain-table');
    assert.equal(SCM_INVENTORY_TABLE_ID.TRANSFER, 'scm-inventory-transfer-table');
    assert.equal(SCM_INVENTORY_TABLE_ID.WARNING, 'scm-inventory-warning-table');
    assert.equal(SCM_INVENTORY_TABLE_ID.WARNING_THRESHOLD, 'scm-inventory-warning-threshold-table');

    const business = TABLE_ID_CONST.BUSINESS;
    assert.equal(business.SCM_INVENTORY_BALANCE, 50017);
    assert.equal(business.SCM_INVENTORY_MOVEMENT, 50018);
    assert.equal(business.SCM_INVENTORY_OUTBOUND, 50019);
    assert.equal(business.SCM_INVENTORY_RESERVATION, 50020);
    assert.equal(business.SCM_INVENTORY_STOCKTAKE, 50021);
    assert.equal(business.SCM_INVENTORY_LOSS_GAIN, 50022);
    assert.equal(business.SCM_INVENTORY_TRANSFER, 50023);
    assert.equal(business.SCM_INVENTORY_WARNING, 50024);
    assert.equal(business.SCM_INVENTORY_WARNING_THRESHOLD, 50025);
    assert.equal(business.SCM_INVENTORY_CONVERSION, 50026);
    assert.equal(business.SCM_INVENTORY_LOT_BALANCE, 50055);
    assert.equal(business.SCM_INVENTORY_TRACE_UNIT, 50056);
    assert.equal(business.SCM_INVENTORY_TRACE_CORRECTION, 50057);
    assert.equal(SCM_INVENTORY_TABLE_ID.CONVERSION, 'scm-inventory-conversion-table');
    assert.equal(SCM_INVENTORY_TABLE_ID.LOT_BALANCE, 'scm-inventory-lot-balance-table');
    assert.equal(SCM_INVENTORY_TABLE_ID.TRACE_UNIT, 'scm-inventory-trace-unit-table');
    assert.equal(SCM_INVENTORY_TABLE_ID.TRACE_CORRECTION, 'scm-inventory-trace-correction-table');

    // 数字 tableId 必须全局唯一（列配置按它持久化，撞了会串列）
    const numeric = Object.values(business).filter((value) => typeof value === 'number');
    assert.equal(new Set(numeric).size, numeric.length, 'TABLE_ID_CONST.BUSINESS 存在重复的数字 id');
  });

  // ------------------------------------------------------------------
  // append-only
  // ------------------------------------------------------------------

  test('the movement frontend API is append-only: query only, no write endpoint', () => {
    const api = code('../src/api/business/scm/inventory-movement-api.ts');
    assert.match(api, /query:/);
    for (const forbidden of ['create', 'update', 'delete', 'remove', 'batchDelete', 'cancel', 'adjust']) {
      assert.doesNotMatch(api, new RegExp(`\\b${forbidden}\\b`), `流水 API 不得出现 ${forbidden}`);
    }

    // 余额 API 同样只有两个只读端点：余额不是可以被赋值的状态，它只能是流水的净和
    const balanceApi = code('../src/api/business/scm/inventory-balance-api.ts');
    assert.match(balanceApi, /query:/);
    assert.match(balanceApi, /detail:/);
    for (const forbidden of ['create', 'update', 'delete', 'remove', 'setQuantity', 'adjust']) {
      assert.doesNotMatch(balanceApi, new RegExp(`\\b${forbidden}\\b`), `余额 API 不得出现 ${forbidden}`);
    }
  });

  test('both inventory pages are read-only and wired to TableOperator + their own DOM id', () => {
    const pages = ['inventory-balance-list.vue', 'inventory-movement-list.vue'];
    for (const page of pages) {
      const source = code('../src/views/business/scm/inventory/' + page);
      assert.match(source, /TableOperator/, page + ' 未使用 TableOperator');
      assert.match(source, /SCM_INVENTORY_TABLE_ID/, page + ' 未绑定表格 DOM id');
      assert.match(source, /v-privilege/, page + ' 缺少权限指令');

      // 只读页不得有任何写入口。判据刻意落在结构而不是文案上：
      // 页面里确实会写「流水不可编辑、不可删除」这类说明文字，用关键词匹配会误报。
      //   - 本项目的写表单一律住在 `<a-modal>` 里（各域的 `*-form-drawer/modal`）；
      //   - 写操作一定表现为调用某个 `create/update/remove/delete` 方法。
      assert.doesNotMatch(source, /<a-modal/, page + ' 出现了写表单弹窗');
      assert.doesNotMatch(source, /\.(create|update|remove|delete)\s*\(/, page + ' 调用了写接口');
    }
  });

  test('inventory sources never depend on the purchase form model (domains stay decoupled)', () => {
    // 库存域是与来源无关的领域原语：它可以复用展示工具（`scm-display`），
    // 但不得反向依赖采购的表单模型 —— 那会让未来接入出库 / 调拨时被迫带上采购语义。
    for (const file of ['inventory-model.ts', 'inventory-types.ts', 'inventory-errors.ts']) {
      const source = code('../src/views/business/scm/inventory/' + file);
      assert.doesNotMatch(source, /purchase-form-model|purchase-errors/, file + ' 反向依赖了采购域');
    }
  });

  // ------------------------------------------------------------------
  // 错误码
  // ------------------------------------------------------------------

  test('inventory errors resolve from body, data and response shapes with actionable text', () => {
    // 4 个库存码都必须有可执行提示（不是复述码名）
    assert.match(inventoryError({code: 40486}), /库存余额不存在/);
    assert.match(inventoryError({code: 41001}), /不做自动换算/);
    assert.match(inventoryError({code: 41002}), /不能重复入库/);
    assert.match(inventoryError({code: 41003}), /入库事实不合法/);

    // 跨域复用的既有码也要登记：让用户看到一个裸错误码是更糟的选择
    assert.match(inventoryError({code: 40485}), /仓库不存在/);
    assert.match(inventoryError({code: 40921}), /刷新/);
    assert.match(inventoryError({code: 40000}), /请求参数/);

    // 三种包裹形状都要认（与采购域同一口径）
    assert.match(inventoryError({data: {code: 41001}}), /不做自动换算/);
    assert.match(inventoryError({response: {data: {code: 41002}}}), /不能重复入库/);

    // 未登记的码不吞掉后端消息
    assert.equal(inventoryError({code: 99999, msg: '后端原文'}), '后端原文');
    assert.equal(inventoryError({msg: '后端原文'}), '后端原文');
    assert.equal(inventoryError({}), '操作失败，请重试');
  });

  test('movement type filter is omitted (not sent as an empty string) when cleared', () => {
    // 后端 `InventoryMovementQueryForm.movementType` 上是 `@Pattern(regexp = "PURCHASE_IN")`，
    // 空串会被 Bean Validation 判成 40000，页面看起来像「一清空筛选就报错」。
    const page = code('../src/views/business/scm/inventory/inventory-movement-list.vue');
    assert.match(page, /movementType: queryForm\.movementType \|\| undefined/);
    assert.match(page, /occurredFrom: occurredRange\.value\?\.\[0\] \?\? null/);
    assert.match(page, /occurredTo: occurredRange\.value\?\.\[1\] \?\? null/);
  });

  // ------------------------------------------------------------------
  // 盘点
  // ------------------------------------------------------------------

  test('the stocktake page is a stateful document page wired to its own DOM id and privileges', () => {
    const page = code('../src/views/business/scm/inventory/inventory-stocktake-list.vue');
    assert.match(page, /TableOperator/);
    assert.match(page, /SCM_INVENTORY_TABLE_ID/);
    assert.match(page, /v-privilege/);

    // 四个权限点都要出现：查询 / 新建 / 编辑 / 确认 / 删除。
    // 「确认盘点」必须是独立权限 —— 它会真实调整库存并写不可逆流水，
    // 允许仓管录数、由主管确认是完全合理的分工。
    for (const perm of [
      'scm:inventory:stocktake:query',
      'scm:inventory:stocktake:add',
      'scm:inventory:stocktake:update',
      'scm:inventory:stocktake:confirm',
      'scm:inventory:stocktake:delete',
    ]) {
      assert.match(page, new RegExp(perm.replace(/:/g, ':')), page + ' 缺少权限 ' + perm);
    }

    // 只有草稿可写：确认 / 取消 / 删除都必须挂在 status === 'DRAFT' 上
    assert.match(page, /record\.status === 'DRAFT'/);

    // 实盘量允许 0（确实一件不剩）：InputNumber 的下限是 0（不是 0.0001），
    // 提交走 fixed4 补足 4 位定点 —— 空值（null）与 0.0000 必须区分。
    assert.match(page, /:min="0"/);
    assert.doesNotMatch(page, /:min="0\.0001"/, '实盘量必须允许 0');
    assert.match(page, /actualQuantity: fixed4\(i\.actualQuantity\) as string/);
  });

  test('the stocktake page documents that delta is applied to the live book quantity', () => {
    // 这里最容易让人误读的一条口径：确认后的账面不一定等于实盘数，
    // 因为「保存草稿 → 确认」之间发生的收货 / 出库会被保留（差异是施加到当前账面量上的）。
    // 页面上必须写明，否则用户会把正确行为当成 bug。
    const detail = code('../src/views/business/scm/inventory/components/inventory-stocktake-detail-drawer.vue');
    assert.match(detail, /差异按确认时的账面量计算/);
    assert.doesNotMatch(detail, /快照/, '账面量的存储方式不写进界面');

    // 明细里的账面量由服务端快照，前端不得提交它（否则盘点能凭空制造差异）
    const api = code('../src/api/business/scm/inventory-stocktake-api.ts');
    assert.match(api, /create:/);
    assert.match(api, /confirm:/);
    // 盘点单是有状态单据，与 append-only 的流水不同：它有 update / cancel / delete
    assert.match(api, /update:/);
    assert.match(api, /cancel:/);
    assert.match(api, /delete:/);

    const types = code('../src/views/business/scm/inventory/inventory-types.ts');
    const addForm = types.slice(types.indexOf('export interface InventoryStocktakeAdd'));
    assert.ok(addForm.length > 0, '未能定位 InventoryStocktakeAdd');
    assert.doesNotMatch(addForm, /bookQuantity/, '新增表单不得提交账面量');
  });

  test('stocktake error codes all have actionable Chinese text', () => {
    // 盘点 9 个码：41019–41027。每一个都必须说「下一步做什么」。
    const codes = [41019, 41020, 41021, 41022, 41023, 41024, 41025, 41026, 41027];
    for (const code of codes) {
      const text = inventoryError({code});
      assert.notEqual(text, '操作失败，请重试', code + ' 未登记可执行提示');
      assert.ok(text.length > 8, code + ' 的提示过于简短，无法指导下一步');
    }
    // 两条最容易被误读的码：提示里必须给出「怎么做」
    assert.match(inventoryError({code: 41023}), /先办理入库/);
    assert.match(inventoryError({code: 41025}), /释放/);
  });

  // ------------------------------------------------------------------
  // 报损报溢
  // ------------------------------------------------------------------

  test('the loss/gain page wires its own DOM id, six privileges and a PENDING-only action set', () => {
    const page = code('../src/views/business/scm/inventory/inventory-loss-gain-list.vue');
    assert.match(page, /TableOperator/);
    assert.match(page, /SCM_INVENTORY_TABLE_ID/);
    assert.match(page, /v-privilege/);

    // 六个权限点都要出现。「审批」与「驳回」必须是两个独立权限 ——
    // 允许主管审批、由另一角色驳回是常见分工，合成一个「审核」会让这两件事无法分权。
    for (const perm of [
      'scm:inventory:loss-gain:query',
      'scm:inventory:loss-gain:add',
      'scm:inventory:loss-gain:update',
      'scm:inventory:loss-gain:approve',
      'scm:inventory:loss-gain:reject',
      'scm:inventory:loss-gain:delete',
    ]) {
      assert.match(page, new RegExp(perm), page + ' 缺少权限 ' + perm);
    }

    // 只有待审核可写：编辑 / 审批 / 驳回 / 删除都必须挂在 status === 'PENDING' 上
    assert.match(page, /record\.status === 'PENDING'/);
    // 两个终态都不能出现在可写条件里
    assert.doesNotMatch(page, /record\.status === 'COMPLETED'/);
    assert.doesNotMatch(page, /record\.status === 'REJECTED'/);
  });

  test('the audit request carries the version the approver saw (optimistic lock)', () => {
    // 审批人必须批准自己读到的内容。若在「打开单据 → 点审批」之间单据被改过，
    // 后端以 40921 拒绝并要求刷新。因此前端必须把打开时的 version 原样回传，
    // 且不得在打开弹窗时重新拉取单据（那等于「总是批准最新的」，防线就没了）。
    const page = code('../src/views/business/scm/inventory/inventory-loss-gain-list.vue');
    assert.match(page, /version:\s*record\.version \?\? 0/);

    // 驳回必须在前端也校验一次意见，避免白跑一次请求才拿到 41037
    assert.match(page, /驳回时必须填写审核意见/);

    const api = code('../src/api/business/scm/inventory-loss-gain-api.ts');
    assert.match(api, /approve:/);
    assert.match(api, /reject:/);
    // 报损报溢是有状态单据，与 append-only 的流水不同
    assert.match(api, /create:/);
    assert.match(api, /update:/);
    assert.match(api, /delete:/);
    // 没有独立的「提交」端点：创建即待审核
    assert.doesNotMatch(api, /submit/);
  });

  test('loss/gain error codes all have actionable Chinese text', () => {
    // 报损报溢 10 个码：41028–41037
    for (let code = 41028; code <= 41037; code++) {
      const text = inventoryError({code});
      assert.notEqual(text, '操作失败，请重试', code + ' 未登记可执行提示');
      assert.ok(text.length > 8, code + ' 的提示过于简短，无法指导下一步');
    }
    // 三条最容易被误读的码：提示里必须给出「怎么做」
    assert.match(inventoryError({code: 41032}), /先办理入库/);
    assert.match(inventoryError({code: 41033}), /不能把库存变成负数/);
    assert.match(inventoryError({code: 41037}), /说明驳回原因/);
  });

  // ------------------------------------------------------------------
  // 调拨
  // ------------------------------------------------------------------

  test('the transfer status machine is two-step and in-transit is not cancellable', () => {
    // 两步式：DRAFT → SHIPPED（在途）→ RECEIVED，草稿可 CANCELLED
    assert.deepEqual(Object.keys(SCM_INVENTORY_TRANSFER_STATUS_ENUM), ['DRAFT', 'SHIPPED', 'RECEIVED', 'CANCELLED']);
    for (const [key, item] of Object.entries(SCM_INVENTORY_TRANSFER_STATUS_ENUM)) {
      assert.equal(item.value, key);
      assert.ok(item.desc && item.desc.length > 0, key + ' 缺少中文描述');
    }
    // 「在途」的中文描述必须是「在途」而不是「已发货」之类的完成态措辞 ——
    // 它代表货不在任何仓库里，措辞上不能让人以为已经落地。
    assert.equal(SCM_INVENTORY_TRANSFER_STATUS_ENUM.SHIPPED.desc, '在途');
  });

  test('the transfer page wires its own DOM id, six privileges and a two-step action set', () => {
    const page = code('../src/views/business/scm/inventory/inventory-transfer-list.vue');
    assert.match(page, /TableOperator/);
    assert.match(page, /SCM_INVENTORY_TABLE_ID/);
    assert.match(page, /v-privilege/);

    // 六个权限点。「发出」与「收货」必须分开 —— 跨仓调拨的常见分工是源仓发货、
    // 目标仓点收；由同一人两头都确认会让在途数量失去复核。
    for (const perm of [
      'scm:inventory:transfer:query',
      'scm:inventory:transfer:add',
      'scm:inventory:transfer:update',
      'scm:inventory:transfer:ship',
      'scm:inventory:transfer:receive',
      'scm:inventory:transfer:delete',
    ]) {
      assert.match(page, new RegExp(perm), page + ' 缺少权限 ' + perm);
    }

    // 发出只能对草稿、收货只能对在途 —— 两个动作各挂在自己的状态上
    assert.match(page, /record\.status === 'DRAFT'/);
    assert.match(page, /record\.status === 'SHIPPED'/);

    // 页面必须写明「在途期间货不在任何余额行里」，否则用户会以为货丢了
    assert.match(page, /在途/);
    assert.match(page, /不在任何仓库的余额/);
  });

  test('the transfer page keeps the four actor/time values as separate columns and keeps the arrow direction', () => {
    const page = code('../src/views/business/scm/inventory/inventory-transfer-list.vue');

    // 调拨的业务时刻是发出与收货；创建时间是技术字段
    assert.doesNotMatch(page, /title: '创建时间'/);
    // 时间与操作者各自成列（一格一值）
    assert.match(page, /title: '发出时间', dataIndex: 'shippedAt'/);
    assert.match(page, /title: '发出人', dataIndex: 'shippedBy'/);
    assert.match(page, /title: '收货时间', dataIndex: 'receivedAt'/);
    assert.match(page, /title: '收货人', dataIndex: 'receivedBy'/);

    // 方向是一格：源仓 → 目标仓，箭头弱化（不再用行内 style）
    assert.match(page, /class="transfer-direction__arrow"/);
    assert.doesNotMatch(page, /style="margin: 0 6px/, '方向箭头应走样式类，不写行内 style');

    // 状态走语义档位；在途仍是橙色（最容易被误读成丢失）
    assert.match(page, /SHIPPED: 'warning'/);
    assert.match(page, /RECEIVED: 'success'/);
    assert.match(page, /CANCELLED: 'neutral'/);

    // 操作列收窄到三个槽位，推进动作（发出 / 收货）留在行内
    assert.match(page, /dataIndex: 'action', align: 'center', fixed: 'right', width: 180/);
    assert.match(page, /ScmActionMore/);
    assert.match(page, /key: 'cancel', label: '取消单据'/);

    // 明细数量是 InputNumber，提交走 fixed4；在途报表的规格编码作 secondary text
    assert.match(page, /<a-input-number[\s\S]{0,120}record\.quantity/);
    assert.match(page, /quantity: fixed4\(i\.quantity\) as string/);
    assert.doesNotMatch(page, /toFixed\(/);
    // 在途报表：商品规格与规格编码各自成列
    assert.match(page, /title: '商品规格编码', dataIndex: 'skuCode'/);
    assert.match(page, /column\.dataIndex === 'skuCode'[\s\S]{0,120}record\.skuCode/);
  });

  test('transfer error codes all have actionable Chinese text', () => {
    // 调拨 11 个码：41038–41048
    for (let code = 41038; code <= 41048; code++) {
      const text = inventoryError({code});
      assert.notEqual(text, '操作失败，请重试', code + ' 未登记可执行提示');
      assert.ok(text.length > 8, code + ' 的提示过于简短，无法指导下一步');
    }
    // 三条最容易被误读的码：提示里必须给出「怎么做」
    assert.match(inventoryError({code: 41042}), /不能相同/);
    assert.match(inventoryError({code: 41044}), /统一两仓的采购单位/);
    assert.match(inventoryError({code: 41039}), /反向调拨/);
  });

  // ------------------------------------------------------------------
  // 阈值预警
  // ------------------------------------------------------------------

  test('the warning status enum matches the backend and treats the boundary as normal', () => {
    assert.deepEqual(Object.keys(SCM_INVENTORY_WARNING_STATUS_ENUM), ['NORMAL', 'LOW', 'HIGH']);
    for (const [key, item] of Object.entries(SCM_INVENTORY_WARNING_STATUS_ENUM)) {
      assert.equal(item.value, key);
      assert.ok(item.desc && item.desc.length > 0, key + ' 缺少中文描述');
    }
    // 状态是派生值，不落库：不得出现任何「已读 / 已忽略」这类需要持久化的状态
    assert.doesNotMatch(JSON.stringify(SCM_INVENTORY_WARNING_STATUS_ENUM), /READ|ACK|IGNORED|DISMISSED/);
  });

  test('the warning list page is read-only and defaults to abnormal-only', () => {
    const page = code('../src/views/business/scm/inventory/inventory-warning-list.vue');
    assert.match(page, /TableOperator/);
    assert.match(page, /SCM_INVENTORY_TABLE_ID/);
    assert.match(page, /v-privilege/);

    // 只读页不得有任何写入口。判据落在结构上而不是文案上：
    // 页面里确实会写「预警不能标记已读」这类说明文字，用关键词匹配会误报。
    //   - 本项目的写表单一律住在 `<a-modal>` 里；
    //   - 写操作一定表现为调用某个 create/update/remove/delete 方法。
    assert.doesNotMatch(page, /<a-modal/, '预警列表出现了写表单弹窗');
    assert.doesNotMatch(page, /\.(create|update|remove|delete)\s*\(/, '预警列表调用了写接口');

    // 判定基准必须在页面上写明：只给一个数字会让用户看不懂预警为什么触发
    assert.match(page, /可用量/);
    assert.match(page, /预留/);

    // 默认项必须是「仅异常」而不是「全部」—— 后端 status 为空时的语义就是只看异常，
    // 标成「全部」会与事实不符（用户选了它却看不到正常项，会以为系统漏数据）。
    assert.match(page, /仅异常/);
    assert.doesNotMatch(page, /label: '全部'/);
  });

  test('the threshold config page is not a stock-mutating page and validates the range client-side', () => {
    const page = code('../src/views/business/scm/inventory/inventory-warning-threshold-list.vue');
    assert.match(page, /TableOperator/);
    assert.match(page, /SCM_INVENTORY_TABLE_ID/);
    for (const perm of [
      'scm:inventory:threshold:query',
      'scm:inventory:threshold:add',
      'scm:inventory:threshold:update',
      'scm:inventory:threshold:delete',
    ]) {
      assert.match(page, new RegExp(perm), page + ' 缺少权限 ' + perm);
    }
    // 三条区间判据必须在提交前拦一道（后端 41051 会再判一次）
    assert.match(page, /下限和上限至少填写一个/);
    assert.match(page, /validator: validateAtLeastOneThreshold/);
    assert.match(page, /不得大于上限/);
    // 数值控件是 InputNumber（:min=0 + :precision=4），不是自由文本
    assert.match(page, /<a-input-number[\s\S]{0,120}form\.warnMin/);
    assert.match(page, /<a-input-number[\s\S]{0,120}form\.warnMax/);
    assert.match(page, /:precision="4"/);
    // 提交时必须补足 4 位定点字符串：后端 ScmStrictDecimalStringDeserializer 拒绝 JSON 数字。
    // 转换只在 inventory-model.fixed4 里做一次，页面不得各写一份 toFixed。
    assert.match(page, /fixed4\(form\.warnMin\)/);
    assert.match(page, /fixed4\(form\.warnMax\)/);
    assert.doesNotMatch(page, /toFixed\(/, '定点转换应由 fixed4 统一提供');
    // 不设边界要转成 null，不能把空串发上去（后端 @Pattern 接受 null、拒绝 ''）
    assert.match(page, /warnMin: min \?\? null/);
    assert.match(page, /warnMax: max \?\? null/);
    // 更新时间是技术字段，不上列
    assert.doesNotMatch(page, /title: '更新时间'/);
    assert.doesNotMatch(page, /dataIndex: 'updatedAt'/);

    // 配置接口没有「确认 / 审批」这类动作：改配置立即生效（预警是读时计算的）
    const api = code('../src/api/business/scm/inventory-warning-threshold-api.ts');
    assert.match(api, /create:/);
    assert.match(api, /update:/);
    assert.match(api, /delete:/);
    assert.doesNotMatch(api, /confirm|approve|submit/);
  });

  // ------------------------------------------------------------------
  // 预留 / 预警列表的展示契约
  // ------------------------------------------------------------------

  test('the reservation page shows the source document number, never a raw technical id', () => {
    const page = code('../src/views/business/scm/inventory/inventory-reservation-list.vue');
    // 来源单号取不到时显示破折号；回落成 `#<sourceDocumentId>` 会把技术主键端给使用者
    assert.match(page, /record\.sourceDocumentNo \|\| '—'/);
    assert.doesNotMatch(page, /sourceDocumentId/, '预留页不得展示裸主键');

    // 状态统一走 ScmStatusTag 语义档位，不再各自写 antd 色名
    assert.match(page, /ScmStatusTag/);
    assert.match(page, /ACTIVE: 'warning'/);
    assert.match(page, /CONSUMED: 'success'/);
    assert.doesNotMatch(page, /'orange'|'green'|'red'/, '状态色应由 tone 档位给出');

    // 数量与规格编码分列，编码可用等宽字形逐位核对
    assert.match(page, /class="scm-quantity"/);
    assert.match(page, /dataIndex === 'skuCode'[\s\S]{0,120}class="scm-mono"/);
  });

  test('the warning page keeps all three quantities and colours the level, not the row', () => {
    const page = code('../src/views/business/scm/inventory/inventory-warning-list.vue');
    // 判定基准是可用量：只给一个数字，用户看不懂预警为什么触发
    for (const index of ['quantity', 'reservedQuantity', 'availableQuantity']) {
      assert.match(page, new RegExp(`dataIndex: '${index}'`), '预警页缺少 ' + index + ' 列');
    }
    // 低于下限 = 断货风险（红），高于上限 = 积压（橙），正常不强调
    assert.match(page, /LOW: 'error'/);
    assert.match(page, /HIGH: 'warning'/);
    assert.match(page, /NORMAL: 'neutral'/);
    // 预警下限与上限各自成列（各自可空）
    assert.match(page, /title: '预警下限', dataIndex: 'warnMin'/);
    assert.match(page, /title: '预警上限', dataIndex: 'warnMax'/);
    // 状态列不是操作列，不该固定在右侧
    assert.doesNotMatch(page, /dataIndex: 'status'[^}]*fixed/);
  });

  test('the outbound page drops the technical timestamp and keeps the confirm action inline', () => {
    const page = code('../src/views/business/scm/inventory/inventory-outbound-list.vue');

    // 出库单的业务时刻是确认时间；创建时间是技术字段，不上列
    assert.doesNotMatch(page, /title: '创建时间'/);
    assert.match(page, /title: '出库时间', dataIndex: 'confirmedAt'/);

    // 状态走语义档位
    assert.match(page, /DRAFT: 'warning'/);
    assert.match(page, /CONFIRMED: 'success'/);
    assert.match(page, /CANCELLED: 'neutral'/);
    assert.doesNotMatch(page, /'orange'|'green'/, '状态色应由 tone 档位给出');

    // 操作列收窄到三个槽位（详情 / 确认出库 / 更多），不可撤销的确认出库留在行内
    assert.match(page, /dataIndex: 'action', align: 'center', fixed: 'right', width: 150/);
    assert.match(
      page,
      /v-if="record\.status === 'DRAFT'"[\s\S]{0,200}scm:inventory:outbound:confirm/,
      '确认出库是当前状态唯一的推进动作，必须留在行内'
    );
    assert.match(page, /ScmActionMore/);
    assert.match(page, /key: 'edit', label: '编辑'/);
    assert.match(page, /key: 'cancel', label: '取消单据'/);
    assert.match(page, /key: 'delete', label: '删除', danger: true/);

    // 明细数量是 InputNumber，提交走 fixed4（后端拒绝 JSON 数字，也拒绝空串）
    assert.match(page, /<a-input-number[\s\S]{0,120}record\.quantity/);
    assert.match(page, /quantity: fixed4\(i\.quantity\) as string/);
    assert.doesNotMatch(page, /toFixed\(/);
    // 明细的规格编码作 secondary text
    assert.match(page, /skuMainText\(record\.specValues, record\.skuName\)/);
  });

  test('warning error codes all have actionable Chinese text', () => {
    // 阈值预警 4 个码：41049–41052
    for (let code = 41049; code <= 41052; code++) {
      const text = inventoryError({code});
      assert.notEqual(text, '操作失败，请重试', code + ' 未登记可执行提示');
      assert.ok(text.length > 8, code + ' 的提示过于简短，无法指导下一步');
    }
    assert.match(inventoryError({code: 41050}), /只允许一条/);
    assert.match(inventoryError({code: 41051}), /下限不得大于上限/);
  });

  // ------------------------------------------------------------------
  // 规格转换
  // ------------------------------------------------------------------

  test('the conversion enums match the backend whitelist and stay cross-SKU', () => {
    assert.deepEqual(Object.keys(SCM_INVENTORY_CONVERSION_TYPE_ENUM), ['SPLIT', 'COMBINE']);
    // 状态机与报损报溢同构（没有 DRAFT：创建即提交待审核）
    assert.deepEqual(Object.keys(SCM_INVENTORY_CONVERSION_STATUS_ENUM), ['PENDING', 'COMPLETED', 'REJECTED']);
    assert.doesNotMatch(JSON.stringify(SCM_INVENTORY_CONVERSION_STATUS_ENUM), /DRAFT/);
    for (const [key, item] of Object.entries(SCM_INVENTORY_CONVERSION_TYPE_ENUM)) {
      assert.equal(item.value, key);
      assert.ok(item.desc && item.desc.length > 0, key + ' 缺少中文描述');
    }
    // 定位换算不受影响：转换是跨 SKU 的（源规格 → 目标规格），
    // 两个 SKU 各自仍只锁一个记账单位。一旦有人往类型枚举里加「单位」维度，这里会失败。
    assert.doesNotMatch(JSON.stringify(SCM_INVENTORY_CONVERSION_TYPE_ENUM), /UNIT/);
  });

  test('the conversion page is an approval page wired to its own DOM id and six privileges', () => {
    const page = code('../src/views/business/scm/inventory/inventory-conversion-list.vue');
    assert.match(page, /TableOperator/);
    assert.match(page, /SCM_INVENTORY_TABLE_ID/);
    for (const perm of [
      'scm:inventory:conversion:query',
      'scm:inventory:conversion:add',
      'scm:inventory:conversion:update',
      'scm:inventory:conversion:approve',
      'scm:inventory:conversion:reject',
      'scm:inventory:conversion:delete',
    ]) {
      assert.match(page, new RegExp(perm), page + ' 缺少权限 ' + perm);
    }
    // 只有待审核可写
    assert.match(page, /record\.status === 'PENDING'/);
    // 审批必须原样回传打开时读到的 version（乐观锁），且不在打开弹窗时重新拉单据
    assert.match(page, /version:\s*record\.version \?\? 0/);
    // 驳回必须在前端也校验一次意见，避免白跑一次请求才拿到 41063
    assert.match(page, /驳回时必须填写审核意见/);
    // 折算关系与两个单位都由单据声明：页面必须让用户填源/目标单位
    assert.match(page, /sourceUnit/);
    assert.match(page, /targetUnit/);
    // 同一行源与目标不得相同（前端先拦一道）
    assert.match(page, /源商品规格与目标商品规格不能相同/);

    const api = code('../src/api/business/scm/inventory-conversion-api.ts');
    assert.match(api, /approve:/);
    assert.match(api, /reject:/);
    assert.match(api, /create:/);
    assert.match(api, /update:/);
    assert.match(api, /delete:/);
  });

  // ------------------------------------------------------------------
  // 单据类页面（盘点 / 报损报溢 / 转换）的展示契约
  // ------------------------------------------------------------------

  test('the three inventory document pages hide the technical timestamp and centre the action column', () => {
    // 三张单据的业务时刻都是「审核 / 确认」时间，创建时间是技术字段
    for (const page of ['inventory-stocktake-list.vue', 'inventory-loss-gain-list.vue', 'inventory-conversion-list.vue']) {
      const source = code('../src/views/business/scm/inventory/' + page);
      assert.doesNotMatch(source, /title: '创建时间'/, page + ' 仍在列表展示创建时间');
      assert.doesNotMatch(source, /'orange'|'green'|'red'/, page + ' 状态色应由 tone 档位给出');
      assert.match(source, /ScmStatusTag/, page + ' 未使用 ScmStatusTag');
      assert.match(source, /ScmActionMore/, page + ' 未把低频动作收进「更多」');
      assert.match(source, /dataIndex: 'action', align: 'center', fixed: 'right'/, page + ' 操作列未居中固定');
      assert.doesNotMatch(source, /toFixed\(/, page + ' 自己做了定点转换');
    }
  });

  test('the stocktake page keeps the confirm action inline and re-counts copied rows', () => {
    const page = code('../src/views/business/scm/inventory/inventory-stocktake-list.vue');
    assert.match(page, /DRAFT: 'warning'/);
    assert.match(page, /CONFIRMED: 'success'/);
    assert.match(page, /CANCELLED: 'neutral'/);
    assert.match(page, /dataIndex: 'action', align: 'center', fixed: 'right', width: 150/);
    assert.match(
      page,
      /v-if="record\.status === 'DRAFT'"[\s\S]{0,200}scm:inventory:stocktake:confirm/,
      '确认盘点是当前状态唯一的推进动作，必须留在行内'
    );
    assert.match(page, /key: 'copy', label: '复制到新建'/);
    assert.match(page, /key: 'delete', label: '删除', danger: true/);
    // 实盘量用 InputNumber（:min=0 允许「一件不剩」），提交走 fixed4
    assert.match(page, /<a-input-number[\s\S]{0,140}record\.actualQuantity/);
    assert.match(page, /:min="0"/);
    assert.match(page, /actualQuantity: fixed4\(i\.actualQuantity\) as string/);
  });

  test('the loss/gain page colours the direction and folds the auditor into the audit time', () => {
    const page = code('../src/views/business/scm/inventory/inventory-loss-gain-list.vue');
    // 方向必须一眼可见：报损红、报溢绿
    assert.match(page, /LOSS: 'error'/);
    assert.match(page, /OVERFLOW: 'success'/);
    assert.match(page, /PENDING: 'warning'/);
    assert.match(page, /COMPLETED: 'success'/);
    assert.match(page, /REJECTED: 'error'/);
    // 审核时间与审核人各自成列
    assert.match(page, /title: '审核时间', dataIndex: 'auditedAt'/);
    assert.match(page, /title: '审核人', dataIndex: 'auditor'/);
    // 数量恒为正：InputNumber 的下限是 0.0001（方向由单据类型表达）
    assert.match(page, /<a-input-number[\s\S]{0,140}record\.quantity/);
    assert.match(page, /:min="0.0001"/);
    assert.match(page, /quantity: fixed4\(i\.quantity\) as string/);
  });

  test('the conversion page keeps the declared units free-text while quantities go through fixed4', () => {
    const page = code('../src/views/business/scm/inventory/inventory-conversion-list.vue');
    assert.match(page, /PENDING: 'warning'/);
    assert.match(page, /dataIndex: 'action', align: 'center', fixed: 'right', width: 160/);
    // 两个数量都是 InputNumber
    assert.match(page, /<a-input-number[\s\S]{0,140}record\.sourceQuantity/);
    assert.match(page, /<a-input-number[\s\S]{0,140}record\.targetQuantity/);
    assert.match(page, /sourceQuantity: fixed4\(i\.sourceQuantity\) as string/);
    assert.match(page, /targetQuantity: fixed4\(i\.targetQuantity\) as string/);
    // 单位是单据声明（折算关系的一部分），仍是自由文本 —— 不做单位换算
    assert.match(page, /<a-input v-model:value="record\.sourceUnit"/);
    assert.match(page, /<a-input v-model:value="record\.targetUnit"/);
  });

  test('conversion error codes all have actionable Chinese text', () => {
    // 规格转换 12 个码：41053–41064
    for (let code = 41053; code <= 41064; code++) {
      const text = inventoryError({code});
      assert.notEqual(text, '操作失败，请重试', code + ' 未登记可执行提示');
      assert.ok(text.length > 8, code + ' 的提示过于简短，无法指导下一步');
    }
    assert.match(inventoryError({code: 41057}), /不能相同/);
    assert.match(inventoryError({code: 41059}), /不做自动换算/);
    assert.match(inventoryError({code: 41061}), /可用量/);
  });

  // ------------------------------------------------------------------
  // node 可加载性
  // ------------------------------------------------------------------

  test('every node-loaded SCM module stays free of value imports on relative paths', () => {
    // `purchase-form-model.ts` 曾用 `export { datetime } from '../common/scm-display'` 转出时间渲染。
    // 打包器（Vite）与 `vue-tsc` 都会自动补全扩展名，所以 `npm run build` / `npm run typecheck`
    // 都发现不了；但 `npm run test` 用 `node --experimental-strip-types --test` 直接加载 `.ts`，
    // node 的 ESM 解析不做补全 → 整份前端单测以 ERR_MODULE_NOT_FOUND 加载失败。
    //
    // 而「补上 `.ts`」这条路是走不通的：本项目 `tsconfig` 未开启 `allowImportingTsExtensions`，
    // 值导入写 `.ts` 会触发 TS5097（被 `tools/ts_baseline_ratchet.py` 的 SCM 零错误区拦下）。
    //
    // 两个约束的交集就是这条纪律：node 可加载的模块只能有 type-only 的相对导入
    // （type-only 会被类型擦除，node 根本不会去解析它）。
    const modules = [
      '../src/views/business/scm/purchase/purchase-form-model.ts',
      '../src/views/business/scm/purchase/purchase-errors.ts',
      '../src/views/business/scm/inventory/inventory-model.ts',
      '../src/views/business/scm/inventory/inventory-errors.ts',
      '../src/views/business/scm/product/product-form-model.ts',
      '../src/views/business/scm/order/order-form-model.ts',
      '../src/views/business/scm/order/order-errors.ts',
      '../src/views/business/scm/customer/customer-form-model.ts',
      '../src/views/business/scm/supplier/supplier-form-model.ts',
      '../src/views/business/scm/common/scm-area.ts',
    ];
    for (const module of modules) {
      const source = code(module);
      // 先剥掉 type-only 的 import/export（含多行形状），剩下的相对路径 from 就是值导入
      const withoutTypeOnly = source.replace(/(import|export)\s+type\s[\s\S]*?from\s+'[^']+';?/g, '');
      const valueImports = [...withoutTypeOnly.matchAll(/from\s+'([^']+)'/g)]
        .map((match) => match[1])
        .filter((specifier) => specifier.startsWith('.'));
      assert.deepEqual(valueImports, [], `${module} 出现了相对路径的值导入：node 直接加载 .ts 时无法解析，补 .ts 又会触发 TS5097`);
    }
  });
}

// ---------------------------------------------------------------- 来源：stocktake-copy-units.test.mjs
{
  /**
   * 「复制历史盘点」按页解析来源 SKU 单位的测试。
   *
   * 钉死的是两个不报错的失效方式：一次拉 2000 行会被后端 `@Max(100)` 拒成 400（功能直接不可用），
   * 而「翻页时静默丢 SKU」会让复制出来的盘点单少几行却毫无提示（漏清点 = 账实差异）。
   *
   * 分页语义按真实接口造：只有整页才需要继续翻页，不足一页即视为最后一页。
   */

  /** 把命中行补到整页（填充 SKU 不在来源里，只负责让这一页「读得完但还没找齐」）。 */
  function fullPage(ids, seed) {
    const rows = ids.map((skuId) => ({skuId, unit: 'kg'}));
    let n = 0;
    while (rows.length < BALANCE_QUERY_PAGE_SIZE) {
      rows.push({skuId: `filler-${seed}-${n++}`, unit: 'kg'});
    }
    return rows;
  }

  /** 最后一页（不足整页）：读到它即收尾。 */
  const lastPage = (ids) => ids.map((skuId) => ({skuId, unit: 'kg'}));

  /** 记录每次请求页参数的假查询：断言「不许越过 pageSize 上限」「不该再翻页」只能靠看到的入参。 */
  function fakeBalance(pages) {
    const requested = [];
    return {
      requested,
      fetchPage: async (pageNum, pageSize) => {
        requested.push({pageNum, pageSize});
        return pages[pageNum - 1] ?? [];
      },
    };
  }

  test('pageSize 恒等于后端上限 100，页码从 1 连续递增', async () => {
    const page = fakeBalance([fullPage([1], 1), fullPage([2], 2), lastPage([3])]);
    await resolveStocktakeCopyUnits([2, 3], page.fetchPage);
    assert.equal(BALANCE_QUERY_PAGE_SIZE, 100);
    assert.deepEqual(
      page.requested.map((r) => r.pageNum),
      [1, 2, 3]
    );
    for (const r of page.requested) {
      assert.equal(r.pageSize, 100, '复制盘点用了后端会拒掉的 pageSize');
    }
  });

  test('来源 SKU 提前找齐即停止翻页，不多打一次余额查询', async () => {
    const page = fakeBalance([fullPage([1, 2], 1), fullPage([3, 4], 2), lastPage([5])]);
    const {unitBySku, missingSkuIds} = await resolveStocktakeCopyUnits([1, 3], page.fetchPage);
    assert.deepEqual(
      page.requested.map((r) => r.pageNum),
      [1, 2],
      '找齐后仍在继续翻页'
    );
    assert.deepEqual([...unitBySku.keys()].sort(), ['1', '3']);
    assert.deepEqual(missingSkuIds, []);
  });

  test('来源 SKU 分散在多页时逐个解析到单位，重复来源只解析一次', async () => {
    const page = fakeBalance([fullPage([1, 2], 1), lastPage([3])]);
    const {unitBySku, missingSkuIds} = await resolveStocktakeCopyUnits([3, 1, 3, 1], page.fetchPage);
    assert.deepEqual(missingSkuIds, []);
    assert.equal(unitBySku.get('1'), 'kg');
    assert.equal(unitBySku.get('3'), 'kg');
    assert.equal(page.requested.length, 2, '重复 SKU 造成额外的余额请求');
  });

  test('整页刚好 100 行时继续翻页，读到不足一页才收尾（不漏最后一页）', async () => {
    const page = fakeBalance([fullPage([5], 1), lastPage([900])]);
    const {unitBySku, missingSkuIds} = await resolveStocktakeCopyUnits([5, 900], page.fetchPage);
    assert.deepEqual(
      page.requested.map((r) => r.pageNum),
      [1, 2]
    );
    assert.equal(unitBySku.get('900'), 'kg');
    assert.deepEqual(missingSkuIds, []);
  });

  test('翻到最后一页仍缺的 SKU 显性返回，不静默丢行', async () => {
    const page = fakeBalance([lastPage([1, 2])]);
    const {unitBySku, missingSkuIds} = await resolveStocktakeCopyUnits([1, 2, 404, 405], page.fetchPage);
    assert.deepEqual(missingSkuIds, ['404', '405']);
    assert.equal(unitBySku.has('404'), false, '缺失 SKU 被填了兜底单位');
  });

  test('余额行缺 unit 时回落空串而不是编造单位', async () => {
    const page = fakeBalance([[{skuId: 7}]]);
    const {unitBySku} = await resolveStocktakeCopyUnits([7], page.fetchPage);
    assert.equal(unitBySku.get('7'), '');
  });

  test('来源行为空 skuId 时不参与解析，交由调用方整单拒绝', async () => {
    const page = fakeBalance([lastPage([1])]);
    const {unitBySku, missingSkuIds} = await resolveStocktakeCopyUnits([null, undefined, '', 1], page.fetchPage);
    assert.deepEqual(missingSkuIds, []);
    assert.equal(unitBySku.has('null'), false);
    assert.equal(unitBySku.has(''), false);
    assert.equal(unitBySku.get('1'), 'kg');
  });
}

// ---------------------------------------------------------------- 来源：stocktake-import-contract.test.mjs
{
  /**
   * 盘点效率（快照模板导出 / Excel 幂等导入 / 复制到新建）的前端契约测试。
   *
   * 钉死这一波最容易被日后改动悄悄破坏、且破坏后不报错的前端边界（后端导入 / 签名 / 幂等由
   * ScmStocktakeImportPgIT 覆盖）：
   * 1. 快照模板走只读 getDownload，导入走带 Idempotency-Key 的 POST，两者都不是确认（不写库存）；
   * 2. 模板与导入按钮都由独立权限 scm:inventory:stocktake:import 把关，不复用 add/update/confirm；
   * 3. 复制历史是纯前端：只读 detail + 余额 query，不引入后端「复制盘点」命令，也不自动落草稿；
   * 4. 导入成功只建草稿（回填实盘量留空、需重新清点），确认仍走既有 confirm 端点。
   */

  /** 读源码并剥掉注释 —— 门禁只针对代码，头注释里提到被禁用的名字是合规的。 */
  function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  const API = '../src/api/business/scm/inventory-stocktake-api.ts';
  const LIST = '../src/views/business/scm/inventory/inventory-stocktake-list.vue';

  test('快照模板走只读 getDownload，导入走带 Idempotency-Key 的 POST，均不触达 confirm（不写库存）', () => {
    const api = code(API);
    assert.match(api, /downloadImportTemplate:[\s\S]*?getDownload\('\/scm\/inventory\/stocktake\/import\/template'/);
    assert.match(api, /url:\s*'\/scm\/inventory\/stocktake\/import'[\s\S]*?method:\s*'post'[\s\S]*?'Idempotency-Key':\s*key/);
    // 同一文件复用同一幂等键（WeakMap），换文件即新命令
    assert.match(api, /new WeakMap<File,\s*string>\(\)/);
    // 导入 / 模板端点绝不指向 /confirm —— 它们只建草稿
    assert.doesNotMatch(api, /stocktake\/import[^']*confirm/);
  });

  test('导出模板与导入按钮均由独立权限 scm:inventory:stocktake:import 把关', () => {
    const list = code(LIST);
    assert.match(list, /@click="onDownloadTemplate"[\s\S]*?v-privilege="'scm:inventory:stocktake:import'"/);
    assert.match(list, /:custom-request="onUploadImport"[\s\S]*?v-privilege="'scm:inventory:stocktake:import'"/);
  });

  test('复制历史是纯前端：只读 detail + 余额 query，不引入后端复制命令，也不自动建草稿', () => {
    const list = code(LIST);
    // 代码里的注释已被 code() 剥掉，因此用下一个函数声明作为边界，而不是注释块。
    const start = list.indexOf('async function openCopy');
    const openCopy = list.slice(start, list.indexOf('async function onDownloadTemplate', start));
    assert.ok(openCopy.length > 0 && openCopy.includes('inventoryStocktakeApi.detail('));
    assert.match(openCopy, /inventoryStocktakeApi\.detail\(/);
    assert.match(openCopy, /inventoryBalanceApi\.query\(/);
    // 复制路径不落草稿、不确认：不调用 create / confirm
    assert.doesNotMatch(openCopy, /inventoryStocktakeApi\.(create|confirm|update|delete)\(/);
    assert.match(openCopy, /已复制到新建表单（未保存）/);
    // API 层不存在后端「复制 / 克隆盘点」命令
    const api = code(API);
    assert.doesNotMatch(api, /\b(copy|duplicate|clone)\b/i);
  });

  test('导入成功回填实盘量留空并复用既有 confirm 端点调整库存', () => {
    const list = code(LIST);
    assert.match(list, /await inventoryStocktakeApi\.confirm\(record\.id\)/);
    // 复制过来的行强制重新清点：实盘量清空，不带入历史 / 当前值。
    // InputNumber 的空值是 null —— `''` 会被判成非法输入，而不是「未填」。
    assert.match(list, /actualQuantity:\s*null/);
  });
}
