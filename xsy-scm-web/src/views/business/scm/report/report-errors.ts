/** 报表错误码转中文提示；未登记的码回落后端 msg，再回落通用文案。 */
type BusinessError = {code?: number; msg?: string; message?: string};

const MESSAGES: Record<number, string> = {
  41110: '请选择完整且顺序正确的日期区间：报表不做无边界扫描，没有日期就没有可复现的结果',
  41111: '日期跨度超过报表上限，请缩小区间（可先查一个月，再按月对比）',
  41112: '当前筛选结果超过导出行数上限，导出已整体拒绝（不会只导前若干行）。请缩小日期范围或增加筛选条件后重试',

  // 通用参数错误码，报表页也会遇到
  40000: '请求参数不正确，请检查筛选条件后重试',
};

export function reportError(error: unknown): string {
  const raw = error as BusinessError & {data?: BusinessError; response?: {data?: BusinessError}};
  const e = raw?.response?.data ?? raw?.data ?? raw;
  const code = e?.code;
  if (typeof code === 'number' && MESSAGES[code]) {
    return MESSAGES[code];
  }
  return e?.msg ?? e?.message ?? '查询失败，请重试';
}

export default reportError;
