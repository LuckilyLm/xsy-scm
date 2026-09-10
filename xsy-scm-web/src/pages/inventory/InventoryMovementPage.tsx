import type {ProColumns} from '@ant-design/pro-components';
import {ProTable} from '@ant-design/pro-components';
import {Button} from 'antd';
import {useNavigate} from 'react-router-dom';
import {fetchInventoryMovements} from '../../api/inventory';
import {PageContainer} from '../../components/common/PageContainer';
import type {InventoryMovement} from '../../types/inventory';

export function InventoryMovementPage() {
    const nav = useNavigate();
    const columns: ProColumns<InventoryMovement>[] = [{title: '流水号', dataIndex: 'movementNo'}, {
        title: '发生时间',
        dataIndex: 'occurredAt'
    }, {title: '仓库', dataIndex: 'warehouseNameSnapshot'}, {
        title: 'SKU',
        dataIndex: 'skuCodeSnapshot'
    }, {title: '类型', dataIndex: 'movementType'}, {
        title: '来源收货单',
        render: (_, r) => <Button type="link"
                                  onClick={() => nav(`/purchases/receipts/${r.sourceDocumentId}`)}>{r.sourceDocumentId}</Button>
    }, {title: '变动前', dataIndex: 'quantityBefore', align: 'right'}, {
        title: '变动量',
        dataIndex: 'quantityChange',
        align: 'right'
    }, {title: '变动后', dataIndex: 'quantityAfter', align: 'right'}, {title: '单位', dataIndex: 'unit'}];
    return <PageContainer><ProTable rowKey="id" search={false} options={false} columns={columns} request={async () => {
        const rows = await fetchInventoryMovements();
        return {data: rows, total: rows.length, success: true};
    }}/></PageContainer>;
}
