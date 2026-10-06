/**
 * 报表中心——收货与入库页的列精简与数值样式契约测试。
 *
 * `report-receipt-list.vue` 是报表中心里**唯一没被列对齐改动覆盖**的页面
 * （那次改了 overview / purchase / sales / inventory 与九个 tab 组件），
 * 所以它是唯一还在用自己那套「编码列 + 局部 `.num`」写法的页面。
 *
 * 本文件钉住两条容易被回退的纪律：
 *
 * 1. **商品身份不做四列**：`商品名称` + `商品规格` 两列足够；把 `商品编码` / `商品规格编码`
 *    也各占一列会让一行商品身份吃满 4 列 600px。规则是「编码默认隐藏，除非是对账 / 导出核验
 *    场景」——本页虽是核验场景，但**同一份报表里沿用已对齐页面（如 sales-product-tab）
 *    的两列写法**才是一致的口径（导出走 Excel，不靠表格列）。
 * 2. **数值样式走公共类**：`.scm-money` / `.scm-quantity` 是全局唯一的金额/数量样式，
 *    页面里再定义一个局部 `.num` 会造成「同一个数字在不同报表里字体不同」。
 *
 * `scroll.x` 必须等于各列 width 之和：同一报表的精度要一致，横向滚动宽度与实际列宽
 * 脱节会让固定列（操作列）压住内容列。
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

/** 读源码并剥掉注释（先剥 HTML 注释再剥块注释，顺序不能反）。 */
function code(relative) {
    return readFileSync(new URL(relative, import.meta.url), 'utf8')
        .replace(/<!--[\s\S]*?-->/g, '')
        .replace(/\/\*[\s\S]*?\*\//g, '')
        .replace(/^\s*\/\/.*$/gm, '');
}

const receipt = code('../src/views/business/scm/report/report-receipt-list.vue');

/** 取某个列数组声明的全部 width 之和；数组名不存在就断言失败。 */
function widthSum(source, arrayName) {
    // 泛型参数里还有 `>`（`ref<TableColumnsType<ReceiptRow>>([])`），不能用 `[^>]*` 收尾
    const matched = new RegExp(`${arrayName}\\s*=\\s*ref[\\s\\S]*?\\(\\[([\\s\\S]*?)\\]\\);`).exec(source);
    assert.ok(matched, `找不到列数组 ${arrayName}`);
    const widths = [...matched[1].matchAll(/width:\s*(\d+)/g)].map((item) => Number(item[1]));
    assert.ok(widths.length > 0, `${arrayName} 里没有任何列宽`);
    return widths.reduce((total, item) => total + item, 0);
}

/** 取某个表格的 scroll.x。 */
function scrollX(source, emptyText) {
    const matched = new RegExp(`emptyText: '${emptyText}'[\\s\\S]{0,80}?:scroll="\\{x: (\\d+)\\}"`).exec(source);
    assert.ok(matched, `找不到「${emptyText}」表格的 scroll.x`);
    return Number(matched[1]);
}

test('收货明细：商品身份只占两列，不复制编码列', () => {
    assert.match(receipt, /title: '商品', dataIndex: 'productName'/);
    assert.match(receipt, /title: '商品规格', dataIndex: 'skuName'/);
    // 编码默认隐藏。商品编码 / 商品规格编码 不得再各占一列
    assert.ok(!/title: '商品编码'/.test(receipt), '收货明细仍有独立的「商品编码」列');
    assert.ok(!/title: '商品规格编码'/.test(receipt), '收货明细仍有独立的「商品规格编码」列');
    assert.ok(!/dataIndex: 'spuCode'/.test(receipt), '收货明细仍在渲染 spuCode');
});

test('收款与入库明细：金额右对齐、数量右对齐，且无局部 .num 样式', () => {
    // 金额与数量列必须显式右对齐
    for (const index of [
        'receivedQuantity',
        'cumulativeReceivedQuantity',
        'remainingQuantity',
        'overReceiptQuantity',
        'receiptDifference',
        'purchasePrice',
        'receiptReferenceAmount',
        'quantity',
        'unitCost',
        'costAmount',
    ]) {
        assert.match(
            receipt,
            new RegExp(`dataIndex: '${index}'[\\s\\S]{0,60}?align: 'right'`),
            `${index} 列未右对齐`,
        );
    }
    // 数值渲染统一走全局类，页面里不得再定义局部 .num
    assert.ok(!/class="num"/.test(receipt), '仍在用局部 .num 类渲染数值');
    assert.ok(!/\.num\s*\{/.test(receipt), '仍定义了局部 .num 样式');
    assert.match(receipt, /class="scm-quantity"/, '数量列未使用 .scm-quantity');
    assert.match(receipt, /class="scm-money"/, '金额列未使用 .scm-money');
});

test('两个明细表的 scroll.x 等于各列宽度之和', () => {
    assert.equal(
        scrollX(receipt, '暂无收货明细'),
        widthSum(receipt, 'receiptColumns'),
        '收货明细的 scroll.x 与列宽之和不一致',
    );
    assert.equal(
        scrollX(receipt, '暂无入库明细'),
        widthSum(receipt, 'inboundColumns'),
        '入库明细的 scroll.x 与列宽之和不一致',
    );
});

test('待入库：操作列居中且只有一个回原单的跳转', () => {
    assert.match(receipt, /dataIndex: 'action'[\s\S]{0,120}?align: 'center'/);
    assert.match(receipt, /查看原收货单/);
    // 报表页不得有入库写入口（finance-report-contract 已锁，这里只锁它没有随列精简被重新引入）
    assert.ok(!/<a-button[^>]*>[^<]*(确认入库|办理入库)/.test(receipt));
});
