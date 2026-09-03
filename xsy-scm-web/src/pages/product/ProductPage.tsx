import { DownOutlined, PlusOutlined, SearchOutlined, UpOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Cascader,
  Form,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Typography,
} from 'antd';
import { useMemo, useRef, useState } from 'react';
import {
  deleteProduct,
  fetchCategoryTree,
  fetchProducts,
  updateProductStatus,
} from '../../api/products';
import { AmountText } from '../../components/common/AmountText';
import { PageContainer } from '../../components/common/PageContainer';
import { StatusTag } from '../../components/common/StatusTag';
import type {
  ProductCategoryTreeNode,
  ProductPageParams,
  ProductSku,
  ProductSummary,
  ProductType,
  ShelfStatus,
} from '../../types/product';
import { ProductDrawer } from './ProductDrawer';
import styles from './ProductPage.module.css';

interface ProductFilterValues {
  keyword?: string;
  categoryPath?: number[];
  spuStatus?: ShelfStatus;
  skuStatus?: ShelfStatus;
  productType?: ProductType;
}

interface ProductPageProps {
  onCreate?: () => void;
  onEdit?: (id: number) => void;
}

interface CategoryOption {
  value: number;
  label: string;
  disabled?: boolean;
  children?: CategoryOption[];
}

function toCategoryOptions(nodes: ProductCategoryTreeNode[]): CategoryOption[] {
  return nodes.map((node) => ({
    value: node.id,
    label: node.name,
    disabled: node.status !== 'ENABLED',
    children: node.children.length ? toCategoryOptions(node.children) : undefined,
  }));
}

function formatPriceRange(record: ProductSummary) {
  if (record.minMarketPrice === record.maxMarketPrice) {
    return <AmountText value={record.minMarketPrice} />;
  }
  return (
    <span className={styles.priceRange}>
      <AmountText value={record.minMarketPrice} />
      <span> – </span>
      <AmountText value={record.maxMarketPrice} />
    </span>
  );
}

const skuColumns = [
  { title: 'SKU 编码', dataIndex: 'skuCode', width: 170 },
  { title: '规格', dataIndex: 'specName', width: 140 },
  { title: '条码', dataIndex: 'barcode', width: 150, render: (value: string | null) => value || '--' },
  { title: '单位', dataIndex: 'saleUnit', width: 80 },
  {
    title: '商品类型',
    dataIndex: 'productType',
    width: 100,
    render: (value: ProductType) => (value === 'STANDARD' ? '标品' : '非标品'),
  },
  {
    title: '市场价',
    dataIndex: 'marketPrice',
    align: 'right' as const,
    width: 130,
    render: (value: string) => <AmountText value={value} />,
  },
  {
    title: '状态',
    dataIndex: 'status',
    align: 'center' as const,
    width: 100,
    render: (value: ShelfStatus) => <StatusTag status={value} />,
  },
  {
    title: '默认规格',
    dataIndex: 'defaultSku',
    align: 'center' as const,
    width: 100,
    render: (value: boolean) => (value ? '是' : '--'),
  },
];

