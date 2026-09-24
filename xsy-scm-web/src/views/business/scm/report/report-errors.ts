/**
 * Finance R0 报表域错误码 → 可执行的中文提示（新增文件，仿 `inventory/inventory-errors.ts`）。
 *
 * 取向与库存 / 采购域一致：**提示要说「下一步做什么」，不是复述错误码名字**。
 * 报表域只有三个码，全部是「查询边界不合法」与「导出规模超限」，没有写入冲突码 ——
 * 这一页本来就是只读的。
 *
 * 码值与后端 `ReportErrorCode`（41110–41112）逐字对应；未登记的码回落到后端 `msg`，
 * 再回落到通用文案，**不吞掉后端消息**。
 */
type BusinessError = { code?: number; msg?: string; message?: string };

const MESSAGES: Record<number, string> = {
    41110: '请选择完整且顺序正确的日期区间：报表不做无边界扫描，没有日期就没有可复现的口径',
    41111: '日期跨度超过报表上限，请缩小区间（可先查一个月，再按月对比）',
    41112: '当前筛选结果超过导出行数上限，导出已整体拒绝（不会只导前若干行）。请缩小日期范围或增加筛选条件后重试',

    // 跨域复用的通用码：报表页确实会遇到，让用户看到一个裸错误码是更糟的选择。
    40000: '请求参数不正确，请检查筛选条件后重试',
};

export function reportError(error: unknown): string {
    const raw = error as BusinessError & { data?: BusinessError; response?: { data?: BusinessError } };
    const e = raw?.response?.data ?? raw?.data ?? raw;
    const code = e?.code;
    if (typeof code === 'number' && MESSAGES[code]) {
        return MESSAGES[code];
    }
    return e?.msg ?? e?.message ?? '查询失败，请重试';
}

export default reportError;
