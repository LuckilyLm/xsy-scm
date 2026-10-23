import {postRequest} from '/@/lib/axios';
import type {ScmId, ScmResponse} from '/@/types/business/scm/customer';
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
  list: (viewKey: ScmFilterPresetView) => postRequest(prefix + '/list', {viewKey}) as unknown as Promise<ScmResponse<ScmFilterPreset[]>>,
  create: (viewKey: ScmFilterPresetView, name: string, conditions: ScmAdvancedFilterCondition[]) =>
    postRequest(prefix + '/create', {viewKey, name, conditions}) as unknown as Promise<ScmResponse<ScmId>>,
  remove: (viewKey: ScmFilterPresetView, id: ScmId) => postRequest(prefix + '/remove', {viewKey, id}) as unknown as Promise<ScmResponse<string>>,
};
