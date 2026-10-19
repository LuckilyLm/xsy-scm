import { getRequest } from '@/lib/smart-request';
import { adaptProduct } from './product-adapter';

export const mallHomeApi = {
  async published(warehouseId) {
    const response = await getRequest('/scm/mall/home', warehouseId ? { warehouseId } : {});
    return {
      ...response,
      data: {
        releaseId: response.data?.releaseId ?? null,
        themeCode: response.data?.themeCode ?? null,
        blocks: (response.data?.blocks || []).map((block) => ({
          ...block,
          products: (block.products || []).map(adaptProduct),
        })),
      },
    };
  },
};
