const messages: Record<number, string> = {
    40933: '该客户与 SKU 的协议价有效期发生重叠',
    40935: '该客户类型与 SKU 的有效期发生重叠',
    40921: '已被其他人修改，请刷新后重试',
    40030: '价格必须为非负四位定点数',
    40031: '结束时间必须晚于开始时间',
    40948: '批次号已成功提交，请勿重复提交',
    40949: 'SKU 不可售、不可见或未定价',
    40037: '可见性明细包含不可售 SKU'
};

export function pricingError(error: unknown): string {
    const e = error as { code?: number; msg?: string; message?: string };
    return messages[e?.code ?? 0] || e?.msg || e?.message || '请求失败，请重试';
}
