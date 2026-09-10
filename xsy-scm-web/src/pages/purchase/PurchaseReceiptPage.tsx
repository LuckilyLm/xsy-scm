import { Alert, Button, Input, Space, Table, message } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { confirmReceipt, fetchReceipt, fetchReceiptConfirmations, fetchReceiptItems } from '../../api/purchases';
import { AUTHORITIES } from '../../auth/authorities';
import { Permission } from '../../auth/Permission';
import { PageContainer } from '../../components/common/PageContainer';
import { toReceiptConfirmPayload, type ReceiptInput } from './receiptFormModel';

export function PurchaseReceiptPage() {
  const id = Number(useParams().id);
  const [inputs, setInputs] = useState<Record<number, ReceiptInput>>({});
  const [submitting, setSubmitting] = useState(false);
  const receipt = useQuery({ queryKey: ['receipt', id], queryFn: () => fetchReceipt(id) });
  const items = useQuery({ queryKey: ['receipt-items', id], queryFn: () => fetchReceiptItems(id) });
  const confirmations = useQuery({ queryKey: ['receipt-confirmations', id], queryFn: () => fetchReceiptConfirmations(id) });

  if (receipt.isError || items.isError || confirmations.isError) {
    return (
      <Alert
        type="error"
        message="收货单加载失败"
        action={<Button onClick={() => { receipt.refetch(); items.refetch(); confirmations.refetch(); }}>重试</Button>}
      />
    );
  }

  const submit = async () => {
    if (!receipt.data || !items.data) return;
    const payload = toReceiptConfirmPayload(receipt.data.version, items.data, inputs);
    if (!payload.items.length) {
      message.warning('请输入有效的本次收货数量');
      return;
    }
    setSubmitting(true);
    try {
      await confirmReceipt(id, payload, crypto.randomUUID());
      message.success('本次收货确认成功');
      setInputs({});
      await Promise.all([receipt.refetch(), items.refetch(), confirmations.refetch()]);
    } catch {
      message.error('确认失败；如数据已被修改，请保留输入并刷新最新数据后重试');
    } finally {
      setSubmitting(false);
    }
  };

  const readOnly = receipt.data?.status === 'CONFIRMED';
  return (
    <PageContainer>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Alert type="info" showIcon message={`收货单状态：${receipt.data?.status ?? '加载中'}`} />
        <Table
          loading={items.isLoading}
          rowKey="id"
          pagination={false}
          dataSource={items.data ?? []}
          columns={[
            { title: 'SKU', dataIndex: 'skuCodeSnapshot' },
            { title: '商品', dataIndex: 'productNameSnapshot' },
            { title: '历史已收', dataIndex: 'receivedQuantity', align: 'right' },
            {
              title: '本次数量',
              render: (_, row) => (
                <Input
                  value={inputs[row.id]?.receivedQuantity}
                  disabled={readOnly}
                  onChange={(event) => setInputs((current) => ({
                    ...current,
                    [row.id]: { ...current[row.id], receivedQuantity: event.target.value },
                  }))}
                />
              ),
            },
            {
              title: '实际重量',
              render: (_, row) => row.productTypeSnapshot === 'NON_STANDARD' ? (
                <Input
                  value={inputs[row.id]?.actualWeight}
                  disabled={readOnly}
                  onChange={(event) => setInputs((current) => ({
                    ...current,
                    [row.id]: { ...current[row.id], actualWeight: event.target.value },
                  }))}
                />
              ) : null,
            },
            {
              title: '人工修正原因',
              render: (_, row) => row.productTypeSnapshot === 'NON_STANDARD' ? (
                <Input
                  value={inputs[row.id]?.correctionReason}
                  disabled={readOnly}
                  onChange={(event) => setInputs((current) => ({
                    ...current,
                    [row.id]: { ...current[row.id], correctionReason: event.target.value },
                  }))}
                />
              ) : null,
            },
          ]}
        />
        <Permission authority={AUTHORITIES.purchaseManage}>
          <Button type="primary" loading={submitting} disabled={readOnly} onClick={submit}>确认本次收货</Button>
        </Permission>
        <Table
          title={() => '确认历史'}
          loading={confirmations.isLoading}
          rowKey="id"
          pagination={false}
          dataSource={confirmations.data ?? []}
          locale={{ emptyText: '暂无确认批次' }}
          columns={[
            { title: '确认批次号', dataIndex: 'confirmationNo' },
            { title: '确认数量', dataIndex: 'totalQuantity', align: 'right' },
            { title: '操作人', dataIndex: 'operator' },
            { title: '确认时间', dataIndex: 'confirmedAt' },
          ]}
        />
      </Space>
    </PageContainer>
  );
}
