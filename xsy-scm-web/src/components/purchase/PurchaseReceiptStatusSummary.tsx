import {Descriptions, Tag} from 'antd';
import type {PurchaseReceipt} from '../../types/purchase';

const modeLabels = {DIRECT: '直接入库', DEFERRED: '二次确认入库'} as const;

export function PurchaseReceiptStatusSummary({receipt}: {receipt: PurchaseReceipt}) {
    const pending = receipt.putawayStatus === 'PENDING_PUTAWAY';
    return <Descriptions size="small" column={2} bordered>
        <Descriptions.Item label="收货模式">{receipt.mode ? modeLabels[receipt.mode] : '未设置'}</Descriptions.Item>
        <Descriptions.Item label="入库状态">
            {pending ? <Tag color="orange">待入库</Tag> : receipt.putawayStatus === 'PUTAWAY_COMPLETED' ? <Tag color="green">已入库</Tag> : <Tag>无需二次入库</Tag>}
        </Descriptions.Item>
        {receipt.plannedQuantity !== undefined && <Descriptions.Item label="计划数量">{receipt.plannedQuantity}</Descriptions.Item>}
        {receipt.arrivedQuantity !== undefined && <Descriptions.Item label="本次到货">{receipt.arrivedQuantity}</Descriptions.Item>}
        {receipt.remainingQuantity !== undefined && <Descriptions.Item label="剩余数量">{receipt.remainingQuantity}</Descriptions.Item>}
        {receipt.differenceQuantity !== undefined && <Descriptions.Item label="少收差异">{receipt.differenceQuantity}</Descriptions.Item>}
        {receipt.overReceivedQuantity !== undefined && <Descriptions.Item label="超收数量">{receipt.overReceivedQuantity}</Descriptions.Item>}
    </Descriptions>;
}
