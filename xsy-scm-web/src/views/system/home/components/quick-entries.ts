export interface HomeQuickEntry {
    id: string;
    title: string;
    path: string;
    permissions: string[];
}

export const HOME_QUICK_ENTRIES: HomeQuickEntry[] = [
    {id: 'new-order', title: '新建销售订单', path: '/order/order-list?action=create',
        permissions: ['scm:order:query', 'scm:order:add']},
    {id: 'purchase-demand', title: '采购需求', path: '/purchase/purchase-demand-list',
        permissions: ['scm:purchase:demand:query']},
    {id: 'purchase-receipt', title: '采购收货', path: '/purchase/purchase-receipt-list',
        permissions: ['scm:purchase:receipt:query']},
    {id: 'inventory-warning', title: '库存预警', path: '/inventory/inventory-warning-list',
        permissions: ['scm:inventory:warning:query']},
    {id: 'delivery-route', title: '配送线路', path: '/delivery/routes',
        permissions: ['scm:delivery:route:query']},
    {id: 'report-overview', title: '经营报表', path: '/report/report-overview-list',
        permissions: ['scm:report:overview:query']},
];

/** 缓存只保存目录标识，旧版自填路径、标题与图标不能绕过当前目录和权限。 */
export function readQuickEntryIds(raw: string): string[] {
    const defaults = HOME_QUICK_ENTRIES.map((entry) => entry.id);
    try {
        const saved: unknown = JSON.parse(raw);
        if (!saved || typeof saved !== 'object' || !('version' in saved) || saved.version !== 1
            || !('ids' in saved) || !Array.isArray(saved.ids)) {
            return defaults;
        }
        const ids = saved.ids;
        return defaults.filter((id) => ids.includes(id));
    } catch {
        return defaults;
    }
}
