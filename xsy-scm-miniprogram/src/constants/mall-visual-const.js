/* 用户交付图片的稳定入口；营销横幅仍从正式装修配置读取。 */
const ROOT = '/static/mall-visual';

export const MALL_SHORTCUT_IMAGES = {
  new: `${ROOT}/shortcut/new.png`,
  frequent: `${ROOT}/shortcut/frequent.png`,
  activities: `${ROOT}/shortcut/activities.png`,
  sale: `${ROOT}/shortcut/sale.png`,
  coupons: `${ROOT}/shortcut/coupons.png`,
  delivery: `${ROOT}/shortcut/delivery.png`,
};

export const MALL_EMPTY_IMAGES = Object.fromEntries(
  ['cart', 'orders', 'coupons', 'search', 'products', 'network', 'address'].map((kind) => [kind, `${ROOT}/empty/${kind}.png`])
);

// 导航使用交付的普通/选中两套原色图片，颜色不通过滤镜重绘。
const NAVIGATION_ROOT = '/static/mall-navigation';
export const MALL_NAVIGATION_IMAGES = {
  home: { normal: `${NAVIGATION_ROOT}/home-normal.png`, active: `${NAVIGATION_ROOT}/home-selected.png` },
  category: { normal: `${NAVIGATION_ROOT}/category-normal.png`, active: `${NAVIGATION_ROOT}/category-selected.png` },
  cart: { normal: `${NAVIGATION_ROOT}/cart-normal.png`, active: `${NAVIGATION_ROOT}/cart-selected.png` },
  orders: { normal: `${NAVIGATION_ROOT}/orders-normal.png`, active: `${NAVIGATION_ROOT}/orders-selected.png` },
  mine: { normal: `${NAVIGATION_ROOT}/mine-normal.png`, active: `${NAVIGATION_ROOT}/mine-selected.png` },
};

const PROFILE_ROOT = '/static/mall-profile';
export const MALL_PROFILE_IMAGES = {
  myCoupons: `${PROFILE_ROOT}/my-coupons.png`,
  orders: `${PROFILE_ROOT}/orders.png`,
  cart: `${PROFILE_ROOT}/cart.png`,
  address: `${PROFILE_ROOT}/address.png`,
  afterSale: `${PROFILE_ROOT}/after-sale.png`,
  trace: `${PROFILE_ROOT}/trace.png`,
  announcement: `${PROFILE_ROOT}/announcement.png`,
  about: `${PROFILE_ROOT}/about.png`,
  terms: `${PROFILE_ROOT}/terms.png`,
  afterSaleRule: `${PROFILE_ROOT}/after-sale-rule.png`,
  password: `${PROFILE_ROOT}/password.png`,
};
