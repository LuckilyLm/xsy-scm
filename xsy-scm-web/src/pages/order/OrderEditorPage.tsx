import { ArrowLeftOutlined, PlusOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Form, Input, InputNumber, Modal, Select, Space, Spin, Table, Typography, message } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { fetchCustomers, fetchCustomerSkus } from '../../api/customers';
import { ApiError } from '../../api/http';
import { createOrder, fetchOrder, submitOrder, updateOrder } from '../../api/orders';
import { PageContainer } from '../../components/common/PageContainer';
import { StatusTag } from '../../components/common/StatusTag';
import { addOrderItem, createEmptyOrderForm, orderDetailToForm, removeOrderItem, setManualPrice, toOrderPayload, updateOrderItem, validateOrderForm } from './orderFormModel';
import type { OrderForm } from './orderFormModel';
import styles from './Sales.module.css';

const key = () => `${Date.now()}-${Math.random().toString(36).slice(2)}`;

export function OrderEditorPage() {
  const navigate = useNavigate();
  const { id } = useParams();
  const orderId = id ? Number(id) : null;
  const [params] = useSearchParams();
  const [form, setForm] = useState<OrderForm>(() => createEmptyOrderForm(params.get('source') === 'SUPPLEMENT' ? 'SUPPLEMENT' : 'NORMAL'));
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<'save' | 'submit' | null>(null);
  const submitKey = useRef(key());
  const detail = useQuery({ queryKey: ['order', orderId], queryFn: () => fetchOrder(orderId!), enabled: orderId !== null });
  const customers = useQuery({ queryKey: ['customers', 'order-select'], queryFn: () => fetchCustomers({ page: 1, pageSize: 100, status: 'ENABLED' }) });
  const skus = useQuery({ queryKey: ['customer-skus', form.customerId], queryFn: () => fetchCustomerSkus(form.customerId!), enabled: form.customerId !== null });

  useEffect(() => { if (detail.data) setForm(orderDetailToForm(detail.data)); }, [detail.data]);
  const editable = !detail.data || detail.data.status === 'DRAFT';
  const skuOptions = useMemo(() => (skus.data ?? []).map((sku) => ({ label: `${sku.productName} · ${sku.skuCode} · ${sku.saleUnit}`, value: sku.skuId })), [skus.data]);

  async function save() {
    if (pending) return null;
    const validation = validateOrderForm(form);
    if (validation) { setError(validation); return null; }
    setPending('save'); setError(null);
    try {
      const payload = toOrderPayload(form);
      if (orderId === null) {
        const newId = await createOrder(payload, key());
        message.success('草稿已保存');
        navigate(`/orders/${newId}/edit`, { replace: true });
        return newId;
      }
      await updateOrder(orderId, payload);
      await detail.refetch();
      message.success('草稿已保存');
      return orderId;
    } catch (cause) {
      setError(cause instanceof ApiError && cause.status === 409 ? '订单已被其他人修改，请重新加载后再编辑' : cause instanceof Error ? cause.message : '保存失败，请重试');
      return null;
    } finally { setPending(null); }
  }

  async function submit() {
    if (pending) return;
    const savedId = await save();
    if (savedId === null) return;
    Modal.confirm({
      title: '提交订单并锁定价格？',
      content: '提交时服务端将重新校验商品与客户定价，非人工改价行会刷新为当前权威价格，提交后不可编辑。',
      okText: '确认提交', cancelText: '返回检查',
      onOk: async () => {
        if (pending) return;
        setPending('submit'); setError(null);
        try {
          const latest = await fetchOrder(savedId);
          await submitOrder(savedId, latest.version, submitKey.current);
          message.success('订单已提交，价格已锁定');
          navigate(`/orders/${savedId}`);
        } catch (cause) {
          setError(cause instanceof ApiError && cause.status === 409 ? '提交冲突：订单或幂等请求已变化，请重新加载' : cause instanceof Error ? cause.message : '提交失败，请重试');
          submitKey.current = key();
        } finally { setPending(null); }
      },
    });
  }

  if (orderId !== null && detail.isLoading) return <PageContainer><Spin tip="订单加载中…" /></PageContainer>;
  if (detail.isError) return <PageContainer><Alert type="error" showIcon message="订单加载失败" action={<Button onClick={() => detail.refetch()}>重试</Button>} /></PageContainer>;

  return <PageContainer>
    <div className={styles.page}>
    <div className={styles.header}><Space><Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/orders')}>返回</Button><Typography.Title level={4} className={styles.title}>{orderId === null ? (form.source === 'SUPPLEMENT' ? '新建补单' : '新建销售订单') : `编辑订单 ${detail.data?.orderNo ?? ''}`}</Typography.Title>{detail.data ? <StatusTag status={detail.data.status} /> : null}</Space></div>
    {error ? <Alert className={styles.error} type="error" showIcon message={error} action={error.includes('修改') ? <Button onClick={() => detail.refetch()}>重新加载</Button> : undefined} /> : null}
    <section className={styles.panel}><h3>订单信息</h3><Form component={false} layout="vertical"><div className={styles.grid}>
      <Form.Item label="客户" required><Select showSearch disabled={!editable || form.items.length > 0} loading={customers.isLoading} placeholder="选择启用客户" options={(customers.data?.records ?? []).map((c) => ({ value: c.id, label: `${c.name} · ${c.customerCode}` }))} value={form.customerId} onChange={(customerId) => setForm((current) => ({ ...current, customerId, items: [] }))} /></Form.Item>
      <Form.Item label="订单类型"><Select disabled={!editable} value={form.source} options={[{ value:'NORMAL',label:'普通订单' },{ value:'SUPPLEMENT',label:'补单' }]} onChange={(source) => setForm((current) => ({ ...current, source }))} /></Form.Item>
      {form.source === 'SUPPLEMENT' ? <><Form.Item label="原订单 ID（选填）"><InputNumber disabled={!editable} min={1} precision={0} value={form.originalOrderId} onChange={(value) => setForm((current) => ({ ...current, originalOrderId: value }))} /></Form.Item><Form.Item className={styles.full} label="补单原因" required><Input.TextArea disabled={!editable} value={form.supplementReason} onChange={(event) => setForm((current) => ({ ...current, supplementReason: event.target.value }))} /></Form.Item></> : null}
    </div></Form></section>
    <section className={styles.panel}><div className={styles.toolbar}><div><h3>订单商品</h3><div className={styles.note}>价格由服务端解析；人工改价必须留痕，提交后展示锁定价。</div></div>{editable ? <Select aria-label="添加商品" showSearch loading={skus.isLoading} disabled={!form.customerId} value={null} placeholder="选择客户可下单商品" options={skuOptions} suffixIcon={<PlusOutlined />} style={{ width: 340 }} onChange={(skuId) => { const sku = skus.data?.find((entry) => entry.skuId === skuId); if (sku) setForm((current) => addOrderItem(current, sku)); }} /> : null}</div>
      <Table pagination={false} rowKey="key" locale={{ emptyText: form.customerId ? '暂无商品，请从右上角添加' : '请先选择客户' }} dataSource={form.items} scroll={{ x: 1150 }} columns={[
        { title:'商品', width:200, render:(_, row) => <><strong>{row.productName}</strong><div className={styles.note}>{row.skuCode} {row.specName}</div></> },
        { title:'类型', width:90, render:(_, row) => row.productType === 'STANDARD' ? '标品' : '非标品' },
        { title:'订购数量', width:150, render:(_, row) => editable ? <Input value={row.orderedQuantity} suffix={row.saleUnit} onChange={(e) => setForm((current) => updateOrderItem(current,row.key,{orderedQuantity:e.target.value}))} /> : `${row.orderedQuantity} ${row.saleUnit}` },
        { title:'单价', width:150, align:'right', render:(_, row) => editable ? <Input value={row.unitPrice} prefix="¥" onChange={(e) => setForm((current) => setManualPrice(current,row.key,e.target.value,row.overrideReason ?? ''))} /> : <span className={row.lockedUnitPrice ? styles.locked : ''}>¥ {row.lockedUnitPrice ?? row.unitPrice}</span> },
        { title:'价格来源', width:110, render:(_, row) => <StatusTag status={row.priceSource} /> },
        { title:'改价原因', width:220, render:(_, row) => row.priceSource === 'OVERRIDE' && editable ? <Input placeholder="必填" value={row.overrideReason ?? ''} onChange={(e) => setForm((current) => setManualPrice(current,row.key,row.unitPrice,e.target.value))} /> : row.overrideReason || '--' },
        { title:'实数/重量', width:130, align:'right', render:(_, row) => row.actualQuantity ? `${row.actualQuantity} ${row.saleUnit}` : <Typography.Text type="secondary">待录入</Typography.Text> },
        { title:'金额', width:130, align:'right', render:(_, row) => row.amount ? `¥ ${row.amount}` : '--' },
        ...(editable ? [{ title:'操作', width:70, fixed:'right' as const, render:(_: unknown, row: OrderForm['items'][number]) => <Button danger type="link" onClick={() => setForm((current) => removeOrderItem(current,row.key))}>移除</Button> }] : []),
      ]} />
    </section>
    {editable ? <div className={styles.actions}><Space><Button disabled={Boolean(pending)} onClick={() => navigate('/orders')}>取消</Button><Button loading={pending === 'save'} disabled={Boolean(pending)} onClick={save}>保存草稿</Button><Button type="primary" loading={pending === 'submit'} disabled={Boolean(pending)} onClick={submit}>保存并提交</Button></Space></div> : null}
    </div>
  </PageContainer>;
}
