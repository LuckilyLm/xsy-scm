import { DrawerForm } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { Alert, DatePicker, Form, Input, Select } from 'antd';
import dayjs from 'dayjs';
import { useEffect, useMemo, useState } from 'react';
import { createAgreementPrice, fetchCustomers, fetchSkuCatalog, updateAgreementPrice } from '../../api/customers';
import { ApiError } from '../../api/http';
import type { AgreementPrice } from '../../types/sales';
import { createEmptyAgreementPriceForm, normalizeAgreementPricePayload, validateAgreementPeriod } from './agreementPriceFormModel';
import type { AgreementPriceForm } from './agreementPriceFormModel';
import styles from './Customer.module.css';

interface Props { open: boolean; record: AgreementPrice | null; onOpenChange: (open: boolean) => void; onSaved: () => void; }
export function AgreementPriceDrawer({ open, record, onOpenChange, onSaved }: Props) {
  const [value, setValue] = useState<AgreementPriceForm>(createEmptyAgreementPriceForm); const [error, setError] = useState<string | null>(null); const [pending, setPending] = useState(false);
  const customers = useQuery({ queryKey: ['agreement-customer-options'], queryFn: () => fetchCustomers({ page: 1, pageSize: 100 }), enabled: open });
  const products = useQuery({ queryKey: ['agreement-sku-options'], queryFn: () => fetchSkuCatalog({ page: 1, pageSize: 100 }), enabled: open });
  useEffect(() => { if (!open) return; setError(null); setValue(record ? { version: record.version, customerId: record.customerId, skuId: record.skuId, unitPrice: record.unitPrice, effectiveFrom: record.effectiveFrom, effectiveTo: record.effectiveTo } : createEmptyAgreementPriceForm()); }, [open, record]);
  const skuOptions = useMemo(() => (products.data?.records ?? []).flatMap((p) => p.skus.map((sku) => ({ value: sku.id, label: `${p.name} / ${sku.specName}（${sku.skuCode}）` }))), [products.data]);
  function field<K extends keyof AgreementPriceForm>(key: K, next: AgreementPriceForm[K]) { setValue((current) => ({ ...current, [key]: next })); }
  async function submit() {
    if (value.customerId === null || value.skuId === null) { setError('请选择客户和 SKU'); return false; }
    if (!/^\d+(\.\d{1,4})?$/.test(value.unitPrice.trim())) { setError('协议价应为非负数字，最多四位小数'); return false; }
    const periodError = validateAgreementPeriod(value.effectiveFrom, value.effectiveTo); if (periodError) { setError(periodError); return false; }
    setPending(true); setError(null); try { const payload = normalizeAgreementPricePayload(value); if (record) await updateAgreementPrice(record.id, payload); else await createAgreementPrice(payload); onSaved(); onOpenChange(false); return true; } catch (e) { setError(e instanceof ApiError && e.status === 409 ? (e.code === 40933 ? '该客户与 SKU 的协议价有效期发生重叠，请调整时间范围' : '协议价已被其他人修改，请重新加载后再保存') : e instanceof Error ? e.message : '保存失败'); return false; } finally { setPending(false); }
  }
  return <DrawerForm open={open} title={record ? '编辑协议价' : '新增协议价'} width={620} onFinish={submit} onOpenChange={(next) => { if (!pending) onOpenChange(next); }} drawerProps={{ destroyOnClose: true, maskClosable: !pending }} submitter={{ searchConfig: { submitText: '保存协议价', resetText: '取消' }, submitButtonProps: { loading: pending, disabled: pending }, resetButtonProps: { disabled: pending } }}>
    <div className={styles.drawerBody}>{error ? <Alert type="error" showIcon message={error} /> : null}<Form layout="vertical" component={false}>
      <Form.Item label="客户" required><Select aria-label="协议价客户" showSearch optionFilterProp="label" loading={customers.isLoading} value={value.customerId} options={(customers.data?.records ?? []).map((c) => ({ value: c.id, label: `${c.name}（${c.customerCode}）`, disabled: c.status !== 'ENABLED' }))} onChange={(id) => field('customerId', id)} /></Form.Item>
      <Form.Item label="SKU" required><Select aria-label="协议价 SKU" showSearch optionFilterProp="label" loading={products.isLoading} value={value.skuId} options={skuOptions} onChange={(id) => field('skuId', id)} /></Form.Item>
      <Form.Item label="协议单价" required><Input aria-label="协议单价" prefix="¥" inputMode="decimal" value={value.unitPrice} onChange={(e) => field('unitPrice', e.target.value)} /></Form.Item>
      <div className={styles.formGrid}><Form.Item label="生效时间" required><DatePicker className={styles.datePicker} aria-label="生效时间" showTime format="YYYY-MM-DD HH:mm" value={value.effectiveFrom ? dayjs(value.effectiveFrom) : null} onChange={(date) => field('effectiveFrom', date?.toISOString() ?? '')} /></Form.Item><Form.Item label="结束时间"><DatePicker className={styles.datePicker} aria-label="结束时间" showTime format="YYYY-MM-DD HH:mm" allowClear value={value.effectiveTo ? dayjs(value.effectiveTo) : null} onChange={(date) => field('effectiveTo', date?.toISOString() ?? null)} /></Form.Item></div>
      <TypographyHint />
    </Form></div>
  </DrawerForm>;
}
function TypographyHint() { return <div className={styles.hint}>有效期按 [生效时间, 结束时间) 计算；结束时间留空表示长期有效。</div>; }
