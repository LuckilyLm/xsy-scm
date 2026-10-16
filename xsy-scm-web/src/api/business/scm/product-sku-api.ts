import {getRequest, postRequest} from '/@/lib/axios';
import type {ScmId, ScmResponse} from '/@/types/business/scm/customer';
import type {SkuOption} from '/@/types/business/scm/pricing';

export interface ProductSkuStockConversion {
    stockUnit: string;
    stockUnitsPerSaleUnit: string;
}

export const productSkuApi = {
    optionList: (form: {
        keyword?: string;
        status?: string | null;
        spuId?: ScmId;
        limit?: number
    }) => postRequest('/scm/product/sku/option-list', form) as unknown as Promise<ScmResponse<{
        options: SkuOption[];
        truncated: boolean
    }>>,
    stockConversions: (skuId: ScmId) =>
        getRequest('/scm/product/sku/stock-conversions', {skuId}) as unknown as Promise<ScmResponse<ProductSkuStockConversion[]>>,
    saveStockConversion: (skuId: ScmId, stockUnit: string, stockUnitsPerSaleUnit: string) =>
        postRequest('/scm/product/sku/stock-conversions', {skuId, stockUnit, stockUnitsPerSaleUnit}),
    deleteStockConversion: (skuId: ScmId, stockUnit: string) =>
        postRequest('/scm/product/sku/stock-conversions/delete', {skuId, stockUnit})
};
