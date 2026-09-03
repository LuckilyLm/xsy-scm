import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Input, Popconfirm, Radio, Select, Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { ProductSkuForm, SpecPairForm } from './productFormModel';
import styles from './ProductDrawer.module.css';

interface SkuEditableTableProps {
  value: ProductSkuForm[];
  onChange: (value: ProductSkuForm[]) => void;
  onRemove: (key: string) => void;
  onSelectDefault: (key: string) => void;
}

export function SkuEditableTable({
  value,
  onChange,
  onRemove,
  onSelectDefault,
}: SkuEditableTableProps) {
  function updateSku(key: string, patch: Partial<ProductSkuForm>) {
    onChange(value.map((sku) => (sku.key === key ? { ...sku, ...patch } : sku)));
  }

  function updateSpecPair(sku: ProductSkuForm, index: number, patch: Partial<SpecPairForm>) {
    updateSku(sku.key, {
      specPairs: sku.specPairs.map((pair, pairIndex) =>
        pairIndex === index ? { ...pair, ...patch } : pair,
      ),
    });
  }

  const columns: ColumnsType<ProductSkuForm> = [
    {
      title: '默认',
      width: 64,
      align: 'center',
      render: (_, sku, index) => (
        <Radio
          aria-label={`默认 SKU ${index + 1}`}
          checked={sku.defaultSku}
          onChange={() => onSelectDefault(sku.key)}
        />
      ),
    },
    {
      title: <><span className={styles.required}>*</span> SKU 编码</>,
      width: 170,
      render: (_, sku) => (
        <Input
          aria-label="SKU 编码"
          maxLength={64}
          placeholder="如 VEG-001-JIN"
          value={sku.skuCode}
          onChange={(event) => updateSku(sku.key, { skuCode: event.target.value })}
        />
      ),
    },
    {
      title: <><span className={styles.required}>*</span> 规格名称</>,
      width: 140,
      render: (_, sku) => (
        <Input
          aria-label="规格名称"
          maxLength={150}
          placeholder="如 散装"
          value={sku.specName}
          onChange={(event) => updateSku(sku.key, { specName: event.target.value })}
        />
      ),
    },
    {
      title: '规格属性',
      width: 240,
      render: (_, sku) => (
        <Space direction="vertical" size={4} className={styles.specPairs}>
          {sku.specPairs.map((pair, index) => (
            <Space.Compact block key={`${sku.key}-spec-${index}`}>
              <Input
                aria-label="规格属性名"
                placeholder="属性名"
                value={pair.key}
                onChange={(event) => updateSpecPair(sku, index, { key: event.target.value })}
              />
              <Input
                aria-label="规格属性值"
                placeholder="属性值"
                value={pair.value}
                onChange={(event) => updateSpecPair(sku, index, { value: event.target.value })}
              />
              <Button
                aria-label="删除规格属性"
                icon={<DeleteOutlined />}
                onClick={() =>
                  updateSku(sku.key, {
                    specPairs: sku.specPairs.filter((_, pairIndex) => pairIndex !== index),
                  })
                }
              />
            </Space.Compact>
          ))}
          <Button
            icon={<PlusOutlined />}
            size="small"
            type="link"
            onClick={() => updateSku(sku.key, { specPairs: [...sku.specPairs, { key: '', value: '' }] })}
          >
            添加属性
          </Button>
        </Space>
      ),
    },
    {
      title: <><span className={styles.required}>*</span> 单位</>,
      width: 100,
      render: (_, sku) => (
        <Input
          aria-label="销售单位"
          maxLength={32}
          placeholder="斤"
          value={sku.saleUnit}
          onChange={(event) => updateSku(sku.key, { saleUnit: event.target.value })}
        />
      ),
    },
    {
      title: <><span className={styles.required}>*</span> 市场价</>,
      width: 130,
      render: (_, sku) => (
        <Input
          aria-label="市场价"
          inputMode="decimal"
          prefix="¥"
          value={sku.marketPrice}
          onChange={(event) => updateSku(sku.key, { marketPrice: event.target.value })}
        />
      ),
    },
    {
      title: '商品类型',
      width: 120,
      render: (_, sku) => (
        <Select
          aria-label="商品类型"
          options={[
            { value: 'STANDARD', label: '标品' },
            { value: 'NON_STANDARD', label: '非标品' },
          ]}
          value={sku.productType}
          onChange={(productType) => updateSku(sku.key, { productType })}
        />
      ),
    },
    {
      title: '条码',
      width: 160,
      render: (_, sku) => (
        <Input
          aria-label="条码"
          maxLength={64}
          placeholder="选填"
          value={sku.barcode}
          onChange={(event) => updateSku(sku.key, { barcode: event.target.value })}
        />
      ),
    },
    {
      title: '状态',
      width: 110,
      render: (_, sku) => (
        <Select
          aria-label="SKU 状态"
          options={[
            { value: 'ON_SHELF', label: '已上架' },
            { value: 'OFF_SHELF', label: '已下架' },
          ]}
          value={sku.status}
          onChange={(status) => updateSku(sku.key, { status })}
        />
      ),
    },
    {
      title: '操作',
      fixed: 'right',
      width: 78,
      align: 'center',
      render: (_, sku, index) => (
        <Popconfirm
          disabled={value.length === 1}
          title="确认删除该 SKU？"
          onConfirm={() => onRemove(sku.key)}
        >
          <Button
            aria-label={`删除 SKU ${index + 1}`}
            danger
            disabled={value.length === 1}
            size="small"
            type="link"
          >
            删除
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <>
      <Table<ProductSkuForm>
        columns={columns}
        dataSource={value}
        pagination={false}
        rowClassName={(sku) => (sku.defaultSku ? styles.defaultSkuRow : '')}
        rowKey="key"
        scroll={{ x: 1300 }}
        size="small"
      />
      <Typography.Text className={styles.skuHint} type="secondary">
        商品至少保留一个 SKU；订单、采购、库存与称重后续均引用 SKU。
      </Typography.Text>
    </>
  );
}
