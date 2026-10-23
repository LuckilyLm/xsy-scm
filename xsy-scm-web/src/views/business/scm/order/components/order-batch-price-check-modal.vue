<template>
  <a-modal :open="visible" title="草稿订单批量核价" :width="1120" :footer="null" @cancel="close">
    <a-alert type="info" show-icon class="smart-margin-bottom10" message="仅比较当前价格，不修改订单。" />
    <a-space class="smart-margin-bottom10" wrap>
      <span>已选 {{ entries.length }} 单，最多 8 单</span>
      <a-button size="small" :loading="loading" @click="reload">重新核价</a-button>
      <a-button size="small" @click="close">关闭</a-button>
    </a-space>
    <a-form-item v-if="canUpdate" label="人工重算原因（逐单执行时必填）">
      <a-textarea v-model:value="reason" :rows="2" :maxlength="500" show-count placeholder="说明为什么需要采用当前价格" />
    </a-form-item>
    <a-spin :spinning="loading">
      <a-collapse v-model:activeKey="expanded">
        <a-collapse-panel v-for="entry in entries" :key="String(entry.id)">
          <template #header>
            <a-space wrap>
              <strong>{{ entry.orderNo }}</strong>
              <span>{{ entry.customerName }}</span>
              <a-tag v-if="entry.applied" color="green">已执行重算</a-tag>
              <a-tag v-else-if="entry.error" color="red">核价失败</a-tag>
              <a-tag v-else-if="entry.result?.blocked" color="orange">存在不可用价格</a-tag>
              <a-tag v-else-if="entry.result?.changed" color="blue">{{ entry.result.changed }} 行有差异</a-tag>
              <a-tag v-else color="default">价格无差异</a-tag>
            </a-space>
          </template>
          <a-alert v-if="entry.error" type="error" show-icon :message="entry.error" />
          <template v-else-if="entry.result">
            <a-table
              :data-source="entry.result.lines"
              :columns="columns"
              :pagination="false"
              row-key="itemId"
              size="small"
              bordered
              :scroll="{x: scmColumnsWidth(columns)}"
            >
              <template #bodyCell="{record, column}">
                <span v-if="column.dataIndex === 'oldPrice'" class="scm-money">
                  {{ money(record.oldPrice) }}
                </span>
                <span v-else-if="column.dataIndex === 'currentPrice'" class="scm-money">
                  {{ money(record.currentPrice) }}
                </span>
                <a-tag v-else-if="column.dataIndex === 'state'" :color="tone(record.state)">{{ stateLabel(record.state) }}</a-tag>
                <span v-else-if="column.dataIndex === 'reason'">
                  {{ record.reason || record.priceSource || '—' }}
                </span>
              </template>
            </a-table>
            <a-space class="smart-margin-top10">
              <a-button
                v-if="canUpdate"
                type="primary"
                size="small"
                :loading="applyingId === String(entry.id)"
                :disabled="loading || !!applyingId || entry.applied || !canApplyPriceCheck(entry.result)"
                @click="confirmApply(entry)"
                >按当前规则重算此单</a-button
              >
              <span v-if="entry.result.blocked" class="scm-cell-hint"> 商品不可售或未定价，不能在这里执行重算 </span>
            </a-space>
          </template>
        </a-collapse-panel>
      </a-collapse>
      <a-empty v-if="!loading && !entries.length" description="没有可核价的草稿订单" />
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {computed, ref} from 'vue';
import {message, Modal, type TableColumnsType} from 'ant-design-vue';
import {orderApi} from '/@/api/business/scm/order-api';
import {hasPermission} from '../../common/scm-permission';
import {orderError} from '../order-errors';
import {canApplyPriceCheck, compareOrderPrices} from '../order-batch-price-check';
import type {PriceCheckResult, PriceCheckLine, PriceCheckState} from '../order-batch-price-check';
import type {Order, Id} from '../order-types';

