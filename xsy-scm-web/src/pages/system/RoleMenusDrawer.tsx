import {Alert, Button, Drawer, Empty, Skeleton, Tag, Tree, message} from 'antd';
import {useCallback, useEffect, useState} from 'react';
import type {ReactNode} from 'react';
import {fetchMenuTree} from '../../api/system/menus';
import {fetchRoleMenus, replaceRoleMenus} from '../../api/system/grants';
import type {MenuNode, Role, RoleMenusGrant} from '../../types/system';
import {describeError} from './systemUtils';

interface RoleMenusDrawerProps {
    open: boolean;
    role: Role | null;
    onClose: () => void;
    onSuccess: () => void;
}

/** 角色—菜单分配：菜单只决定导航可见性，API 可执行能力仍由角色权限决定。 */
export function RoleMenusDrawer({open, role, onClose, onSuccess}: RoleMenusDrawerProps) {
    const [menus, setMenus] = useState<MenuNode[]>([]);
    const [grant, setGrant] = useState<RoleMenusGrant | null>(null);
    const [checkedKeys, setCheckedKeys] = useState<number[]>([]);
    const [loading, setLoading] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    const load = useCallback(async () => {
        if (!role) {
            return;
        }
        setLoading(true);
        setErrorMessage(null);
        try {
            const [tree, current] = await Promise.all([
                fetchMenuTree({status: 'ENABLED'}),
                fetchRoleMenus(role.id),
            ]);
            setMenus(tree);
            setGrant(current);
            setCheckedKeys(current.menus.map((item) => item.id));
        } catch (error) {
            setErrorMessage(describeError(error, '菜单数据加载失败'));
        } finally {
            setLoading(false);
        }
    }, [role]);

    useEffect(() => {
        if (open) {
            void load();
        }
    }, [load, open]);

    async function submit() {
        if (!role || !grant) {
            return;
        }
        setSubmitting(true);
        setErrorMessage(null);
        try {
            await replaceRoleMenus(role.id, checkedKeys, grant.version);
            message.success('菜单分配已更新');
            onSuccess();
        } catch (error) {
            setErrorMessage(describeError(error, '菜单分配失败，请刷新后重试'));
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <Drawer
            onClose={onClose}
            open={open}
            title={role ? `分配菜单 — ${role.name}` : '分配菜单'}
            width={480}
            footer={
                <div style={{display: 'flex', justifyContent: 'flex-end', gap: 8}}>
                    <Button onClick={onClose}>取消</Button>
                    <Button loading={submitting} onClick={() => void submit()} type="primary">
                        保存
                    </Button>
                </div>
            }
        >
            <Alert
                style={{marginBottom: 16}}
                type="info"
                showIcon
                message="菜单只控制导航可见性；是否真的能调用接口由“分配权限”决定。"
            />
            {loading ? (
                <Skeleton active/>
            ) : menus.length === 0 ? (
                <Empty description="暂无启用菜单，请先在菜单管理中创建"/>
            ) : (
                <Tree
                    checkable
                    defaultExpandAll
                    checkedKeys={checkedKeys}
                    onCheck={(checked) => setCheckedKeys(checked as number[])}
                    selectable={false}
                    treeData={menus.map(toTreeData)}
                />
            )}
            {errorMessage ? (
                <Alert style={{marginTop: 16}} type="error" showIcon message={errorMessage}/>
            ) : null}
        </Drawer>
    );
}

interface MenuTreeNode {
    key: number;
    title: ReactNode;
    children?: MenuTreeNode[];
}

function toTreeData(node: MenuNode): MenuTreeNode {
    return {
        key: node.id,
        title: (
            <span>
        {node.name}
                <Tag style={{marginLeft: 6}}>{node.type === 'DIRECTORY' ? '目录' : '页面'}</Tag>
                {node.requiredPermission ? <Tag>{node.requiredPermission}</Tag> : null}
      </span>
        ),
        children: (node.children ?? []).map(toTreeData),
    };
}
