import { Alert, Button, DatePicker, Form, Input, Select, Space, Table, message } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { createPurchaseOrder, fetchPurchaseDemands, fetchPurchaseOrder, updatePurchaseOrder } from '../../api/purchases';
import { fetchSuppliers, fetchWarehouses } from '../../api/suppliers';
import { PageContainer } from '../../components/common/PageContainer';
import type { PurchaseDemand, PurchaseOrderPayload } from '../../types/purchase';
import { isPositiveDecimal, subtractDecimal } from '../../utils/decimal';

interface HeaderValues {
  supplierId: number;
  warehouseId: number;
  plannedArrivalDate?: { format(format: string): string };
  remark?: string;
}

interface DraftRow {
  id?: number;
  version?: number;
  demandId: number;
  skuId: number;
  skuCode: string;
  productName: string;
  quantity: string;
  price: string;
}

export function PurchaseOrderEditorPage() {
  const routeId = useParams().id;
  const orderId = routeId ? Number(routeId) : undefined;
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [form] = Form.useForm<HeaderValues>();
  const [submitting, setSubmitting] = useState(false);
  const [draftOverrides, setDraftOverrides] = useState<Record<number, Partial<DraftRow>>>({});

  const demandIds = useMemo(
    () => searchParams.getAll('demandId').map(Number).filter(Number.isSafeInteger),
    [searchParams],
  );
  const demands = useQuery({ queryKey: ['purchase-demands'], queryFn: fetchPurchaseDemands });
  const order = useQuery({
    queryKey: ['purchase-order', orderId],
    queryFn: () => fetchPurchaseOrder(orderId!),
    enabled: orderId !== undefined,
  });
  const suppliers = useQuery({ queryKey: ['suppliers'], queryFn: fetchSuppliers });
  const warehouses = useQuery({ queryKey: ['warehouses'], queryFn: fetchWarehouses });

  const baseRows = useMemo<DraftRow[]>(() => {
    if (order.data) {
      return order.data.items.map((item) => {
        const allocation = order.data.allocations.find((row) => row.purchaseOrderItemId === item.id);
        return {
          id: item.id,
          version: item.version,
          demandId: allocation?.purchaseDemandId ?? 0,
          skuId: item.skuId,
          skuCode: item.skuCode,
          productName: item.productName,
          quantity: item.plannedQuantity,
          price: item.purchasePrice,
        };
      });
    }
    return (demands.data ?? [])
      .filter((demand) => demandIds.includes(demand.id))
      .map((demand) => demandRow(demand));
  }, [demandIds, demands.data, order.data]);

  const rows = baseRows.map((row) => ({ ...row, ...draftOverrides[row.demandId] }));
  const loading = demands.isLoading || suppliers.isLoading || warehouses.isLoading || (orderId !== undefined && order.isLoading);
  const loadError = demands.isError || suppliers.isError || warehouses.isError || order.isError;

  useEffect(() => {
    if (!order.data) return;
    form.setFieldsValue({
      supplierId: order.data.supplierId,
      warehouseId: order.data.warehouseId,
      remark: order.data.remark,
    });
  }, [form, order.data]);

  const updateRow = (demandId: number, patch: Partial<DraftRow>) => {
    setDraftOverrides((current) => ({
      ...current,
      [demandId]: { ...current[demandId], ...patch },
    }));
  };

  const submit = async () => {
    if (!rows.length) {
      message.warning('请先从采购需求页选择待采购需求');
      return;
    }
    if (rows.some((row) => !isPositiveDecimal(row.quantity) || !/^\d+(\.\d{1,4})?$/.test(row.price))) {
      message.warning('采购数量必须大于零，数量和价格最多四位小数');
      return;
    }

    const values = await form.validateFields();
    const payload: PurchaseOrderPayload = {
      supplierId: values.supplierId,
      warehouseId: values.warehouseId,
      plannedArrivalDate: values.plannedArrivalDate?.format('YYYY-MM-DD'),
      remark: values.remark,
      version: order.data?.version,
      items: rows.map((row) => ({
        id: row.id,
        version: row.version,
        demandId: row.demandId,
        skuId: row.skuId,
        quantity: row.quantity.trim(),
        price: row.price.trim(),
      })),
    };

    setSubmitting(true);
    try {
      if (orderId !== undefined) {
        await updatePurchaseOrder(orderId, payload);
        message.success('采购单已更新');
        navigate(`/purchases/orders/${orderId}`);
      } else {
        const createdId = await createPurchaseOrder(payload, crypto.randomUUID());
        message.success('采购单已创建');
        navigate(`/purchases/orders/${createdId}`);
      }
    } catch {
      message.error('保存失败；如数据已被修改，请刷新后核对并重试');
    } finally {
      setSubmitting(false);
    }
  };

  if (loadError) {
    return <Alert type="error" showIcon message="采购单编辑数据加载失败" />;
  }

  return (
    <PageContainer>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {!orderId && !demandIds.length && (
          <Alert type="info" showIcon message="请从采购需求页选择需求后创建采购单" />
        )}
        <Form
          form={form}
          layout="inline"
          initialValues={{
            supplierId: order.data?.supplierId,
            warehouseId: order.data?.warehouseId,
            remark: order.data?.remark,
          }}
        >
          <Form.Item name="supplierId" label="供应商" rules={[{ required: true, message: '请选择供应商' }]}>
            <Select
              style={{ width: 220 }}
              options={(suppliers.data ?? []).filter((x) => x.status === 'ENABLED').map((x) => ({ value: x.id, label: x.name }))}
            />
          </Form.Item>
          <Form.Item name="warehouseId" label="仓库" rules={[{ required: true, message: '请选择仓库' }]}>
            <Select
              style={{ width: 220 }}
              options={(warehouses.data ?? []).filter((x) => x.status === 'ENABLED').map((x) => ({ value: x.id, label: x.name }))}
            />
          </Form.Item>
          <Form.Item name="plannedArrivalDate" label="计划到货"><DatePicker /></Form.Item>
          <Form.Item name="remark" label="备注"><Input maxLength={500} /></Form.Item>
        </Form>
        <Table
          loading={loading}
          rowKey="demandId"
          pagination={false}
          dataSource={rows}
          columns={[
            { title: 'SKU', dataIndex: 'skuCode' },
            { title: '商品', dataIndex: 'productName' },
            { title: '需求来源', dataIndex: 'demandId' },
            {
              title: '采购数量',
              render: (_, row) => <Input value={row.quantity} onChange={(event) => updateRow(row.demandId, { quantity: event.target.value })} />,
            },
            {
              title: '采购单价',
              render: (_, row) => <Input value={row.price} onChange={(event) => updateRow(row.demandId, { price: event.target.value })} />,
            },
          ]}
        />
        <Space>
          <Button onClick={() => navigate(-1)}>取消</Button>
          <Button type="primary" loading={submitting} disabled={loading || !rows.length} onClick={submit}>保存采购单</Button>
        </Space>
      </Space>
    </PageContainer>
  );
}

function demandRow(demand: PurchaseDemand): DraftRow {
  return {
    demandId: demand.id,
    skuId: demand.skuId,
    skuCode: demand.skuCodeSnapshot,
    productName: demand.productNameSnapshot,
    quantity: subtractDecimal(demand.requiredQuantity, demand.allocatedQuantity),
    price: '0.0000',
  };
}
