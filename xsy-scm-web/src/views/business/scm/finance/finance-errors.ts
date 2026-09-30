type BusinessError = {code?: number; msg?: string; message?: string};

const MESSAGES: Record<number, string> = {
    41110: '请选择完整且顺序正确的日期区间。',
    41111: '日期跨度超过 366 天，请缩小查询范围后重试。',
    41112: '结果超过导出上限；请缩小日期区间或增加筛选条件后重试。',
    41130: '应收单已不存在，请刷新列表后重试。',
    41131: '应付单已不存在，请刷新列表后重试。',
    41132: '收款单已不存在，请刷新列表后重试。',
    41133: '付款单已不存在，请刷新列表后重试。',
    41134: '核销记录已不存在，请刷新列表后重试。',
    41135: '金额超过可核销余额，请刷新明细并调整分配金额。',
    41136: '资金方与目标往来方不一致，请选择同一客户或供应商。',
    41137: '红字金额超过原应付可冲金额，请调整明细。',
    41138: '请填写操作原因。',
    41139: '退款来源无效或退款金额与来源不一致，请核对退款单。',
    41140: '请选择有效的收付款方式。',
    41141: '请选择有效的收付款时间。',
    41142: '这笔收付款仍有有效核销，先反向核销后再反向收付款。',
    41143: '这笔记录已被反向，刷新列表查看现状。',
    40000: '请求参数不正确，请检查后重试。',
    30005: '当前账号没有此操作权限。',
};

export function financeError(error: unknown): string {
    const raw = error as BusinessError & {data?: BusinessError; response?: {data?: BusinessError}};
    const value = raw?.response?.data ?? raw?.data ?? raw;
    const message = value?.code == null ? undefined : MESSAGES[value.code];
    return message ?? value?.msg ?? value?.message ?? '财务操作失败，请重试。';
}

export default financeError;
