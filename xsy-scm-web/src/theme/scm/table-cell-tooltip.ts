/** 表格文本只有真正被截断时才显示完整内容提示，随列宽变化自动判断。 */
const TITLE_ATTR = 'title';

/** 单元格正文是否已被省略号截断。以渲染后的溢出为准，不看字符数。 */
function isClipped(cell: HTMLElement): boolean {
    return cell.scrollWidth > cell.clientWidth;
}

/**
 * 按测量结果同步 `title`。
 *
 * 含按钮 / 链接的单元格（典型是操作列）正文就是按钮文字，挂 `title` 只会变成噪音，跳过。
 */
function applyTitle(cell: HTMLElement, clipped: boolean) {
    if (!clipped || cell.querySelector('button, a')) {
        if (cell.hasAttribute(TITLE_ATTR)) {
            cell.removeAttribute(TITLE_ATTR);
        }
        return;
    }
    const text = (cell.innerText || '').trim();
    if (text && cell.getAttribute(TITLE_ATTR) !== text) {
        cell.setAttribute(TITLE_ATTR, text);
    }
}

/**
 * 批量补扫。
 *
 * 先量后写：逐格「读 scrollWidth → 写 title」交替会反复触发布局，表格大时明显变慢。
 */
function syncWithin(root: ParentNode) {
    const cells = Array.from(root.querySelectorAll<HTMLElement>('td.ant-table-cell'));
    const plan = cells.map((cell) => ({cell, clipped: cell.isConnected && isClipped(cell)}));
    for (const {cell, clipped} of plan) {
        applyTitle(cell, clipped);
    }
}

let refreshTimer: number | undefined;

/** 合并同一批 DOM 变更，避免逐行插入时反复全表补扫。 */
function scheduleRefresh() {
    if (refreshTimer !== undefined) {
        return;
    }
    refreshTimer = window.setTimeout(() => {
        refreshTimer = undefined;
        syncWithin(document);
    }, 120);
}

export function installTableCellTooltip() {
    // 悬浮时再确认一次：这是提示真正被用到的时刻，此时布局必然已稳定。
    document.addEventListener('mouseover', (event) => {
        const cell = (event.target as HTMLElement | null)?.closest?.('td.ant-table-cell') as HTMLElement | null;
        if (cell) {
            applyTitle(cell, isClipped(cell));
        }
    });

    // 窗口变化会改列宽，「哪些格子真的被截断」要重新判定。
    window.addEventListener('resize', scheduleRefresh);

    // 表格是异步渲染的：只在真的插入了单元格时补扫，不跟着页面其它 DOM 变更空转。
    const observer = new MutationObserver((records) => {
        for (const record of records) {
            for (const node of Array.from(record.addedNodes)) {
                if (!(node instanceof HTMLElement)) {
                    continue;
                }
                if (node.matches('td.ant-table-cell') || node.querySelector('td.ant-table-cell')) {
                    scheduleRefresh();
                    return;
                }
            }
        }
    });
    observer.observe(document.body, {childList: true, subtree: true});

    scheduleRefresh();
}
