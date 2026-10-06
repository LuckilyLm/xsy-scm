import test from 'node:test';
import assert from 'node:assert/strict';
import {existsSync, readFileSync} from 'node:fs';

const VIEW_DIR = '../src/views/business/scm/finance/';
const API_PATH = '../src/api/business/scm/finance-api.ts';
const CONST_PATH = '../src/constants/business/scm/finance-const.ts';
const PAGES = {
  receivable: 'finance-receivable-list.vue',
  payable: 'finance-payable-list.vue',
  receipt: 'finance-receipt-list.vue',
  payment: 'finance-payment-list.vue',
  writeOff: 'finance-write-off-list.vue',
};

function raw(path) {
  return readFileSync(new URL(path, import.meta.url), 'utf8');
}

function code(path) {
  return raw(path).replace(/<!--[\s\S]*?-->/g, '').replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
}

const pageCode = Object.fromEntries(Object.entries(PAGES).map(([key, name]) => [key, code(`${VIEW_DIR}${name}`)]));
const allPages = Object.values(pageCode).join('\n');
const API = code(API_PATH);
const CONST = code(CONST_PATH);

test('五个路由组件与 Finance 共用模型均已存在', () => {
  for (const path of [
    ...Object.values(PAGES),
    'finance-types.ts', 'finance-form-model.ts', 'finance-errors.ts', 'finance-detail-drawer.vue',
    'finance-record-picker.vue', 'finance-refund-picker.vue', 'use-finance-page.ts', 'use-finance-permission.ts',
    'use-finance-mobile-table.ts',
  ]) {
    assert.ok(existsSync(new URL(`${VIEW_DIR}${path}`, import.meta.url)), `Finance 缺少 ${path}`);
  }
});

