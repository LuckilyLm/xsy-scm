import type {ActionType, ProColumns} from '@ant-design/pro-components';
import {ProTable} from '@ant-design/pro-components';
import {Alert, Button, Space} from 'antd';
import {useRef, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {fetchPurchaseDemands} from '../../api/purchases';
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
