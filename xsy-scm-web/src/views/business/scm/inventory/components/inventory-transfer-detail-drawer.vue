<template>
  <a-drawer :open="open" title="调拨单详情" width="860" @close="emit('update:open', false)">
    <a-descriptions :column="2" bordered size="small">
      <a-descriptions-item label="调拨单号">{{ detail.transferNo }}</a-descriptions-item>
      <a-descriptions-item label="状态">
        <a-tag :color="statusColor(detail.status)">{{ detail.statusDesc || detail.status }}</a-tag>
      </a-descriptions-item>
      <a-descriptions-item label="源仓库">{{ detail.fromWarehouseName || '—' }}</a-descriptions-item>
      <a-descriptions-item label="目标仓库">{{ detail.toWarehouseName || '—' }}</a-descriptions-item>
      <a-descriptions-item label="发出人">{{ detail.shippedBy || '—' }}</a-descriptions-item>
      <a-descriptions-item label="发出时间">{{ datetime(detail.shippedAt) }}</a-descriptions-item>
      <a-descriptions-item label="收货人">{{ detail.receivedBy || '—' }}</a-descriptions-item>
      <a-descriptions-item label="收货时间">{{ datetime(detail.receivedAt) }}</a-descriptions-item>
      <a-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</a-descriptions-item>
    </a-descriptions>
    <a-table
        style="margin-top: 12px"
        size="small"
        :data-source="detail.items || []"
        :columns="itemColumns"
        row-key="id"
        bordered
        :pagination="false"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'quantity'">
          <span class="num">{{ quantityText(record.quantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'unitSnapshot'">
          {{ record.unitSnapshot || '（草稿未发出）' }}
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
    <a-typography-text v-if="detail.status === 'SHIPPED'" type="secondary" style="display: block; margin-top: 8px">
      在途：源仓已扣减、目标仓尚未增加。这批货当前不在任何仓库的余额里，需由目标仓收货后才落地。
    </a-typography-text>
  </a-drawer>
</template>

<script setup lang="ts">
import type {TableColumnsType} from 'ant-design-vue';
import {quantityText} from '../inventory-model';
import type {InventoryTransfer} from '../inventory-types';
import {datetime} from '../../common/scm-display';

defineProps<{
  open: boolean;
  detail: Partial<InventoryTransfer>;
}>();

const emit = defineEmits<{
  'update:open': [open: boolean];
}>();

const itemColumns: TableColumnsType = [
  {title: '商品规格编码', dataIndex: 'skuCode', width: 160},
  {title: '商品规格名称', dataIndex: 'skuName', width: 150},
  {title: '商品名称', dataIndex: 'productName', width: 150},
  {title: '数量', dataIndex: 'quantity', align: 'right', width: 110},
  {title: '单位', dataIndex: 'unitSnapshot', align: 'center', width: 130},
];

function statusColor(status?: string) {
  if (status === 'RECEIVED') return 'green';
  if (status === 'CANCELLED') return 'default';
  if (status === 'SHIPPED') return 'orange';
  return 'blue';
}
</script>

<style scoped>
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
</style>
