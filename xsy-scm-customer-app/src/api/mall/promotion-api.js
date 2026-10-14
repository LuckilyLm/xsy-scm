import { getRequest, postRequest } from '@/lib/smart-request';
import { adaptProducts } from './product-adapter';

export const mallPromotionApi = {
  activities: async () => {
    const response = await getRequest('/scm/mall/promotion/activities');
    return {
      ...response,
      data: (response.data || []).map((activity) => ({
        ...activity,
        product: activity.product ? adaptProducts([activity.product])[0] : null,
      })),
    };
  },
  newProducts: async () => {
    const response = await getRequest('/scm/mall/promotion/new-products');
    return { ...response, data: adaptProducts(response.data || []) };
  },
  claimableCoupons: (query) => getRequest('/scm/mall/promotion/coupons/claimable', query),
  myCoupons: (query) => getRequest('/scm/mall/promotion/coupons/mine', query),
  claimCoupon: (couponId, idempotencyKey) => postRequest(`/scm/mall/promotion/coupons/${couponId}/claim`, {}, { idempotencyKey }),
};
