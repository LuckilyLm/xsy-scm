import {Alert, Button, Drawer, Form, Input, InputNumber, Radio, TreeSelect} from 'antd';
import {useEffect, useState} from 'react';
import {createCategory, updateCategory} from '../../api/product-categories';
import type {ProductCategoryInput, ProductCategoryTreeNode} from '../../types/product';
import {describeError, STATUS_OPTIONS, type TreeOption} from '../system/systemUtils';

interface ProductCategoryForm {
    categoryCode: string;
    name: string;
    parentId?: number | null;
    sortOrder: number;
    status: 'ENABLED' | 'DISABLED';
}

interface ProductCategoryDrawerProps {
    open: boolean;
    /** 传值即编辑，空即新增。 */
    value: ProductCategoryTreeNode | null;
    /** 新增时预设的上级分类。 */
    parentId?: number | null;
    tree: ProductCategoryTreeNode[];
    onClose: () => void;
    onSuccess: () => void;
}

export function ProductCategoryDrawer({
                                          open,
                                          value,
                                          parentId = null,
                                          tree,
                                          onClose,
                                          onSuccess,
                                      }: ProductCategoryDrawerProps) {
    const [form] = Form.useForm<ProductCategoryForm>();
    const [submitting, setSubmitting] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    useEffect(() => {
        if (!open) {
            return;
        }
        setErrorMessage(null);
        form.setFieldsValue({
            categoryCode: value?.categoryCode ?? '',
            name: value?.name ?? '',
            parentId: value?.parentId ?? parentId ?? null,
            sortOrder: value?.sortOrder ?? 0,
            status: value?.status ?? 'ENABLED',
        });
    }, [form, open, parentId, value]);

    // 编辑时排除自身及后代，服务端仍会二次校验，这里只是提前避免明显非法选择。
    const excluded = value
        ? new Set(collectCategoryDescendantIds(findCategoryNode(tree, value.id) ?? {id: value.id, children: []}))
        : undefined;

    async function submit() {
        let values: ProductCategoryForm;
        try {
            values = await form.validateFields();
        } catch {
            return;
        }
        setSubmitting(true);
        setErrorMessage(null);
        try {
            const payload: ProductCategoryInput = {
                categoryCode: values.categoryCode.trim(),
                name: values.name.trim(),
                parentId: values.parentId ?? null,
                sortOrder: values.sortOrder ?? 0,
                status: values.status,
            };
            if (value) {
                await updateCategory(value.id, payload);
            } else {
                await createCategory(payload);
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
            title={value ? '编辑分类' : '新增分类'}
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
            <Form form={form} layout="vertical" disabled={submitting} preserve={false}>
                <Form.Item label="上级分类" name="parentId" extra="留空表示一级分类">
                    <TreeSelect
                        allowClear
                        placeholder="请选择上级分类"
                        treeData={toCategoryTreeData(tree, excluded)}
                        treeDefaultExpandAll
                    />
                </Form.Item>
                <Form.Item
                    label="分类编码"
                    name="categoryCode"
                    rules={[
                        {required: true, message: '请输入分类编码'},
                        {max: 64, message: '最多 64 个字符'},
                    ]}
                >
                    <Input placeholder="请输入分类编码"/>
                </Form.Item>
                <Form.Item
                    label="分类名称"
                    name="name"
                    rules={[
                        {required: true, message: '请输入分类名称'},
                        {max: 100, message: '最多 100 个字符'},
                    ]}
                >
                    <Input placeholder="请输入分类名称"/>
                </Form.Item>
                <Form.Item label="排序" name="sortOrder" rules={[{required: true, message: '请输入排序值'}]}>
                    <InputNumber min={0} precision={0} style={{width: '100%'}}/>
                </Form.Item>
                <Form.Item label="状态" name="status" rules={[{required: true, message: '请选择状态'}]}>
                    <Radio.Group optionType="button" options={STATUS_OPTIONS}/>
                </Form.Item>
            </Form>
            {errorMessage ? <Alert type="error" showIcon message={errorMessage}/> : null}
        </Drawer>
    );
}

/** 商品分类树转 TreeSelect 数据源；编辑时排除自身及后代，防止成环。 */
function toCategoryTreeData(nodes: ProductCategoryTreeNode[], excludeIds?: Set<number>): TreeOption[] {
    return nodes
        .filter((node) => !excludeIds?.has(node.id))
        .map((node) => ({
            value: node.id,
            title: node.status === 'DISABLED' ? `${node.name}（停用）` : node.name,
            children: toCategoryTreeData(node.children ?? [], excludeIds),
        }));
}

function collectCategoryDescendantIds(node: Pick<ProductCategoryTreeNode, 'id' | 'children'>): number[] {
    const result: number[] = [node.id];
    for (const child of node.children ?? []) {
        result.push(...collectCategoryDescendantIds(child));
    }
    return result;
}

function findCategoryNode(nodes: ProductCategoryTreeNode[], id: number): ProductCategoryTreeNode | null {
    for (const node of nodes) {
        if (node.id === id) {
            return node;
        }
        const found = findCategoryNode(node.children ?? [], id);
        if (found) {
            return found;
        }
    }
    return null;
}
