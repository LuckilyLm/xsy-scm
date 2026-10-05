<template>
  <a-drawer :open="open" title="转换单详情" width="1000" @close="emit('update:open', false)">
    <a-descriptions :column="2" bordered size="small">
      <a-descriptions-item label="转换单号">{{ detail.conversionNo }}</a-descriptions-item>
      <a-descriptions-item label="类型">
        <a-tag color="blue">{{ detail.convertTypeDesc || detail.convertType }}</a-tag>
      </a-descriptions-item>
      <a-descriptions-item label="状态">
        <a-tag :color="statusColor(detail.status)">{{ detail.statusDesc || detail.status }}</a-tag>
      </a-descriptions-item>
      <a-descriptions-item label="仓库">{{ detail.warehouseName || '—' }}</a-descriptions-item>
      <a-descriptions-item label="原因" :span="2">{{ detail.reason || '—' }}</a-descriptions-item>
      <a-descriptions-item label="审核人">{{ detail.auditor || '—' }}</a-descriptions-item>
      <a-descriptions-item label="审核时间">{{ datetime(detail.auditedAt) }}</a-descriptions-item>
      <a-descriptions-item label="审核意见" :span="2">{{ detail.auditOpinion || '—' }}</a-descriptions-item>
      <a-descriptions-item label="创建时间">{{ datetime(detail.createdAt) }}</a-descriptions-item>
      <a-descriptions-item label="备注">{{ detail.remark || '—' }}</a-descriptions-item>
    </a-descriptions>
    <a-table
        style="margin-top: 12px"
        size="small"
        :data-source="detail.items || []"
        :columns="itemColumns"
        row-key="id"
        bordered
        :pagination="false"
        :scroll="{ x: 1100 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'sourceQuantity'">
          <span class="num">{{ quantityText(record.sourceQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'targetQuantity'">
          <span class="num">{{ quantityText(record.targetQuantity) }}</span>
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
    <a-typography-text v-if="detail.status === 'COMPLETED'" type="secondary" style="display: block; margin-top: 8px">
      审批已生成两条流水：源 SKU「转换出」、目标 SKU「转换入」。若要冲销，请新建一张反向转换单
      —— 流水不可修改、不可删除。
    </a-typography-text>
  </a-drawer>
</template>

<script setup lang="ts">
import type {TableColumnsType} from 'ant-design-vue';
import {quantityText} from '../inventory-model';
import type {InventoryConversion} from '../inventory-types';
import {datetime} from '../../common/scm-display';

defineProps<{
  open: boolean;
  detail: Partial<InventoryConversion>;
}>();

const emit = defineEmits<{
  'update:open': [open: boolean];
}>();

const itemColumns: TableColumnsType = [
  {title: '源 SKU', dataIndex: 'sourceSkuCode', width: 150},
  {title: '源商品', dataIndex: 'sourceProductName', width: 140},
  {title: '源数量', dataIndex: 'sourceQuantity', align: 'right', width: 110},
  {title: '源单位', dataIndex: 'sourceUnit', align: 'center', width: 90},
  {title: '目标 SKU', dataIndex: 'targetSkuCode', width: 150},
  {title: '目标商品', dataIndex: 'targetProductName', width: 140},
  {title: '目标数量', dataIndex: 'targetQuantity', align: 'right', width: 110},
  {title: '目标单位', dataIndex: 'targetUnit', align: 'center', width: 90},
];

function statusColor(status?: string) {
  if (status === 'COMPLETED') return 'green';
  if (status === 'REJECTED') return 'red';
  return 'orange';
}
</script>

<style scoped>
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
</style>
