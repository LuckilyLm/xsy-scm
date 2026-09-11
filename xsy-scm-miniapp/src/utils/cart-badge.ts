import Taro from '@tarojs/taro'

/** 购物车角标（购物车 Tab 在 tabBar 中的索引为 2）。H5 不支持时静默忽略。 */
export function setCartBadge(count: number): void {
  try {
    if (count > 0) {
      Taro.setTabBarBadge({ index: 2, text: String(count) })
    } else {
      Taro.removeTabBarBadge({ index: 2 })
    }
  } catch {
    /* 非小程序环境忽略 */
  }
}
