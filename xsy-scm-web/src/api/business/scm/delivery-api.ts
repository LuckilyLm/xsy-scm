import {request} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {Warehouse} from '/@/views/business/scm/purchase/purchase-types';
import type {ScmLocation} from '/@/components/business/scm/map/types';
import type {
    CandidateOrder,
    DeliveryRoute,
    DispatchResult,
    Driver,
    Id,
    PrintResult,
    Query,
    RouteCustomerView,
    RouteDetail,
    RouteForm,
    RouteOrderView,
    RoutePrint,
    SignPayload,
    Vehicle,
} from '/@/views/business/scm/delivery/delivery-types';

function call<T>(method: string, path: string, data?: unknown): Promise<ScmResponse<T>> {
    return request({
        url: `/scm/delivery${path}`,
        method, ...(method === 'get' ? {params: data} : {data})
    }) as unknown as Promise<ScmResponse<T>>;
}

// 带 Idempotency-Key 的命令通道：失败保留同一 UUID 供重试回放原结果，成功即释放、内容变化后换用新键。
// 打印计次与发车共用一条通道，因为两者重复提交都会留下不可自动撤销的事实（计次虚增 / 重复扣库存）。
const idempotentKeys = new Map<string, string>();

async function idempotentCommand<T>(path: string, data: unknown): Promise<ScmResponse<T>> {
    const signature = path + JSON.stringify(data);
    let key = idempotentKeys.get(signature);
    if (!key) {
        key = crypto.randomUUID();
        idempotentKeys.set(signature, key);
    }
    const result = await request({
        url: `/scm/delivery${path}`,
        method: 'post',
        data,
        headers: {'Idempotency-Key': key}
    }) as unknown as ScmResponse<T>;
    idempotentKeys.delete(signature);
    return result;
}

/** 打印计次命令；端点与载荷保持 L0–L2 原样，只是改走共用通道。 */
function printCommand<T>(path: string, data: unknown): Promise<ScmResponse<T>> {
    return idempotentCommand<T>(path, data);
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
    /**
     * L3 发车：PLANNED → DISPATCHED，在同一事务内按分拣实发量生成出库单并扣库存。
     * 载荷只有线路版本（`reason` 服务端不消费，因此不发，避免每次重试的签名都不一样）；
     * 返回的 `outboundNo` 为 null 表示整条线路实发 0 —— 那是成功，不是失败。
     */
    dispatch: (id: Id, version: number) => idempotentCommand<DispatchResult>(`/routes/${id}/dispatch`, {version}),
    /**
     * L3 订单签收：IN_TRANSIT → SIGNED | EXCEPTION。
     * 不带 Idempotency-Key —— 后端签名里没有这个头，重复提交由**行版本**乐观锁拒绝；
     * 加了反而会把「别人已先签了」的冲突掩盖成一次成功回放。
     */
    sign: (id: Id, orderId: Id, form: SignPayload) => call<string>('post', `/routes/${id}/orders/${orderId}/sign`, form),
    /** L3 完成线路：DISPATCHED → COMPLETED；仍有活动订单未签收时服务端返回 41117。 */
    complete: (id: Id, version: number) => call<string>('post', `/routes/${id}/complete`, {version}),
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
    ordersView: (id: Id) => call<RouteOrderView[]>('get', `/routes/${id}/orders-view`),
    customersView: (id: Id) => call<RouteCustomerView[]>('get', `/routes/${id}/customers-view`),
    printOrders: (id: Id, version: number, orderIds: Id[]) =>
        printCommand<PrintResult>(`/routes/${id}/print/orders`, {version, orderIds}),
    printCustomers: (id: Id,
                     version: number,
                     customerIds: Id[] | undefined,
                     customerStatusFilter: 'ALL' | 'PRINTED' | 'UNPRINTED' | 'PARTIAL',
                     orderPrintFilter: 'ALL' | 'PRINTED' | 'UNPRINTED') =>
        printCommand<PrintResult>(`/routes/${id}/print/customers`,
            {version, customerIds, customerStatusFilter, orderPrintFilter}),
    warehouses: () => call<Warehouse[]>('get', '/options/warehouses'),
    drivers: () => call<Driver[]>('get', '/options/drivers'),
    vehicles: () => call<Vehicle[]>('get', '/options/vehicles'),
    queryDrivers: (query: Query) => call<ScmPage<Driver>>('get', '/drivers', query),
    saveDriver: (form: Partial<Driver>) => call<Id>('post', '/drivers', form),
    queryVehicles: (query: Query) => call<ScmPage<Vehicle>>('get', '/vehicles', query),
    saveVehicle: (form: Partial<Vehicle>) => call<Id>('post', '/vehicles', form),
};
