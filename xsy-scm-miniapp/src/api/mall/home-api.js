/*
 * 商城首页接口
 *
 * ⚠️ 后端尚未实现，路径与字段以后端冻结契约为准。
 *
 * 首页楼层结构见规划文档 §8；装修组件类型见 §9。
 * P0 阶段只消费「一组配置 + 一处生效」，不做通用拖拽装修器。
 */
import { getRequest } from '@/lib/smart-request';

export const mallHomeApi = {
  /**
   * 首页聚合数据
   * 返回：{ store, theme, popup, sections, generatedAt }
   * sections 为楼层数组，客户端按 sectionType 分发渲染。
   */
  getHome: () => getRequest('/scm/mall/home'),

  /** 商城主题 / 店铺配置 */
  getTheme: () => getRequest('/scm/mall/theme'),
};
