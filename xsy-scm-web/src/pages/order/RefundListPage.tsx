import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { Alert, Button, Modal, Select } from 'antd';
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchRefunds } from '../../api/afterSales';
import { AUTHORITIES } from '../../auth/authorities';
import { Permission } from '../../auth/Permission';
import { AmountText } from '../../components/common/AmountText';
import { PageContainer } from '../../components/common/PageContainer';
import { StatusTag } from '../../components/common/StatusTag';
import type { Refund, RefundStatus } from '../../types/sales';
import { RefundCompleteModal } from './RefundCompleteModal';
import styles from './Sales.module.css';

export function RefundListPage() {
  const navigate = useNavigate();
  const actionRef = useRef<ActionType>(null);
  const [status, setStatus] = useState<RefundStatus>();
  const [loadError, setLoadError] = useState<string | null>(null);
  const [selectedRefund, setSelectedRefund] = useState<Refund | null>(null);

  const columns: ProColumns<Refund>[] = [
    { title: '退款单号', dataIndex: 'refundNo', width: 210, render: (_, row) => <Button type="link" onClick={() => navigate(`/order-refunds/${row.id}`)}>{row.refundNo}</Button> },
    { title: '退货单 ID', dataIndex: 'returnId', width: 130 },
    { title: '退款金额', dataIndex: 'refundAmount', width: 160, align: 'right', render: (_, row) => <AmountText value={row.refundAmount} /> },
    { title: '外部凭证', dataIndex: 'externalReference', render: (_, row) => row.externalReference || '--' },
    { title: '状态', dataIndex: 'status', width: 110, align: 'center', render: (_, row) => <StatusTag status={row.status} /> },
    { title: '操作', valueType: 'option', width: 120, render: (_, row) => row.status === 'PENDING'
      ? <Permission authority={AUTHORITIES.orderManage}><Button type="link" onClick={() => setSelectedRefund(row)}>完成退款</Button></Permission>
      : <Button type="link" onClick={() => Modal.info({ title: row.refundNo, content: <><p>退货单：{row.returnId}</p><p>金额：¥ {row.refundAmount}</p><p>外部凭证：{row.externalReference || '--'}</p></> })}>详情</Button> },
  ];

  return <PageContainer>
    <div className={styles.toolbar}>
      <Select allowClear value={status} placeholder="全部状态" options={[{ value: 'PENDING', label: '待退款' }, { value: 'COMPLETED', label: '已完成' }]} onChange={setStatus} />
      <Button type="primary" onClick={() => actionRef.current?.reload()}>查询</Button>
    </div>
    {loadError ? <Alert className={styles.error} type="error" showIcon message={loadError} action={<Button onClick={() => actionRef.current?.reload()}>重新加载</Button>} /> : null}
    <ProTable actionRef={actionRef} rowKey="id" search={false} options={false} columns={columns} pagination={{ defaultPageSize: 20, showSizeChanger: true }} request={async (params) => {
      try {
        const data = await fetchRefunds({ page: params.current ?? 1, pageSize: params.pageSize ?? 20, status });
        setLoadError(null);
        return { data: data.records, total: data.total, success: true };
      } catch {
        setLoadError('退款记录加载失败');
        return { data: [], total: 0, success: true };
      }
    }} />
    <RefundCompleteModal refund={selectedRefund} onOpenChange={(open) => { if (!open) setSelectedRefund(null); }} onCompleted={() => actionRef.current?.reload() ?? Promise.resolve()} />
  </PageContainer>;
}
