import {PlusOutlined} from '@ant-design/icons';
import {DrawerForm} from '@ant-design/pro-components';
import {useQuery} from '@tanstack/react-query';
import {Alert, Button, Cascader, Form, Input, Radio, Skeleton} from 'antd';
import {useEffect, useMemo, useState} from 'react';
import {
    createProduct,
    fetchCategoryTree,
    fetchProduct,
    updateProduct,
} from '../../api/products';
import {ApiError} from '../../api/http';
import type {ProductCategoryTreeNode, ShelfStatus} from '../../types/product';
import {SkuEditableTable} from './SkuEditableTable';
import {
    addSku,
    createEmptyProductForm,
    normalizeProductPayload,
    productDetailToForm,
    removeSku,
    selectDefaultSku,
} from './productFormModel';
import type {ProductForm} from './productFormModel';
import styles from './ProductDrawer.module.css';

interface ProductDrawerProps {
    open: boolean;
    productId: number | null;
    onOpenChange: (open: boolean) => void;
    onSaved: () => void;
}

interface CategoryOption {
    value: number;
    label: string;
    disabled?: boolean;
    children?: CategoryOption[];
}

function toCategoryOptions(nodes: ProductCategoryTreeNode[]): CategoryOption[] {
    return nodes.map((node) => ({
        value: node.id,
        label: node.name,
        disabled: node.status !== 'ENABLED' || (node.children.length === 0 && node.level !== 3),
        children: node.children.length ? toCategoryOptions(node.children) : undefined,
    }));
}

function findCategoryPath(nodes: ProductCategoryTreeNode[], targetId: number, path: number[] = []): number[] {
    for (const node of nodes) {
        const current = [...path, node.id];
        if (node.id === targetId) return current;
        const childPath = findCategoryPath(node.children, targetId, current);
        if (childPath.length) return childPath;
    }
    return [];
}

function validateForm(form: ProductForm) {
    if (!form.spuCode.trim()) return '请输入 SPU 编码';
    if (!form.name.trim()) return '请输入商品名称';
    if (form.categoryId === null) return '请选择三级商品分类';
    if (form.skus.length === 0) return '商品至少需要一个 SKU';
    if (form.skus.filter((sku) => sku.defaultSku).length !== 1) return '必须设置且只能设置一个默认 SKU';
    for (const sku of form.skus) {
        if (!sku.skuCode.trim()) return '请输入 SKU 编码';
        if (!sku.specName.trim()) return '请输入规格名称';
        if (!sku.saleUnit.trim()) return '请输入销售单位';
        if (!/^\d+(\.\d{1,4})?$/.test(sku.marketPrice.trim())) return '市场价应为非负数字，最多四位小数';
    }
    return null;
}

