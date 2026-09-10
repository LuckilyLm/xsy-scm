import {ProTable} from '@ant-design/pro-components';
import type {ActionType, ProColumns} from '@ant-design/pro-components';
import {Button, Modal, Popconfirm, Space, Tag, message} from 'antd';
import dayjs from 'dayjs';
import {useCallback, useEffect, useRef, useState} from 'react';
import {
    changePermissionStatus,
    deletePermission,
    fetchPermissions,
} from '../../api/system/permissions';
import {AUTHORITIES} from '../../auth/authorities';
import {Permission} from '../../auth/Permission';
import {PageContainer} from '../../components/common/PageContainer';
import {StatusTag} from '../../components/common/StatusTag';
import type {PermissionItem} from '../../types/system';
import {PermissionDrawer} from './PermissionDrawer';
import {PERMISSION_TYPE_ENUM, STATUS_ENUM, describeError} from './systemUtils';

export function PermissionPage() {
    const actionRef = useRef<ActionType>(null);
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [editing, setEditing] = useState<PermissionItem | null>(null);
    const [modules, setModules] = useState<string[]>([]);

    const loadModules = useCallback(async () => {
        try {
            const page = await fetchPermissions({page: 1, pageSize: 100});
            setModules([...new Set(page.records.map((item) => item.module))].sort());
        } catch {
            // 模块名只用于输入建议，失败不阻断列表。
        }
    }, []);

    useEffect(() => {
        void loadModules();
    }, [loadModules]);

    async function toggleStatus(record: PermissionItem) {
        try {
            await changePermissionStatus(
                record.id,
                record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED',
                record.version,
            );
            message.success(record.status === 'ENABLED' ? '已停用' : '已启用');
            actionRef.current?.reload();
            void loadModules();
        } catch (error) {
            message.error(describeError(error, '状态更新失败'));
        }
    }

    async function remove(record: PermissionItem) {
        try {
            await deletePermission(record.id, record.version);
            message.success('权限已删除');
            actionRef.current?.reload();
            void loadModules();
        } catch (error) {
            Modal.error({title: '删除失败', content: describeError(error, '请稍后重试')});
        }
    }

    const columns: ProColumns<PermissionItem>[] = [
        {title: '权限编码', dataIndex: 'permissionCode', width: 220},
        {title: '权限名称', dataIndex: 'name', width: 160},
        {
            title: '类型',
            dataIndex: 'type',
            width: 90,
            valueType: 'select',
            valueEnum: PERMISSION_TYPE_ENUM,
            render: (value) => <Tag>{String(value)}</Tag>,
        },
        {
            title: '模块',
            dataIndex: 'module',
            width: 120,
            valueType: 'select',
            valueEnum: Object.fromEntries(modules.map((item) => [item, {text: item}])),
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 90,
            valueType: 'select',
            valueEnum: STATUS_ENUM,
            render: (_value, record) => <StatusTag status={record.status}/>,
        },
        {
            title: '类型标记',
            dataIndex: 'systemPermission',
            width: 110,
            search: false,
            render: (_value, record) =>
                record.systemPermission ? <Tag color="orange">系统权限</Tag> : <Tag>自定义</Tag>,
        },
        {
            title: '创建时间',
            dataIndex: 'createdAt',
            width: 160,
            search: false,
            render: (value) => (value ? dayjs(value as string).format('YYYY-MM-DD HH:mm') : '-'),
        },
        {
            title: '操作',
            key: 'actions',
            width: 200,
            search: false,
            render: (_value, record) => (
                <Space size={4}>
                    <Permission authority={AUTHORITIES.permissionUpdate}>
                        <Button
                            onClick={() => {
                                setEditing(record);
                                setDrawerOpen(true);
                            }}
                            size="small"
                            type="link"
                        >
                            编辑
                        </Button>
                    </Permission>
                    <Permission authority={AUTHORITIES.permissionStatus}>
                        <Button onClick={() => void toggleStatus(record)} size="small" type="link">
                            {record.status === 'ENABLED' ? '停用' : '启用'}
                        </Button>
                    </Permission>
                    <Permission authority={AUTHORITIES.permissionDelete}>
                        <Popconfirm
                            okText="删除"
                            cancelText="取消"
                            title="删除权限"
                            description="已分配给角色的权限删除后相关能力立即失效，确定继续？"
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
        <PageContainer title="权限管理">
            <ProTable<PermissionItem>
                actionRef={actionRef}
                columns={columns}
                rowKey="id"
                size="small"
                search={{labelWidth: 'auto'}}
                pagination={{pageSize: 20, showSizeChanger: false}}
                toolBarRender={() => [
                    <Permission authority={AUTHORITIES.permissionCreate} key="create">
                        <Button
                            onClick={() => {
                                setEditing(null);
                                setDrawerOpen(true);
                            }}
                            size="small"
                            type="primary"
                        >
                            新增权限
                        </Button>
                    </Permission>,
                ]}
                request={async (params) => {
                    const result = await fetchPermissions({
                        page: params.current ?? 1,
                        pageSize: params.pageSize ?? 20,
                        keyword: params.keyword,
                        module: params.module,
                        type: params.type,
                        status: params.status,
                    });
                    return {data: result.records, success: true, total: result.total};
                }}
            />

            <PermissionDrawer
                modules={modules}
                onClose={() => setDrawerOpen(false)}
                onSuccess={() => {
                    setDrawerOpen(false);
                    actionRef.current?.reload();
                    void loadModules();
                }}
                open={drawerOpen}
                value={editing}
            />
        </PageContainer>
    );
}
