import type {SmartEnum} from '/@/types/smart-enum';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';

/** 与 ScmTraceInspectionStatusEnum 和报告表 CHECK 白名单逐值对应。过期是派生状态。 */
export const SCM_TRACE_INSPECTION_STATUS_ENUM = {
  DRAFT: {value: 'DRAFT', desc: '草稿'},
  PUBLISHED: {value: 'PUBLISHED', desc: '已发布'},
  WITHDRAWN: {value: 'WITHDRAWN', desc: '已撤回'},
} as const satisfies SmartEnum<string>;

/** 与 ScmTraceInspectionConclusionEnum 对应；未判定不能按合格展示。 */
export const SCM_TRACE_INSPECTION_CONCLUSION_ENUM = {
  QUALIFIED: {value: 'QUALIFIED', desc: '合格'},
  UNQUALIFIED: {value: 'UNQUALIFIED', desc: '不合格'},
  UNDETERMINED: {value: 'UNDETERMINED', desc: '未判定'},
} as const satisfies SmartEnum<string>;

export type InspectionStatus = keyof typeof SCM_TRACE_INSPECTION_STATUS_ENUM;
export type InspectionConclusion = keyof typeof SCM_TRACE_INSPECTION_CONCLUSION_ENUM;

export const INSPECTION_STATUS_TONE: Record<InspectionStatus, ScmStatusTone> = {
  DRAFT: 'warning',
  PUBLISHED: 'processing',
  WITHDRAWN: 'neutral',
};
export const INSPECTION_CONCLUSION_TONE: Record<InspectionConclusion, ScmStatusTone> = {
  QUALIFIED: 'success',
  UNQUALIFIED: 'error',
  UNDETERMINED: 'neutral',
};

export const inspectionStatusLabel = (value: string) => (SCM_TRACE_INSPECTION_STATUS_ENUM as SmartEnum<string>)[value]?.desc ?? '未知状态';
export const inspectionConclusionLabel = (value: string) => (SCM_TRACE_INSPECTION_CONCLUSION_ENUM as SmartEnum<string>)[value]?.desc ?? '未知结论';

export function inspectionPublicResult(report: {status: string; expired: boolean; conclusion: string}) {
  if (report.status !== SCM_TRACE_INSPECTION_STATUS_ENUM.PUBLISHED.value) return {label: '报告未公开', tone: 'neutral' as const};
  if (report.expired) return {label: '报告已过期', tone: 'warning' as const};
  const conclusion = report.conclusion as InspectionConclusion;
  return {
    label: conclusion === SCM_TRACE_INSPECTION_CONCLUSION_ENUM.UNDETERMINED.value ? '检测结论未判定' : '检测' + inspectionConclusionLabel(conclusion),
    tone: INSPECTION_CONCLUSION_TONE[conclusion] ?? 'neutral',
  };
}

export default {SCM_TRACE_INSPECTION_STATUS_ENUM, SCM_TRACE_INSPECTION_CONCLUSION_ENUM};
