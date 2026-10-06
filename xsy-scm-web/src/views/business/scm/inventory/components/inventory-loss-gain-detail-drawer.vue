<template>
  <a-drawer :open="open" title="报损报溢单详情" :width="scmDrawerWidth('l')" @close="emit('update:open', false)">
    <a-descriptions :column="2" bordered size="small">
      <a-descriptions-item label="单据号">{{ detail.lossGainNo }}</a-descriptions-item>
      <a-descriptions-item label="类型">
        <a-tag :color="typeColor(detail.adjustType)">{{ detail.adjustTypeDesc || detail.adjustType }}</a-tag>
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
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'quantity'">
          <span class="scm-quantity">{{ quantityText(record.quantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'unitSnapshot'">
          {{ record.unitSnapshot || '（待审核未审批）' }}
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
  </a-drawer>
</template>

<script setup lang="ts">
import type {TableColumnsType} from 'ant-design-vue';
import {quantityText} from '../inventory-model';
import type {InventoryLossGain} from '../inventory-types';
import {datetime} from '../../common/scm-display';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';

defineProps<{
  open: boolean;
  detail: Partial<InventoryLossGain>;
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

function typeColor(adjustType?: string) {
  if (adjustType === 'LOSS') return 'red';
  if (adjustType === 'OVERFLOW') return 'green';
  return 'default';
}

function statusColor(status?: string) {
  if (status === 'COMPLETED') return 'green';
  if (status === 'REJECTED') return 'red';
  return 'orange';
}
</script>
