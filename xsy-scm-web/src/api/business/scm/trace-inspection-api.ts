import {getRequest, postRequest} from '/@/lib/axios';
import type {TraceAttachment} from './trace-attachment-api';
import type {InspectionConclusion, InspectionStatus} from '/@/constants/business/scm/trace-const';
export type {InspectionConclusion, InspectionStatus} from '/@/constants/business/scm/trace-const';
export interface InspectionPhoto {
  id: number | string;
  version: number;
  reportName: string;
  createdAt: string;
  createdBy: string;
  attachments: TraceAttachment[];
}
export interface InspectionReport {
  id: number | string;
  lotId: number | string;
  version: number;
  reportNo: string;
  reportName: string;
  inspectionDate: string;
  inspectionOrg: string;
  conclusion: InspectionConclusion;
  expiresAt?: string | null;
  status: InspectionStatus;
  expired: boolean;
  publishedAt?: string;
  withdrawnAt?: string;
  attachments: TraceAttachment[];
}
export interface InspectionReportSave {
  id?: number | string;
  version?: number;
  lotId: number | string;
  reportNo: string;
  reportName: string;
  inspectionDate: string;
  inspectionOrg: string;
  conclusion: InspectionConclusion;
  expiresAt?: string | null;
  fileKeys: string[];
}
const options = {suppressGlobalErrorMessage: true};
const base = '/scm/trace/inspection';
export const traceInspectionApi = {
  photos: (beforeId?: number | string) => getRequest<InspectionPhoto[]>(`${base}/photos`, {beforeId}, options),
  uploadPhotos: (fileKeys: string[]) => postRequest<InspectionPhoto>(`${base}/photos`, {fileKeys}, options),
  removePhotos: (report: InspectionPhoto) => postRequest(`${base}/photos/${report.id}/remove`, {version: report.version}),
  list: async (lotId: number | string) => {
    const result = await getRequest<InspectionReport[]>(`${base}/lot/${lotId}`, undefined, options);
    return result.data ?? [];
  },
  save: (form: InspectionReportSave) => postRequest<InspectionReport>(`${base}/save`, form, options),
  publish: (report: InspectionReport) => postRequest(`${base}/${report.id}/publish`, {version: report.version}, options),
  withdraw: (report: InspectionReport) => postRequest(`${base}/${report.id}/withdraw`, {version: report.version}, options),
};
