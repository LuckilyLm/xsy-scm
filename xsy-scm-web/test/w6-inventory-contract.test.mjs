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
 *    `STOCKTAKE_LOSS`，来源 `PURCHASE_RECEIPT_ITEM` / `SALES_OUTBOUND_ITEM` /
 *    `SALES_ORDER_ITEM` / `STOCKTAKE_ITEM`；
 * 5. **错误码有可执行文案**：库存域全部码（含盘点波次 41019–41027）都必须在映射表里，
 *    且提示要说「下一步做什么」；
 * 6. **盘点口径不被误读**：确认后的账面不一定等于实盘数（差异施加到确认瞬间的账面量上），
 *    页面必须写明，且新增表单不得提交账面量。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {
  singleWarehouseDefault,
  quantityText,
  specText,
  movementTypeText,
} from '../src/views/business/scm/inventory/inventory-model.ts';
import {inventoryError} from '../src/views/business/scm/inventory/inventory-errors.ts';
import {
  SCM_INVENTORY_MOVEMENT_TYPE_ENUM,
  SCM_INVENTORY_SOURCE_TYPE_ENUM,
  SCM_INVENTORY_TABLE_ID,
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

test('movement type text falls back to the raw value so an unmapped type stays visible', () => {
  const labels = SCM_INVENTORY_MOVEMENT_TYPE_ENUM;
  assert.equal(movementTypeText('PURCHASE_IN', labels), '采购入库');
  assert.equal(movementTypeText('', labels), '—');
  assert.equal(movementTypeText(null, labels), '—');
  assert.equal(movementTypeText(undefined, labels), '—');
  // 未知类型**不显示成「—」**：一个后端新增而前端未跟上的类型本身就是有用信号
  assert.equal(movementTypeText('TRANSFER_IN', labels), 'TRANSFER_IN');
  // 文案表里缺 desc 时同样回落到原值，而不是显示空白
  assert.equal(movementTypeText('PURCHASE_IN', {}), 'PURCHASE_IN');
});

// ------------------------------------------------------------------
// 枚举 / 表格 id
// ------------------------------------------------------------------

test('inventory enums expose exactly the values the backend CHECK whitelist allows', () => {
  assert.deepEqual(Object.keys(SCM_INVENTORY_MOVEMENT_TYPE_ENUM),
      ['PURCHASE_IN', 'SALES_OUT', 'STOCKTAKE_GAIN', 'STOCKTAKE_LOSS']);
  assert.deepEqual(Object.keys(SCM_INVENTORY_SOURCE_TYPE_ENUM),
      ['PURCHASE_RECEIPT_ITEM', 'SALES_OUTBOUND_ITEM', 'SALES_ORDER_ITEM', 'STOCKTAKE_ITEM']);

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
  // `ck_inventory_movement_snap` 无法判定 after 该加还是该减。
  assert.doesNotMatch(JSON.stringify(SCM_INVENTORY_MOVEMENT_TYPE_ENUM), /STOCKTAKE_ADJUST/);

  // 盘点波次已落地；报损报溢 / 调拨 / 规格转换仍不得提前出现
  assert.doesNotMatch(JSON.stringify(SCM_INVENTORY_MOVEMENT_TYPE_ENUM), /TRANSFER|LOSS_REPORT|GAIN_REPORT|CONVERT/);
});

test('inventory table DOM ids are distinct, non-empty and registered with numeric table ids', () => {
  assert.deepEqual(Object.keys(SCM_INVENTORY_TABLE_ID),
      ['BALANCE', 'MOVEMENT', 'OUTBOUND', 'RESERVATION', 'STOCKTAKE']);
  assert.equal(SCM_INVENTORY_TABLE_ID.BALANCE, 'scm-inventory-balance-table');
  assert.equal(SCM_INVENTORY_TABLE_ID.MOVEMENT, 'scm-inventory-movement-table');
  assert.equal(SCM_INVENTORY_TABLE_ID.STOCKTAKE, 'scm-inventory-stocktake-table');

  const business = TABLE_ID_CONST.BUSINESS;
  assert.equal(business.SCM_INVENTORY_BALANCE, 50017);
  assert.equal(business.SCM_INVENTORY_MOVEMENT, 50018);
  assert.equal(business.SCM_INVENTORY_OUTBOUND, 50019);
  assert.equal(business.SCM_INVENTORY_RESERVATION, 50020);
  assert.equal(business.SCM_INVENTORY_STOCKTAKE, 50021);

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
