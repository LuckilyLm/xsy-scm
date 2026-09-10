import { Alert, Button, Checkbox, Drawer, Empty, Input, Skeleton, Space, Tag, message } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { fetchPermissions } from '../../api/system/permissions';
import { fetchRolePermissions, replaceRolePermissions } from '../../api/system/grants';
import type { PermissionItem, Role, RolePermissionsGrant } from '../../types/system';
import { describeError } from './systemUtils';

interface RolePermissionsDrawerProps {
  open: boolean;
  role: Role | null;
  onClose: () => void;
  onSuccess: () => void;
}

const MAX_GRANT_ITEMS = 100;

/** 角色—权限分配：后端按 version 原子全量替换，最终集合决定该角色可执行的能力。 */
export function RolePermissionsDrawer({ open, role, onClose, onSuccess }: RolePermissionsDrawerProps) {
  const [permissions, setPermissions] = useState<PermissionItem[]>([]);
  const [grant, setGrant] = useState<RolePermissionsGrant | null>(null);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [keyword, setKeyword] = useState('');
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
      const [page, current] = await Promise.all([
        fetchPermissions({ page: 1, pageSize: MAX_GRANT_ITEMS, status: 'ENABLED' }),
        fetchRolePermissions(role.id),
      ]);
      setPermissions(page.records);
      setGrant(current);
      setSelectedIds(current.permissions.map((item) => item.id));
    } catch (error) {
      setErrorMessage(describeError(error, '权限数据加载失败'));
    } finally {
      setLoading(false);
    }
  }, [role]);

  useEffect(() => {
    if (open) {
      setKeyword('');
      void load();
    }
  }, [load, open]);

  /** 已停用但仍被授予的权限无法再次提交，需要提前告知会被移除。 */
  const droppedCount = grant
    ? grant.permissions.filter((item) => !permissions.some((p) => p.id === item.id)).length
    : 0;

  const grouped = useMemo(() => {
    const normalized = keyword.trim().toLowerCase();
    const filtered = normalized
      ? permissions.filter(
          (item) =>
            item.permissionCode.toLowerCase().includes(normalized) ||
            item.name.toLowerCase().includes(normalized),
        )
      : permissions;
    const map = new Map<string, PermissionItem[]>();
    for (const item of filtered) {
      const list = map.get(item.module) ?? [];
      list.push(item);
      map.set(item.module, list);
    }
    return [...map.entries()];
  }, [keyword, permissions]);

  async function submit() {
    if (!role || !grant) {
      return;
    }
    setSubmitting(true);
    setErrorMessage(null);
    try {
      await replaceRolePermissions(role.id, selectedIds, grant.version);
      message.success('权限分配已更新，相关用户会话已失效');
      onSuccess();
    } catch (error) {
      setErrorMessage(describeError(error, '权限分配失败，请刷新后重试'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Drawer
      onClose={onClose}
      open={open}
      title={role ? `分配权限 — ${role.name}` : '分配权限'}
      width={560}
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
        message="提交后以选中结果整体替换原有权限，最多 100 项。变更后拥有该角色的用户会立即失效并要求重新登录。"
      />
      {droppedCount > 0 ? (
        <Alert
          style={{ marginBottom: 16 }}
          type="warning"
          showIcon
          message={`该角色当前包含 ${droppedCount} 个已停用权限，保存后将被移除`}
        />
      ) : null}
      <Input.Search
        allowClear
        onChange={(event) => setKeyword(event.target.value)}
        placeholder="搜索权限编码或名称"
        style={{ marginBottom: 12 }}
        value={keyword}
      />
      {loading ? (
        <Skeleton active />
      ) : grouped.length === 0 ? (
        <Empty description="没有可分配的启用权限" />
      ) : (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          {grouped.map(([module, items]) => (
            <div key={module}>
              <div style={{ marginBottom: 6, color: '#4e5969', fontWeight: 500 }}>{module}</div>
              <Checkbox.Group
                onChange={(value) => {
                  const next = value as number[];
                  const moduleIds = items.map((item) => item.id);
                  setSelectedIds((previous) => [
                    ...previous.filter((id) => !moduleIds.includes(id)),
                    ...next,
                  ]);
                }}
                style={{ width: '100%' }}
                value={selectedIds.filter((id) => items.some((item) => item.id === id))}
              >
                <Space direction="vertical" size={6} style={{ width: '100%' }}>
                  {items.map((item) => (
                    <Checkbox key={item.id} value={item.id}>
                      <Space size={6}>
                        <span>{item.name}</span>
                        <Tag>{item.permissionCode}</Tag>
                        <Tag>{item.type}</Tag>
                        {item.systemPermission ? <Tag color="orange">系统权限</Tag> : null}
                      </Space>
                    </Checkbox>
                  ))}
                </Space>
              </Checkbox.Group>
            </div>
          ))}
        </Space>
      )}
      {errorMessage ? (
        <Alert style={{ marginTop: 16 }} type="error" showIcon message={errorMessage} />
      ) : null}
    </Drawer>
  );
}
