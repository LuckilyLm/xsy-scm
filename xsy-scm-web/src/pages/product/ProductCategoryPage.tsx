import type { ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { Button, Modal, Popconfirm, Space, Tag, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { AUTHORITIES } from '../../auth/authorities';
import { Permission } from '../../auth/Permission';
import { deleteCategory, fetchCategoryTree } from '../../api/product-categories';
import { PageContainer } from '../../components/common/PageContainer';
import { StatusTag } from '../../components/common/StatusTag';
import type { ProductCategoryTreeNode } from '../../types/product';
import { ProductCategoryDrawer } from './ProductCategoryDrawer';
import { describeError } from '../system/systemUtils';

export function ProductCategoryPage() {
  const [tree, setTree] = useState<ProductCategoryTreeNode[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<ProductCategoryTreeNode | null>(null);
  const [defaultParentId, setDefaultParentId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setTree(await fetchCategoryTree());
    } catch (loadError) {
      setError(describeError(loadError, '商品分类树加载失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  function openCreate(parentId: number | null) {
    setEditing(null);
    setDefaultParentId(parentId);
    setDrawerOpen(true);
  }

  function openEdit(record: ProductCategoryTreeNode) {
    setDefaultParentId(null);
    setEditing(record);
    setDrawerOpen(true);
  }

  async function remove(record: ProductCategoryTreeNode) {
    try {
      await deleteCategory(record.id);
      message.success('分类已删除');
      await load();
    } catch (removeError) {
      Modal.error({ title: '删除失败', content: describeError(removeError, '请稍后重试') });
    }
  }

  const columns: ProColumns<ProductCategoryTreeNode>[] = [
    { title: '分类名称', dataIndex: 'name', width: 240 },
    { title: '分类编码', dataIndex: 'categoryCode', width: 160 },
    { title: '层级', dataIndex: 'level', width: 70, search: false },
    { title: '排序', dataIndex: 'sortOrder', width: 80, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (_value, record) => <StatusTag status={record.status} />,
    },
    {
      title: '操作',
      key: 'actions',
      width: 240,
      search: false,
      render: (_value, record) => (
        <Space size={4}>
          <Permission authority={AUTHORITIES.productManage}>
            <Button onClick={() => openCreate(record.id)} size="small" type="link">
              新增下级
            </Button>
          </Permission>
          <Permission authority={AUTHORITIES.productManage}>
            <Button onClick={() => openEdit(record)} size="small" type="link">
              编辑
            </Button>
          </Permission>
          <Permission authority={AUTHORITIES.productManage}>
            <Popconfirm
              okText="删除"
              cancelText="取消"
              title="删除分类"
              description="存在子分类或已关联商品的分类将无法删除，确定继续？"
              onConfirm={() => void remove(record)}
            >
              <Button danger size="small" type="link">
                删除
              </Button>
            </Popconfirm>
          </Permission>
        </Space>
      ),
    },
  ];

  return (
    <PageContainer
      title={
        <Space>
          <span>商品分类</span>
          <Permission authority={AUTHORITIES.productManage}>
            <Button onClick={() => openCreate(null)} size="small" type="primary">
              新增分类
            </Button>
          </Permission>
        </Space>
      }
    >
      {error ? (
        <div style={{ padding: 24 }}>
          <Tag color="error">{error}</Tag>
          <Button onClick={() => void load()} size="small" type="link">
            重试
          </Button>
        </div>
      ) : (
        <ProTable<ProductCategoryTreeNode>
          columns={columns}
          dataSource={tree}
          loading={loading}
          options={{ reload: () => void load() }}
          pagination={false}
          rowKey="id"
          search={false}
          size="small"
        />
      )}

      <ProductCategoryDrawer
        key={editing ? `edit-${editing.id}` : `create-${defaultParentId ?? 'root'}`}
        onClose={() => setDrawerOpen(false)}
        onSuccess={() => {
          setDrawerOpen(false);
          void load();
        }}
        open={drawerOpen}
        parentId={defaultParentId}
        tree={tree}
        value={editing}
      />
    </PageContainer>
  );
}
