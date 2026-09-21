/* 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/api/business/customer/customer-goods-visible-api.ts
 * 复制日期：2026-09-15。剪枝：全部写接口。适配：只读 SKU 反查与类型。验收：W3 E2E。 */
import {postRequest} from '/@/lib/axios';
import type {ScmId, ScmResponse, ScmPage} from '/@/types/business/scm/customer';
import type {VisibilityRow} from '/@/types/business/scm/pricing';

export const customerVisibilityApi = {
    query: (form: {
        pageNum: number;
        pageSize: number;
        customerId?: ScmId;
        skuId?: ScmId;
        visibilityPolicy?: string
    }) => postRequest('/scm/customer/visibility/reverse/query', form) as unknown as Promise<ScmResponse<ScmPage<VisibilityRow>>>
};
