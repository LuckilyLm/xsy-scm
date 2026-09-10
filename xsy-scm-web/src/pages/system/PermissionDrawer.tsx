import {Alert, AutoComplete, Button, Drawer, Form, Input, Radio} from 'antd';
import {useEffect, useState} from 'react';
import {createPermission, updatePermission} from '../../api/system/permissions';
import type {PermissionInput, PermissionItem} from '../../types/system';
import {describeError} from './systemUtils';

interface PermissionForm {
    permissionCode: string;
    name: string;
    type: 'PAGE' | 'ACTION' | 'API';
    module: string;
}

interface PermissionDrawerProps {
    open: boolean;
    value: PermissionItem | null;
    /** 已有模块名，用于下拉建议；新权限编码同样受后端唯一约束。 */
    modules: string[];
    onClose: () => void;
    onSuccess: () => void;
}

const TYPE_OPTIONS = [
    {value: 'API', label: 'API'},
    {value: 'PAGE', label: '页面'},
    {value: 'ACTION', label: '按钮'},
];

export function PermissionDrawer({open, value, modules, onClose, onSuccess}: PermissionDrawerProps) {
    const [form] = Form.useForm<PermissionForm>();
    const [submitting, setSubmitting] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    useEffect(() => {
        if (!open) {
            return;
        }
        setErrorMessage(null);
        form.setFieldsValue({
            permissionCode: value?.permissionCode ?? '',
            name: value?.name ?? '',
            type: value?.type ?? 'API',
            module: value?.module ?? '',
        });
    }, [form, open, value]);

    async function submit() {
        let values: PermissionForm;
        try {
            values = await form.validateFields();
        } catch {
            return;
        }
        setSubmitting(true);
        setErrorMessage(null);
        try {
            const payload: PermissionInput = {
                permissionCode: values.permissionCode.trim(),
                name: values.name.trim(),
                type: values.type,
                module: values.module.trim(),
            };
            if (value) {
                await updatePermission(value.id, {...payload, version: value.version});
            } else {
                await createPermission(payload);
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
            title={value ? '编辑权限' : '新增权限'}
            width={420}
            destroyOnHidden
            footer={
                <div style={{display: 'flex', justifyContent: 'flex-end', gap: 8}}>
                    <Button onClick={onClose}>取消</Button>
                    <Button loading={submitting} onClick={() => void submit()} type="primary">
                        保存
                    </Button>
                </div>
            }
        >
            {value?.systemPermission ? (
                <Alert
                    style={{marginBottom: 16}}
                    type="warning"
                    showIcon
                    message="系统权限由迁移脚本维护，改名可能导致后端授权失效"
                />
            ) : null}
            <Form form={form} layout="vertical" disabled={submitting} preserve={false}>
                <Form.Item
                    label="权限编码"
                    name="permissionCode"
                    extra="业务模块使用 product.read 形式；系统模块使用 system:user:list 形式"
                    rules={[
                        {required: true, message: '请输入权限编码'},
                        {max: 160, message: '最多 160 个字符'},
                    ]}
                >
                    <Input placeholder="例如：inventory.read"/>
                </Form.Item>
                <Form.Item
                    label="权限名称"
                    name="name"
                    rules={[
                        {required: true, message: '请输入权限名称'},
                        {max: 100, message: '最多 100 个字符'},
                    ]}
                >
                    <Input placeholder="请输入权限名称"/>
                </Form.Item>
                <Form.Item label="类型" name="type" rules={[{required: true, message: '请选择类型'}]}>
                    <Radio.Group optionType="button" options={TYPE_OPTIONS}/>
                </Form.Item>
                <Form.Item
                    label="所属模块"
                    name="module"
                    rules={[
                        {required: true, message: '请输入所属模块'},
                        {max: 64, message: '最多 64 个字符'},
                    ]}
                >
                    <AutoComplete
                        options={modules.map((item) => ({value: item, label: item}))}
                        placeholder="选择或输入模块名"
                    />
                </Form.Item>
            </Form>
            {errorMessage ? <Alert type="error" showIcon message={errorMessage}/> : null}
        </Drawer>
    );
}
