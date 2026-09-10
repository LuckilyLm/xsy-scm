import type { ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import type { ActionType } from '@ant-design/pro-components';
import { Button, Space, message } from 'antd';
import { useRef, useState } from 'react';
import { createCustomerType, fetchCustomerTypes, updateCustomerType } from '../../api/customers';
import { AUTHORITIES } from '../../auth/authorities';
import { Permission } from '../../auth/Permission';
import { PageContainer } from '../../components/common/PageContainer';
import { StatusTag } from '../../components/common/StatusTag';
import type { CustomerType, CustomerTypeInput } from '../../types/sales';
import { CustomerTypeDrawer } from './CustomerTypeDrawer';
import { describeError } from '../system/systemUtils';

export function CustomerTypePage() {
  const actionRef = useRef<ActionType>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<CustomerType | null>(null);

  function openCreate() {
    setEditing(null);
    setDrawerOpen(true);
  }

  function openEdit(record: CustomerType) {
    setEditing(record);
    setDrawerOpen(true);
  }

  async function save(payload: CustomerTypeInput) {
    try {
      if (editing) {
        await updateCustomerType(editing.id, { ...payload, version: editing.version });
      } else {
        await createCustomerType(payload);
      }
      message.success(editing ? '客户类型已更新' : '客户类型已创建');
      setDrawerOpen(false);
      actionRef.current?.reload();
    } catch (error) {
      message.error(describeError(error, '保存失败，请稍后重试'));
    }
  }

  const columns: ProColumns<CustomerType>[] = [
    { title: '类型编码', dataIndex: 'typeCode', width: 200 },
    { title: '类型名称', dataIndex: 'name', width: 200 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_value, record) => <StatusTag status={record.status} />,
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      search: false,
      render: (_value, record) => (
        <Space size={4}>
          <Permission authority={AUTHORITIES.customerManage}>
            <Button onClick={() => openEdit(record)} size="small" type="link">
              编辑
            </Button>
          </Permission>
        </Space>
      ),
    },
  ];

  return (
    <PageContainer
      title={
        <Space>
          <span>客户类型</span>
          <Permission authority={AUTHORITIES.customerManage}>
            <Button onClick={openCreate} size="small" type="primary">
              新增类型
            </Button>
          </Permission>
        </Space>
      }
    >
      <ProTable<CustomerType>
        actionRef={actionRef}
        columns={columns}
        rowKey="id"
        size="small"
        search={false}
        pagination={{ pageSize: 20, showSizeChanger: false }}
        toolBarRender={() => []}
        request={async () => {
          const list = await fetchCustomerTypes();
          return { data: list, success: true, total: list.length };
        }}
      />

      <CustomerTypeDrawer
        onClose={() => setDrawerOpen(false)}
        onSuccess={save}
        open={drawerOpen}
        value={editing}
      />
    </PageContainer>
  );
}
