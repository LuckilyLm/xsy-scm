import { Alert, Button, Checkbox, Drawer, Empty, Skeleton, Space, Tag, message } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { fetchRoles } from '../../api/system/roles';
import { fetchUserRoles, replaceUserRoles } from '../../api/system/grants';
import type { Role, SystemUser, UserRolesGrant } from '../../types/system';
import { describeError } from './systemUtils';

interface UserRolesDrawerProps {
  open: boolean;
  user: SystemUser | null;
  onClose: () => void;
  onSuccess: () => void;
}

const MAX_GRANT_ITEMS = 100;

/** 用户—角色分配：后端按 version 原子全量替换，因此提交的是最终角色集合。 */
export function UserRolesDrawer({ open, user, onClose, onSuccess }: UserRolesDrawerProps) {
  const [roles, setRoles] = useState<Role[]>([]);
  const [grant, setGrant] = useState<UserRolesGrant | null>(null);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!user) {
      return;
    }
    setLoading(true);
    setErrorMessage(null);
    try {
      const [rolePage, current] = await Promise.all([
        fetchRoles({ page: 1, pageSize: MAX_GRANT_ITEMS }),
        fetchUserRoles(user.id),
      ]);
      setRoles(rolePage.records);
      setGrant(current);
      setSelectedIds(current.roles.map((role) => role.id));
    } catch (error) {
      setErrorMessage(describeError(error, '角色数据加载失败'));
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    if (open) {
      void load();
    }
  }, [load, open]);

  const disabledIds = useMemo(
    () => new Set(roles.filter((role) => role.status !== 'ENABLED').map((role) => role.id)),
    [roles],
  );

  async function submit() {
    if (!user || !grant) {
      return;
    }
    setSubmitting(true);
    setErrorMessage(null);
    try {
      await replaceUserRoles(user.id, selectedIds, grant.version);
      message.success('角色分配已更新，相关会话已失效');
      onSuccess();
    } catch (error) {
      setErrorMessage(describeError(error, '角色分配失败，请刷新后重试'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Drawer
      onClose={onClose}
      open={open}
      title={user ? `分配角色 — ${user.displayName}` : '分配角色'}
      width={480}
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button onClick={onClose}>取消</Button>
          <Button loading={submitting} onClick={() => void submit()} type="primary">
            保存
          </Button>
        </div>
      }
    >
      <Alert
        style={{ marginBottom: 16 }}
        type="info"
        showIcon
        message="提交后以选中结果整体替换原有角色，最多 100 项。变更后该用户的所有登录会话立即失效。"
      />
      {loading ? (
        <Skeleton active />
      ) : roles.length === 0 ? (
        <Empty description="暂无可选角色，请先在角色管理中创建" />
      ) : (
        <Checkbox.Group
          onChange={(value) => setSelectedIds(value as number[])}
          style={{ width: '100%' }}
          value={selectedIds}
        >
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            {roles.map((role) => (
              <Checkbox key={role.id} disabled={disabledIds.has(role.id)} value={role.id}>
                <Space size={6}>
                  <span>{role.name}</span>
                  <Tag>{role.roleCode}</Tag>
                  {role.status === 'ENABLED' ? null : <Tag color="default">停用</Tag>}
                  {role.systemRole ? <Tag color="orange">系统角色</Tag> : null}
                </Space>
              </Checkbox>
            ))}
          </Space>
        </Checkbox.Group>
      )}
      {errorMessage ? (
        <Alert style={{ marginTop: 16 }} type="error" showIcon message={errorMessage} />
      ) : null}
    </Drawer>
  );
}
