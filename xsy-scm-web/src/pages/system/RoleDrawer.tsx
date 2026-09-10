import { Alert, Button, Drawer, Form, Input } from 'antd';
import { useEffect, useState } from 'react';
import { createRole, updateRole } from '../../api/system/roles';
import type { Role } from '../../types/system';
import { describeError } from './systemUtils';

interface RoleForm {
  roleCode: string;
  name: string;
  description?: string;
}

interface RoleDrawerProps {
  open: boolean;
  value: Role | null;
  onClose: () => void;
  onSuccess: () => void;
}

const ROLE_CODE_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$/;

export function RoleDrawer({ open, value, onClose, onSuccess }: RoleDrawerProps) {
  const [form] = Form.useForm<RoleForm>();
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    setErrorMessage(null);
    form.setFieldsValue({
      roleCode: value?.roleCode ?? '',
      name: value?.name ?? '',
      description: value?.description ?? '',
    });
  }, [form, open, value]);

  async function submit() {
    let values: RoleForm;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    setSubmitting(true);
    setErrorMessage(null);
    try {
      const payload = {
        roleCode: values.roleCode.trim(),
        name: values.name.trim(),
        description: values.description?.trim() ? values.description.trim() : null,
      };
      if (value) {
        await updateRole(value.id, { ...payload, version: value.version });
      } else {
        await createRole(payload);
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
      title={value ? '编辑角色' : '新增角色'}
      width={420}
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
        <Form.Item
          label="角色编码"
          name="roleCode"
          extra="字母或数字开头，可包含字母、数字、点、下划线和中划线"
          rules={[
            { required: true, message: '请输入角色编码' },
            { pattern: ROLE_CODE_PATTERN, message: '编码格式不符合要求' },
          ]}
        >
          <Input placeholder="例如：purchase.operator" />
        </Form.Item>
        <Form.Item
          label="角色名称"
          name="name"
          rules={[
            { required: true, message: '请输入角色名称' },
            { max: 100, message: '最多 100 个字符' },
          ]}
        >
          <Input placeholder="请输入角色名称" />
        </Form.Item>
        <Form.Item label="描述" name="description" rules={[{ max: 500, message: '最多 500 个字符' }]}>
          <Input.TextArea placeholder="选填" rows={3} />
        </Form.Item>
      </Form>
      {errorMessage ? <Alert type="error" showIcon message={errorMessage} /> : null}
    </Drawer>
  );
}
