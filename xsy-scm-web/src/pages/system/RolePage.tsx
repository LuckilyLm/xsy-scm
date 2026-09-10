import {ProTable} from '@ant-design/pro-components';
import type {ActionType, ProColumns} from '@ant-design/pro-components';
import {Button, Modal, Popconfirm, Space, Tag, message} from 'antd';
import dayjs from 'dayjs';
import {useRef, useState} from 'react';
import {changeRoleStatus, deleteRole, fetchRoles} from '../../api/system/roles';
import {AUTHORITIES} from '../../auth/authorities';
import {Permission} from '../../auth/Permission';
import {PageContainer} from '../../components/common/PageContainer';
import {StatusTag} from '../../components/common/StatusTag';
import type {Role} from '../../types/system';
import {RoleDrawer} from './RoleDrawer';
import {RoleMenusDrawer} from './RoleMenusDrawer';
import {RolePermissionsDrawer} from './RolePermissionsDrawer';
import {STATUS_ENUM, describeError} from './systemUtils';

export function RolePage() {
    const actionRef = useRef<ActionType>(null);
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [editing, setEditing] = useState<Role | null>(null);
    const [permissionRole, setPermissionRole] = useState<Role | null>(null);
    const [menuRole, setMenuRole] = useState<Role | null>(null);

    async function toggleStatus(record: Role) {
        try {
            await changeRoleStatus(
                record.id,
                record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED',
                record.version,
            );
            message.success(record.status === 'ENABLED' ? '已停用' : '已启用');
            actionRef.current?.reload();
        } catch (error) {
            message.error(describeError(error, '状态更新失败'));
        }
    }

    async function remove(record: Role) {
        try {
            await deleteRole(record.id, record.version);
            message.success('角色已删除');
            actionRef.current?.reload();
        } catch (error) {
            Modal.error({title: '删除失败', content: describeError(error, '请稍后重试')});
        }
    }

    const columns: ProColumns<Role>[] = [
        {title: '角色编码', dataIndex: 'roleCode', width: 180},
        {title: '角色名称', dataIndex: 'name', width: 160},
        {
            title: '描述',
            dataIndex: 'description',
            search: false,
            render: (value) => value || '-',
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
            title: '类型',
            dataIndex: 'systemRole',
            width: 110,
            search: false,
            render: (_value, record) =>
                record.systemRole ? <Tag color="orange">系统角色</Tag> : <Tag>普通角色</Tag>,
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
            width: 280,
            search: false,
            render: (_value, record) => (
                <Space size={4}>
                    <Permission authority={AUTHORITIES.roleUpdate}>
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
                    <Permission authority={AUTHORITIES.roleAssignPermissions}>
                        <Button onClick={() => setPermissionRole(record)} size="small" type="link">
                            分配权限
                        </Button>
                    </Permission>
                    <Permission authority={AUTHORITIES.roleAssignMenus}>
                        <Button onClick={() => setMenuRole(record)} size="small" type="link">
                            分配菜单
                        </Button>
                    </Permission>
                    <Permission authority={AUTHORITIES.roleStatus}>
                        <Button onClick={() => void toggleStatus(record)} size="small" type="link">
                            {record.status === 'ENABLED' ? '停用' : '启用'}
                        </Button>
                    </Permission>
                    <Permission authority={AUTHORITIES.roleDelete}>
                        <Popconfirm
                            okText="删除"
                            cancelText="取消"
                            title="删除角色"
                            description="删除后拥有该角色的用户将失去对应能力，确定继续？"
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
        <PageContainer title="角色管理">
            <ProTable<Role>
                actionRef={actionRef}
                columns={columns}
                rowKey="id"
                size="small"
                search={{labelWidth: 'auto'}}
                pagination={{pageSize: 20, showSizeChanger: false}}
                toolBarRender={() => [
                    <Permission authority={AUTHORITIES.roleCreate} key="create">
                        <Button
                            onClick={() => {
                                setEditing(null);
                                setDrawerOpen(true);
                            }}
                            size="small"
                            type="primary"
                        >
                            新增角色
                        </Button>
                    </Permission>,
                ]}
                request={async (params) => {
                    const result = await fetchRoles({
                        page: params.current ?? 1,
                        pageSize: params.pageSize ?? 20,
                        keyword: params.keyword,
                        status: params.status,
                    });
                    return {data: result.records, success: true, total: result.total};
                }}
            />

            <RoleDrawer
                onClose={() => setDrawerOpen(false)}
                onSuccess={() => {
                    setDrawerOpen(false);
                    actionRef.current?.reload();
                }}
                open={drawerOpen}
                value={editing}
            />

            <RolePermissionsDrawer
                onClose={() => setPermissionRole(null)}
                onSuccess={() => {
                    setPermissionRole(null);
                    actionRef.current?.reload();
                }}
                open={permissionRole !== null}
                role={permissionRole}
            />

            <RoleMenusDrawer
                onClose={() => setMenuRole(null)}
                onSuccess={() => {
                    setMenuRole(null);
                    actionRef.current?.reload();
                }}
                open={menuRole !== null}
                role={menuRole}
            />
        </PageContainer>
    );
}
