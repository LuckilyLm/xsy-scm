<template>
  <a-drawer :open="open" title="盘点单详情" width="860" @close="emit('update:open', false)">
    <a-descriptions :column="2" bordered size="small">
      <a-descriptions-item label="盘点单号">{{ detail.stocktakeNo }}</a-descriptions-item>
      <a-descriptions-item label="状态">
        <a-tag :color="statusColor(detail.status)">{{ detail.statusDesc || detail.status }}</a-tag>
      </a-descriptions-item>
      <a-descriptions-item label="仓库">{{ detail.warehouseName || '—' }}</a-descriptions-item>
      <a-descriptions-item label="确认人">{{ detail.operator || '—' }}</a-descriptions-item>
      <a-descriptions-item label="确认时间">{{ datetime(detail.confirmedAt) }}</a-descriptions-item>
      <a-descriptions-item label="创建时间">{{ datetime(detail.createdAt) }}</a-descriptions-item>
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
        :scroll="{ x: 900 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'bookQuantity'">
          <span class="num">{{ quantityText(record.bookQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'actualQuantity'">
          <span class="num">{{ quantityText(record.actualQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'deltaQuantity'">
          <span class="num" :class="deltaClass(record.deltaQuantity)">{{ deltaText(record.deltaQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'unitSnapshot'">
          {{ record.unitSnapshot || '（草稿未确认）' }}
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
    <a-typography-text v-if="detail.status === 'CONFIRMED'" type="secondary" style="display: block; margin-top: 8px">
      差异 = 实盘量 − 账面量快照。确认时系统把差异施加到**确认瞬间的账面量**上，
      因此若在保存草稿之后发生过收货或出库，确认后的账面不会等于实盘量 —— 那笔变动被保留了。
    </a-typography-text>
  </a-drawer>
</template>

<script setup lang="ts">
import type {TableColumnsType} from 'ant-design-vue';
import {quantityText} from '../inventory-model';
import type {InventoryStocktake} from '../inventory-types';
import {datetime} from '../../common/scm-display';

defineProps<{
  open: boolean;
  detail: Partial<InventoryStocktake>;
}>();

const emit = defineEmits<{
  'update:open': [open: boolean];
}>();

const itemColumns: TableColumnsType = [
  {title: 'SKU 编码', dataIndex: 'skuCode', width: 150},
  {title: 'SKU 名称', dataIndex: 'skuName', width: 130},
  {title: '商品名称', dataIndex: 'productName', width: 130},
  {title: '账面量', dataIndex: 'bookQuantity', align: 'right', width: 110},
  {title: '实盘量', dataIndex: 'actualQuantity', align: 'right', width: 110},
  {title: '差异', dataIndex: 'deltaQuantity', align: 'right', width: 110},
  {title: '单位', dataIndex: 'unitSnapshot', align: 'center', width: 110},
];

/** 差异带符号展示：盘盈 `+`、盘亏 `−`。后端已给 4 位定点字符串，这里只补符号。 */
function deltaText(value?: string | null) {
  if (value === undefined || value === null || value === '') return '—';
  const n = Number(value);
  if (!Number.isFinite(n)) return value;
  if (n === 0) return '0.0000';
  return n > 0 ? `+${value}` : `−${value.replace('-', '')}`;
}

function deltaClass(value?: string | null) {
  const n = Number(value);
  if (!Number.isFinite(n) || n === 0) return '';
  return n > 0 ? 'delta-up' : 'delta-down';
}

function statusColor(status?: string) {
  if (status === 'CONFIRMED') return 'green';
  if (status === 'CANCELLED') return 'default';
  return 'orange';
}
</script>

<style scoped>
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.delta-up {
  color: #389e0d;
}

.delta-down {
  color: #cf1322;
}
</style>
