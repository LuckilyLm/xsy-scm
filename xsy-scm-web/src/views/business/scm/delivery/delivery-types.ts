import type {ScmLocation} from '/@/components/business/scm/map/types';
import type {AreaColumns} from '/@/types/business/scm/area';

export type Id = string | number;

export interface Driver {
  id: Id;
  version: number;
  driverCode: string;
  driverName: string;
  phone: string;
  /** 绑定的系统员工 id，决定司机能看到哪些线路。启用状态必须绑定，停用行可为空。 */
  employeeId?: Id | null;
  /** 绑定员工姓名，仅用于展示。 */
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

/** 履约状态表示是否送达，与是否还在线路上无关；移出线路不等于拒收。 */
export type FulfillmentStatus = 'PENDING' | 'IN_TRANSIT' | 'SIGNED' | 'EXCEPTION';

/** 签收只有正常和异常两种，没有部分签收。 */
export type SignResult = 'SIGNED' | 'EXCEPTION';

/** 签收参数；version 锁的是线路订单行，不是线路。 */
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
  /** 发车生成的出库单；整条线路都没有实发时为空，界面显示「—」不能显示 0。 */
  outboundId?: Id | null;
  outboundNo?: string | null;
  /** 发车、完成时间由服务端随状态一起写入。 */
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
  /** 行级版本，签收时提交，不能用线路版本代替。 */
  version: number;
  stopId: Id;
  orderId: Id;
  customerId: Id;
  orderNoSnapshot: string;
  orderAmountSnapshot?: string | null;
  expectDeliveryTimeSnapshot?: string | null;
  /** 是否仍在线路上；已取消线路的详情也会带回 RELEASED 行，展示时按 ACTIVE 筛选。 */
  assignmentStatus: 'ACTIVE' | 'RELEASED';
  /** 发车前为 PENDING，发车后在途，签收后不再变化。 */
  fulfillmentStatus: FulfillmentStatus;
  signedAt?: string | null;
  signedBy?: string | null;
  /** 异常签收必有；正常签收可为一句备注（如「客户不在，邻居代收」）。 */
  signReason?: string | null;
}

/** 发车结果，全部由服务端算好。 */
export interface DispatchResult {
  routeId: Id;
  status: RouteStatus;
  dispatchedAt: string;
  /** 整条线路没有实发时为空，此时不生成出库单。 */
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

export const routeStatuses: Record<RouteStatus, {label: string; color: string}> = {
  DRAFT: {label: '草稿', color: 'default'},
  PLANNED: {label: '已规划', color: 'green'},
  DISPATCHED: {label: '已发车', color: 'blue'},
  COMPLETED: {label: '已完成', color: 'cyan'},
  CANCELLED: {label: '已取消', color: 'default'},
};

export const printStatuses: Record<PrintStatus, {label: string; color: string}> = {
  PRINTED: {label: '已打印', color: 'green'},
  UNPRINTED: {label: '未打印', color: 'default'},
  PARTIAL: {label: '部分打印', color: 'orange'},
};

/** 履约状态标签。取值与数据库约束一致；已签收和异常签收要区分颜色，异常不能用灰色。 */
export const fulfillmentStatuses: Record<FulfillmentStatus, {label: string; color: string}> = {
  PENDING: {label: '未发车', color: 'default'},
  IN_TRANSIT: {label: '在途', color: 'blue'},
  SIGNED: {label: '已签收', color: 'green'},
  EXCEPTION: {label: '异常签收', color: 'red'},
};

/** 只有在途的订单能签收。 */
export const SIGNABLE_FULFILLMENT: FulfillmentStatus[] = ['IN_TRANSIT'];

/** 签收结果文案；异常签收必须填写原因。 */
export const signResults: Record<SignResult, {label: string; color: string}> = {
  SIGNED: {label: '正常签收', color: 'green'},
  EXCEPTION: {label: '异常签收', color: 'red'},
};

export function deliveryError(error: unknown): string {
  const response = error as {data?: {msg?: string}; response?: {data?: {msg?: string}}; message?: string};
  return response?.data?.msg ?? response?.response?.data?.msg ?? response?.message ?? '操作失败，请刷新后重试';
}

// 辅助排线建议

/** 一段行程的距离和累计距离，用来解释为什么这样排序。 */
export interface DeliveryPlanLeg {
  seq: number;
  /** 生成建议时的站点顺序；旧数据可能没有。 */
  originalSeq?: number | null;
  stopId: Id;
  customerNameSnapshot?: string;
  addressSnapshot?: string;
  /** 四位定点字符串（米）。 */
  legDistance?: string | null;
  cumulativeDistance?: string | null;
  longitude?: string | null;
  latitude?: string | null;
}

export interface DeliveryPlanProposal {
  id: Id;
  routeId: Id;
  status: 'PROPOSED' | 'APPLIED' | 'DISCARDED' | string;
  providerCode: string;
  providerVersion: string;
  /** true 表示估算，不是真实路网或实时路况，界面要明确提示。 */
  estimated: boolean;
  ruleCode: string;
  stopCount: number;
  sourceRouteVersion?: number | null;
  sourceLongitude?: string | number | null;
  sourceLatitude?: string | number | null;
  sourceGeomCrs?: ScmLocation['geomCrs'];
  ignoredFactors?: string[];
  totalDistance?: string | null;
  legs?: DeliveryPlanLeg[];
  createdAt?: string;
  createdBy?: string;
  appliedAt?: string | null;
  appliedBy?: string | null;
  discardedAt?: string | null;
  discardedBy?: string | null;
  version?: number;
}

export const planProposalStatuses: Record<string, {label: string; color: string}> = {
  PROPOSED: {label: '待确认', color: 'blue'},
  APPLIED: {label: '已应用', color: 'green'},
  DISCARDED: {label: '已放弃', color: 'default'},
};

/** 排序规则的可读说明；后端只回代码，文案在前端维护。 */
export const planRuleLabels: Record<string, string> = {
  NEAREST_NEIGHBOUR_FROM_START: '从起点出发，每步去最近的未访问停靠点（启发式，不保证最短路径）',
};
