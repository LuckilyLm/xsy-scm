import { Alert, Button, Drawer, Form, Input, InputNumber, Radio, Select, Switch, TreeSelect } from 'antd';
import { useEffect, useState } from 'react';
import { createMenu, updateMenu } from '../../api/system/menus';
import { ROUTE_REGISTRY } from '../../router/routeRegistry';
import type { MenuInput, MenuNode } from '../../types/system';
import { collectMenuIds, describeError, toMenuTreeData } from './systemUtils';

interface MenuForm {
  type: 'DIRECTORY' | 'MENU';
  parentId?: number | null;
  name: string;
  routeKey?: string | null;
  path?: string | null;
  icon?: string | null;
  requiredPermission?: string | null;
  sort: number;
  visible: boolean;
  status: 'ENABLED' | 'DISABLED';
}

interface MenuDrawerProps {
  open: boolean;
  value: MenuNode | null;
  /** 新增时预设的上级菜单。 */
  parentId?: number | null;
  tree: MenuNode[];
  onClose: () => void;
  onSuccess: () => void;
}

/** 路由键只能取受控注册表中的值，数据库不得保存任意前端模块路径。 */
const ROUTE_KEY_OPTIONS = ROUTE_REGISTRY.map((route) => ({
  value: route.key,
  label: `${route.title}（${route.key}）`,
}));

export function MenuDrawer({
  open,
  value,
  parentId = null,
  tree,
  onClose,
  onSuccess,
}: MenuDrawerProps) {
  const [form] = Form.useForm<MenuForm>();
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [type, setType] = useState<'DIRECTORY' | 'MENU'>('MENU');

  useEffect(() => {
    if (!open) {
      return;
    }
    setErrorMessage(null);
    const nextType = value?.type ?? 'MENU';
    setType(nextType);
    form.setFieldsValue({
      type: nextType,
      parentId: value?.parentId ?? parentId ?? null,
      name: value?.name ?? '',
      routeKey: value?.routeKey ?? null,
      path: value?.path ?? null,
      icon: value?.icon ?? '',
      requiredPermission: value?.requiredPermission ?? '',
      sort: value?.sort ?? 0,
      visible: value?.visible ?? true,
      status: value?.status ?? 'ENABLED',
    });
  }, [form, open, parentId, value]);

  const excluded = value ? new Set(collectMenuIds(findNode(tree, value.id) ?? { ...value, children: [] })) : undefined;

  async function submit() {
    let values: MenuForm;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    setSubmitting(true);
    setErrorMessage(null);
    try {
      const payload: MenuInput = {
        type: values.type,
        parentId: values.parentId ?? null,
        name: values.name.trim(),
        routeKey: values.type === 'MENU' ? values.routeKey?.trim() || null : null,
        path: values.type === 'MENU' ? values.path?.trim() || null : null,
        icon: values.icon?.trim() || null,
        requiredPermission: values.requiredPermission?.trim() || null,
        sort: values.sort ?? 0,
        visible: values.visible,
        status: values.status,
      };
      if (value) {
        await updateMenu(value.id, { ...payload, version: value.version });
      } else {
        await createMenu(payload);
      }
      onSuccess();
    } catch (error) {
      setErrorMessage(describeError(error, '保存失败，请稍后重试'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Drawer
      onClose={onClose}
      open={open}
      title={value ? '编辑菜单' : '新增菜单'}
      width={460}
      destroyOnHidden
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button onClick={onClose}>取消</Button>
          <Button loading={submitting} onClick={() => void submit()} type="primary">
            保存
          </Button>
        </div>
      }
    >
      <Form form={form} layout="vertical" disabled={submitting} preserve={false}>
        <Form.Item label="类型" name="type" rules={[{ required: true, message: '请选择类型' }]}>
          <Radio.Group
            onChange={(event) => setType(event.target.value)}
            optionType="button"
            options={[
              { value: 'DIRECTORY', label: '目录' },
              { value: 'MENU', label: '页面' },
            ]}
          />
        </Form.Item>
        <Form.Item label="上级菜单" name="parentId" extra="留空表示一级菜单">
          <TreeSelect
            allowClear
            placeholder="请选择上级菜单"
            treeData={toMenuTreeData(tree, excluded)}
            treeDefaultExpandAll
          />
        </Form.Item>
        <Form.Item
          label="菜单名称"
          name="name"
          rules={[
            { required: true, message: '请输入菜单名称' },
            { max: 100, message: '最多 100 个字符' },
          ]}
        >
          <Input placeholder="请输入菜单名称" />
        </Form.Item>
        {type === 'MENU' ? (
          <>
            <Form.Item
              label="路由键"
              name="routeKey"
              extra="只能选择前端受控注册表中已实现的页面"
              rules={[{ required: true, message: '请选择路由键' }]}
            >
              <Select allowClear options={ROUTE_KEY_OPTIONS} placeholder="请选择路由键" showSearch />
            </Form.Item>
            <Form.Item label="访问路径" name="path" rules={[{ max: 240, message: '最多 240 个字符' }]}>
              <Input placeholder="例如 /products" />
            </Form.Item>
          </>
        ) : null}
        <Form.Item label="所需权限" name="requiredPermission" extra="选填，用于菜单可见性校验">
          <Input placeholder="例如 product.read" />
        </Form.Item>
        <Form.Item label="图标" name="icon" rules={[{ max: 64, message: '最多 64 个字符' }]}>
          <Input placeholder="选填" />
        </Form.Item>
        <Form.Item label="排序" name="sort" rules={[{ required: true, message: '请输入排序值' }]}>
          <InputNumber min={0} precision={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item label="可见" name="visible" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item label="状态" name="status" rules={[{ required: true, message: '请选择状态' }]}>
          <Radio.Group
            optionType="button"
            options={[
              { value: 'ENABLED', label: '启用' },
              { value: 'DISABLED', label: '停用' },
            ]}
          />
        </Form.Item>
      </Form>
      {errorMessage ? <Alert type="error" showIcon message={errorMessage} /> : null}
    </Drawer>
  );
}

function findNode(nodes: MenuNode[], id: number): MenuNode | null {
  for (const node of nodes) {
    if (node.id === id) {
      return node;
    }
    const found = findNode(node.children ?? [], id);
    if (found) {
      return found;
    }
  }
  return null;
}
