import {getRequest, postRequest} from '/@/lib/axios';
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
  list: () => getRequest<OrderTag[]>('/scm/order/tags', {}),
  create: (name: string, color: OrderTagColor) => postRequest<Id>('/scm/order/tags/create', {name, color}),
  apply: (tagId: Id, orderIds: Id[], action: OrderTagAction) => postRequest<string>('/scm/order/tags/apply', {tagId, orderIds, action}),
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
