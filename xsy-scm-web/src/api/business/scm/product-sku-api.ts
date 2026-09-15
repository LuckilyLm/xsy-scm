import {postRequest} from '/@/lib/axios';
import type {ScmId,ScmResponse} from '/@/types/business/scm/customer';
import type {SkuOption} from '/@/types/business/scm/pricing';
export const productSkuApi={optionList:(form:{keyword?:string;status?:string|null;spuId?:ScmId;limit?:number})=>postRequest('/scm/product/sku/option-list',form) as unknown as Promise<ScmResponse<{options:SkuOption[];truncated:boolean}>>};
