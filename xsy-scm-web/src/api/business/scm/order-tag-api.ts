import {getRequest, postRequest} from '/@/lib/axios';
import type {ScmResponse} from '/@/types/business/scm/customer';
import type {Id} from '/@/views/business/scm/order/order-types';

export type OrderTagColor = 'BLUE' | 'GREEN' | 'ORANGE' | 'RED' | 'PURPLE' | 'GRAY';
export interface OrderTag {
  tagId: Id;
  name: string;
  color: OrderTagColor;
  orderId?: Id;
}
export type OrderTagAction = 'ADD' | 'REMOVE';

export const orderTagApi = {
  list: () => getRequest('/scm/order/tags', {}) as unknown as Promise<ScmResponse<OrderTag[]>>,
  create: (name: string, color: OrderTagColor) => postRequest('/scm/order/tags/create', {name, color}) as unknown as Promise<ScmResponse<Id>>,
  apply: (tagId: Id, orderIds: Id[], action: OrderTagAction) =>
    postRequest('/scm/order/tags/apply', {tagId, orderIds, action}) as unknown as Promise<ScmResponse<string>>,
};
export const tagColor = (color: OrderTagColor): string =>
  ({
    BLUE: 'blue',
    GREEN: 'green',
    ORANGE: 'orange',
    RED: 'red',
    PURPLE: 'purple',
    GRAY: 'default',
  })[color];
