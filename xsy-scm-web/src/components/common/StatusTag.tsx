import { Tag } from 'antd';
import type { ShelfStatus } from '../../types/product';

export function StatusTag({ status }: { status: ShelfStatus }) {
  return status === 'ON_SHELF' ? (
    <Tag color="success">已上架</Tag>
  ) : (
    <Tag>已下架</Tag>
  );
}