export function ProductPage({ onCreate, onEdit }: ProductPageProps) {
  const [form] = Form.useForm<ProductFilterValues>();
  const actionRef = useRef<ActionType>(null);
  const [advanced, setAdvanced] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const [pendingAction, setPendingAction] = useState<number | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerProductId, setDrawerProductId] = useState<number | null>(null);
  const categories = useQuery({ queryKey: ['product-categories'], queryFn: fetchCategoryTree });
  const categoryOptions = useMemo(
    () => toCategoryOptions(categories.data ?? []),
    [categories.data],
  );

  async function runMutation(id: number, action: () => Promise<void>) {
    setPendingAction(id);
    try {
      await action();
      await actionRef.current?.reload();
    } finally {
      setPendingAction(null);
    }
  }

  function openCreate() {
    if (onCreate) {
      onCreate();
      return;
    }
    setDrawerProductId(null);
    setDrawerOpen(true);
  }

  function openEdit(id: number) {
    if (onEdit) {
      onEdit(id);
      return;
    }
    setDrawerProductId(id);
    setDrawerOpen(true);
  }

  const columns: ProColumns<ProductSummary>[] = [
    {
      title: '商品名称',
      dataIndex: 'name',
      width: 150,
      fixed: 'left',
      render: (_, record) => (
        <button className={styles.nameButton} type="button" onClick={() => openEdit(record.id)}>
          {record.name}
        </button>
      ),
    },
    { title: 'SPU 编码', dataIndex: 'spuCode', width: 150 },
    { title: '分类', dataIndex: 'categoryPath', width: 190 },
    {
      title: '默认单位',
      width: 90,
      render: (_, record) => record.defaultSku.saleUnit,
    },
    {
      title: '市场价',
      width: 210,
      align: 'right',
      render: (_, record) => formatPriceRange(record),
    },
    { title: 'SKU 数', dataIndex: 'skuCount', width: 85, align: 'right' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      align: 'center',
      render: (_, record) => <StatusTag status={record.status} />,
    },
    { title: '别名', dataIndex: 'alias', width: 120, render: (_, record) => record.alias || '--' },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 180,
      render: (_, record) => new Date(record.updatedAt).toLocaleString('zh-CN', { hour12: false }),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 210,
      fixed: 'right',
      render: (_, record) => [
        <Button key="edit" size="small" type="link" onClick={() => openEdit(record.id)}>
          编辑
        </Button>,
        <Popconfirm
          key="status"
          title={`确认${record.status === 'ON_SHELF' ? '下架' : '上架'}该商品？`}
          onConfirm={() =>
            runMutation(record.id, () =>
              updateProductStatus(
                record.id,
                record.version,
                record.status === 'ON_SHELF' ? 'OFF_SHELF' : 'ON_SHELF',
              ),
            )
          }
        >
          <Button loading={pendingAction === record.id} size="small" type="link">
            {record.status === 'ON_SHELF' ? '下架' : '上架'}
          </Button>
        </Popconfirm>,
        <Popconfirm
          key="delete"
          okButtonProps={{ danger: true }}
          okText="删除"
          title="确认删除该商品？"
          onConfirm={() =>
            runMutation(record.id, () => deleteProduct(record.id, record.version))
          }
        >
          <Button danger loading={pendingAction === record.id} size="small" type="link">
            删除
          </Button>
        </Popconfirm>,
      ],
    },
  ];

  return (
    <PageContainer>
      <Tabs
        className={styles.tabs}
        items={[
          { key: 'base', label: '基础商品', children: null },
          { key: 'processed', label: '加工品', disabled: true, children: null },
        ]}
      />

      <Form<ProductFilterValues> form={form} className={styles.filters} layout="inline">
        <Form.Item label="商品分类" name="categoryPath">
          <Cascader
            changeOnSelect={false}
            options={categoryOptions}
            placeholder="请选择商品分类"
            showSearch
          />
        </Form.Item>
        <Form.Item label="搜索" name="keyword">
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="商品名 / SPU编码 / SKU编码 / 条码"
          />
        </Form.Item>
        <Form.Item label="商品状态" name="spuStatus">
          <Select
            allowClear
            options={[
              { value: 'ON_SHELF', label: '已上架' },
              { value: 'OFF_SHELF', label: '已下架' },
            ]}
            placeholder="全部状态"
          />
        </Form.Item>
        {advanced ? (
          <>
            <Form.Item label="SKU 状态" name="skuStatus">
              <Select
                allowClear
                options={[
                  { value: 'ON_SHELF', label: '已上架' },
                  { value: 'OFF_SHELF', label: '已下架' },
                ]}
                placeholder="全部状态"
              />
            </Form.Item>
            <Form.Item label="商品类型" name="productType">
              <Select
                allowClear
                options={[
                  { value: 'STANDARD', label: '标品' },
                  { value: 'NON_STANDARD', label: '非标品' },
                ]}
                placeholder="全部类型"
              />
            </Form.Item>
          </>
        ) : null}
        <Form.Item className={styles.filterActions}>
          <Space>
            <Button type="primary" onClick={() => actionRef.current?.reloadAndRest?.()}>
              查询
            </Button>
            <Button
              onClick={() => {
                form.resetFields();
                actionRef.current?.reloadAndRest?.();
              }}
            >
              重置
            </Button>
            <Button
              icon={advanced ? <UpOutlined /> : <DownOutlined />}
              type="link"
              onClick={() => setAdvanced((value) => !value)}
            >
              {advanced ? '收起筛选' : '高级筛选'}
            </Button>
          </Space>
        </Form.Item>
      </Form>

      <div className={styles.toolbar}>
        <Button icon={<PlusOutlined />} type="primary" onClick={openCreate}>
          新增商品
        </Button>
        <Typography.Text type="secondary">商品交易单位以 SKU 为准</Typography.Text>
      </div>

      {loadError ? (
        <Alert
          action={
            <Button
              size="small"
              onClick={() => {
                setLoadError(false);
                actionRef.current?.reload();
              }}
            >
              重新加载
            </Button>
          }
          className={styles.error}
          message="商品数据加载失败"
          showIcon
          type="error"
        />
      ) : null}

      <ProTable<ProductSummary>
        actionRef={actionRef}
        className={styles.table}
        columns={columns}
        options={false}
        pagination={{
          defaultPageSize: 20,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条`,
        }}
        request={async (params) => {
          const values = form.getFieldsValue();
          const categoryPath = values.categoryPath;
          const query: ProductPageParams = {
            page: params.current ?? 1,
            pageSize: params.pageSize ?? 20,
            keyword: values.keyword?.trim() || undefined,
            categoryId: categoryPath?.at(-1),
            spuStatus: values.spuStatus,
            skuStatus: values.skuStatus,
            productType: values.productType,
          };
          try {
            const data = await fetchProducts(query);
            setLoadError(false);
            return { data: data.records, success: true, total: data.total };
          } catch {
            setLoadError(true);
            return { data: [], success: true, total: 0 };
          }
        }}
        rowKey="id"
        scroll={{ x: 1450 }}
        search={false}
        expandable={{
          expandIcon: ({ expanded, onExpand, record }) => (
            <Button
              aria-label={`${expanded ? '收起' : '展开'}${record.name}的 SKU`}
              className={styles.expandButton}
              icon={expanded ? <UpOutlined /> : <DownOutlined />}
              size="small"
              type="text"
              onClick={(event) => onExpand(record, event)}
            />
          ),
          expandedRowRender: (record) => (
            <div className={styles.skuPanel}>
              <div className={styles.skuHeading}>SKU 规格明细</div>
              <Table<ProductSku>
                columns={skuColumns}
                dataSource={record.skus}
                pagination={false}
                rowKey="id"
                scroll={{ x: 1000 }}
                size="small"
              />
            </div>
          ),
        }}
      />
      <ProductDrawer
        open={drawerOpen}
        productId={drawerProductId}
        onOpenChange={setDrawerOpen}
        onSaved={() => actionRef.current?.reload()}
      />
    </PageContainer>
  );
}
