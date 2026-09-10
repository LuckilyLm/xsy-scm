import type { ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { Button, Modal, Popconfirm, Space, Tag, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { AUTHORITIES } from '../../auth/authorities';
import { Permission } from '../../auth/Permission';
import { changeDepartmentStatus, deleteDepartment, fetchDepartmentTree } from '../../api/system/departments';
import { PageContainer } from '../../components/common/PageContainer';
import { StatusTag } from '../../components/common/StatusTag';
import type { Department, DepartmentTreeNode } from '../../types/system';
import { DepartmentDrawer } from './DepartmentDrawer';
import { describeError } from './systemUtils';

export function DepartmentPage() {
  const [tree, setTree] = useState<DepartmentTreeNode[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Department | null>(null);
  const [defaultParentId, setDefaultParentId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setTree(await fetchDepartmentTree());
    } catch (loadError) {
      setError(describeError(loadError, '部门树加载失败'));
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

  function openEdit(record: DepartmentTreeNode) {
    setDefaultParentId(null);
    setEditing({ ...record, createdAt: '', updatedAt: '' });
    setDrawerOpen(true);
  }

  async function toggleStatus(record: DepartmentTreeNode) {
    try {
      await changeDepartmentStatus(
        record.id,
        record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED',
        record.version,
      );
      message.success(record.status === 'ENABLED' ? '已停用' : '已启用');
      await load();
    } catch (toggleError) {
      message.error(describeError(toggleError, '状态更新失败'));
    }
  }

  async function remove(record: DepartmentTreeNode) {
    try {
      await deleteDepartment(record.id, record.version);
      message.success('部门已删除');
      await load();
    } catch (removeError) {
      Modal.error({ title: '删除失败', content: describeError(removeError, '请稍后重试') });
    }
  }

  const columns: ProColumns<DepartmentTreeNode>[] = [
    { title: '部门名称', dataIndex: 'name', width: 240 },
    { title: '部门编码', dataIndex: 'code', width: 160 },
    { title: '排序', dataIndex: 'sortOrder', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (_value, record) => <StatusTag status={record.status} />,
    },
    {
      title: '操作',
      key: 'actions',
      width: 260,
      render: (_value, record) => (
        <Space size={4}>
          <Permission authority={AUTHORITIES.departmentCreate}>
            <Button onClick={() => openCreate(record.id)} size="small" type="link">
              新增下级
            </Button>
          </Permission>
          <Permission authority={AUTHORITIES.departmentUpdate}>
            <Button onClick={() => openEdit(record)} size="small" type="link">
              编辑
            </Button>
          </Permission>
          <Permission authority={AUTHORITIES.departmentStatus}>
            <Button onClick={() => void toggleStatus(record)} size="small" type="link">
              {record.status === 'ENABLED' ? '停用' : '启用'}
            </Button>
          </Permission>
          <Permission authority={AUTHORITIES.departmentDelete}>
            <Popconfirm
              okText="删除"
              cancelText="取消"
              title="删除部门"
              description="存在子部门或用户时将无法删除，确定继续？"
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
          <span>部门管理</span>
          <Permission authority={AUTHORITIES.departmentCreate}>
            <Button onClick={() => openCreate(null)} size="small" type="primary">
              新增部门
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
        <ProTable<DepartmentTreeNode>
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

      <DepartmentDrawer
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
