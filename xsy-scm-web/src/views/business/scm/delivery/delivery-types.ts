import type {ScmLocation} from '/@/components/business/scm/map/types';
import type {AreaColumns} from '/@/types/business/scm/area';

export type Id = string | number;

export interface Driver {
    id: Id;
    version: number;
    driverCode: string;
    driverName: string;
    phone: string;
    /**
     * 绑定的系统员工 id：把登录人映射回司机档案，是线路数据范围的唯一依据。
     * 启用状态必须绑定（后端 requireBindableEmployee）；历史停用行可为空。
     */
    employeeId?: Id | null;
    /** 绑定员工姓名快照，仅展示；范围判定仍用 employeeId 本身。 */
    employeeName?: string | null;
    status: string;
    remark?: string | null;
}

export interface Vehicle {
    id: Id;
    version: number;
    vehicleNo: string;
    vehicleType?: string | null;
    loadWeight?: string | null;
    loadVolume?: string | null;
    status: string;
    remark?: string | null;
}

export type RouteStatus = 'DRAFT' | 'PLANNED' | 'DISPATCHED' | 'COMPLETED' | 'CANCELLED';

/**
 * 订单级履约状态（V63 `ck_delivery_order_fulfillment`）。
 *
 * 与 `assignmentStatus`（ACTIVE / RELEASED）**正交**：一个说「货到没到手」，
 * 一个说「这单还在不在线路上」。取消线路会写 RELEASED，那与拒收不是同一个事实，
 * 因此两者不能互相推导，页面上也不能拿其中一个替代另一个渲染。
 */
export type FulfillmentStatus = 'PENDING' | 'IN_TRANSIT' | 'SIGNED' | 'EXCEPTION';

/** 签收结果：终态只有这两个，没有「部分签收」（P2 裁决第 12 条）。 */
export type SignResult = 'SIGNED' | 'EXCEPTION';

/** 签收命令载荷；`version` 锁的是 `delivery_route_order` 行，不是线路。 */
export interface SignPayload {
    version: number;
    result: SignResult;
    reason?: string;
}

export interface RouteForm {
    version?: number;
    routeName: string;
    deliveryDate: string;
    warehouseId?: Id;
    driverId?: Id | null;
    vehicleId?: Id | null;
    plannedDepartureTime?: string | null;
    remark?: string | null;
}

export interface DeliveryRoute extends RouteForm {
    id: Id;
    version: number;
    routeNo: string;
    status: RouteStatus;
    warehouseNameSnapshot: string;
    warehouseAddressSnapshot?: string | null;
    startLongitude?: string | number | null;
    startLatitude?: string | number | null;
    startGeomCrs?: ScmLocation['geomCrs'];
    driverNameSnapshot?: string | null;
    driverPhoneSnapshot?: string | null;
    vehicleNoSnapshot?: string | null;
    stopCount: number;
    orderCount: number;
    locatedCount: number;
    totalAmount?: string | null;
    cancelReason?: string | null;
    /**
     * 发车产生的出库单：整条线路零实发（每行都 OUT_OF_STOCK）时**没有**出库单，
     * 两列同时为 null。这是合法成功，不是失败，UI 只能显示「—」不能显示 0。
     */
    outboundId?: Id | null;
    outboundNo?: string | null;
    /** 发车与完成时点由服务端与状态同事务写入；状态到了但时点为空只可能是绕过服务端的写入。 */
    dispatchedAt?: string | null;
    dispatchedBy?: string | null;
    completedAt?: string | null;
    completedBy?: string | null;
}

export interface DeliveryStop extends ScmLocation, Partial<AreaColumns> {
    id: Id;
    stopSeq: number;
    customerId: Id;
    customerNameSnapshot: string;
    receiverNameSnapshot?: string | null;
    receiverPhoneSnapshot?: string | null;
    addressSnapshot: string;
    orderCount: number;
    totalAmount?: string | null;
    plannedArrivalTime?: string | null;
    remark?: string | null;
}

/** 线路订单关系行（服务端返回 `DeliveryRouteOrderEntity`，取消线路时也会随详情返回）。 */
export interface RouteOrder {
    id: Id;
    /** 行级乐观锁版本：签收命令锁的就是这一行，不能用线路版本代替。 */
    version: number;
    stopId: Id;
    orderId: Id;
    customerId: Id;
    orderNoSnapshot: string;
    orderAmountSnapshot?: string | null;
    expectDeliveryTimeSnapshot?: string | null;
    /** 在不在线路上；CANCELLED 线路的详情会把 RELEASED 行一并带回，按 ACTIVE 筛显示行。 */
    assignmentStatus: 'ACTIVE' | 'RELEASED';
    /** 货到没到手；发车前恒为 PENDING，发车后进入 IN_TRANSIT，签收后停在终态不再变。 */
    fulfillmentStatus: FulfillmentStatus;
    signedAt?: string | null;
    signedBy?: string | null;
    /** 异常签收必有；正常签收可为一句备注（如「客户不在，邻居代收」）。 */
    signReason?: string | null;
}

