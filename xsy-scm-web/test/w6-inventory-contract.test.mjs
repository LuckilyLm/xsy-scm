/**
 * W6 库存域契约单测（新增文件，仿 W5 `w5-purchase-contract.test.mjs`）。
 *
 * 这里守的是**前端最容易悄悄违背、且违背后不会报错**的几条契约：
 * 1. **Q12 默认仓库**：恰好 1 个启用仓库才默认带出，否则一个都不选 ——
 *    多仓下自动选一个会让用户误以为在看全部库存；
 * 2. **三态展示**（A18 同族）：`null` / `undefined` / 空串 → `—`，
 *    `"0.0000"` → `0.0000`；「没有值」与「值是零」不得被合并；
 * 3. **append-only**：流水前端只有 query，没有新增 / 编辑 / 删除入口；
 * 4. **枚举与 DB 白名单同源**：流水类型 `PURCHASE_IN` / `SALES_OUT` / `STOCKTAKE_GAIN` /
 *    `STOCKTAKE_LOSS` / `LOSS_REPORT` / `GAIN_REPORT`，来源 `PURCHASE_RECEIPT_ITEM` /
 *    `SALES_OUTBOUND_ITEM` / `SALES_ORDER_ITEM` / `STOCKTAKE_ITEM` / `LOSS_GAIN_ITEM`；
 * 5. **错误码有可执行文案**：库存域全部码（含盘点 41019–41027、报损报溢 41028–41037）
 *    都必须在映射表里，且提示要说「下一步做什么」；
 * 6. **盘点口径不被误读**：确认后的账面不一定等于实盘数（差异施加到确认瞬间的账面量上），
 *    页面必须写明，且新增表单不得提交账面量；
 * 7. **审批乐观锁**：报损报溢的审批请求必须带上审批人看到的 `version`，
 *    且打开审批弹窗时不得重新拉取单据 —— 否则「审批人必须批准自己读到的内容」这条防线失效。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {
  moneyText,
  singleWarehouseDefault,
  quantityText,
  specText,
  movementTypeText,
} from '../src/views/business/scm/inventory/inventory-model.ts';
import {inventoryError} from '../src/views/business/scm/inventory/inventory-errors.ts';
import {
  SCM_INVENTORY_CONVERSION_STATUS_ENUM,
  SCM_INVENTORY_CONVERSION_TYPE_ENUM,
  SCM_INVENTORY_LOSS_GAIN_STATUS_ENUM,
  SCM_INVENTORY_LOSS_GAIN_TYPE_ENUM,
  SCM_INVENTORY_MOVEMENT_TYPE_ENUM,
  SCM_INVENTORY_SOURCE_TYPE_ENUM,
  SCM_INVENTORY_TABLE_ID,
  SCM_INVENTORY_TRANSFER_STATUS_ENUM,
  SCM_INVENTORY_WARNING_STATUS_ENUM,
} from '../src/constants/business/scm/inventory-const.ts';
import {TABLE_ID_CONST} from '../src/constants/support/table-id-const.ts';

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
// Q12：默认仓库
// ------------------------------------------------------------------

test('Q12 default warehouse only applies when exactly one enabled warehouse exists', () => {
  // 恰好 1 个 → 默认带出
  assert.equal(singleWarehouseDefault([{id: 7}]), 7);
  assert.equal(singleWarehouseDefault([{id: '7'}]), '7');

  // 0 个 → 不选（没什么可选，且 0 个仓库本身是异常状态，页面应以空态呈现）
  assert.equal(singleWarehouseDefault([]), undefined);
  assert.equal(singleWarehouseDefault(null), undefined);
  assert.equal(singleWarehouseDefault(undefined), undefined);

  // 2 个及以上 → **不自动选任何一个**（多仓下自动选中一个仓会让人误以为在看全部库存）
  assert.equal(singleWarehouseDefault([{id: 1}, {id: 2}]), undefined);
  assert.equal(singleWarehouseDefault([{id: 1}, {id: 2}, {id: 3}]), undefined);
});

test('Q12 wiring: the balance page applies the default before the first query, and reset clears it', () => {
  const page = code('../src/views/business/scm/inventory/inventory-balance-list.vue');

  // 默认仓库必须在第一屏查询**之前**落定，否则首屏会先显示「全部仓库」再跳成「默认仓库」
  assert.match(page, /applySingleWarehouseDefault/);
  assert.match(page, /singleWarehouseDefault/);
  assert.ok(
    page.indexOf('await applySingleWarehouseDefault()') < page.indexOf('await queryData()'),
    '默认仓库必须在首次查询之前应用'
  );

  // 重置的语义是「回到无筛选」，不得重新套用默认值
  const reset = page.slice(
    page.indexOf('function resetQuery'),
    page.indexOf('onMounted(async')
  );
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

// ------------------------------------------------------------------
// V34 移动加权成本
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

test('V34: the balance page shows the moving-average cost and the derived amount', () => {
  const page = code('../src/views/business/scm/inventory/inventory-balance-list.vue');
  assert.match(page, /dataIndex: 'avgCost'/, '余额页必须有均价列');
  assert.match(page, /dataIndex: 'amount'/, '余额页必须有金额列');
  // 两列都要走 moneyText，不能落到「原样 {{ record[column.dataIndex] }}」的兜底分支
  assert.match(page, /column\.dataIndex === 'avgCost'[\s\S]{0,120}moneyText\(record\.avgCost\)/);
  assert.match(page, /column\.dataIndex === 'amount'[\s\S]{0,120}moneyText\(record\.amount\)/);

  const types = code('../src/views/business/scm/inventory/inventory-types.ts');
  // 均价是 NOT NULL DEFAULT 0，所以类型里**不带** `| null`；金额允许为空
  assert.match(types, /avgCost\?:\s*string;/);
  assert.match(types, /amount\?:\s*string \| null;/);
});

test('movement type text falls back to the raw value so an unmapped type stays visible', () => {
  const labels = SCM_INVENTORY_MOVEMENT_TYPE_ENUM;
  assert.equal(movementTypeText('PURCHASE_IN', labels), '采购入库');
  assert.equal(movementTypeText('', labels), '—');
  assert.equal(movementTypeText(null, labels), '—');
  assert.equal(movementTypeText(undefined, labels), '—');
  // 未知类型**不显示成「—」**：一个后端新增而前端未跟上的类型本身就是有用信号。
  // 用 UNKNOWN_IN（**明确不存在**的名字）：十个真实类型已全部落地，
  // 再拿「未实现的业务类型」当反例会每落地一个就要改一次。
  assert.equal(movementTypeText('UNKNOWN_IN', labels), 'UNKNOWN_IN');
  // 文案表里缺 desc 时同样回落到原值，而不是显示空白
  assert.equal(movementTypeText('PURCHASE_IN', {}), 'PURCHASE_IN');
});

// ------------------------------------------------------------------
// 枚举 / 表格 id
// ------------------------------------------------------------------

test('inventory enums expose exactly the values the backend CHECK whitelist allows', () => {
  assert.deepEqual(Object.keys(SCM_INVENTORY_MOVEMENT_TYPE_ENUM),
      ['PURCHASE_IN', 'SALES_OUT', 'STOCKTAKE_GAIN', 'STOCKTAKE_LOSS', 'LOSS_REPORT', 'GAIN_REPORT',
        'TRANSFER_OUT', 'TRANSFER_IN', 'CONVERT_OUT', 'CONVERT_IN']);
  assert.deepEqual(Object.keys(SCM_INVENTORY_SOURCE_TYPE_ENUM),
      ['PURCHASE_RECEIPT_ITEM', 'SALES_OUTBOUND_ITEM', 'SALES_ORDER_ITEM', 'STOCKTAKE_ITEM',
        'LOSS_GAIN_ITEM', 'TRANSFER_OUT_ITEM', 'TRANSFER_IN_ITEM', 'CONVERT_OUT_ITEM',
        'CONVERT_IN_ITEM']);

  // 值与键逐字一致（后端 `ScmInventoryMovementTypeEnum.name()` 就是持久化值）
  for (const [key, item] of Object.entries(SCM_INVENTORY_MOVEMENT_TYPE_ENUM)) {
    assert.equal(item.value, key);
    assert.ok(item.desc && item.desc.length > 0, key + ' 缺少中文描述');
  }
  for (const [key, item] of Object.entries(SCM_INVENTORY_SOURCE_TYPE_ENUM)) {
    assert.equal(item.value, key);
    assert.ok(item.desc && item.desc.length > 0, key + ' 缺少中文描述');
  }

  // 盘盈与盘亏必须是**两个**类型：方向要能从类型本身读出来，否则 DB 的
  // `ck_inventory_movement_snap` 无法判定 after 该加还是该减。报损 / 报溢、转出 / 转入同理。
  assert.doesNotMatch(JSON.stringify(SCM_INVENTORY_MOVEMENT_TYPE_ENUM), /STOCKTAKE_ADJUST/);

  // 十个类型必须**恰好**分成两个方向组、每组五个 ——
  // 这是 `ck_inventory_movement_snap`「按方向分组」写法的前提。
  const inbound = ['PURCHASE_IN', 'STOCKTAKE_GAIN', 'GAIN_REPORT', 'TRANSFER_IN', 'CONVERT_IN'];
  const outbound = ['SALES_OUT', 'STOCKTAKE_LOSS', 'LOSS_REPORT', 'TRANSFER_OUT', 'CONVERT_OUT'];
  assert.deepEqual([...inbound, ...outbound].sort(),
      Object.keys(SCM_INVENTORY_MOVEMENT_TYPE_ENUM).sort());

  // 十个真实类型已全部落地；这里断言的是「白名单之外一律拒绝」这条性质本身
  assert.doesNotMatch(JSON.stringify(SCM_INVENTORY_MOVEMENT_TYPE_ENUM), /UNKNOWN|ADJUST/);
});

test('loss/gain document enums match the backend state machine', () => {
  // 方向是**单据级**属性：一张单要么全报损、要么全报溢
  assert.deepEqual(Object.keys(SCM_INVENTORY_LOSS_GAIN_TYPE_ENUM), ['LOSS', 'OVERFLOW']);
  // 没有 DRAFT：报损报溢创建即提交待审核（录单与审批应当由不同的人完成）
  assert.deepEqual(Object.keys(SCM_INVENTORY_LOSS_GAIN_STATUS_ENUM),
      ['PENDING', 'COMPLETED', 'REJECTED']);
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
  assert.deepEqual(Object.keys(SCM_INVENTORY_TABLE_ID),
      ['BALANCE', 'MOVEMENT', 'OUTBOUND', 'RESERVATION', 'STOCKTAKE', 'LOSS_GAIN', 'TRANSFER',
        'WARNING', 'WARNING_THRESHOLD', 'CONVERSION']);
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
  assert.equal(SCM_INVENTORY_TABLE_ID.CONVERSION, 'scm-inventory-conversion-table');

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

    // 只读页不得有任何写入口。判据刻意落在**结构**而不是文案上：
    // 页面里确实会写「流水不可编辑、不可删除」这类说明文字，用关键词匹配会误报。
    //   - 本项目的写表单一律住在 `<a-modal>` 里（见 W2–W5 的各 `*-form-drawer/modal`）；
    //   - 写操作一定表现为调用某个 `create/update/remove/delete` 方法。
    assert.doesNotMatch(source, /<a-modal/, page + ' 出现了写表单弹窗');
    assert.doesNotMatch(source, /\.(create|update|remove|delete)\s*\(/, page + ' 调用了写接口');
  }
});

test('inventory sources never depend on the purchase form model (domains stay decoupled)', () => {
  // 库存域是**与来源无关**的领域原语：它可以复用展示工具（`scm-display`），
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

  // 三种包裹形状都要认（W5 同款）
  assert.match(inventoryError({data: {code: 41001}}), /不做自动换算/);
  assert.match(inventoryError({response: {data: {code: 41002}}}), /不能重复入库/);

  // 未登记的码**不吞掉后端消息**
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
// 盘点波次
// ------------------------------------------------------------------

test('the stocktake page is a stateful document page wired to its own DOM id and privileges', () => {
  const page = code('../src/views/business/scm/inventory/inventory-stocktake-list.vue');
  assert.match(page, /TableOperator/);
  assert.match(page, /SCM_INVENTORY_TABLE_ID/);
  assert.match(page, /v-privilege/);

  // 四个权限点都要出现：查询 / 新建 / 编辑 / 确认 / 删除。
  // 「确认盘点」必须是**独立权限** —— 它会真实调整库存并写不可逆流水，
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

  // 实盘量允许 0（确实一件不剩），因此校验正则不得要求大于 0
  assert.match(page, /\^\\d\+\(\\.\\d\{1,4\}\)\?\$/);
});

test('the stocktake page documents that delta is applied to the live book quantity', () => {
  // 这是本波次最容易让人误读的一条口径：确认后的账面**不一定等于实盘数**，
  // 因为「保存草稿 → 确认」之间发生的收货 / 出库会被保留（差异是施加到当前账面量上的）。
  // 页面上必须写明，否则用户会把正确行为当成 bug。
  const page = code('../src/views/business/scm/inventory/inventory-stocktake-list.vue');
  assert.match(page, /确认瞬间的账面量/);

  // 明细里的账面量由服务端快照，前端不得提交它（否则盘点能凭空制造差异）
  const api = code('../src/api/business/scm/inventory-stocktake-api.ts');
  assert.match(api, /create:/);
  assert.match(api, /confirm:/);
  // 盘点单是**有状态单据**，与 append-only 的流水不同：它有 update / cancel / delete
  assert.match(api, /update:/);
  assert.match(api, /cancel:/);
  assert.match(api, /delete:/);

  const types = code('../src/views/business/scm/inventory/inventory-types.ts');
  const addForm = types.slice(types.indexOf('export interface InventoryStocktakeAdd'));
  assert.ok(addForm.length > 0, '未能定位 InventoryStocktakeAdd');
  assert.doesNotMatch(addForm, /bookQuantity/, '新增表单不得提交账面量');
});

test('stocktake error codes all have actionable Chinese text', () => {
  // 盘点波次 9 个码：41019–41027。每一个都必须说「下一步做什么」。
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
// 报损报溢波次
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
  // 且**不得**在打开弹窗时重新拉取单据（那等于「总是批准最新的」，防线就没了）。
  const page = code('../src/views/business/scm/inventory/inventory-loss-gain-list.vue');
  assert.match(page, /version:\s*record\.version \?\? 0/);

  // 驳回必须在前端也校验一次意见，避免白跑一次请求才拿到 41037
  assert.match(page, /驳回时必须填写审核意见/);

  const api = code('../src/api/business/scm/inventory-loss-gain-api.ts');
  assert.match(api, /approve:/);
  assert.match(api, /reject:/);
  // 报损报溢是**有状态单据**，与 append-only 的流水不同
  assert.match(api, /create:/);
  assert.match(api, /update:/);
  assert.match(api, /delete:/);
  // 没有独立的「提交」端点：创建即待审核
  assert.doesNotMatch(api, /submit/);
});

test('loss/gain error codes all have actionable Chinese text', () => {
  // 报损报溢波次 10 个码：41028–41037
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
// 调拨波次
// ------------------------------------------------------------------

test('the transfer status machine is two-step and in-transit is not cancellable', () => {
  // 两步式：DRAFT → SHIPPED（在途）→ RECEIVED，草稿可 CANCELLED
  assert.deepEqual(Object.keys(SCM_INVENTORY_TRANSFER_STATUS_ENUM),
      ['DRAFT', 'SHIPPED', 'RECEIVED', 'CANCELLED']);
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

test('transfer error codes all have actionable Chinese text', () => {
  // 调拨波次 11 个码：41038–41048
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
// 阈值预警波次
// ------------------------------------------------------------------

test('the warning status enum matches the backend and treats the boundary as normal', () => {
  assert.deepEqual(Object.keys(SCM_INVENTORY_WARNING_STATUS_ENUM), ['NORMAL', 'LOW', 'HIGH']);
  for (const [key, item] of Object.entries(SCM_INVENTORY_WARNING_STATUS_ENUM)) {
    assert.equal(item.value, key);
    assert.ok(item.desc && item.desc.length > 0, key + ' 缺少中文描述');
  }
  // 状态是**派生值**，不落库：不得出现任何「已读 / 已忽略」这类需要持久化的状态
  assert.doesNotMatch(JSON.stringify(SCM_INVENTORY_WARNING_STATUS_ENUM),
      /READ|ACK|IGNORED|DISMISSED/);
});

test('the warning list page is read-only and defaults to abnormal-only', () => {
  const page = code('../src/views/business/scm/inventory/inventory-warning-list.vue');
  assert.match(page, /TableOperator/);
  assert.match(page, /SCM_INVENTORY_TABLE_ID/);
  assert.match(page, /v-privilege/);

  // 只读页不得有任何写入口。判据落在**结构**上而不是文案上：
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
  assert.match(page, /上下限至少填写一个/);
  assert.match(page, /不得大于上限/);
  // 空串要转成 null（= 清空该边界），不能把空串发上去
  assert.match(page, /warnMin: min \?\? null/);
  assert.match(page, /warnMax: max \?\? null/);

  // 配置接口没有「确认 / 审批」这类动作：改配置立即生效（预警是读时计算的）
  const api = code('../src/api/business/scm/inventory-warning-threshold-api.ts');
  assert.match(api, /create:/);
  assert.match(api, /update:/);
  assert.match(api, /delete:/);
  assert.doesNotMatch(api, /confirm|approve|submit/);
});

test('warning error codes all have actionable Chinese text', () => {
  // 阈值预警波次 4 个码：41049–41052
  for (let code = 41049; code <= 41052; code++) {
    const text = inventoryError({code});
    assert.notEqual(text, '操作失败，请重试', code + ' 未登记可执行提示');
    assert.ok(text.length > 8, code + ' 的提示过于简短，无法指导下一步');
  }
  assert.match(inventoryError({code: 41050}), /只允许一条/);
  assert.match(inventoryError({code: 41051}), /下限不得大于上限/);
});

// ------------------------------------------------------------------
// 规格转换波次
// ------------------------------------------------------------------

test('the conversion enums match the backend whitelist and stay cross-SKU', () => {
  assert.deepEqual(Object.keys(SCM_INVENTORY_CONVERSION_TYPE_ENUM), ['SPLIT', 'COMBINE']);
  // 状态机与报损报溢同构（没有 DRAFT：创建即提交待审核）
  assert.deepEqual(Object.keys(SCM_INVENTORY_CONVERSION_STATUS_ENUM),
      ['PENDING', 'COMPLETED', 'REJECTED']);
  assert.doesNotMatch(JSON.stringify(SCM_INVENTORY_CONVERSION_STATUS_ENUM), /DRAFT/);
  for (const [key, item] of Object.entries(SCM_INVENTORY_CONVERSION_TYPE_ENUM)) {
    assert.equal(item.value, key);
    assert.ok(item.desc && item.desc.length > 0, key + ' 缺少中文描述');
  }
  // **Q13 不受影响**：转换是跨 SKU 的（源规格 → 目标规格），
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
  assert.match(page, /源 SKU 与目标 SKU 不能相同/);

  const api = code('../src/api/business/scm/inventory-conversion-api.ts');
  assert.match(api, /approve:/);
  assert.match(api, /reject:/);
  assert.match(api, /create:/);
  assert.match(api, /update:/);
  assert.match(api, /delete:/);
});

test('conversion error codes all have actionable Chinese text', () => {
  // 规格转换波次 12 个码：41053–41064
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
  // 背景（W6 验收期间发现）：提交 48134bf 在 `purchase-form-model.ts` 里加了
  // `export { datetime } from '../common/scm-display'`。打包器（Vite）与 `vue-tsc` 都会自动补全
  // 扩展名，所以 `npm run build` / `npm run typecheck` **都发现不了**；
  // 但 `npm run test` 用 `node --experimental-strip-types --test` 直接加载 `.ts`，
  // node 的 ESM 解析不做补全 → 整份前端单测以 ERR_MODULE_NOT_FOUND 加载失败。
  //
  // 而「补上 `.ts`」这条路是走不通的：本项目 `tsconfig` 未开启 `allowImportingTsExtensions`，
  // 值导入写 `.ts` 会触发 TS5097（被 `tools/ts_baseline_ratchet.py` 的 SCM 零错误区拦下）。
  //
  // 两个约束的交集就是这条纪律：**node 可加载的模块只能有 type-only 的相对导入**
  // （type-only 会被类型擦除，node 根本不会去解析它）。
  const modules = [
    '../src/views/business/scm/purchase/purchase-form-model.ts',
    '../src/views/business/scm/purchase/purchase-errors.ts',
    '../src/views/business/scm/inventory/inventory-model.ts',
    '../src/views/business/scm/inventory/inventory-errors.ts',
    '../src/views/business/scm/product/product-form-model.ts',
  ];
  for (const module of modules) {
    const source = code(module);
    // 先剥掉 type-only 的 import/export（含多行形状），剩下的相对路径 from 就是值导入
    const withoutTypeOnly = source.replace(
      /(import|export)\s+type\s[\s\S]*?from\s+'[^']+';?/g,
      ''
    );
    const valueImports = [...withoutTypeOnly.matchAll(/from\s+'([^']+)'/g)]
      .map((match) => match[1])
      .filter((specifier) => specifier.startsWith('.'));
    assert.deepEqual(
      valueImports,
      [],
      `${module} 出现了相对路径的值导入：node 直接加载 .ts 时无法解析，补 .ts 又会触发 TS5097`
    );
  }
});
