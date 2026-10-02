/**
 * 打印渲染（纯函数 + 一次浏览器打印调用）。
 *
 * 前端只做三件事：**按列顺序取值、转义、排版**。它不决定打印哪些列、不重算数量金额 ——
 * 列定义与单元格文本都由后端算好（权限剔除也在服务端完成），前端剔除等于没剔除。
 *
 * `renderPrintDocument` 是**无 DOM 的纯字符串函数**，可在 node 下直接单测；
 * 只有 `printRenders` 触达浏览器 DOM（隐藏 iframe，避开弹窗拦截）。
 *
 * 所有来自数据的字段一律转义后再落 HTML：模板文本在服务端已拒绝尖括号，但业务数据
 * （商品名、备注）没有也不该有这条限制。
 */
import type {PrintRender} from './print-types';

function esc(value: unknown): string {
    if (value === null || value === undefined) {
        return '';
    }
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

/** `@page` 与字号按纸张给出：小票是定宽卷纸，字号与内边距都要收窄。 */
function pageRule(render: PrintRender): string {
    if (render.paper === 'TICKET_80') {
        return '@page{size:80mm auto;margin:4mm}';
    }
    return render.orientation === 'LANDSCAPE'
        ? '@page{size:A4 landscape;margin:10mm}'
        : '@page{size:A4 portrait;margin:12mm}';
}

function bodyClass(render: PrintRender): string {
    return render.paper === 'TICKET_80' ? 'ticket' : 'a4';
}

/** 页脚溯源行：模板编码 + 版本 +（冻结时的）打印时间，让重印件与原件可分辨。 */
function traceLine(render: PrintRender): string {
    const parts = [`模板 ${render.templateCode ?? '—'} v${render.templateVersion ?? '—'}`];
    if (render.frozen && render.printedAt) {
        parts.push(`打印于 ${render.printedAt}`);
    }
    if (render.printedBy) {
        parts.push(`操作人 ${render.printedBy}`);
    }
    return `<p class="trace">${esc(parts.join('　'))}</p>`;
}

/** 一张单据的打印片段。 */
export function renderPrintHtml(render: PrintRender): string {
    const header = render.headerFields
        .map((field) => `<span class="f"><b>${esc(field.label)}</b>：${esc(field.value)}</span>`)
        .join('');
    const head = '<tr>' + render.columns
        .map((column) => `<th${column.numeric ? ' class="r"' : ''}>${esc(column.label)}</th>`)
        .join('') + '</tr>';
    const body = render.rows
        .map((row) => '<tr>' + render.columns
            .map((column) => `<td${column.numeric ? ' class="r"' : ''}>${esc(row[column.key] ?? '')}</td>`)
            .join('') + '</tr>')
        .join('');
    const totals = render.showTotals && render.totals.length
        ? `<div class="totals">${render.totals
            .map((field) => `<span class="f"><b>${esc(field.label)}</b>：${esc(field.value)}</span>`)
            .join('')}</div>`
        : '';
    return (
        '<section class="doc">' +
        `<h2>${esc(render.title || '')}${render.businessNo ? ' ' + esc(render.businessNo) : ''}</h2>` +
        `<div class="meta">${header}</div>` +
        `<table><thead>${head}</thead><tbody>${body}</tbody></table>` +
        totals +
        (render.footerNote ? `<p class="note">${esc(render.footerNote)}</p>` : '') +
        traceLine(render) +
        '</section>'
    );
}

/** 把若干份版面拼成一页完整打印文档（批量打印时一份一页）。 */
export function renderPrintDocument(renders: PrintRender[]): string {
    const first = renders[0];
    return (
        '<!doctype html><html lang="zh"><head><meta charset="utf-8"/><title>打印</title><style>' +
        (first ? pageRule(first) : '') +
        'body{font-family:system-ui,-apple-system,"Microsoft YaHei",sans-serif;color:#1F2329;margin:16px;}' +
        'body.ticket{margin:2mm;font-size:11px;}' +
        'body.a4{font-size:13px;}' +
        '.doc{page-break-after:always;}' +
        '.doc:last-child{page-break-after:auto;}' +
        'h2{font-size:16px;margin:0 0 8px;}' +
        'body.ticket h2{font-size:13px;}' +
        '.meta{margin-bottom:10px;}' +
        '.meta .f,.totals .f{margin-right:18px;}' +
        'table{border-collapse:collapse;width:100%;}' +
        'body.ticket table{font-size:10px;}' +
        'th,td{border:1px solid #E5E6EB;padding:4px 6px;text-align:left;}' +
        'body.ticket th,body.ticket td{border:none;border-bottom:1px dashed #999;padding:2px 3px;}' +
        '.r{text-align:right;font-variant-numeric:tabular-nums;}' +
        '.totals{margin-top:8px;}' +
        '.note{margin-top:10px;}' +
        '.trace{margin-top:6px;color:#86909C;font-size:11px;}' +
        '</style></head><body class="' +
        (first ? bodyClass(first) : 'a4') +
        '">' +
        renders.map(renderPrintHtml).join('') +
        '</body></html>'
    );
}

/**
 * 在隐藏 iframe 中渲染并调起浏览器打印。
 *
 * 用 iframe 而非 `window.open` 是为避开弹窗拦截；打印完即移除节点。纯客户端动作，
 * 不触发任何业务状态变化（正式打印的记录已在调用前由接口冻结）。
 */
export function printRenders(renders: PrintRender[]): void {
    if (!renders.length) {
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
    doc.write(renderPrintDocument(renders));
    doc.close();
}
