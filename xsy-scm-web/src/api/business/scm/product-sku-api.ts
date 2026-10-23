import {getRequest, postRequest} from '/@/lib/axios';
import type {ScmId} from '/@/types/business/scm/customer';
import type {SkuOption} from '/@/types/business/scm/pricing';

export interface ProductSkuStockConversion {
  stockUnit: string;
  stockUnitsPerSaleUnit: string;
}

export const productSkuApi = {
  optionList: (form: {keyword?: string; status?: string | null; spuId?: ScmId; limit?: number}) =>
    postRequest<{
      options: SkuOption[];
      truncated: boolean;
    }>('/scm/product/sku/option-list', form),
  stockConversions: (skuId: ScmId) => getRequest<ProductSkuStockConversion[]>('/scm/product/sku/stock-conversions', {skuId}),
  saveStockConversion: (skuId: ScmId, stockUnit: string, stockUnitsPerSaleUnit: string) =>
    postRequest('/scm/product/sku/stock-conversions', {skuId, stockUnit, stockUnitsPerSaleUnit}),
  deleteStockConversion: (skuId: ScmId, stockUnit: string) => postRequest('/scm/product/sku/stock-conversions/delete', {skuId, stockUnit}),
};
