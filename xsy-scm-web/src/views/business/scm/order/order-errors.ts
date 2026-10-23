type BusinessError = {code?: number; msg?: string; message?: string};

export function orderError(error: unknown): string {
  const raw = error as BusinessError & {data?: BusinessError; response?: {data?: BusinessError}};
  const e = raw?.response?.data ?? raw?.data ?? raw;
  const messages: Record<number, string> = {
    40921: '订单已变更，请刷新后重试',
    40965: '订单明细已变更，请刷新后重试',
    40949: '商品不可售、不可见或未定价，请检查后提交',
    40963: '请先录入所有非标品的实际数量',
    40966: '请求内容与幂等记录不一致，请刷新后重试',
    40969: '退货数量超过剩余可退数量',
    40973: '订单已被接单或指派，请刷新待分配列表',
    40974: '目标业务员不存在、已停用或没有订单查看权限',
    40975: '订单报价依据已变化，请手动重算并填写原因',
    40072: '请填写有效批准数量，批准金额必须大于零',
  };
  return messages[e?.code ?? 0] ?? e?.msg ?? e?.message ?? '操作失败，请重试';
}