/** 发车命令返回：出库事实与进入在途的订单规模，全部由服务端算好，UI 只渲染。 */
export interface DispatchResult {
    routeId: Id;
    status: RouteStatus;
    dispatchedAt: string;
    /** 整条线路零实发时为 null —— 没有实物离开仓库就不该存在一张出库单。 */
    outboundId?: Id | null;
    outboundNo?: string | null;
    orderCount: number;
    shippedLineCount: number;
}

export interface CandidateOrder extends ScmLocation, Partial<AreaColumns> {
    orderId: Id;
    orderNo: string;
    customerId: Id;
    customerName: string;
    address: string;
    receiverName?: string;
    receiverPhone?: string;
    itemCount: number;
    orderAmount?: string | null;
    expectDeliveryTime?: string | null;
}

export interface RouteDetail {
    route: DeliveryRoute;
    stops: DeliveryStop[];
    orders: RouteOrder[];
}

export type PrintStatus = 'PRINTED' | 'UNPRINTED' | 'PARTIAL';

/** 订单视角：一条 ACTIVE 线路订单的打印状态（订单级只有已打印 / 未打印）。 */
export interface RouteOrderView {
    orderId: Id;
    orderNo: string;
    customerId: Id;
    customerName: string;
    stopId: Id;
    stopSeq: number;
    address: string;
    itemCount: number;
    orderAmount?: string | null;
    printCount: number;
    lastPrintedAt?: string | null;
    printStatus: 'PRINTED' | 'UNPRINTED';
}

/** 客户视角：按客户聚合的打印进度；PARTIAL 表示该客户下部分订单已打印。 */
export interface RouteCustomerView {
    customerId: Id;
    customerName: string;
    orderCount: number;
    itemCount: number;
    totalAmount?: string | null;
    printedOrderCount: number;
    printStatus: PrintStatus;
}

/** 正式生成打印命令的返回：本次实际计入的订单集合与汇总。 */
export interface PrintResult {
    routeId: Id;
    generatedAt: string;
    orderCount: number;
    totalAmount?: string | null;
    orders: RouteOrderView[];
}

export interface PrintItem {
    id: Id;
    orderId: Id;
    productNameSnapshot: string;
    specNameSnapshot?: string;
    saleUnitSnapshot: string;
    orderedQuantity: string;
    actualQuantity?: string | null;
    orderedLineAmount?: string | null;
    settlementLineAmount?: string | null;
}

export interface RoutePrint {
    detail: RouteDetail;
    items: PrintItem[];
}

export interface Query extends Partial<AreaColumns> {
    pageNum: number;
    pageSize: number;
    keyword?: string;
    customerKeyword?: string;
    status?: string;
    deliveryDate?: string;
    warehouseId?: Id;
    driverId?: Id;
    vehicleId?: Id;
    customerId?: Id;
    locatedOnly?: boolean;
    deliveryTimeFrom?: string;
    deliveryTimeTo?: string;
    minAmount?: string | null;
    maxAmount?: string | null;
    minItemCount?: number | null;
    maxItemCount?: number | null;
}

export const routeStatuses: Record<RouteStatus, { label: string; color: string }> = {
    DRAFT: {label: '草稿', color: 'default'},
    PLANNED: {label: '已规划', color: 'green'},
    DISPATCHED: {label: '已发车', color: 'blue'},
    COMPLETED: {label: '已完成', color: 'cyan'},
    CANCELLED: {label: '已取消', color: 'default'},
};

export const printStatuses: Record<PrintStatus, { label: string; color: string }> = {
    PRINTED: {label: '已打印', color: 'green'},
    UNPRINTED: {label: '未打印', color: 'default'},
    PARTIAL: {label: '部分打印', color: 'orange'},
};

/**
 * 履约状态标签：取值必须与 V63 `ck_delivery_order_fulfillment` 同集合。
 *
 * 「已签收」与「异常签收」都是终态（含拒收），列表上要给两者不同颜色，
 * 因为财务与售后看的是「这单到底送到了没有」，把异常混成灰色会和「还没发车」同色。
 */
export const fulfillmentStatuses: Record<FulfillmentStatus, { label: string; color: string }> = {
    PENDING: {label: '未发车', color: 'default'},
    IN_TRANSIT: {label: '在途', color: 'blue'},
    SIGNED: {label: '已签收', color: 'green'},
    EXCEPTION: {label: '异常签收', color: 'red'},
};

/** 可签收的履约状态：只有已在途能签；终态行只读，PENDING 说明线路还没发车。 */
export const SIGNABLE_FULFILLMENT: FulfillmentStatus[] = ['IN_TRANSIT'];

/** 签收结果的按钮 / 标签文案；EXCEPTION 的原因在后端与库里都是必填。 */
export const signResults: Record<SignResult, { label: string; color: string }> = {
    SIGNED: {label: '正常签收', color: 'green'},
    EXCEPTION: {label: '异常签收', color: 'red'},
};

export function deliveryError(error: unknown): string {
    const response = error as { data?: { msg?: string }; response?: { data?: { msg?: string } }; message?: string };
    return response?.data?.msg ?? response?.response?.data?.msg ?? response?.message ?? '操作失败，请刷新后重试';
}
