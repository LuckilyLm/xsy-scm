import { getRequest } from '@/lib/smart-request';
import { adaptProduct } from './product-adapter';

export const mallHomeApi = {
  /** 轻量主题读取：匿名可用，只含 releaseId / themeCode，登录与改密页也依赖它初始化配色 */
  async theme() {
    const response = await getRequest('/scm/mall/home/theme', {}, { anonymous: true, silent: true });
    return {
      ...response,
      data: {
        releaseId: response.data?.releaseId ?? null,
        themeCode: response.data?.themeCode ?? null,
      },
    };
  },

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
