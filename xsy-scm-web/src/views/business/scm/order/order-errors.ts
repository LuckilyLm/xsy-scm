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
    40912: '订单已锁单，请新建加单而不是修改原单',
    40913: '订单已发车出库，改单需要发车后改单权限',
    40914: '订单已冻结优惠，不能直接改单；请取消后重下或新建加单',
    40915: '订单已分拣或出库，不能增删商品；请新建加单',
    40916: '已确认订单不能直接新增需称重的商品；请新建加单',
    40917: '订单已发车出库，不能再修改实发数量',
    40072: '请填写有效批准数量，批准金额必须大于零',
  };
  return messages[e?.code ?? 0] ?? e?.msg ?? e?.message ?? '操作失败，请重试';
}
