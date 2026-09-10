import type {ProColumns} from '@ant-design/pro-components';
import {ProTable} from '@ant-design/pro-components';
import {Button, Modal, Popconfirm, Space, message} from 'antd';
import dayjs from 'dayjs';
import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import type {ActionType} from '@ant-design/pro-components';
import {fetchUsers, changeUserStatus, deleteUser} from '../../api/system/users';
import {fetchDepartmentTree} from '../../api/system/departments';
import {AUTHORITIES} from '../../auth/authorities';
import {Permission} from '../../auth/Permission';
import {PageContainer} from '../../components/common/PageContainer';
import {StatusTag} from '../../components/common/StatusTag';
import type {DepartmentTreeNode, SystemUser} from '../../types/system';
import {ResetPasswordModal} from './ResetPasswordModal';
import {UserDrawer} from './UserDrawer';
import {UserRolesDrawer} from './UserRolesDrawer';
import {STATUS_ENUM, describeError} from './systemUtils';

export function UserPage() {
    const actionRef = useRef<ActionType>(null);
    const [departments, setDepartments] = useState<DepartmentTreeNode[]>([]);
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [editing, setEditing] = useState<SystemUser | null>(null);
    const [rolesUser, setRolesUser] = useState<SystemUser | null>(null);
    const [resetUser, setResetUser] = useState<SystemUser | null>(null);

    const loadDepartments = useCallback(async () => {
        try {
            setDepartments(await fetchDepartmentTree());
        } catch {
            // 部门树仅用于展示与选择，失败不阻断用户列表。
        }
    }, []);

    useEffect(() => {
        void loadDepartments();
    }, [loadDepartments]);

    const departmentNames = useMemo(() => buildDepartmentNames(departments), [departments]);

    async function toggleStatus(record: SystemUser) {
        try {
            await changeUserStatus(
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

    async function remove(record: SystemUser) {
        try {
            await deleteUser(record.id, record.version);
            message.success('用户已删除');
            actionRef.current?.reload();
        } catch (error) {
            Modal.error({title: '删除失败', content: describeError(error, '请稍后重试')});
        }
    }

    const columns: ProColumns<SystemUser>[] = [
        {title: '用户名', dataIndex: 'username', width: 140},
        {title: '姓名', dataIndex: 'displayName', width: 120},
        {
            title: '部门',
            dataIndex: 'departmentId',
            width: 140,
            search: false,
            render: (_value, record) => (record.departmentId ? departmentNames.get(record.departmentId) ?? '-' : '-'),
        },
        {title: '邮箱', dataIndex: 'email', search: false, render: (value) => value || '-'},
        {title: '手机号', dataIndex: 'phone', width: 130, search: false, render: (value) => value || '-'},
        {
            title: '状态',
            dataIndex: 'status',
            width: 90,
            valueType: 'select',
            valueEnum: STATUS_ENUM,
            render: (_value, record) => <StatusTag status={record.status}/>,
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
                    <Permission authority={AUTHORITIES.userUpdate}>
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
                    <Permission authority={AUTHORITIES.userAssignRoles}>
                        <Button onClick={() => setRolesUser(record)} size="small" type="link">
                            分配角色
                        </Button>
                    </Permission>
                    <Permission authority={AUTHORITIES.userResetPassword}>
                        <Button onClick={() => setResetUser(record)} size="small" type="link">
                            重置密码
                        </Button>
                    </Permission>
                    <Permission authority={AUTHORITIES.userStatus}>
                        <Button onClick={() => void toggleStatus(record)} size="small" type="link">
                            {record.status === 'ENABLED' ? '停用' : '启用'}
                        </Button>
                    </Permission>
                    <Permission authority={AUTHORITIES.userDelete}>
                        <Popconfirm
                            okText="删除"
                            cancelText="取消"
                            title="删除用户"
                            description="删除后该用户无法登录且会话立即失效，确定继续？"
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
        <PageContainer title="用户管理">
            <ProTable<SystemUser>
                actionRef={actionRef}
                columns={columns}
                rowKey="id"
                size="small"
                search={{labelWidth: 'auto'}}
                pagination={{pageSize: 20, showSizeChanger: false}}
                toolBarRender={() => [
                    <Permission authority={AUTHORITIES.userCreate} key="create">
                        <Button
                            onClick={() => {
                                setEditing(null);
                                setDrawerOpen(true);
                            }}
                            size="small"
                            type="primary"
                        >
                            新增用户
                        </Button>
                    </Permission>,
                ]}
                request={async (params) => {
                    const result = await fetchUsers({
                        page: params.current ?? 1,
                        pageSize: params.pageSize ?? 20,
                        keyword: params.keyword,
                        status: params.status,
                    });
                    return {data: result.records, success: true, total: result.total};
                }}
            />

            <UserDrawer
                departments={departments}
                onClose={() => setDrawerOpen(false)}
                onSuccess={() => {
                    setDrawerOpen(false);
                    actionRef.current?.reload();
                }}
                open={drawerOpen}
                value={editing}
            />

            <UserRolesDrawer
                onClose={() => setRolesUser(null)}
                onSuccess={() => {
                    setRolesUser(null);
                    actionRef.current?.reload();
                }}
                open={rolesUser !== null}
                user={rolesUser}
            />

            <ResetPasswordModal
                onClose={() => setResetUser(null)}
                onSuccess={() => {
                    setResetUser(null);
                    actionRef.current?.reload();
                }}
                open={resetUser !== null}
                user={resetUser}
            />
        </PageContainer>
    );
}

function buildDepartmentNames(nodes: DepartmentTreeNode[]): Map<number, string> {
    const result = new Map<number, string>();
    const walk = (items: DepartmentTreeNode[]) => {
        for (const item of items) {
            result.set(item.id, item.name);
            walk(item.children ?? []);
        }
    };
    walk(nodes);
    return result;
}
