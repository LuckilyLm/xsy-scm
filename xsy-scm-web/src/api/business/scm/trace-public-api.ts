/**
 * 公开扫码溯源：免登录，凭据是标签上的令牌。
 *
 * 只读接口，失败由页面内联提示，不让全局 toast 再重复说一遍。
 */
import {getRequest} from '/@/lib/axios';
import type {ScmResponse} from '/@/types/business/scm/customer';

export interface TracePublicAttachment {
    fileKey: string;
    fileName: string;
    fileType?: string;
    fileSize?: number;
    /** QUALIFICATION 商品资质 / INSPECTION 检测报告。 */
    category: string;
}

export interface TracePublicView {
    unitCode: string;
    /** ACTIVE 或 VOID；作废的码仍可查到，页面据此提示勿食用。 */
    status: string;
    productName?: string;
    skuName?: string;
    packageType?: string;
    quantity?: string;
    unit?: string;
    netWeight?: string;
    weightUnit?: string;
    measuredAt?: string;
    lotNo?: string;
    sourceLotNo?: string;
    supplierName?: string;
    receivedAt?: string;
    /** 已脱敏。 */
    customerName?: string;
    /** 已脱敏。 */
    orderNo?: string;
    attachments?: TracePublicAttachment[];
}

const REQUEST_OPTIONS = {suppressGlobalErrorMessage: true};

export const tracePublicApi = {
    detail: (token: string) =>
        getRequest(`/scm/trace/public/${encodeURIComponent(token)}`, undefined, REQUEST_OPTIONS) as unknown as Promise<
            ScmResponse<TracePublicView>
        >,
    /** 附件下载地址：与接口同源，带 API 前缀，直接给 <a href> 用。 */
    fileUrl: (token: string, fileKey: string) =>
        `${import.meta.env.VITE_APP_API_URL}/scm/trace/public/${encodeURIComponent(token)}/files?fileKey=${encodeURIComponent(fileKey)}`,
};
