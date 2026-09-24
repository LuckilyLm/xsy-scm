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

export interface RouteOrder {
    id: Id;
    stopId: Id;
    orderId: Id;
    customerId: Id;
    orderNoSnapshot: string;
    orderAmountSnapshot?: string | null;
    expectDeliveryTimeSnapshot?: string | null;
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

export function deliveryError(error: unknown): string {
    const response = error as { data?: { msg?: string }; response?: { data?: { msg?: string } }; message?: string };
    return response?.data?.msg ?? response?.response?.data?.msg ?? response?.message ?? '操作失败，请刷新后重试';
}
