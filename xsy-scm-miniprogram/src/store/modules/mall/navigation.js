/*
 * 商城页面间的一次性导航意图（内存态，**不持久化**）
 *
 * 背景：Home 点击某个一级分类后，需要跳到分类 Tab 并选中它。
 * 但 tabBar 页必须用 `uni.switchTab` 跳转，而 switchTab **不支持 query**，
 * 也没有其它可用的传参通道，所以只能用一个瞬时的内存状态把「跳过去要选谁」带过去。
 *
 * 设计约束（刻意保持最小）：
 *   - **不写 storage、不持久化**：刷新即失效是期望行为，避免陈旧意图影响下次进入
 *   - **只存 id，不存分类对象**：分类清单始终以服务端 `getCategories()` 为准，
 *     这里不缓存、不复制业务数据，更不新增后端接口或字段
 *   - **一次性消费**：分类页取用后立即清空
 *   - 不承载任何业务状态，不作为数据缓存使用
 */
import { defineStore } from 'pinia';

export const useMallNavigationStore = defineStore({
  id: 'mallNavigation',
  state: () => ({
    /** 待选中的一级分类 id；null 表示当前没有待处理的导航意图 */
    pendingCategoryId: null,
  }),

  actions: {
    /** 记录待选中的一级分类；传空值等同清除 */
    setPendingCategoryId(categoryId) {
      const isEmpty = categoryId === undefined || categoryId === null || categoryId === '';
      this.pendingCategoryId = isEmpty ? null : Number(categoryId);
    },

    /** 取出并清空（一次性消费），返回被消费的 id */
    consumePendingCategoryId() {
      const id = this.pendingCategoryId;
      this.pendingCategoryId = null;
      return id;
    },

    /** 主动清除（例如目标分类不存在时的安全回退） */
    clearPendingCategoryId() {
      this.pendingCategoryId = null;
    },
  },
});
