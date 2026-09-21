import type { ScmLocation } from '/@/components/business/scm/map/types';
import type { AreaColumns } from '/@/types/business/scm/area';
export type Id = string | number;
export interface Driver {
  id: Id;
  version: number;
  driverCode: string;
  driverName: string;
  phone: string;
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
  DRAFT: { label: '草稿', color: 'default' },
  PLANNED: { label: '已规划', color: 'green' },
  DISPATCHED: { label: '已发车', color: 'blue' },
  COMPLETED: { label: '已完成', color: 'cyan' },
  CANCELLED: { label: '已取消', color: 'default' },
};
export function deliveryError(error: unknown): string {
  const response = error as { data?: { msg?: string }; response?: { data?: { msg?: string } }; message?: string };
  return response?.data?.msg ?? response?.response?.data?.msg ?? response?.message ?? '操作失败，请刷新后重试';
}
