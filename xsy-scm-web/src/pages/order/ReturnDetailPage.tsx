import { ArrowLeftOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Descriptions, Input, Modal, Space, Spin, Table, Typography, message } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { approveReturn, cancelReturn, fetchReturn, rejectReturn } from '../../api/afterSales';
import { ApiError } from '../../api/http';
import { AUTHORITIES } from '../../auth/authorities';
import { Permission } from '../../auth/Permission';
import { PageContainer } from '../../components/common/PageContainer';
import { StatusTag } from '../../components/common/StatusTag';
import type { ReturnItem } from '../../types/sales';
import { createApprovalDraft, toApproveReturnPayload, updateApprovalQuantity, validateApprovalDraft } from './returnApprovalModel';
import type { ReturnApprovalDraft } from './returnApprovalModel';
import styles from './Sales.module.css';

const key = () => `${Date.now()}-${Math.random().toString(36).slice(2)}`;

export function ReturnDetailPage() {
  const { id } = useParams();
  const returnId = Number(id);
  const navigate = useNavigate();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [approvalDraft, setApprovalDraft] = useState<ReturnApprovalDraft>([]);
  const requestKey = useRef(key());
  const query = useQuery({ queryKey: ['return', returnId], queryFn: () => fetchReturn(returnId) });
  const data = query.data;

  useEffect(() => {
    if (data?.status === 'PENDING') setApprovalDraft(createApprovalDraft(data));
  }, [data]);

  async function act(action: () => Promise<void>, successText: string) {
    if (pending) return;
    setPending(true);
    setError(null);
    try {
      await action();
      message.success(successText);
      await query.refetch();
      requestKey.current = key();
    } catch (cause) {
      setError(cause instanceof ApiError && cause.status === 409
        ? '退货申请已被其他人处理，请重新加载'
        : cause instanceof Error ? cause.message : '操作失败，请重试');
      requestKey.current = key();
    } finally {
      setPending(false);
    }
  }

  function withReason(kind: 'reject' | 'cancel') {
    let reason = '';
    Modal.confirm({
      title: kind === 'reject' ? '驳回退货申请' : '取消退货申请',
      content: <Input.TextArea placeholder="请输入原因" onChange={(event) => { reason = event.target.value; }} />,
      okButtonProps: { danger: true },
      onOk: async () => {
        if (!reason.trim()) throw new Error('请输入原因');
        await act(
          () => kind === 'reject'
            ? rejectReturn(returnId, data!.version, reason.trim(), requestKey.current)
            : cancelReturn(returnId, data!.version, reason.trim(), requestKey.current),
          kind === 'reject' ? '退货申请已驳回' : '退货申请已取消',
        );
      },
    });
  }

  function approve() {
    if (!data) return;
    const validation = validateApprovalDraft(approvalDraft);
    if (validation) {
      setError(validation);
      return;
    }
    void act(
      () => approveReturn(returnId, toApproveReturnPayload(data.version, approvalDraft), requestKey.current),
      '退货已批准，退款记录已生成',
    );
  }

  if (query.isLoading) return <PageContainer><Spin /></PageContainer>;
  if (query.isError || !data) return <PageContainer><Alert type="error" message="退货详情加载失败" action={<Button onClick={() => query.refetch()}>重试</Button>} /></PageContainer>;

  return <PageContainer>
    <div className={styles.header}>
      <Space><Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/order-returns')}>返回列表</Button><Typography.Title level={4} className={styles.title}>{data.returnNo}</Typography.Title><StatusTag status={data.status} /></Space>
      {data.status === 'PENDING' ? <Permission authority={AUTHORITIES.orderManage}><Space><Button danger disabled={pending} onClick={() => withReason('cancel')}>取消申请</Button><Button danger disabled={pending} onClick={() => withReason('reject')}>驳回</Button><Button type="primary" loading={pending} disabled={pending} onClick={approve}>批准退货</Button></Space></Permission> : null}
    </div>
    {error ? <Alert className={styles.error} type="error" message={error} showIcon action={<Button onClick={() => query.refetch()}>重新加载</Button>} /> : null}
    <Descriptions bordered size="small" items={[
      { key: 'order', label: '原订单 ID', children: <Button type="link" onClick={() => navigate(`/orders/${data.orderId}`)}>{data.orderId}</Button> },
      { key: 'amount', label: '批准/预计金额', children: `¥ ${data.approvedAmount}` },
      { key: 'version', label: '版本', children: data.version },
      { key: 'reason', label: '退货原因', children: data.reason, span: 3 },
    ]} />
    <Table style={{ marginTop: 16 }} pagination={false} rowKey="id" dataSource={data.items} columns={[
      { title: '原订单行 ID', dataIndex: 'orderItemId' },
      { title: '申请数量', dataIndex: 'requestedQuantity', align: 'right' },
      { title: '批准数量', dataIndex: 'approvedQuantity', align: 'right', render: (_, row: ReturnItem) => data.status === 'PENDING'
        ? <Permission authority={AUTHORITIES.orderManage} fallback={<span>{row.approvedQuantity ?? '--'}</span>}>
          <Input aria-label={`订单行 ${row.orderItemId} 批准数量`} inputMode="decimal" value={approvalDraft.find((item) => item.returnItemId === row.id)?.approvedQuantity ?? ''} onChange={(event) => setApprovalDraft((current) => updateApprovalQuantity(current, row.id, event.target.value))} />
        </Permission>
        : row.approvedQuantity ?? '--' },
      { title: '锁定单价', dataIndex: 'lockedUnitPrice', align: 'right' },
      { title: '批准金额', dataIndex: 'approvedAmount', align: 'right' },
    ]} />
  </PageContainer>;
}
