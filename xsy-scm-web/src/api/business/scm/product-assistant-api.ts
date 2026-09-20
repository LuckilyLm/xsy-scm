import { getRequest, postRequest } from '/@/lib/axios';
import type {
  AssistantQuery, ProductId, ProductTag, ProductTagForm, ProductUom, ProductUomAddForm, ProductUomUpdateForm, ScmResponse,
} from '/@/types/business/scm/product';

/** options 只给可用行，供商品表单选择；list 含停用行与引用计数，供辅助资料页维护。 */
export const productUomApi = {
  list: (form: AssistantQuery) => postRequest('/scm/product/uom/list', form) as unknown as Promise<ScmResponse<ProductUom[]>>,
  options: () => getRequest('/scm/product/uom/options', {}) as unknown as Promise<ScmResponse<ProductUom[]>>,
  add: (form: ProductUomAddForm) => postRequest('/scm/product/uom/add', form) as unknown as Promise<ScmResponse<ProductId>>,
  update: (form: ProductUomUpdateForm) => postRequest('/scm/product/uom/update', form),
  delete: (uomId: ProductId, version: number) => postRequest('/scm/product/uom/delete', { uomId, version }),
};

export const productTagApi = {
  list: (form: AssistantQuery) => postRequest('/scm/product/tag/list', form) as unknown as Promise<ScmResponse<ProductTag[]>>,
  options: () => getRequest('/scm/product/tag/options', {}) as unknown as Promise<ScmResponse<ProductTag[]>>,
  add: (form: ProductTagForm) => postRequest('/scm/product/tag/add', form) as unknown as Promise<ScmResponse<ProductId>>,
  update: (form: ProductTagForm) => postRequest('/scm/product/tag/update', form),
  delete: (tagId: ProductId, version: number) => postRequest('/scm/product/tag/delete', { tagId, version }),
};