interface Entry {
  id: Id;
  orderNo: string;
  customerName: string;
  result?: PriceCheckResult;
  error?: string;
  applied: boolean;
}
const emit = defineEmits<{(e: 'saved'): void}>();
const visible = ref(false);
const loading = ref(false);
const reason = ref('');
const expanded = ref<string[]>([]);
const entries = ref<Entry[]>([]);
const applyingId = ref('');
let generation = 0;
const canUpdate = computed(() => hasPermission('scm:order:update') && hasPermission('scm:pricing:resolve:query'));
const columns: TableColumnsType<PriceCheckLine> = [
  {title: '商品', dataIndex: 'productName', width: 180},
  {title: '规格编码', dataIndex: 'skuCode', width: 130},
  {title: '下单量', dataIndex: 'quantity', width: 100},
  {title: '原草稿价', dataIndex: 'oldPrice', width: 120},
  {title: '当前解析价', dataIndex: 'currentPrice', width: 120},
  {title: '比较结果', dataIndex: 'state', align: 'center', width: 120},
  {title: '说明', dataIndex: 'reason', width: 180},
];
const money = (value: string | null) => (value == null ? '未定价' : '¥ ' + value);
const stateLabel = (state: PriceCheckState) =>
  ({
    UP: '涨价',
    DOWN: '降价',
    SAME: '无变化',
    NEW: '现已定价',
    MANUAL: '人工改价',
    BLOCKED: '不可核价',
  })[state];
const tone = (state: PriceCheckState) =>
  ({
    UP: 'red',
    DOWN: 'green',
    SAME: 'default',
    NEW: 'blue',
    MANUAL: 'default',
    BLOCKED: 'orange',
  })[state];

function open(orders: Order[]): void {
  if (!orders.length || orders.length > 8 || orders.some((row) => row.status !== 'DRAFT' || row.orderId == null)) {
    void message.warning('请选择 1 至 8 张草稿订单');
    return;
  }
  generation++;
  entries.value = orders.map((row) => ({
    id: row.orderId!,
    orderNo: row.orderNo || String(row.orderId),
    customerName: row.customerNameSnapshot || '—',
    applied: false,
  }));
  expanded.value = entries.value.map((entry) => String(entry.id));
  reason.value = '';
  visible.value = true;
  void reload();
}
function close(): void {
  if (applyingId.value) return;
  generation++;
  visible.value = false;
  loading.value = false;
}
async function reload(): Promise<void> {
  if (!visible.value || applyingId.value) return;
  const ticket = ++generation;
  loading.value = true;
  entries.value = entries.value.map((entry) => ({...entry, result: undefined, error: undefined}));
  for (const entry of entries.value) {
    try {
      const detail = (await orderApi.detail(entry.id, {suppressGlobalErrorMessage: true})).data;
      if (ticket !== generation) return;
      if (detail.status !== 'DRAFT' || detail.version == null || !detail.customerId) {
        throw new Error('订单状态已变化，请返回列表刷新');
      }
      const skus = [
        ...new Set(
          detail.items
            .filter((item) => !item.manualPriceOverride)
            .map((item) => String(item.skuId))
            .filter((id) => /^[1-9]\d*$/.test(id))
        ),
      ];
      if (detail.items.some((item) => !item.manualPriceOverride) && !skus.length) {
        throw new Error('订单没有可核价的商品规格');
      }
      const prices = skus.length
        ? (
            await orderApi.preview(
              {
                customerId: detail.customerId,
                skuIds: skus,
                warehouseId: detail.mallDeliveryWarehouseId ?? null,
              },
              {suppressGlobalErrorMessage: true}
            )
          ).data.items
        : [];
      if (ticket !== generation) return;
      entry.result = compareOrderPrices(detail, prices);
      entry.orderNo = detail.orderNo || String(entry.id);
      entry.customerName = detail.customerNameSnapshot || '—';
    } catch (cause) {
      if (ticket !== generation) return;
      entry.error = orderError(cause);
    }
  }
  if (ticket === generation) loading.value = false;
}
function confirmApply(entry: Entry): void {
  if (!canUpdate.value || !entry.result || !canApplyPriceCheck(entry.result) || entry.applied) return;
  if (!reason.value.trim()) {
    void message.warning('请填写重算原因');
    return;
  }
  const previewVersion = entry.result.version;
  const actionReason = reason.value.trim();
  Modal.confirm({
    title: '确认重算该草稿订单？',
    content: '系统将以执行时的价格重新核价并记录操作日志，不保证与预览价格完全一致。',
    okText: '确认重算',
    async onOk() {
      applyingId.value = String(entry.id);
      try {
        const latest = (await orderApi.detail(entry.id, {suppressGlobalErrorMessage: true})).data;
        if (latest.status !== 'DRAFT' || latest.version !== previewVersion) {
          throw new Error('订单版本或状态已变化，请重新核价');
        }
        await orderApi.reprice({orderId: entry.id, version: previewVersion, reason: actionReason});
        entry.applied = true;
        void message.success('该订单已按当前定价规则重算');
        emit('saved');
      } catch (cause) {
        void message.error(orderError(cause));
        throw cause;
      } finally {
        applyingId.value = '';
      }
    },
  });
}

defineExpose({open});
</script>
