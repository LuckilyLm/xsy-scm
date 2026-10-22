// 与服务端 ScmTraceInspection*Enum 和管理端 trace-const.ts 保持相同编码及中文含义。
export const TRACE_INSPECTION_STATUS_ENUM = {
  DRAFT: { value: 'DRAFT', desc: '草稿' },
  PUBLISHED: { value: 'PUBLISHED', desc: '已发布' },
  WITHDRAWN: { value: 'WITHDRAWN', desc: '已撤回' },
};

export const TRACE_INSPECTION_CONCLUSION_ENUM = {
  QUALIFIED: { value: 'QUALIFIED', desc: '合格' },
  UNQUALIFIED: { value: 'UNQUALIFIED', desc: '不合格' },
  UNDETERMINED: { value: 'UNDETERMINED', desc: '未判定' },
};

const CONCLUSION_TONE = { QUALIFIED: 'success', UNQUALIFIED: 'error', UNDETERMINED: 'neutral' };

export const inspectionStatusLabel = (value) => TRACE_INSPECTION_STATUS_ENUM[value]?.desc || '未知状态';
export const inspectionConclusionLabel = (value) => TRACE_INSPECTION_CONCLUSION_ENUM[value]?.desc || '未知结论';

export function inspectionPublicResult(report) {
  if (report.status !== TRACE_INSPECTION_STATUS_ENUM.PUBLISHED.value) return { label: '报告未公开', tone: 'neutral' };
  if (report.expired) return { label: '报告已过期', tone: 'warning' };
  return {
    label: report.conclusion === TRACE_INSPECTION_CONCLUSION_ENUM.UNDETERMINED.value
      ? '检测结论未判定' : '检测' + inspectionConclusionLabel(report.conclusion),
    tone: CONCLUSION_TONE[report.conclusion] || 'neutral',
  };
}
