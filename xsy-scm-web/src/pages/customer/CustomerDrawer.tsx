import { DrawerForm } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Form, Input, Radio, Select, Skeleton } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { createCustomer, fetchCustomer, fetchCustomerTypes, fetchSkuCatalog, updateCustomer } from '../../api/customers';
import { ApiError } from '../../api/http';
import type { CustomerStatus, VisibilityPolicy } from '../../types/sales';
import { createEmptyCustomerForm, customerDetailToForm, normalizeCustomerPayload } from './customerFormModel';
import type { CustomerForm } from './customerFormModel';
import styles from './Customer.module.css';

interface Props { open: boolean; customerId: number | null; onOpenChange: (open: boolean) => void; onSaved: () => void; }

export function CustomerDrawer({ open, customerId, onOpenChange, onSaved }: Props) {
  const [value, setValue] = useState<CustomerForm>(createEmptyCustomerForm);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const types = useQuery({ queryKey: ['customer-types'], queryFn: fetchCustomerTypes, enabled: open });
  const products = useQuery({ queryKey: ['customer-sku-catalog'], queryFn: () => fetchSkuCatalog({ page: 1, pageSize: 100 }), enabled: open });
  const detail = useQuery({ queryKey: ['customer', customerId], queryFn: () => fetchCustomer(customerId!), enabled: open && customerId !== null });
  useEffect(() => {
    if (!open) return;
    setError(null);
    if (customerId === null) setValue(createEmptyCustomerForm());
    else if (detail.data) setValue(customerDetailToForm(detail.data));
  }, [open, customerId, detail.data]);
  const skuOptions = useMemo(() => (products.data?.records ?? []).flatMap((p) => p.skus.map((sku) => ({
    value: sku.id, label: `${p.name} / ${sku.specName}（${sku.skuCode}）`, disabled: p.status !== 'ON_SHELF' || sku.status !== 'ON_SHELF',
  }))), [products.data]);
  function field<K extends keyof CustomerForm>(key: K, next: CustomerForm[K]) { setValue((current) => ({ ...current, [key]: next })); }
  async function submit() {
    if (!value.customerCode.trim() || !value.name.trim() || value.customerTypeId === null) { setError('请完整填写客户编码、名称和类型'); return false; }
    if (value.visibilityPolicy === 'ALLOWLIST' && value.visibilitySkuIds.length === 0) { setError('白名单策略至少选择一个 SKU'); return false; }
    setPending(true); setError(null);
    try {
      const payload = normalizeCustomerPayload(value);
      if (customerId === null) await createCustomer(payload); else await updateCustomer(customerId, payload);
      onSaved(); onOpenChange(false); return true;
    } catch (caught) {
      setError(caught instanceof ApiError && caught.status === 409 ? '客户资料已被其他人修改，请重新加载后再保存' : caught instanceof Error ? caught.message : '保存失败');
      return false;
    } finally { setPending(false); }
  }
  return <DrawerForm open={open} title={customerId === null ? '新增客户' : '编辑客户'} width={720} onFinish={submit}
    onOpenChange={(next) => { if (!pending) onOpenChange(next); }} drawerProps={{ destroyOnClose: true, maskClosable: !pending }}
    submitter={{ searchConfig: { submitText: '保存客户', resetText: '取消' }, submitButtonProps: { loading: pending, disabled: pending }, resetButtonProps: { disabled: pending } }}>
    {customerId !== null && detail.isLoading ? <Skeleton active /> : detail.isError ? <Alert type="error" showIcon message="客户详情加载失败" action={<Button size="small" onClick={() => detail.refetch()}>重新加载</Button>} /> :
      <div className={styles.drawerBody}>
        {error ? <Alert type="error" showIcon message={error} /> : null}
        <Form layout="vertical" className={styles.formGrid} component={false}>
          <Form.Item label="客户编码" required><Input aria-label="客户编码" value={value.customerCode} onChange={(e) => field('customerCode', e.target.value)} /></Form.Item>
          <Form.Item label="客户名称" required><Input aria-label="客户名称" value={value.name} onChange={(e) => field('name', e.target.value)} /></Form.Item>
          <Form.Item label="客户类型" required><Select aria-label="客户类型" loading={types.isLoading} options={(types.data ?? []).map((t) => ({ value: t.id, label: t.name, disabled: t.status !== 'ENABLED' }))} value={value.customerTypeId} onChange={(id) => field('customerTypeId', id)} /></Form.Item>
          <Form.Item label="客户状态"><Radio.Group value={value.status} onChange={(e) => field('status', e.target.value as CustomerStatus)}><Radio value="ENABLED">启用</Radio><Radio value="DISABLED">停用</Radio></Radio.Group></Form.Item>
          <Form.Item label="商品可见范围" className={styles.fullWidth}><Radio.Group value={value.visibilityPolicy} onChange={(e) => field('visibilityPolicy', e.target.value as VisibilityPolicy)}><Radio value="ALL_ENABLED">全部在售 SKU</Radio><Radio value="ALLOWLIST">仅白名单 SKU</Radio></Radio.Group></Form.Item>
          {value.visibilityPolicy === 'ALLOWLIST' ? <Form.Item label="可见 SKU" required className={styles.fullWidth}><Select aria-label="可见 SKU" mode="multiple" showSearch optionFilterProp="label" loading={products.isLoading} options={skuOptions} value={value.visibilitySkuIds} onChange={(ids) => field('visibilitySkuIds', ids)} placeholder="搜索并选择客户可下单的 SKU" /></Form.Item> : null}
        </Form>
      </div>}
  </DrawerForm>;
}
