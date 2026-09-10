import { Alert, Button, Drawer, Form, Input, InputNumber, TreeSelect } from 'antd';
import { useEffect, useState } from 'react';
import { createDepartment, updateDepartment } from '../../api/system/departments';
import type { Department, DepartmentTreeNode } from '../../types/system';
import { collectDescendantIds, describeError, toDepartmentTreeData } from './systemUtils';

interface DepartmentForm {
  code: string;
  name: string;
  parentId?: number | null;
  sortOrder: number;
}

interface DepartmentDrawerProps {
  open: boolean;
  /** 传值即编辑，空即新增。 */
  value: Department | null;
  /** 新增时预设的上级部门。 */
  parentId?: number | null;
  tree: DepartmentTreeNode[];
  onClose: () => void;
  onSuccess: () => void;
}

export function DepartmentDrawer({
  open,
  value,
  parentId = null,
  tree,
  onClose,
  onSuccess,
}: DepartmentDrawerProps) {
  const [form] = Form.useForm<DepartmentForm>();
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    setErrorMessage(null);
    form.setFieldsValue({
      code: value?.code ?? '',
      name: value?.name ?? '',
      parentId: value?.parentId ?? parentId ?? null,
      sortOrder: value?.sortOrder ?? 0,
    });
  }, [form, open, parentId, value]);

  // 编辑时排除自身及后代，服务端仍会二次校验，这里只是提前避免明显非法选择。
  const excluded = value ? new Set(collectDescendantIds(findNode(tree, value.id) ?? { ...value, children: [] })) : undefined;

  async function submit() {
    let values: DepartmentForm;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    setSubmitting(true);
    setErrorMessage(null);
    try {
      const payload = {
        code: values.code.trim(),
        name: values.name.trim(),
        parentId: values.parentId ?? null,
        sortOrder: values.sortOrder ?? 0,
      };
      if (value) {
        await updateDepartment(value.id, { ...payload, version: value.version });
      } else {
        await createDepartment(payload);
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
      title={value ? '编辑部门' : '新增部门'}
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
          label="上级部门"
          name="parentId"
          extra="留空表示一级部门"
        >
          <TreeSelect
            allowClear
            placeholder="请选择上级部门"
            treeData={toDepartmentTreeData(tree, excluded)}
            treeDefaultExpandAll
          />
        </Form.Item>
        <Form.Item
          label="部门编码"
          name="code"
          rules={[
            { required: true, message: '请输入部门编码' },
            { max: 64, message: '最多 64 个字符' },
          ]}
        >
          <Input placeholder="请输入部门编码" />
        </Form.Item>
        <Form.Item
          label="部门名称"
          name="name"
          rules={[
            { required: true, message: '请输入部门名称' },
            { max: 100, message: '最多 100 个字符' },
          ]}
        >
          <Input placeholder="请输入部门名称" />
        </Form.Item>
        <Form.Item label="排序" name="sortOrder" rules={[{ required: true, message: '请输入排序值' }]}>
          <InputNumber min={0} precision={0} style={{ width: '100%' }} />
        </Form.Item>
      </Form>
      {errorMessage ? <Alert type="error" showIcon message={errorMessage} /> : null}
    </Drawer>
  );
}

function findNode(nodes: DepartmentTreeNode[], id: number): DepartmentTreeNode | null {
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
