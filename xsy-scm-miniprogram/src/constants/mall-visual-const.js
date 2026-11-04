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
  ['cart', 'orders', 'coupons', 'search', 'products', 'network'].map((kind) => [kind, `${ROOT}/empty/${kind}.png`])
);
