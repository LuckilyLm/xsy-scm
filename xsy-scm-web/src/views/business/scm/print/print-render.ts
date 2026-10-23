/** 前端只负责排版和转义；金额列与金额由服务端决定。 */
import QRCode from 'qrcode';
import type {PrintRender} from './print-types';

/** 二维码图片按内容缓存；渲染是同步的，先算好再取用。 */
const qrImages = new Map<string, string>();

function esc(value: unknown): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

/** `@page` 与字号按纸张给出：小票是定宽卷纸，字号与内边距都要收窄。 */
function pageRule(render: PrintRender): string {
  if (render.paper === 'TICKET_80') {
    return '@page{size:auto;margin:4mm}';
  }
  return render.orientation === 'LANDSCAPE' ? '@page{size:A4 landscape;margin:10mm}' : '@page{size:A4 portrait;margin:12mm}';
}

function bodyClass(render: PrintRender): string {
  return render.paper === 'TICKET_80' ? 'ticket' : 'a4';
}

/** 页脚标注模板名称、版本和打印时间，便于识别历史重印。 */
function traceLine(render: PrintRender): string {
  const template = render.templateName || render.templateCode;
  const parts = [`模板 ${template ?? '—'} v${render.templateVersion ?? '—'}`];
  if (render.frozen && render.printedAt) {
    parts.push(`打印于 ${render.printedAt}`);
  }
  return `<p class="trace">${esc(parts.join('　'))}</p>`;
}

/** 一张单据的打印片段。 */
export function renderPrintHtml(render: PrintRender): string {
  const header = render.headerFields
    // traceUrl 只用于二维码，不印在纸上
    .filter((field) => field.key !== 'traceUrl')
    .map((field) => `<span class="f"><b>${esc(field.label)}</b>：${esc(field.value)}</span>`)
    .join('');
  const head = '<tr>' + render.columns.map((column) => `<th${column.numeric ? ' class="r"' : ''}>${esc(column.label)}</th>`).join('') + '</tr>';
  const body = render.rows
    .map(
      (row) =>
        '<tr>' + render.columns.map((column) => `<td${column.numeric ? ' class="r"' : ''}>${esc(row[column.key] ?? '')}</td>`).join('') + '</tr>'
    )
    .join('');
  const totals =
    render.showTotals && render.totals.length
      ? `<div class="totals">${render.totals.map((field) => `<span class="f"><b>${esc(field.label)}</b>：${esc(field.value)}</span>`).join('')}</div>`
      : '';
  return (
    '<section class="doc">' +
    `<h2>${esc(render.title || '')}${render.businessNo ? ' ' + esc(render.businessNo) : ''}</h2>` +
    `<div class="meta">${header}</div>` +
    qrBlock(render) +
    `<table><thead>${head}</thead><tbody>${body}</tbody></table>` +
    totals +
    (render.footerNote ? `<p class="note">${esc(render.footerNote)}</p>` : '') +
    traceLine(render) +
    '</section>'
  );
}

/** 包装码标签上的二维码；其它单据不显示。 */
function qrBlock(render: PrintRender): string {
  if (render.documentType !== 'TRACE_UNIT') {
    return '';
  }
  const traceUrl = render.headerFields.find((field) => field.key === 'traceUrl')?.value;
  const image = traceUrl ? qrImages.get(traceUrl) : undefined;
  if (!image) {
    return '';
  }
  return `<div class="qr"><img src="${image}" alt="${esc(traceUrl)}"/></div>`;
}

/** 打印前先算好二维码；没有公开地址时不印二维码。 */
export async function prepareQrCodes(renders: PrintRender[]): Promise<void> {
  const urls = renders
    .filter((render) => render.documentType === 'TRACE_UNIT')
    .map((render) => render.headerFields.find((field) => field.key === 'traceUrl')?.value)
    .filter((url): url is string => Boolean(url) && !qrImages.has(url as string));
  for (const url of new Set(urls)) {
    // 240px 足够打印分辨率
    qrImages.set(url, await QRCode.toDataURL(url, {margin: 0, width: 240}));
  }
}

/** 把若干份版面拼成一页完整打印文档（批量打印时一份一页）。 */
export function renderPrintDocument(renders: PrintRender[]): string {
  const first = renders[0];
  return (
    '<!doctype html><html lang="zh"><head><meta charset="utf-8"/><title>打印</title><style>' +
    (first ? pageRule(first) : '') +
    'body{font-family:system-ui,-apple-system,"Microsoft YaHei",sans-serif;color:#1F2329;margin:16px;}' +
    'body.ticket{width:72mm;margin:0;font-size:11px;}' +
    'body.a4{font-size:13px;}' +
    '.doc{page-break-after:always;}' +
    '.doc:last-child{page-break-after:auto;}' +
    'h2{font-size:16px;margin:0 0 8px;}' +
    'body.ticket h2{font-size:13px;}' +
    '.meta{margin-bottom:10px;}' +
    '.meta .f,.totals .f{margin-right:18px;}' +
    'table{border-collapse:collapse;width:100%;}thead{display:table-header-group}tr{break-inside:avoid}th,td{overflow-wrap:anywhere}' +
    'body.ticket table{font-size:10px;}' +
    'th,td{border:1px solid #E5E6EB;padding:4px 6px;text-align:left;}' +
    'body.ticket th,body.ticket td{border:none;border-bottom:1px dashed #999;padding:2px 3px;}' +
    '.r{text-align:right;font-variant-numeric:tabular-nums;}' +
    '.totals{margin-top:8px;}' +
    '.note{margin-top:10px;}' +
    '.qr{margin:6px 0;}' +
    '.qr img{width:22mm;height:22mm;}' +
    '.trace{margin-top:6px;color:#86909C;font-size:11px;}' +
    '</style></head><body class="' +
    (first ? bodyClass(first) : 'a4') +
    '">' +
    renders.map(renderPrintHtml).join('') +
    '</body></html>'
  );
}

/** 在隐藏 iframe 中渲染并调起打印；用 iframe 避开弹窗拦截。 */
export async function printRenders(renders: PrintRender[]): Promise<void> {
  if (!renders.length) return;
  await prepareQrCodes(renders);
  const iframe = document.createElement('iframe');
  iframe.setAttribute('aria-hidden', 'true');
  iframe.title = '单据打印';
  iframe.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0';
  try {
    await new Promise<void>((resolve, reject) => {
      const timeout = window.setTimeout(() => {
        iframe.onload = null;
        reject(new Error('打印页面加载超时，请重试'));
      }, 15000);
      iframe.onload = () => {
        window.clearTimeout(timeout);
        iframe.onload = null;
        resolve();
      };
      iframe.srcdoc = renderPrintDocument(renders);
      document.body.appendChild(iframe);
    });
    const win = iframe.contentWindow;
    const doc = iframe.contentDocument;
    if (!win || !doc) throw new Error('无法打开打印窗口，请重试');
    await doc.fonts.ready;
    // 保留 iframe 到打印结束，避免出现空白页。
    const cleanupTimer = window.setTimeout(() => iframe.remove(), 60000);
    win.addEventListener(
      'afterprint',
      () => {
        window.clearTimeout(cleanupTimer);
        iframe.remove();
      },
      {once: true}
    );
    win.focus();
    win.print();
  } catch (error) {
    iframe.remove();
    throw error;
  }
}
