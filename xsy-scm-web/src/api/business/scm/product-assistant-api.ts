import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {
  AssistantQuery,
  ProductId,
  ProductTag,
  ProductTagForm,
  ProductUom,
  ProductUomAddForm,
  ProductUomUpdateForm,
} from '/@/types/business/scm/product';
/** options 只给可用行，供商品表单选择；list 含停用行与引用计数，供辅助资料页维护。 */
export const productUomApi = {
  list: (form: AssistantQuery, options?: RequestOptions) => postRequest<ProductUom[]>('/scm/product/uom/list', form, options),
  options: (options?: RequestOptions) => getRequest<ProductUom[]>('/scm/product/uom/options', {}, options),
  add: (form: ProductUomAddForm) => postRequest<ProductId>('/scm/product/uom/add', form),
  update: (form: ProductUomUpdateForm) => postRequest('/scm/product/uom/update', form),
  delete: (uomId: ProductId, version: number) => postRequest('/scm/product/uom/delete', {uomId, version}),
};
export const productTagApi = {
  list: (form: AssistantQuery, options?: RequestOptions) => postRequest<ProductTag[]>('/scm/product/tag/list', form, options),
  options: (options?: RequestOptions) => getRequest<ProductTag[]>('/scm/product/tag/options', {}, options),
  add: (form: ProductTagForm) => postRequest<ProductId>('/scm/product/tag/add', form),
  update: (form: ProductTagForm) => postRequest('/scm/product/tag/update', form),
  delete: (tagId: ProductId, version: number) => postRequest('/scm/product/tag/delete', {tagId, version}),
};
