import { getRequest } from '@/lib/smart-request';
import { adaptProduct } from './product-adapter';

export const mallHomeApi = {
  /** 轻量主题读取：匿名可用，含主题编码与服务端派生的完整色阶，登录与改密页也依赖它初始化配色 */
  async theme() {
    const response = await getRequest('/scm/mall/home/theme', {}, { anonymous: true, silent: true });
    return {
      ...response,
      data: {
        releaseId: response.data?.releaseId ?? null,
        themeCode: response.data?.themeCode ?? null,
        themeColor: response.data?.themeColor ?? null,
        palette: response.data?.primary
          ? {
              primary: response.data.primary,
              dark: response.data.dark,
              ink: response.data.ink,
              light: response.data.light,
              disabled: response.data.disabled,
            }
          : null,
      },
    };
  },

  /** 首页弹窗：服务端已按启用状态与生效时间过滤，频控由客户端记录 */
  async popup() {
    const response = await getRequest('/scm/mall/home/popup', {}, { silent: true });
    return {
      ...response,
      data: {
        version: response.data?.version ?? 0,
        enabled: Boolean(response.data?.enabled),
        active: Boolean(response.data?.active),
        displayRule: response.data?.displayRule === 'EVERY_ENTRY' ? 'EVERY_ENTRY' : 'DAILY',
        items: (response.data?.items || []).filter((item) => item?.imageUrl),
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
