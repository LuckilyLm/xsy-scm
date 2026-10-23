/**
 * 溯源附件：商品资质与批次检测报告。
 *
 * 查看沿用商品/包装码的权限；管理统一收在 `scm:trace:file:manage`。
 */
import {getRequest, postRequest} from '/@/lib/axios';
import type {ScmResponse} from '/@/types/business/scm/customer';

export interface TraceAttachment {
  fileKey: string;
  fileName: string;
  fileType?: string;
  fileSize?: number;
  /** QUALIFICATION 商品资质 / INSPECTION 检测报告。 */
  category: string;
}

const REQUEST_OPTIONS = {suppressGlobalErrorMessage: true};

function path(scope: 'PRODUCT' | 'LOT', targetId: number | string): string {
  return scope === 'PRODUCT' ? `/scm/trace/attachment/product/${targetId}` : `/scm/trace/attachment/lot/${targetId}`;
}

export const traceAttachmentApi = {
  list: async (scope: 'PRODUCT' | 'LOT', targetId: number | string) => {
    const result = (await getRequest(path(scope, targetId), undefined, REQUEST_OPTIONS)) as unknown as ScmResponse<TraceAttachment[]>;
    return result.data ?? [];
  },
  /** 传当前引用的全部 fileKey：列表里没有的旧附件由后端撤销授权。 */
  sync: (scope: 'PRODUCT' | 'LOT', targetId: number | string, fileKeys: string[]) =>
    postRequest(`${path(scope, targetId)}/sync`, {fileKeys}) as unknown as Promise<ScmResponse<string>>,
};