export function ProductDrawer({open, productId, onOpenChange, onSaved}: ProductDrawerProps) {
    const [form, setForm] = useState<ProductForm>(() => createEmptyProductForm());
    const [formError, setFormError] = useState<string | null>(null);
    const [pending, setPending] = useState(false);
    const categories = useQuery({
        queryKey: ['product-categories'],
        queryFn: fetchCategoryTree,
        enabled: open,
    });
    const detail = useQuery({
        queryKey: ['product', productId],
        queryFn: () => fetchProduct(productId!),
        enabled: open && productId !== null,
    });
    const categoryOptions = useMemo(
        () => toCategoryOptions(categories.data ?? []),
        [categories.data],
    );
    const categoryPath = useMemo(() => {
        if (form.categoryId === null) return [];
        return findCategoryPath(categories.data ?? [], form.categoryId).length
            ? findCategoryPath(categories.data ?? [], form.categoryId)
            : [form.categoryId];
    }, [categories.data, form.categoryId]);

    useEffect(() => {
        if (!open) return;
        setFormError(null);
        if (productId === null) {
            setForm(createEmptyProductForm());
        } else if (detail.data) {
            setForm(productDetailToForm(detail.data));
        }
    }, [detail.data, open, productId]);

    function updateField<K extends keyof ProductForm>(field: K, value: ProductForm[K]) {
        setForm((current) => ({...current, [field]: value}));
    }

    async function submit() {
        const validationError = validateForm(form);
        if (validationError) {
            setFormError(validationError);
            return false;
        }
        setPending(true);
        setFormError(null);
        try {
            const payload = normalizeProductPayload(form);
            if (productId === null) {
                await createProduct(payload);
            } else {
                await updateProduct(productId, payload);
            }
            onSaved();
            onOpenChange(false);
            setForm(createEmptyProductForm());
            return true;
        } catch (error) {
            setFormError(
                error instanceof ApiError && error.status === 409
                    ? '数据已被其他人修改，请刷新详情后重试'
                    : error instanceof Error
                        ? error.message
                        : '保存失败，请稍后重试',
            );
            return false;
        } finally {
            setPending(false);
        }
    }

    return (
        <DrawerForm
            drawerProps={{
                destroyOnClose: true,
                maskClosable: !pending,
                styles: {body: {padding: 0}},
            }}
            open={open}
            submitter={{
                searchConfig: {submitText: '保存', resetText: '取消'},
                submitButtonProps: {disabled: pending, loading: pending},
                resetButtonProps: {disabled: pending},
            }}
            title={productId === null ? '新增商品' : '编辑商品'}
            width={1080}
            onFinish={submit}
            onOpenChange={(nextOpen) => {
                if (!pending) onOpenChange(nextOpen);
            }}
        >
            {productId !== null && detail.isLoading ? (
                <div className={styles.loading}><Skeleton active/></div>
            ) : (
                <div className={styles.content}>
                    {formError ? <Alert message={formError} showIcon type="error"/> : null}

                    <section className={styles.section}>
                        <h3>基础信息</h3>
                        <div className={styles.basicGrid}>
                            <Form component={false} layout="vertical">
                                <Form.Item label="商品分类" required>
                                    <Cascader
                                        options={categoryOptions}
                                        placeholder="请选择三级商品分类"
                                        showSearch
                                        value={categoryPath}
                                        onChange={(value) => updateField('categoryId', Number(value.at(-1)) || null)}
                                    />
                                </Form.Item>
                                <Form.Item label="商品名称" required>
                                    <Input
                                        aria-label="商品名称"
                                        maxLength={150}
                                        placeholder="如：西红柿"
                                        showCount
                                        value={form.name}
                                        onChange={(event) => updateField('name', event.target.value)}
                                    />
                                </Form.Item>
                                <Form.Item label="SPU 编码" required>
                                    <Input
                                        aria-label="SPU 编码"
                                        maxLength={64}
                                        placeholder="如：VEG-TOMATO"
                                        value={form.spuCode}
                                        onChange={(event) => updateField('spuCode', event.target.value)}
                                    />
                                </Form.Item>
                                <Form.Item label="商品别名">
                                    <Input
                                        aria-label="商品别名"
                                        maxLength={150}
                                        placeholder="选填"
                                        value={form.alias}
                                        onChange={(event) => updateField('alias', event.target.value)}
                                    />
                                </Form.Item>
                                <Form.Item label="商品状态">
                                    <Radio.Group
                                        value={form.status}
                                        onChange={(event) => updateField('status', event.target.value as ShelfStatus)}
                                    >
                                        <Radio value="ON_SHELF">已上架</Radio>
                                        <Radio value="OFF_SHELF">已下架</Radio>
                                    </Radio.Group>
                                </Form.Item>
                                <Form.Item className={styles.description} label="商品简介">
                                    <Input.TextArea
                                        aria-label="商品简介"
                                        maxLength={1000}
                                        placeholder="选填"
                                        rows={3}
                                        showCount
                                        value={form.description}
                                        onChange={(event) => updateField('description', event.target.value)}
                                    />
                                </Form.Item>
                            </Form>
                        </div>
                    </section>

                    <section className={styles.section}>
                        <div className={styles.sectionHeading}>
                            <div>
                                <h3>SKU 规格设置</h3>
                                <span>一个 SPU 可包含多个可交易规格，必须指定一个默认 SKU</span>
                            </div>
                            <Button
                                icon={<PlusOutlined/>}
                                type="primary"
                                onClick={() => setForm((current) => addSku(current))}
                            >
                                新增 SKU
                            </Button>
                        </div>
                        <SkuEditableTable
                            value={form.skus}
                            onChange={(skus) => updateField('skus', skus)}
                            onRemove={(key) => {
                                try {
                                    setForm((current) => removeSku(current, key));
                                } catch (error) {
                                    setFormError(error instanceof Error ? error.message : 'SKU 删除失败');
                                }
                            }}
                            onSelectDefault={(key) => setForm((current) => selectDefaultSku(current, key))}
                        />
                    </section>
                </div>
            )}
        </DrawerForm>
    );
}
