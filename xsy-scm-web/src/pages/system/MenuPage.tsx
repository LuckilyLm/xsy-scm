import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { Button, Modal, Popconfirm, Space, Tag, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { changeMenuStatus, deleteMenu, fetchMenuTree } from '../../api/system/menus';
import { AUTHORITIES } from '../../auth/authorities';
import { Permission } from '../../auth/Permission';
import { PageContainer } from '../../components/common/PageContainer';
import { StatusTag } from '../../components/common/StatusTag';
import type { MenuNode } from '../../types/system';
import { MenuDrawer } from './MenuDrawer';
import { describeError } from './systemUtils';

export function MenuPage() {
  const [tree, setTree] = useState<MenuNode[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<MenuNode | null>(null);
  const [parentId, setParentId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setTree(await fetchMenuTree());
    } catch (loadError) {
      setError(describeError(loadError, '菜单树加载失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  function openCreate(parent: number | null) {
    setEditing(null);
    setParentId(parent);
    setDrawerOpen(true);
  }

  async function toggleStatus(record: MenuNode) {
    try {
      await changeMenuStatus(
        record.id,
        record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED',
        record.version,
      );
      message.success(record.status === 'ENABLED' ? '已停用' : '已启用');
      await load();
    } catch (statusError) {
      message.error(describeError(statusError, '状态更新失败'));
    }
  }

  async function remove(record: MenuNode) {
    try {
      await deleteMenu(record.id, record.version);
      message.success('菜单已删除');
      await load();
    } catch (removeError) {
      Modal.error({ title: '删除失败', content: describeError(removeError, '请稍后重试') });
    }
  }

  const columns: ProColumns<MenuNode>[] = [
    { title: '菜单名称', dataIndex: 'name', width: 200 },
    {
      title: '类型',
      dataIndex: 'type',
      width: 90,
      render: (value) => <Tag>{value === 'DIRECTORY' ? '目录' : '页面'}</Tag>,
    },
    { title: '路由键', dataIndex: 'routeKey', width: 180, render: (value) => value || '-' },
    { title: '路径', dataIndex: 'path', width: 200, render: (value) => value || '-' },
    { title: '所需权限', dataIndex: 'requiredPermission', width: 160, render: (value) => value || '-' },
    { title: '排序', dataIndex: 'sort', width: 70 },
    {
      title: '可见',
      dataIndex: 'visible',
      width: 70,
      render: (value) => (value ? '是' : '否'),
    },
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
          <Permission authority={AUTHORITIES.menuCreate}>
            <Button onClick={() => openCreate(record.id)} size="small" type="link">
              新增下级
            </Button>
          </Permission>
          <Permission authority={AUTHORITIES.menuUpdate}>
            <Button
              onClick={() => {
                setParentId(null);
                setEditing(record);
                setDrawerOpen(true);
              }}
              size="small"
              type="link"
            >
              编辑
            </Button>
          </Permission>
          <Permission authority={AUTHORITIES.menuStatus}>
            <Button onClick={() => void toggleStatus(record)} size="small" type="link">
              {record.status === 'ENABLED' ? '停用' : '启用'}
            </Button>
          </Permission>
          <Permission authority={AUTHORITIES.menuDelete}>
            <Popconfirm
              okText="删除"
              cancelText="取消"
              title="删除菜单"
              description="存在子菜单时将无法删除，确定继续？"
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
          <span>菜单管理</span>
          <Permission authority={AUTHORITIES.menuCreate}>
            <Button onClick={() => openCreate(null)} size="small" type="primary">
              新增菜单
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
        <ProTable<MenuNode>
          columns={columns}
          dataSource={tree}
          loading={loading}
          options={{ reload: () => void load(), density: false, setting: false, fullScreen: false }}
          pagination={false}
          rowKey="id"
          search={false}
          size="small"
        />
      )}

      <MenuDrawer
        key={editing ? `edit-${editing.id}` : `create-${parentId ?? 'root'}`}
        onClose={() => setDrawerOpen(false)}
        onSuccess={() => {
          setDrawerOpen(false);
          void load();
        }}
        open={drawerOpen}
        parentId={parentId}
        tree={tree}
        value={editing}
      />
    </PageContainer>
  );
}
