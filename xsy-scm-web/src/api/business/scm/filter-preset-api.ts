import {postRequest} from '/@/lib/axios';
import type {ScmId} from '/@/types/business/scm/customer';
import type {ScmAdvancedFilterCondition} from '/@/types/business/scm/advanced-filter';
export type ScmFilterPresetView = 'PRODUCT' | 'SALES_ORDER' | 'PURCHASE_PRODUCT';
export interface ScmFilterPreset {
  id: ScmId;
  name: string;
  conditions: ScmAdvancedFilterCondition[];
  available: boolean;
  createdAt: string;
}
const prefix = '/scm/preferences/filters';
export const scmFilterPresetApi = {
  list: (viewKey: ScmFilterPresetView) => postRequest<ScmFilterPreset[]>(prefix + '/list', {viewKey}),
  create: (viewKey: ScmFilterPresetView, name: string, conditions: ScmAdvancedFilterCondition[]) =>
    postRequest<ScmId>(prefix + '/create', {viewKey, name, conditions}),
  remove: (viewKey: ScmFilterPresetView, id: ScmId) => postRequest<string>(prefix + '/remove', {viewKey, id}),
};
