const messages: Record<number, string> = {
  40933: '该客户与商品规格的协议价有效期发生重叠',
  40921: '已被其他人修改，请刷新后重试',
  40030: '价格必须为非负四位定点数',
  40031: '结束时间必须晚于开始时间',
  40948: '批次号已成功提交，请勿重复提交',
  40949: '商品规格不可售、不可见或未定价',
  40037: '可见性明细包含不可售商品规格',
  // 40930 的文案由后端按客户实际状态生成（黑名单 / 暂停合作），这里不要用固定文案覆盖。
};

type ErrorEnvelope = {code?: number; msg?: string};
type ErrorLike = ErrorEnvelope & {
  message?: string;
  /** Axios 响应拦截器在业务码非 1 时直接 reject 整个 AxiosResponse，信封在 data 里。 */
  data?: ErrorEnvelope;
  response?: {data?: ErrorEnvelope};
};

export function pricingError(error: unknown): string {
  const e = error as ErrorLike | undefined;
  const envelope = e?.data ?? e?.response?.data ?? e;
  return messages[envelope?.code ?? 0] || envelope?.msg || e?.message || '请求失败，请重试';
}
