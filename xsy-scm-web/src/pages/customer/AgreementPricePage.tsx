import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Form, Input, Popconfirm, Space, Typography } from 'antd';
import { useMemo, useRef, useState } from 'react';
import { deleteAgreementPrice, fetchAgreementPrices, fetchCustomers } from '../../api/customers';
import { ApiError } from '../../api/http';
import { AUTHORITIES } from '../../auth/authorities';
import { Permission } from '../../auth/Permission';
import { AmountText } from '../../components/common/AmountText';
import { PageContainer } from '../../components/common/PageContainer';
import type { AgreementPrice } from '../../types/sales';
import { AgreementPriceDrawer } from './AgreementPriceDrawer';
import styles from './Customer.module.css';

interface Filters { keyword?: string; }
export function AgreementPricePage() {
  const [form] = Form.useForm<Filters>(); const actionRef = useRef<ActionType>(null);
  const customers = useQuery({ queryKey: ['agreement-customers'], queryFn: () => fetchCustomers({ page: 1, pageSize: 100 }) });
  const [record, setRecord] = useState<AgreementPrice | null>(null); const [open, setOpen] = useState(false); const [loadError, setLoadError] = useState(false); const [pendingId, setPendingId] = useState<number | null>(null); const [mutationError, setMutationError] = useState<string | null>(null);
  const customerById = useMemo(() => new Map((customers.data?.records ?? []).map((c) => [c.id, c])), [customers.data]);
  async function remove(item: AgreementPrice) { setPendingId(item.id); setMutationError(null); try { await deleteAgreementPrice(item.id, item.version); await actionRef.current?.reload(); } catch (e) { setMutationError(e instanceof ApiError && e.status === 409 ? '协议价已被更新，请重新加载后再操作' : e instanceof Error ? e.message : '删除失败'); } finally { setPendingId(null); } }
  const columns: ProColumns<AgreementPrice>[] = [
    { title: '客户', width: 190, fixed: 'left', render: (_, item) => customerById.get(item.customerId)?.name ?? `客户 #${item.customerId}` },
    { title: '客户编码', width: 150, render: (_, item) => customerById.get(item.customerId)?.customerCode ?? '--' }, { title: 'SKU ID', dataIndex: 'skuId', width: 110 },
    { title: '协议单价', dataIndex: 'unitPrice', width: 140, align: 'right', render: (_, item) => <AmountText value={item.unitPrice} /> },
    { title: '生效时间', dataIndex: 'effectiveFrom', width: 190, render: (_, item) => new Date(item.effectiveFrom).toLocaleString('zh-CN', { hour12: false }) },
    { title: '结束时间', dataIndex: 'effectiveTo', width: 190, render: (_, item) => item.effectiveTo ? new Date(item.effectiveTo).toLocaleString('zh-CN', { hour12: false }) : '长期有效' },
    { title: '操作', valueType: 'option', fixed: 'right', width: 140, render: (_, item) => [
      <Permission key="edit" authority={AUTHORITIES.customerManage}>
        <Button size="small" type="link" onClick={() => { setRecord(item); setOpen(true); }}>编辑</Button>
      </Permission>,
      <Permission key="delete" authority={AUTHORITIES.customerManage}>
        <Popconfirm title="确认删除该协议价？" okText="删除" okButtonProps={{ danger: true }} onConfirm={() => remove(item)}><Button danger size="small" type="link" loading={pendingId === item.id}>删除</Button></Popconfirm>
      </Permission>,
    ] },
  ];
  return <PageContainer>
    <div className={styles.pageTitle}><Typography.Title level={4}>客户协议价</Typography.Title><Typography.Text type="secondary">协议价优先于市场价，有效期采用半开区间</Typography.Text></div>
    <Form form={form} layout="inline" className={styles.filters}><Form.Item label="搜索" name="keyword"><Input allowClear prefix={<SearchOutlined />} placeholder="客户名称 / 编码 / SKU" /></Form.Item><Form.Item className={styles.filterActions}><Space><Button type="primary" onClick={() => actionRef.current?.reloadAndRest?.()}>查询</Button><Button onClick={() => { form.resetFields(); actionRef.current?.reloadAndRest?.(); }}>重置</Button></Space></Form.Item></Form>
    <div className={styles.toolbar}><Permission authority={AUTHORITIES.customerManage}><Button type="primary" icon={<PlusOutlined />} onClick={() => { setRecord(null); setOpen(true); }}>新增协议价</Button></Permission></div>
    {loadError ? <Alert className={styles.error} type="error" showIcon message="协议价数据加载失败" action={<Button size="small" onClick={() => { setLoadError(false); actionRef.current?.reload(); }}>重新加载</Button>} /> : null}
    {mutationError ? <Alert closable className={styles.error} type="error" showIcon message={mutationError} onClose={() => setMutationError(null)} /> : null}
    <ProTable<AgreementPrice> actionRef={actionRef} columns={columns} className={styles.table} options={false} search={false} rowKey="id" scroll={{ x: 1100 }} pagination={{ defaultPageSize: 20, showSizeChanger: true, showTotal: (n) => `共 ${n} 条` }} request={async (params) => { const values = form.getFieldsValue(); try { const data = await fetchAgreementPrices({ page: params.current ?? 1, pageSize: params.pageSize ?? 20, keyword: values.keyword?.trim() || undefined }); setLoadError(false); return { data: data.records, success: true, total: data.total }; } catch { setLoadError(true); return { data: [], success: true, total: 0 }; } }} locale={{ emptyText: '暂无协议价，点击“新增协议价”开始维护' }} />
    <AgreementPriceDrawer open={open} record={record} onOpenChange={setOpen} onSaved={() => actionRef.current?.reload()} />
  </PageContainer>;
}
