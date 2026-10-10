/**
 * 表格单元格的「最多 12 个字符 + 省略号 + 悬浮提示」。
 *
 * 主题层（`theme/scm/table.less`）让单元格默认 `nowrap` + 省略号，不再靠换行把行高撑成两行；
 * 但列宽是按内容给的，同一个字段在不同表里可能显示 14 个字符、另一个只显示 6 个。
 * 这里统一成一条规则：**单元格正文最多 15 个字符，超出用 `…` 收尾，全文进原生 `title`**。
 *
 * 两个边界：
 * - 只对「含中文的长文本」生效。日期、金额、业务单号是固定格式的短串 / 等宽串，
 *   截断会直接丢信息（`2026-10-06 00:06:41` 截到 12 个字就没法看了）。
 * - 只裁剪「纯文本宿主」：单元格本身，或它内部唯一那层 `<span>`。
 *   单元格里同时有链接 / 按钮 / 状态标签的（子元素不止一个，或宿主是 `<a>`）不动 ——
 *   改写 textContent 会把这些元素一起删掉。
 *
 * 只挂一次、只读 DOM，不参与渲染，因此不需要在每个列表页各写一遍。
 */
const MAX_VISIBLE_CHARS = 15;
const FULL_TEXT_ATTR = 'data-scm-full';

const CJK = /[\u4e00-\u9fa5]/;

/**
 * 找出单元格里的纯文本宿主：单元格自身，或它内部唯一那层 `<span>`。
 * 返回 null 表示这个单元格结构不适合裁剪（有多个子元素、或宿主是链接 / 按钮）。
 */
function textHost(cell: HTMLElement): HTMLElement | null {
    let node: HTMLElement = cell;
    while (node.children.length === 1) {
        const child = node.children[0] as HTMLElement;
        if (child.tagName !== 'SPAN') {
            return null;
        }
        node = child;
    }
    return node.children.length === 0 ? node : null;
}

function capCell(cell: HTMLElement) {
    const host = textHost(cell);
    if (!host) {
        return;
    }
    const stored = cell.getAttribute(FULL_TEXT_ATTR);
    const full = (stored ?? host.textContent ?? '').trim();
    if (full.length <= MAX_VISIBLE_CHARS || !CJK.test(full)) {
        return;
    }
    if (!stored) {
        cell.setAttribute(FULL_TEXT_ATTR, full);
    }
    // 提示挂在单元格上：悬浮整格都能看到全文
    if (cell.getAttribute('title') !== full) {
        cell.setAttribute('title', full);
    }
    const shown = full.slice(0, MAX_VISIBLE_CHARS) + '…';
    if (host.textContent !== shown) {
        host.textContent = shown;
    }
}

function capWithin(root: ParentNode) {
    root.querySelectorAll<HTMLElement>('td.ant-table-cell').forEach(capCell);
}

export function installTableCellTooltip() {
    // 被裁剪过的单元格：保留全文提示，不要被下面的「截断判断」清掉
    document.addEventListener('mouseover', (event) => {
        const target = event.target as HTMLElement | null;
        const cell = target?.closest?.('td.ant-table-cell') as HTMLElement | null;
        if (!cell) {
            return;
        }
        const capped = cell.getAttribute(FULL_TEXT_ATTR);
        if (capped) {
            if (cell.getAttribute('title') !== capped) {
                cell.setAttribute('title', capped);
            }
            return;
        }
        // 没裁剪过的（纯英文长串、等宽编码）：按列宽判断是否被省略号截断
        if (cell.scrollWidth <= cell.clientWidth) {
            cell.removeAttribute('title');
            return;
        }
        const text = cell.innerText.trim();
        if (text && cell.getAttribute('title') !== text) {
            cell.setAttribute('title', text);
        }
    });

    // 表格是异步渲染的：初次挂载后还要跟着数据更新，所以用 observer 补扫新增的单元格
    const observer = new MutationObserver((records) => {
        for (const record of records) {
            for (const node of record.addedNodes) {
                if (!(node instanceof HTMLElement)) {
                    continue;
                }
                if (node.matches('td.ant-table-cell')) {
                    capCell(node);
                } else {
                    capWithin(node);
                }
            }
        }
    });
    observer.observe(document.body, {childList: true, subtree: true});
    capWithin(document);
}
