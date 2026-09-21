import {request} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {Warehouse} from '/@/views/business/scm/purchase/purchase-types';
import type {ScmLocation} from '/@/components/business/scm/map/types';
import type {
    CandidateOrder,
    DeliveryRoute,
    Driver,
    Id,
    Query,
    RouteDetail,
    RouteForm,
    RoutePrint,
    Vehicle,
} from '/@/views/business/scm/delivery/delivery-types';

function call<T>(method: string, path: string, data?: unknown): Promise<ScmResponse<T>> {
    return request({
        url: `/scm/delivery${path}`,
        method, ...(method === 'get' ? {params: data} : {data})
    }) as unknown as Promise<ScmResponse<T>>;
}

export const deliveryApi = {
    routes: (query: Query) => call<ScmPage<DeliveryRoute>>('get', '/routes', query),
    detail: (id: Id) => call<RouteDetail>('get', `/routes/${id}`),
    create: (form: RouteForm) => call<Id>('post', '/routes', form),
    update: (id: Id, form: RouteForm) => call<string>('put', `/routes/${id}`, form),
    plan: (id: Id, version: number) => call<string>('post', `/routes/${id}/plan`, {version}),
    cancel: (id: Id, version: number, reason: string) => call<string>('post', `/routes/${id}/cancel`, {
        version,
        reason
    }),
    candidates: (query: Query) => call<ScmPage<CandidateOrder>>('get', '/candidate-orders', query),
    addOrders: (id: Id, version: number, orderIds: Id[], reason: string) => call<string>('post', `/routes/${id}/orders`, {
        version,
        orderIds,
        reason
    }),
    removeOrder: (id: Id, orderId: Id, version: number, reason: string) =>
        call<string>('delete', `/routes/${id}/orders/${orderId}`, {version, reason}),
    reorder: (id: Id, version: number, stopIds: Id[]) => call<string>('put', `/routes/${id}/stops/reorder`, {
        version,
        stopIds
    }),
    locate: (id: Id, stopId: Id, form: ScmLocation & {
        version: number;
        plannedArrivalTime?: string | null;
        remark?: string | null
    }) =>
        call<string>('put', `/routes/${id}/stops/${stopId}`, form),
    print: (id: Id) => call<RoutePrint>('get', `/routes/${id}/print`),
    warehouses: () => call<Warehouse[]>('get', '/options/warehouses'),
    drivers: () => call<Driver[]>('get', '/options/drivers'),
    vehicles: () => call<Vehicle[]>('get', '/options/vehicles'),
    queryDrivers: (query: Query) => call<ScmPage<Driver>>('get', '/drivers', query),
    saveDriver: (form: Partial<Driver>) => call<Id>('post', '/drivers', form),
    queryVehicles: (query: Query) => call<ScmPage<Vehicle>>('get', '/vehicles', query),
    saveVehicle: (form: Partial<Vehicle>) => call<Id>('post', '/vehicles', form),
};
