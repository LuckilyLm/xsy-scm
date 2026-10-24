/**
 * 财务模块契约测试。
 *
 * 由 2 个独立测试文件按模块合并：每个 `{ … }` 块对应原来一份文件，
 * 块内 helper / 常量各自作用域隔离；只有 import 提到顶部并去重。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, existsSync} from 'node:fs';

// ---------------------------------------------------------------- 来源：finance-contract.test.mjs
{
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
    return raw(path)
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  const pageCode = Object.fromEntries(Object.entries(PAGES).map(([key, name]) => [key, code(`${VIEW_DIR}${name}`)]));
  const allPages = Object.values(pageCode).join('\n');
  const API = code(API_PATH);
  const CONST = code(CONST_PATH);

  test('五个路由组件与 Finance 共用模型均已存在', () => {
    for (const path of [
      ...Object.values(PAGES),
      'finance-types.ts',
      'finance-form-model.ts',
      'finance-errors.ts',
      'finance-detail-drawer.vue',
      'finance-record-picker.vue',
      'finance-refund-picker.vue',
      'use-finance-page.ts',
      'use-finance-permission.ts',
      'use-finance-mobile-table.ts',
    ]) {
      assert.ok(existsSync(new URL(`${VIEW_DIR}${path}`, import.meta.url)), `Finance 缺少 ${path}`);
    }
  });

  test('列表、详情、写入、反向和五类导出 API 与后端路径一致', () => {
    const paths = [
      '/receivable/query',
      '/receivable/${id}',
      '/receivable/export',
      '/payable/query',
      '/payable/${id}',
      '/payable/red',
      '/payable/export',
      '/receipt/query',
      '/receipt/${id}',
      '/receipt/add',
      '/receipt/reverse',
      '/receipt/export',
      '/payment/query',
      '/payment/${id}',
      '/payment/refund-options',
      '/payment/add',
      '/payment/reverse',
      '/payment/export',
      '/write-off/query',
      '/write-off/add',
      '/write-off/reverse',
      '/write-off/export',
      '/log/query',
    ];
    for (const path of paths) assert.ok(API.includes(path), `API 缺少 ${path}`);
    assert.equal((API.match(/postDownload\(/g) ?? []).length, 5, '五个导出都应走统一下载封装');
    assert.ok(!/new Blob|createObjectURL|\.xlsx['"]/.test(API), '不得在 API 层拼文件内容或硬编码文件名');
  });

  test('13 个前后端权限串逐字对应，查询和导出都受权限控制', () => {
    const permissions = [
      'scm:finance:receivable:query',
      'scm:finance:payable:query',
      'scm:finance:receipt:query',
      'scm:finance:payment:query',
      'scm:finance:write-off:query',
      'scm:finance:receipt:add',
      'scm:finance:payment:add',
      'scm:finance:write-off:add',
      'scm:finance:payable:red',
      'scm:finance:write-off:reverse',
      'scm:finance:receipt:reverse',
      'scm:finance:payment:reverse',
      'scm:finance:export',
    ];
    assert.equal(permissions.length, 13);
    for (const permission of permissions) assert.ok(CONST.includes(`'${permission}'`), `缺少 ${permission}`);

    const queryKey = {
      receivable: 'RECEIVABLE_QUERY',
      payable: 'PAYABLE_QUERY',
      receipt: 'RECEIPT_QUERY',
      payment: 'PAYMENT_QUERY',
      writeOff: 'WRITE_OFF_QUERY',
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
    const financeIds = [...tableIds.matchAll(/SCM_FINANCE_(?:RECEIVABLE|PAYABLE|RECEIPT|PAYMENT|WRITE_OFF):\s*(\d+)/g)].map((match) =>
      Number.parseInt(match[1], 10)
    );
    assert.deepEqual(financeIds, [50047, 50048, 50049, 50050, 50051]);
    assert.equal(new Set(financeIds).size, 5);
  });

  test('表格只固定操作列，单号与未核销余额随表横向滚动', () => {
    for (const [name, source] of Object.entries(pageCode)) {
      assert.doesNotMatch(source, /fixed: 'left'/, `${name} 仍有左侧固定列，只有操作列可以固定在右侧`);
      assert.match(source, /title: '操作', dataIndex: 'action', fixed: actionColumnFixed/, `${name} 的操作列没有固定`);
    }
    assert.match(pageCode.receivable, /finance-mobile-balance-list[\s\S]*record\.openAmount/);
    assert.match(pageCode.payable, /finance-mobile-balance-list[\s\S]*record\.openAmount/);
    assert.match(pageCode.receipt, /finance-mobile-balance-list[\s\S]*record\.pendingWriteOffAmount/);
    assert.match(pageCode.payment, /finance-mobile-balance-list[\s\S]*record\.pendingWriteOffAmount/);
    // 横向滚动提示已删除：窄屏表格本身可滑动，不需要一句常驻说明（见 CONTRIBUTING.md SCM 界面规范）。
    assert.doesNotMatch(pageCode.receivable, /table-scroll-hint/);
    assert.doesNotMatch(pageCode.writeOff, /table-scroll-hint/);
    assert.match(pageCode.writeOff, /source\.partyName[\s\S]*source\.availableAmount/);
  });

  test('供应商与往来方筛选提供名称搜索，退款付款从已完成退款选择器取事实', () => {
    assert.match(pageCode.payable, /label="供应商"[\s\S]*query\.supplierName/);
    assert.match(pageCode.payment, /label="往来方"[\s\S]*query\.counterpartyName/);
    assert.match(pageCode.payment, /FinanceRefundPicker/);
    assert.match(code(API_PATH), /refundOptions:[\s\S]*\/payment\/refund-options/);
    assert.match(pageCode.payment, /addForm\.selectedRefund\?\.customerId/);
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
    const migration = readFileSync(
      new URL('../../xsy-scm-server/sa-admin/src/main/resources/db/migration/V71__scm_finance_pages.sql', import.meta.url),
      'utf8'
    );
    const paths = [
      '/business/scm/finance/finance-receivable-list.vue',
      '/business/scm/finance/finance-payable-list.vue',
      '/business/scm/finance/finance-receipt-list.vue',
      '/business/scm/finance/finance-payment-list.vue',
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
    assert.match(pageCode.receipt, /record\.entryType\s*===\s*'NORMAL'/);
    assert.match(pageCode.payment, /record\.entryType\s*===\s*'NORMAL'/);
    assert.match(pageCode.writeOff, /record\.entryType\s*===\s*'NORMAL'/);
  });

  // ------------------------------------------------------------------ 财务列表的展示契约

  test('五张财务列表的操作列统一居中固定', () => {
    for (const [key, source] of Object.entries(pageCode)) {
      assert.match(source, /dataIndex: 'action', fixed: actionColumnFixed, align: 'center'/, `${PAGES[key]} 的操作列未居中`);
    }
  });

  test('核销页把「资金」与「目标」的类型、单号各自成列', () => {
    // 只看主列表的列定义：抽屉里的分配子表另有自己的「目标单号」，
    // 那是登记核销时逐条录入的对象，不属于列表展示口径。
    const mainColumns = pageCode.writeOff.match(/const columns = ref<TableColumnsType<FinanceWriteOff>>\(\[[\s\S]*?\n\]\);/)?.[0];
    assert.ok(mainColumns, '未取到核销页主列表的列定义');
    for (const want of ['资金方', '资金类型', '资金单号', '核销目标', '目标类型', '目标单号']) {
      assert.ok(mainColumns.includes(`title: '${want}'`), `核销页缺少独立列：${want}`);
    }
    // 两组「类型」仍要落成中文
    assert.match(pageCode.writeOff, /column\.dataIndex\s*===\s*'sourceType'[\s\S]{0,200}sourceTypeText\(record\.sourceType\)/);
    assert.match(pageCode.writeOff, /column\.dataIndex\s*===\s*'targetType'[\s\S]{0,200}targetTypeText\(record\.targetType\)/);
    // 余额消费来自流水表，不在 SCM_FINANCE_SOURCE_TYPE_ENUM 里，必须单独补
    assert.match(pageCode.writeOff, /BALANCE_MOVEMENT: '余额消费'/);
    // 撤销核销是追加反向事实：danger 视觉 + 独立二次确认弹窗（不能与普通编辑同权重）
    assert.match(pageCode.writeOff, /title="撤销核销"/);
    assert.match(pageCode.writeOff, /将追加一条反向核销/);
  });

  test('付款页不再展示裸的技术主键，往来方与往来方类型各自成列', () => {
    const payment = pageCode.payment;
    // sourceId 是退款单的数据库主键：对使用者没有信息量
    assert.ok(!payment.includes(`title: '来源编号'`), '付款页仍在展示裸主键');
    assert.ok(!payment.includes(`dataIndex: 'sourceId'`), '付款页仍绑定了 sourceId 列');
    // 往来方类型（客户 / 供应商）决定这笔付款的性质，与名称并列成独立列
    assert.match(payment, /title: '往来方', dataIndex: 'counterpartyName'/);
    assert.match(payment, /title: '往来方类型', dataIndex: 'counterpartyType'/);
    assert.match(payment, /counterpartyTypeText\(record\.counterpartyType\)/);
  });

  test('收付款页的金额列统一走 scm-money（等宽数字与语义颜色）', () => {
    for (const key of ['receipt', 'payment']) {
      const source = pageCode[key];
      assert.match(source, /class="scm-money"|moneyClass\(text,/, `${PAGES[key]} 的金额未走 scm-money`);
      assert.doesNotMatch(source, /<a-tag :color="SCM_FINANCE_ENTRY_COLOR/, `${PAGES[key]} 的方向标签应走 ScmStatusTag`);
    }
  });

  test('余额类金额列按全站口径左对齐，金额样式只负责等宽数字与颜色', () => {
    const REQUIRED = {
      payable: [
        ['未核销', 'openAmount'],
        ['已核销', 'writtenOffAmount'],
        ['超额核销', 'overAppliedAmount'],
      ],
      receivable: [
        ['未核销', 'openAmount'],
        ['已核销', 'writtenOffAmount'],
        ['超额核销', 'overAppliedAmount'],
      ],
      receipt: [
        ['待核销', 'pendingWriteOffAmount'],
        ['已核销', 'usedAmount'],
      ],
      payment: [
        ['待核销', 'pendingWriteOffAmount'],
        ['已核销', 'usedAmount'],
      ],
    };
    for (const [key, columns] of Object.entries(REQUIRED)) {
      for (const [title, index] of columns) {
        assert.doesNotMatch(
          pageCode[key],
          new RegExp(`title: '${title}', dataIndex: '${index}'[^}]*align:`),
          `${PAGES[key]} 的「${title}」列不应单独设置对齐`
        );
      }
    }
  });

  test('窄屏财务列表只保留卡片，避免表格与卡片重复', () => {
    for (const key of ['receivable', 'payable', 'receipt', 'payment']) {
      assert.match(pageCode[key], /@media \(max-width: 768px\)[\s\S]*?\.finance-table\s*\{\s*display:\s*none/, `${PAGES[key]} 未隐藏窄屏表格`);
    }
  });
}

// ---------------------------------------------------------------- 来源：finance-detail-drawer-contract.test.mjs
{
  /**
   * 财务详情抽屉七段分区的契约测试。
   *
   * `finance-detail-drawer.vue` 统一分区为：
   *   1 单据概要 / 2 对象信息 / 3 金额组成 / 4 核销·退款·红字关系 /
   *   5 来源单据 / 6 流水记录 / 7 系统信息，
   * 并强调「金额摘要应比 ID、编码更突出」。
   *
   * 改造前它只有 5 段，且金额与单号混在同一张 descriptions 里 —— 金额和 ID 视觉权重相同。
   * 本文件钉住三件事，防止被后续改动合并回去：
   *
   * 1. 段标题存在且顺序正确：段名是用户找信息的唯一锚点，缺一段就等于丢掉一类事实。
   * 2. 金额组成用独立的高权重结构与 .scm-money：不能退回 descriptions 里的普通一行。
   * 3. 系统信息只显示单据表头实际审计列，不使用业务事件时间或操作日志替代。
   */

  /** 读源码并剥掉注释（先剥 HTML 注释再剥块注释，顺序不能反）。 */
  function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  const drawer = code('../src/views/business/scm/finance/finance-detail-drawer.vue');

  test('抽屉按固定顺序分区', () => {
    const expected = ['单据概要', '对象信息', '金额组成', '来源单据', '核销 / 红字关系', '流水记录', '系统信息'];
    const positions = expected.map((title) => {
      // h3 可能带 class（金额组成就是 detail-section--nested），不能写死 `<h3>`
      const index = drawer.search(new RegExp(`<h3[^>]*>${title}</h3>`));
      assert.ok(index >= 0, `抽屉缺少分区标题「${title}」`);
      return index;
    });
    // 顺序固定：金额组成在对象信息之后、来源单据之前
    const sorted = [...positions].sort((a, b) => a - b);
    assert.deepEqual(positions, sorted, '分区顺序不符');
  });

  test('金额组成比 ID / 编码更突出', () => {
    // 金额摘要走独立的 amount-grid 结构，而不是普通 descriptions 行
    assert.match(drawer, /class="amount-grid scm-summary"/);
    assert.match(drawer, /amount-cell__value scm-summary__value scm-money/);
    // 净额 / 有效金额放在摘要的第一格
    assert.match(drawer, /净应收 \/ 净应付/);
    assert.match(drawer, /有效金额/);
    // 单据概要段里不得再出现金额（金额已迁到金额组成段）
    const header = drawer.slice(drawer.indexOf('<h3>单据概要</h3>'), drawer.indexOf('<h3>对象信息</h3>'));
    assert.ok(!/label="金额"/.test(header), '单据概要段仍有「金额」行，金额未迁到金额组成段');
  });

  test('系统信息使用四类财务单据的真实表头审计列', () => {
    const section = drawer.slice(drawer.indexOf('<h3>系统信息</h3>'), drawer.indexOf('</section>', drawer.indexOf('<h3>系统信息</h3>')));
    const root = '../../xsy-scm-server/sa-admin/src/main/';
    for (const [vo, dao, alias] of [
      ['FinanceReceivableVO', 'FinanceReceivableDao', 'r'],
      ['FinancePayableVO', 'FinancePayableDao', 'p'],
      ['FinanceReceiptQueryVO', 'FinanceReceiptDao', 'r'],
      ['FinancePaymentQueryVO', 'FinancePaymentDao', 'p'],
    ]) {
      const model = code(`${root}java/com/xsy/scm/finance/domain/vo/${vo}.java`);
      const mapper = code(`${root}resources/mapper/scm/finance/${dao}.xml`);
      for (const [column, field] of [
        ['created_at', 'createdAt'],
        ['updated_at', 'updatedAt'],
        ['created_by', 'createdBy'],
        ['updated_by', 'updatedBy'],
      ]) {
        assert.ok(model.includes(` ${field};`), `${vo} 缺少 ${field}`);
        assert.ok(mapper.includes(`${alias}.${column} AS ${field}`), `${dao} 未读取单据的 ${column}`);
        assert.ok(section.includes(`header.${field}`), `系统信息未展示 ${field}`);
      }
    }
    assert.doesNotMatch(section, /operationLogs|eventAt|receivedAt|paidAt|createTime|updateTime|creatorName/);
    assert.match(drawer, /:row-key="redEntryKey"/);
    assert.match(drawer, /'receivableId' in row \? row.receivableId : row.payableId/);
  });

  test('核销与红字合为同一段，仍各自带小标题', () => {
    const relation = drawer.slice(drawer.indexOf('<h3>核销 / 红字关系</h3>'));
    assert.match(relation, /核销记录/);
    assert.match(relation, /红字关联/);
    // 原来的两个独立 section 标题不得再作为顶级分区存在
    assert.ok(!/<h3>红字关联<\/h3>/.test(drawer), '红字关联仍是独立顶级分区');
    assert.ok(!/<h3>核销记录<\/h3>/.test(drawer), '核销记录仍是独立顶级分区');
  });
}