test('列表、详情、写入、反向和五类导出 API 与后端路径一致', () => {
  const paths = [
    '/receivable/query', '/receivable/${id}', '/receivable/export',
    '/payable/query', '/payable/${id}', '/payable/red', '/payable/export',
    '/receipt/query', '/receipt/${id}', '/receipt/add', '/receipt/reverse', '/receipt/export',
    '/payment/query', '/payment/${id}', '/payment/refund-options', '/payment/add', '/payment/reverse', '/payment/export',
    '/write-off/query', '/write-off/add', '/write-off/reverse', '/write-off/export', '/log/query',
  ];
  for (const path of paths) assert.ok(API.includes(path), `API 缺少 ${path}`);
  assert.equal((API.match(/postDownload\(/g) ?? []).length, 5, '五个导出都应走统一下载封装');
  assert.ok(!/new Blob|createObjectURL|\.xlsx['"]/.test(API), '不得在 API 层拼文件内容或硬编码文件名');
});

test('13 个前后端权限串逐字对应，查询和导出都受权限控制', () => {
  const permissions = [
    'scm:finance:receivable:query', 'scm:finance:payable:query', 'scm:finance:receipt:query',
    'scm:finance:payment:query', 'scm:finance:write-off:query', 'scm:finance:receipt:add',
    'scm:finance:payment:add', 'scm:finance:write-off:add', 'scm:finance:payable:red',
    'scm:finance:write-off:reverse', 'scm:finance:receipt:reverse', 'scm:finance:payment:reverse',
    'scm:finance:export',
  ];
  assert.equal(permissions.length, 13);
  for (const permission of permissions) assert.ok(CONST.includes(`'${permission}'`), `缺少 ${permission}`);

  const queryKey = {
    receivable: 'RECEIVABLE_QUERY', payable: 'PAYABLE_QUERY', receipt: 'RECEIPT_QUERY',
    payment: 'PAYMENT_QUERY', writeOff: 'WRITE_OFF_QUERY',
  };
  for (const [page, permission] of Object.entries(queryKey)) {
    assert.match(pageCode[page], new RegExp(`v-privilege="PERM\.${permission}"[^>]*@click="onSearch"`), `${page} 查询按钮缺权限`);
    const reset = pageCode[page].match(/<a-button([^>]*)@click="resetQuery"/);
    assert.ok(reset, `${page} 页面缺重置按钮`);
    assert.ok(!/v-privilege/.test(reset[1]), `${page} 的重置动作不应被权限隐藏`);
    assert.match(pageCode[page], /v-privilege="PERM\.EXPORT"/, `${page} 导出按钮缺独立导出权限`);
  }
  for (const permission of ['RECEIPT_ADD', 'RECEIPT_REVERSE']) assert.ok(pageCode.receipt.includes(`v-privilege="PERM.${permission}"`));
  for (const permission of ['PAYMENT_ADD', 'PAYMENT_REVERSE']) assert.ok(pageCode.payment.includes(`v-privilege="PERM.${permission}"`));
  assert.ok(pageCode.payable.includes('v-privilege="PERM.PAYABLE_RED"'));
  for (const permission of ['WRITE_OFF_ADD', 'WRITE_OFF_REVERSE']) assert.ok(pageCode.writeOff.includes(`v-privilege="PERM.${permission}"`));
});

test('五个页面表格定位符及列配置 id 全部唯一', () => {
  const domIds = [...allPages.matchAll(/<a-table[^>]*\bid="([^"]+)"/g)].map((match) => match[1]);
  assert.equal(domIds.length, 5);
  assert.equal(new Set(domIds).size, 5);
  for (const id of domIds) assert.ok(id.startsWith('scm-finance-'), `表格 id 不属于 Finance：${id}`);

  const tableIds = code('../src/constants/support/table-id-const.ts');
  const financeIds = [...tableIds.matchAll(/SCM_FINANCE_(?:RECEIVABLE|PAYABLE|RECEIPT|PAYMENT|WRITE_OFF):\s*(\d+)/g)]
    .map((match) => Number.parseInt(match[1], 10));
  assert.deepEqual(financeIds, [50047, 50048, 50049, 50050, 50051]);
  assert.equal(new Set(financeIds).size, 5);
});

test('未核销余额固定在首屏，横向滚动提示只在窄屏出现', () => {
  assert.match(pageCode.receivable, /dataIndex: 'openAmount', fixed: 'left'/);
  assert.match(pageCode.payable, /dataIndex: 'openAmount', fixed: 'left'/);
  assert.match(pageCode.receipt, /dataIndex: 'pendingWriteOffAmount', fixed: 'left'/);
  assert.match(pageCode.payment, /dataIndex: 'pendingWriteOffAmount', fixed: 'left'/);
  assert.match(pageCode.receivable, /finance-mobile-balance-list[\s\S]*record\.openAmount/);
  assert.match(pageCode.payable, /finance-mobile-balance-list[\s\S]*record\.openAmount/);
  assert.match(pageCode.receipt, /finance-mobile-balance-list[\s\S]*record\.pendingWriteOffAmount/);
  assert.match(pageCode.payment, /finance-mobile-balance-list[\s\S]*record\.pendingWriteOffAmount/);
  assert.match(pageCode.receivable, /table-scroll-hint[\s\S]*@media \(max-width: 768px\)/);
  assert.match(pageCode.writeOff, /左右滑动表格查看/);
  assert.match(pageCode.writeOff, /source\.partyName[\s\S]*source\.availableAmount/);
});

test('供应商与往来方筛选提供名称搜索，退款付款从已完成退款选择器取事实', () => {
  assert.match(pageCode.payable, /label="供应商"[\s\S]*query\.supplierName/);
  assert.match(pageCode.payment, /label="往来方"[\s\S]*query\.counterpartyName/);
  assert.match(pageCode.payment, /FinanceRefundPicker/);
  assert.match(code(API_PATH), /refundOptions:[\s\S]*\/payment\/refund-options/);
  assert.match(pageCode.payment, /selectedRefund\.value\?\.customerId/);
  assert.match(pageCode.payment, /selectedRefund\.refundAmount/);
  assert.match(code(`${VIEW_DIR}finance-refund-picker.vue`), /退款单号[\s\S]*客户[\s\S]*退款金额/);
});

test('命令金额按定点字符串提交，页面不自行重算财务派生列', () => {
  assert.match(code(`${VIEW_DIR}finance-types.ts`), /amount:\s*string/);
  assert.match(API, /Idempotency-Key/);
  assert.match(API, /crypto\.randomUUID\(\)/);
  assert.equal((allPages.match(/string-mode/g) ?? []).length >= 5, true, '金额输入需保留字符串精度');
  assert.ok(!/\bNumber\s*\(|parseFloat\s*\(|\.reduce\s*\(/.test(allPages), '页面不得自行聚合或换算财务金额');
});

test('页面菜单迁移只在五个组件存在后发布正确路径并授予正式财务角色', () => {
  const migration = readFileSync(new URL('../../xsy-scm-server/sa-admin/src/main/resources/db/migration/V71__scm_finance_pages.sql', import.meta.url), 'utf8');
  const paths = [
    '/business/scm/finance/finance-receivable-list.vue', '/business/scm/finance/finance-payable-list.vue',
    '/business/scm/finance/finance-receipt-list.vue', '/business/scm/finance/finance-payment-list.vue',
    '/business/scm/finance/finance-write-off-list.vue',
  ];
  for (const path of paths) assert.ok(migration.includes(path), `页面迁移缺少 ${path}`);
  assert.match(migration, /UPDATE t_menu[\s\S]*visible_flag = TRUE/);
  assert.match(migration, /WHERE r\.role_code = 'SCM_FINANCE'/);
  assert.match(migration, /SELECT 1, m\.menu_id/);
});

test('超额核销、负净应收和反向单据都用事实字段展示', () => {
  assert.ok(allPages.includes('超额核销待处理'));
  assert.ok(code(`${VIEW_DIR}finance-detail-drawer.vue`).includes('header.netAmount'));
  assert.ok(pageCode.receipt.includes("record.entryType==='NORMAL'"));
  assert.ok(pageCode.payment.includes("record.entryType==='NORMAL'"));
  assert.ok(pageCode.writeOff.includes("record.entryType==='NORMAL'"));
});

// ------------------------------------------------------------------ 财务列表的展示契约

test('五张财务列表的操作列统一居中固定', () => {
  for (const [key, source] of Object.entries(pageCode)) {
    assert.match(source, /dataIndex: 'action', fixed: actionColumnFixed, align: 'center'/,
        `${PAGES[key]} 的操作列未居中`);
  }
});

test('核销页把「资金」与「目标」各自合成一格', () => {
  // 只看<b>主列表</b>的列定义：抽屉里的分配子表另有自己的「目标单号」，
  // 那是登记核销时逐条录入的对象，不属于列表展示口径。
  const mainColumns = pageCode.writeOff
      .match(/const columns = ref<TableColumnsType<FinanceWriteOff>>\(\[[\s\S]*?\n\]\);/)?.[0];
  assert.ok(mainColumns, '未取到核销页主列表的列定义');
  for (const gone of ['资金类型', '资金单号', '资金方', '目标类型', '目标单号', '目标方']) {
    assert.ok(!mainColumns.includes(`title: '${gone}'`), `核销页仍有独立列：${gone}`);
  }
  assert.match(mainColumns, /title: '来源', dataIndex: 'source'/);
  assert.match(mainColumns, /title: '对象', dataIndex: 'target'/);
  // 两组「类型 + 单号」都下沉为次要行，且类型仍要落成中文
  assert.match(pageCode.writeOff, /column\.dataIndex==='source'[\s\S]{0,400}sourceTypeText\(record\.sourceType\)/);
  assert.match(pageCode.writeOff, /column\.dataIndex==='target'[\s\S]{0,400}targetTypeText\(record\.targetType\)/);
  // 余额消费来自流水表，不在 SCM_FINANCE_SOURCE_TYPE_ENUM 里，必须单独补
  assert.match(pageCode.writeOff, /BALANCE_MOVEMENT: '余额消费'/);
  // 撤销核销是追加反向事实：danger 视觉 + 独立二次确认弹窗（不能与普通编辑同权重）
  assert.match(pageCode.writeOff, /title="撤销核销"/);
  assert.match(pageCode.writeOff, /将追加一条反向核销/);
});

test('付款页不再展示裸的技术主键，往来方类型下沉为次要行', () => {
  const payment = pageCode.payment;
  // sourceId 是退款单的数据库主键：对使用者没有信息量
  assert.ok(!payment.includes(`title: '来源编号'`), '付款页仍在展示裸主键');
  assert.ok(!payment.includes(`dataIndex: 'sourceId'`), '付款页仍绑定了 sourceId 列');
  // 往来方类型（客户 / 供应商）决定这笔付款的性质，作往来方的次要行而不是独占一列
  assert.ok(!payment.includes(`title: '往来方类型'`));
  assert.match(payment, /title: '往来方', dataIndex: 'counterparty'/);
  assert.match(payment, /column\.dataIndex==='counterparty'[\s\S]{0,400}record\.counterpartyType/);
});

test('收付款页的金额列统一走 scm-money（右对齐 + 等宽数字）', () => {
  for (const key of ['receipt', 'payment']) {
    const source = pageCode[key];
    assert.match(source, /class="scm-money"/, `${PAGES[key]} 的金额未走 scm-money`);
    assert.doesNotMatch(source, /<a-tag :color="SCM_FINANCE_ENTRY_COLOR/,
        `${PAGES[key]} 的方向标签应走 ScmStatusTag`);
  }
});
