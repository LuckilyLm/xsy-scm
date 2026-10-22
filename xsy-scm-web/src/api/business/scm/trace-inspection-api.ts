import {getRequest, postRequest} from '/@/lib/axios';
import type {ScmResponse} from '/@/types/business/scm/customer';
import type {TraceAttachment} from './trace-attachment-api';

import type {InspectionConclusion, InspectionStatus} from '/@/constants/business/scm/trace-const';
export type {InspectionConclusion, InspectionStatus} from '/@/constants/business/scm/trace-const';

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
    list: async (lotId: number | string) => {
        const result = await getRequest(`${base}/lot/${lotId}`, undefined, options) as unknown as ScmResponse<InspectionReport[]>;
        return result.data ?? [];
    },
    save: (form: InspectionReportSave) =>
        postRequest(`${base}/save`, form, options) as unknown as Promise<ScmResponse<InspectionReport>>,
    publish: (report: InspectionReport) =>
        postRequest(`${base}/${report.id}/publish`, {version: report.version}, options),
    withdraw: (report: InspectionReport) =>
        postRequest(`${base}/${report.id}/withdraw`, {version: report.version}, options),
};
