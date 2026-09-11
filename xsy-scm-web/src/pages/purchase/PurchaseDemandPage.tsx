import type {ActionType, ProColumns} from '@ant-design/pro-components';
import {ProTable} from '@ant-design/pro-components';
import {Alert, Button, Checkbox, DatePicker, Form, Modal, Space, Table, Typography, message} from 'antd';
import {useRef, useState} from 'react';
import dayjs from 'dayjs';
import {fetchPurchaseDemands, generatePurchaseDemands, previewPurchaseDemandGeneration} from '../../api/purchases';
import {fetchWarehouses} from '../../api/suppliers';
import {toGenerationPayload, type PurchaseDemandGenerationValues} from './purchaseDemandGenerationModel';
import type {PurchaseDemandGenerationPreview} from '../../types/purchase';
import {useEffect} from 'react';
import {useNavigate} from 'react-router-dom';
import {AUTHORITIES} from '../../auth/authorities';
import {Permission} from '../../auth/Permission';
import {PageContainer} from '../../components/common/PageContainer';
import type {PurchaseDemand} from '../../types/purchase';
import {subtractDecimal} from '../../utils/decimal';

export function PurchaseDemandPage() {
    const action = useRef<ActionType>(null);
    const navigate = useNavigate();
    const [error, setError] = useState(false);
    const [selectedIds, setSelectedIds] = useState<number[]>([]);
    const [generationOpen, setGenerationOpen] = useState(false);
    const [preview, setPreview] = useState<PurchaseDemandGenerationPreview>();
    const [warehouses, setWarehouses] = useState<Array<{id: number; name: string}>>([]);
    const [form] = Form.useForm<PurchaseDemandGenerationValues>();

    useEffect(() => { fetchWarehouses().then(setWarehouses).catch(() => undefined); }, []);

    const openGeneration = () => { form.setFieldsValue({calculateInventory: true, timeRange: [dayjs().startOf('day'), dayjs().add(1, 'day').startOf('day')]}); setGenerationOpen(true); };
    const previewGeneration = async () => { const values = await form.validateFields(); setPreview(await previewPurchaseDemandGeneration(toGenerationPayload(values))); };
    const generate = async () => { if (!preview) return; const values = await form.validateFields(); await generatePurchaseDemands(toGenerationPayload(values), `purchase-demand-${Date.now()}`); message.success('采购需求生成成功'); setGenerationOpen(false); setPreview(undefined); action.current?.reload(); };
    const generationModal = <Modal title="按时间段生成采购需求" open={generationOpen} onCancel={() => {setGenerationOpen(false); setPreview(undefined);}} onOk={generate} okText="确认生成" width={900}>
        <Form form={form} layout="inline">
            <Form.Item name="warehouseId" label="仓库" rules={[{required: true}]}><select style={{height: 32, minWidth: 160}}>{warehouses.map(w => <option key={w.id} value={w.id}>{w.name}</option>)}</select></Form.Item>
            <Form.Item name="timeRange" label="统计时间段" rules={[{required: true}]}><DatePicker.RangePicker showTime /></Form.Item>
            <Form.Item name="calculateInventory" valuePropName="checked"><Checkbox>计算库存</Checkbox></Form.Item>
        </Form>
        <Button style={{marginTop: 16}} onClick={previewGeneration}>预览</Button>
        {preview && <><Typography.Paragraph>销售订单 {preview.sourceOrderCount} 单，原始需求 {preview.originalQuantity}，库存抵扣 {preview.inventoryDeductionQuantity}，建议采购 {preview.suggestedPurchaseQuantity}</Typography.Paragraph><Table size="small" rowKey="salesOrderItemId" pagination={false} dataSource={preview.items} columns={[{title: '销售单', dataIndex: 'salesOrderNo'}, {title: 'SKU', dataIndex: 'skuCode'}, {title: '原始需求', dataIndex: 'originalQuantity'}, {title: '库存抵扣', dataIndex: 'inventoryDeductionQuantity'}, {title: '建议采购', dataIndex: 'suggestedPurchaseQuantity'}]} /></>}
    </Modal>;

    const columns: ProColumns<PurchaseDemand>[] = [
        {title: '来源销售单', dataIndex: 'salesOrderNoSnapshot'},
        {title: 'SKU', dataIndex: 'skuCodeSnapshot'},
        {title: '商品', dataIndex: 'productNameSnapshot'},
        {title: '需求数量', dataIndex: 'requiredQuantity', align: 'right'},
        {title: '已分配', dataIndex: 'allocatedQuantity', align: 'right'},
        {
            title: '待分配',
            align: 'right',
            render: (_, row) => subtractDecimal(row.requiredQuantity, row.allocatedQuantity),
        },
        {title: '单位', dataIndex: 'purchaseUnitSnapshot'},
        {title: '状态', dataIndex: 'status'},
    ];

    const createOrder = () => {
        const query = new URLSearchParams();
        selectedIds.forEach((id) => query.append('demandId', String(id)));
        navigate(`/purchases/orders/new?${query.toString()}`);
    };

    return (
        <PageContainer>
            <Space direction="vertical" style={{width: '100%'}}>
                <Permission authority={AUTHORITIES.purchaseManage}><Button onClick={openGeneration}>按时间段生成采购需求</Button></Permission>
                {generationModal}
                {error && (
                    <Alert
                        type="error"
                        showIcon
                        message="采购需求加载失败"
                        action={<Button onClick={() => action.current?.reload()}>重试</Button>}
                    />
                )}
                <ProTable
                    actionRef={action}
                    rowKey="id"
                    search={false}
                    options={false}
                    columns={columns}
                    rowSelection={{
                        selectedRowKeys: selectedIds,
                        onChange: (keys) => setSelectedIds(keys.map(Number)),
                        getCheckboxProps: (row) => ({
                            disabled: subtractDecimal(row.requiredQuantity, row.allocatedQuantity) === '0.0000',
                        }),
                    }}
                    tableAlertOptionRender={() => (
                        <Permission authority={AUTHORITIES.purchaseManage}>
                            <Button type="primary" disabled={!selectedIds.length} onClick={createOrder}>
                                分组预览并创建采购单
                            </Button>
                        </Permission>
                    )}
                    request={async () => {
                        try {
                            const rows = await fetchPurchaseDemands();
                            setError(false);
                            return {data: rows, total: rows.length, success: true};
                        } catch {
                            setError(true);
                            return {data: [], total: 0, success: true};
                        }
                    }}
                />
            </Space>
        </PageContainer>
    );
}
