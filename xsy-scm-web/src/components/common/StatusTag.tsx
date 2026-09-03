import { Tag } from 'antd';
import type { ReactNode } from 'react';
import type { CustomerStatus, OrderStatus, PriceSource, RefundStatus, ReturnStatus } from '../../types/sales';
import type { ShelfStatus } from '../../types/product';

type Status = ShelfStatus | CustomerStatus | OrderStatus | ReturnStatus | RefundStatus | PriceSource;
const labels: Record<Status, string> = {
  ON_SHELF: '已上架', OFF_SHELF: '已下架', ENABLED: '启用', DISABLED: '停用',
  DRAFT: '草稿', PENDING: '待处理', CONFIRMED: '已确认', CANCELLED: '已取消',
  APPROVED: '已批准', REJECTED: '已驳回', COMPLETED: '已完成', AGREEMENT: '协议价', MARKET: '市场价', OVERRIDE: '人工改价',
};
const colors: Partial<Record<Status, string>> = { ON_SHELF: 'success', ENABLED: 'success', CONFIRMED: 'success', APPROVED: 'success', COMPLETED: 'success', REJECTED: 'error', CANCELLED: 'default', OVERRIDE: 'warning' };
export function StatusTag({ status, label, icon }: { status: Status; label?: ReactNode; icon?: ReactNode }) {
  return <Tag color={colors[status]} icon={icon}>{label ?? labels[status]}</Tag>;
}
