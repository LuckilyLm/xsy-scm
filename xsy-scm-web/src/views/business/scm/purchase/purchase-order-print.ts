/**
 * 采购单打印（Wave 2B §6.3 / §6.8，纯前端）。
 *
 * 只把**已经查到的**采购单详情渲染成打印文档并调起浏览器打印：不调用任何写接口、
 * 不改变采购状态，也不引入第二套后端打印子系统（AGENTS §23 反对为单页各自造打印设施）。
 *
 * HTML 构建（{@link purchaseOrderPrintHtml}）是**无 DOM 的纯字符串函数**，可在 node 下直接单测；
 * 只有 {@link printPurchaseOrders} 触达浏览器 DOM。所有来自数据行的字段一律转义后再落 HTML。
 */
import type {Order, OrderItem} from './purchase-types.ts';

/** HTML 文本转义：数据里的 `<` / `&` / 引号不得被当成标记解析。 */
function esc(value: unknown): string {
    if (value === null || value === undefined || value === '') {
        return '';
    }
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

/** 数量 / 金额：后端已下发 4 位定点字符串，打印原样呈现（空值渲染为空，不伪造 0）。 */
function num(value: string | null | undefined): string {
    return esc(value);
}

function itemRow(item: OrderItem): string {
    return (
        '<tr>' +
        `<td>${esc(item.productName)}</td>` +
        `<td>${esc(item.skuCode)}</td>` +
        `<td>${esc(item.skuName)}</td>` +
        `<td>${esc(item.purchaseUnit)}</td>` +
        `<td class="r">${num(item.plannedQuantity)}</td>` +
        `<td class="r">${num(item.receivedQuantity)}</td>` +
        `<td class="r">${num(item.remainingQuantity)}</td>` +
        `<td class="r">${num(item.purchasePrice)}</td>` +
        `<td class="r">${num(item.lineAmount)}</td>` +
        '</tr>'
    );
}

/** 生成一张采购单的打印片段（表头 + 明细表）。 */
export function purchaseOrderPrintHtml(order: Order): string {
    const head = [
        ['供应商', order.supplierName],
        ['收货仓库', order.warehouseName],
        ['采购员', order.purchaserName],
        ['计划到货', order.plannedArrivalDate],
    ]
        .map(([label, value]) => `<span class="f"><b>${esc(label)}</b>：${esc(value)}</span>`)
        .join('');
    const items = (order.items ?? []).map(itemRow).join('');
    return (
        '<section class="doc">' +
        `<h2>采购单 ${esc(order.orderNo)}</h2>` +
        `<div class="meta">${head}<span class="f"><b>合计</b>：¥ ${num(order.totalAmount)}</span></div>` +
        '<table><thead><tr>' +
        '<th>商品</th><th>SKU 编码</th><th>规格</th><th>单位</th>' +
        '<th class="r">计划量</th><th class="r">已收</th><th class="r">剩余</th><th class="r">单价</th><th class="r">金额</th>' +
        '</tr></thead><tbody>' +
        items +
        '</tbody></table>' +
        (order.remark ? `<p class="remark"><b>备注</b>：${esc(order.remark)}</p>` : '') +
        '</section>'
    );
}

/** 把若干采购单拼成一页完整打印文档。 */
export function purchaseOrdersPrintDocument(orders: Order[]): string {
    return (
        '<!doctype html><html lang="zh"><head><meta charset="utf-8"/><title>采购单打印</title><style>' +
        'body{font-family:system-ui,-apple-system,"Microsoft YaHei",sans-serif;color:#1F2329;margin:16px;}' +
        '.doc{page-break-after:always;}' +
        'h2{font-size:18px;margin:0 0 8px;}' +
        '.meta{margin-bottom:10px;font-size:13px;}' +
        '.meta .f{margin-right:18px;}' +
        'table{border-collapse:collapse;width:100%;font-size:13px;}' +
        'th,td{border:1px solid #E5E6EB;padding:4px 6px;text-align:left;}' +
        '.r{text-align:right;font-variant-numeric:tabular-nums;}' +
        '.remark{font-size:13px;}' +
        '</style></head><body>' +
        orders.map(purchaseOrderPrintHtml).join('') +
        '</body></html>'
    );
}

/**
 * 在隐藏 iframe 中渲染采购单并调起浏览器打印。
 *
 * 用 iframe 而非 `window.open` 是为避开弹窗拦截；打印完即移除节点。纯客户端动作。
 */
export function printPurchaseOrders(orders: Order[]): void {
    if (!orders.length) {
        return;
    }
    const iframe = document.createElement('iframe');
    iframe.setAttribute('aria-hidden', 'true');
    iframe.style.position = 'fixed';
    iframe.style.right = '0';
    iframe.style.bottom = '0';
    iframe.style.width = '0';
    iframe.style.height = '0';
    iframe.style.border = '0';
    document.body.appendChild(iframe);

    const done = () => {
        iframe.removeEventListener('load', onload);
        window.setTimeout(() => iframe.remove(), 0);
    };
    const onload = () => {
        const win = iframe.contentWindow;
        if (!win) {
            done();
            return;
        }
        win.focus();
        win.print();
        done();
    };
    iframe.addEventListener('load', onload);

    const doc = iframe.contentDocument;
    if (!doc) {
        iframe.remove();
        return;
    }
    doc.open();
    doc.write(purchaseOrdersPrintDocument(orders));
    doc.close();
}
