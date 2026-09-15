/* 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/api/business/product/product-price-api.ts
 * 复制日期：2026-09-15。剪枝：去批量删除。适配：两个资源、版本、定点字符串、SmartAdmin 信封。验收：W3 E2E。 */
import { getRequest,postRequest } from '/@/lib/axios';
import type {ScmId,ScmResponse,ScmPage} from '/@/types/business/scm/customer';
import type {PriceForm,PriceQuery,PriceRow,BatchRow,BatchResult,HistoryRow,ResolveResult} from '/@/types/business/scm/pricing';
function resource(path:string) {return {
 query:(form:PriceQuery)=>postRequest(path+'/query',form) as unknown as Promise<ScmResponse<ScmPage<PriceRow>>>,
 detail:(id:ScmId)=>getRequest(path+'/detail/'+id,{}) as unknown as Promise<ScmResponse<PriceRow>>,
 add:(form:PriceForm)=>postRequest(path+'/add',form),update:(form:PriceForm)=>postRequest(path+'/update',form),delete:(form:PriceForm)=>postRequest(path+'/delete',form),
};}
export const pricingApi={agreement:resource('/scm/pricing/agreement-price'),typePrice:resource('/scm/pricing/type-price'),
 batch:(form:{batchKey:string;rows:BatchRow[]})=>postRequest('/scm/pricing/type-price/batch',form) as unknown as Promise<ScmResponse<BatchResult>>,
 history:(form:PriceQuery & {source?:string;operationType?:string;operatedFrom?:string|null;operatedTo?:string|null})=>postRequest('/scm/pricing/history/query',form) as unknown as Promise<ScmResponse<ScmPage<HistoryRow>>>,
 resolve:(form:{customerId:ScmId;skuIds:ScmId[];at?:string|null})=>postRequest('/scm/pricing/resolve',form) as unknown as Promise<ScmResponse<ResolveResult>>,
};
