/**
 * 打印模块契约测试。
 *
 * 由 2 个独立测试文件按模块合并：每个 `{ … }` 块对应原来一份文件，
 * 块内 helper / 常量各自作用域隔离；只有 import 提到顶部并去重。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

// ---------------------------------------------------------------- 来源：print-center-contract.test.mjs
{
  /**
   * 打印中心（模板配置 + 打印记录）的前端契约测试。
   *
   * 只钉「违背之后页面照样能跑、但事实已经错了」的那一类：
   *
   * 1. 打印记录是审计凭据：必须能说清当初用的是哪一版模板，但模板编码 / 版本
   *    不该各占一列 —— 它们下沉为模板名的次要行。
   * 2. 「谁在什么时候打的」是同一件事的两面，合成一格。
   * 3. 模板配置页的编码有维护价值，与普通主数据不同。
   * 4. 操作列统一居中固定，不再右对齐。
   */

  /** 读源码并剥掉注释（先剥 HTML 注释再剥块注释，顺序不能反）。 */
  function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
  }

  const templateList = code('../src/views/business/scm/print/print-template-list.vue');
  const recordList = code('../src/views/business/scm/print/print-record-list.vue');

  test('打印记录把模板名称 / 编码 / 版本与打印人各自成列', () => {
    // 审计场景要能逐项核对：模板名、编码、版本、打印时间、打印人各自成列
    assert.match(recordList, /title: '模板名称', dataIndex: 'templateName'/);
    assert.match(recordList, /title: '模板编码', dataIndex: 'templateCode'/);
    assert.match(recordList, /title: '模板版本', dataIndex: 'templateVersion'/);
    assert.match(recordList, /title: '打印时间', dataIndex: 'printedAt'/);
    assert.match(recordList, /title: '打印人', dataIndex: 'printedBy'/);
    assert.match(recordList, /record\.templateCode/);
    assert.match(recordList, /record\.templateVersion/);
    assert.match(recordList, /record\.printedAt/);
    assert.match(recordList, /record\.printedBy/);
  });

  test('打印模板配置页保留模板编码（与普通主数据的口径不同）', () => {
    assert.match(templateList, /title: '模板编码', dataIndex: 'templateCode'/);
    assert.match(templateList, /title: '模板名称', dataIndex: 'templateName'/);
    assert.match(templateList, /title: '更新时间'/);
    // 明细列数是模板内部实现细节，不属于配置页要回答的问题
    assert.ok(!templateList.includes(`title: '明细列数'`), '模板页仍有明细列数列');
    // 两个开关走语义档位
    assert.match(templateList, /ScmStatusTag/);
    assert.match(templateList, /tone="processing" label="默认"/);
  });

  test('两个页面的操作列统一居中固定，金额 / 状态不再各写色名', () => {
    for (const [name, source] of [
      ['模板页', templateList],
      ['记录页', recordList],
    ]) {
      assert.match(source, /dataIndex: 'action', align: 'center', fixed: 'right'/, `${name} 的操作列未居中`);
      assert.doesNotMatch(source, /<a-tag :color=/, `${name} 仍有裸 a-tag 色名`);
    }
  });

  test('打印模板页的操作列收到 160px 以内，低频动作进「更多」', () => {
    // 「设为默认」与「删除」不再常驻；只有「编辑」留在行内
    const width = /dataIndex: 'action', align: 'center', fixed: 'right', width: (\d+)/.exec(templateList);
    assert.ok(width, '未取到模板页操作列宽度');
    assert.ok(Number(width[1]) <= 160, `模板页操作列 ${width[1]}px 超出 160px`);
    assert.match(templateList, /ScmActionMore/);
    // v-privilege 对菜单项不生效，权限必须在 rowActions 里显式裁剪
    assert.match(templateList, /hasPermission\('scm:print:template:update'\)/);
    assert.match(templateList, /hasPermission\('scm:print:template:delete'\)/);
    assert.match(templateList, /onRowAction[\s\S]{0,300}setDefault\(record\)/);
    assert.match(templateList, /onRowAction[\s\S]{0,300}remove\(record\)/);
    // 默认模板不提供「设为默认 / 删除」
    assert.match(templateList, /hidden: isDefault/);
  });
}

// ---------------------------------------------------------------- 来源：sales-order-print-contract.test.mjs
{
  const read = (p) => readFileSync(new URL(p, import.meta.url), 'utf8');
  const type = read('../src/views/business/scm/print/print-types.ts');
  const list = read('../src/views/business/scm/order/order-list.vue');
  const detail = read('../src/views/business/scm/order/order-detail-page.vue');
  const modal = read('../src/views/business/scm/print/print-document-modal.vue');
  const provider = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/print/support/SalesOrderPrintSourceProvider.java');
  const service = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/print/service/ScmPrintService.java');
  const migration = read('../../xsy-scm-server/sa-admin/src/main/resources/db/migration/V146__scm_sales_order_print.sql');

  test('复用打印中心的模板、快照与记录，不另建订单打印写接口', () => {
    assert.match(type, /'SALES_ORDER'/);
    assert.match(detail, /document-type="SALES_ORDER"/);
    assert.match(list, /document-type="SALES_ORDER"/);
    assert.match(modal, /printApi\.preview\(/);
    assert.match(modal, /printApi\.print\(/);
    assert.match(service, /scmPrintRecordDao\.insertRecord\(record\)/);
    assert.match(service, /scmIdempotencyService\.claim\(/);
  });

  test('订单打印来源从正式订单查询获取，金额与行项目使用原订单快照', () => {
    assert.match(provider, /return OrderPermission\.QUERY/);
    assert.match(provider, /orderQueryService\.detail\(businessId\)/);
    assert.match(provider, /getLockedUnitPrice\(\)/);
    assert.match(provider, /getDraftUnitPrice\(\)/);
    assert.match(provider, /getSettlementTotalAmount\(\)/);
    assert.doesNotMatch(provider, /PriceResolver|update\(|insert\(|selectById\(/);
  });

  test('默认模板与 DB 类型约束同步扩展；批量打印有数量上限', () => {
    assert.match(migration, /ck_scm_print_record_document_type/);
    assert.match(migration, /SALES_ORDER_DEFAULT/);
    assert.match(migration, /"columns"/);
    assert.match(list, /selected\.length > 10/);
    assert.match(list, /selectedRows\.value\.length !== selected\.value\.length/);
    assert.match(detail, /hasPermission\('scm:order:query'\)/);
  });
}

// ---------------------------------------------------------------- 来源：trace-unit-print-qr.test.mjs
{
  const read = (p) => readFileSync(new URL(p, import.meta.url), 'utf8');
  const render = read('../src/views/business/scm/print/print-render.ts');
  const type = read('../../xsy-scm-server/sa-admin/src/main/java/com/xsy/scm/print/constant/ScmPrintDocumentTypeEnum.java');
  const migration = read('../../xsy-scm-server/sa-admin/src/main/resources/db/migration/V177__scm_trace_unit_label_qr.sql');

  test('包装码标签的扫码地址只生成二维码，不印 URL 文本', () => {
    assert.match(render, /field\.key !== 'traceUrl'/, '表头文本必须排除 traceUrl');
    assert.match(render, /qrImages\.get\(traceUrl\)/, '二维码仍从 traceUrl 生成');
    assert.match(type, /new ScmPrintField\("traceUrl", "溯源二维码"/, '白名单必须允许 traceUrl');
    assert.match(migration, /document_type = 'TRACE_UNIT'/, '迁移只改包装码标签');
    assert.match(migration, /traceUrl/, '迁移必须补扫码地址');
  });
}
