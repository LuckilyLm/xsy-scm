import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Form, Input, Popconfirm, Select, Space, Typography } from 'antd';
import { useRef, useState } from 'react';
import { deleteCustomer, fetchCustomers, fetchCustomerTypes, updateCustomerStatus } from '../../api/customers';
import { ApiError } from '../../api/http';
import { PageContainer } from '../../components/common/PageContainer';
import { StatusTag } from '../../components/common/StatusTag';
import type { CustomerSummary } from '../../types/sales';
import { CustomerDrawer } from './CustomerDrawer';
import styles from './Customer.module.css';

interface Filters { keyword?: string; customerTypeId?: number; }
export function CustomerPage() {
  const [form] = Form.useForm<Filters>(); const actionRef = useRef<ActionType>(null);
  const [loadError, setLoadError] = useState(false); const [mutationError, setMutationError] = useState<string | null>(null);
  const [pendingId, setPendingId] = useState<number | null>(null); const [drawerId, setDrawerId] = useState<number | null>(null); const [open, setOpen] = useState(false);
  const types = useQuery({ queryKey: ['customer-types'], queryFn: fetchCustomerTypes });
  async function mutate(id: number, action: () => Promise<void>) { setPendingId(id); setMutationError(null); try { await action(); await actionRef.current?.reload(); } catch (e) { setMutationError(e instanceof ApiError && e.status === 409 ? '客户数据已更新，请重新加载后再操作' : e instanceof Error ? e.message : '操作失败'); } finally { setPendingId(null); } }
  const columns: ProColumns<CustomerSummary>[] = [
    { title: '客户名称', dataIndex: 'name', fixed: 'left', width: 180, render: (_, r) => <Button type="link" className={styles.nameButton} onClick={() => { setDrawerId(r.id); setOpen(true); }}>{r.name}</Button> },
    { title: '客户编码', dataIndex: 'customerCode', width: 150 }, { title: '客户类型', dataIndex: 'customerTypeName', width: 140 },
    { title: '可见范围', dataIndex: 'visibilityPolicy', width: 150, render: (_, r) => r.visibilityPolicy === 'ALL_ENABLED' ? '全部在售 SKU' : '白名单 SKU' },
    { title: '状态', dataIndex: 'status', width: 100, align: 'center', render: (_, r) => <StatusTag status={r.status} /> },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (_, r) => new Date(r.updatedAt).toLocaleString('zh-CN', { hour12: false }) },
    { title: '操作', valueType: 'option', fixed: 'right', width: 210, render: (_, r) => [
      <Button key="edit" type="link" size="small" onClick={() => { setDrawerId(r.id); setOpen(true); }}>编辑</Button>,
      <Popconfirm key="status" title={`确认${r.status === 'ENABLED' ? '停用' : '启用'}该客户？`} onConfirm={() => mutate(r.id, () => updateCustomerStatus(r.id, r.version, r.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'))}><Button type="link" size="small" loading={pendingId === r.id}>{r.status === 'ENABLED' ? '停用' : '启用'}</Button></Popconfirm>,
      <Popconfirm key="delete" title="确认删除该客户？" okText="删除" okButtonProps={{ danger: true }} onConfirm={() => mutate(r.id, () => deleteCustomer(r.id, r.version))}><Button danger type="link" size="small" loading={pendingId === r.id}>删除</Button></Popconfirm>,
    ] },
  ];
  return <PageContainer>
    <div className={styles.pageTitle}><Typography.Title level={4}>客户档案</Typography.Title><Typography.Text type="secondary">维护客户状态与可下单 SKU 范围</Typography.Text></div>
    <Form form={form} layout="inline" className={styles.filters}><Form.Item label="搜索" name="keyword"><Input allowClear prefix={<SearchOutlined />} placeholder="客户名称 / 客户编码" /></Form.Item><Form.Item label="客户类型" name="customerTypeId"><Select allowClear placeholder="全部类型" options={(types.data ?? []).map((t) => ({ value: t.id, label: t.name }))} /></Form.Item><Form.Item className={styles.filterActions}><Space><Button type="primary" onClick={() => actionRef.current?.reloadAndRest?.()}>查询</Button><Button onClick={() => { form.resetFields(); actionRef.current?.reloadAndRest?.(); }}>重置</Button></Space></Form.Item></Form>
    <div className={styles.toolbar}><Button type="primary" icon={<PlusOutlined />} onClick={() => { setDrawerId(null); setOpen(true); }}>新增客户</Button></div>
    {loadError ? <Alert className={styles.error} type="error" showIcon message="客户数据加载失败" action={<Button size="small" onClick={() => { setLoadError(false); actionRef.current?.reload(); }}>重新加载</Button>} /> : null}
    {mutationError ? <Alert closable className={styles.error} type="error" showIcon message={mutationError} onClose={() => setMutationError(null)} /> : null}
    <ProTable<CustomerSummary> actionRef={actionRef} columns={columns} className={styles.table} options={false} search={false} rowKey="id" scroll={{ x: 1100 }} pagination={{ defaultPageSize: 20, showSizeChanger: true, showTotal: (n) => `共 ${n} 条` }} request={async (params) => { const v = form.getFieldsValue(); try { const data = await fetchCustomers({ page: params.current ?? 1, pageSize: params.pageSize ?? 20, keyword: v.keyword?.trim() || undefined, customerTypeId: v.customerTypeId }); setLoadError(false); return { data: data.records, success: true, total: data.total }; } catch { setLoadError(true); return { data: [], success: true, total: 0 }; } }} />
    <CustomerDrawer open={open} customerId={drawerId} onOpenChange={setOpen} onSaved={() => actionRef.current?.reload()} />
  </PageContainer>;
}
