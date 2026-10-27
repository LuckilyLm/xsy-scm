<template>
  <a-table
    id="order-item-table"
    size="small"
    bordered
    :data-source="items"
    :columns="columns"
    :pagination="false"
    :scroll="{x: scmColumnsWidth(columns)}"
    :row-key="(_row: Item, index?: number) => index ?? 0"
  >
    <template #bodyCell="{record, column, index}">
      <template v-if="column.dataIndex === 'skuId'">
        <SkuSelect
          :value="record.skuId"
          @select="(option) => applySku(record, option)"
          @update:value="
            (v) => {
              record.skuId = Array.isArray(v) ? v[0] : v;
              emit('price');
            }
          "
          width="270px"
          :disabled-statuses="[]"
        />
      </template>
      <template v-else-if="column.dataIndex === 'rowNo'">
        <span class="row-no">{{ index + 1 }}</span>
      </template>
      <template v-else-if="column.dataIndex === 'specNameSnapshot'">
        {{ record.specNameSnapshot || '—' }}
      </template>
      <template v-else-if="column.dataIndex === 'saleUnitSnapshot'">
        {{ record.saleUnitSnapshot || '—' }}
      </template>
      <template v-else-if="column.dataIndex === 'orderedQuantity'">
        <a-input-number string-mode :precision="4" :min="'0.0001'" v-model:value="record.orderedQuantity" :aria-label="'下单数量 ' + (index + 1)" />
      </template>
      <template v-else-if="column.dataIndex === 'actualQuantity'">
        <span class="actual-quantity">{{ record.actualQuantity || '' }}</span>
      </template>
      <template v-else-if="column.dataIndex === 'draftUnitPrice'">
        <div class="price-cell">
          <div class="price-main">
            <span>{{ amount(record.draftUnitPrice, true) }}</span>
            <!-- 最近采购价常显：录单时不用点开浮层就能看到成本参考 -->
            <span v-if="purchaseOf(record)" class="purchase-line" :title="purchaseMetaText(record)">
              采购 {{ purchasePriceText(record) }}
              <span v-if="purchaseUnitMismatch(record)" class="purchase-mismatch">单位不同</span>
            </span>
            <span v-else-if="purchaseReady(record)" class="purchase-line purchase-line--empty">暂无采购价</span>
            <span v-if="belowPurchasePrice(record)" class="purchase-warn" :title="purchaseWarning(record)">低于采购价</span>
          </div>
          <a-popover
            v-if="customerId && record.skuId"
            trigger="click"
            placement="left"
            :open="recentOpenIndex === index"
            @update:open="(v: boolean) => (recentOpenIndex = v ? index : null)"
            :get-popup-container="recentPopupContainer"
            :title="'最近已确认订单价 · ' + (record.skuCodeSnapshot || '商品规格')"
          >
            <a-button type="link" size="small" class="recent-btn" aria-label="最近已确认订单价" @click="loadRecent(record)">历史价</a-button>
            <template #content>
              <a-spin :spinning="recentLoadingOf(record)">
                <div class="recent-wrap">
                  <a-empty v-if="recentEmpty(record)" description="该客户该商品暂无已确认历史价" />
                  <div v-else-if="recentReady(record)" class="recent-list">
                    <div v-for="p in recentRows(record)" :key="p.itemId" class="recent-row">
                      <div class="recent-head">
                        <span class="recent-price"
                          >{{ amount(p.unitPrice) }}<span v-if="p.saleUnit" class="recent-unit">/{{ p.saleUnit }}</span></span
                        >
                        <a-tag v-if="p.priceSource">{{ priceSourceDesc(p.priceSource) }}</a-tag>
                      </div>
                      <div v-if="p.saleUnit && record.saleUnitSnapshot && p.saleUnit !== record.saleUnitSnapshot" class="recent-warn">
                        单位「{{ p.saleUnit }}」与当前「{{ record.saleUnitSnapshot }}」不同，不可直接比较
                      </div>
                      <div class="recent-meta">{{ p.orderNo }} · {{ p.orderedQuantity }} · {{ dayjs(p.confirmedAt).format('YYYY-MM-DD') }}</div>
                    </div>
                  </div>
                </div>
              </a-spin>
            </template>
          </a-popover>
          <a-popover
            v-if="record.skuId"
            trigger="click"
            placement="left"
            :open="purchaseOpenIndex === index"
            @update:open="(v: boolean) => (purchaseOpenIndex = v ? index : null)"
            :get-popup-container="recentPopupContainer"
            :title="'最近采购价 · ' + (record.skuCodeSnapshot || '商品规格')"
          >
            <a-button type="link" size="small" class="recent-btn" aria-label="最近采购价" @click="loadPurchase(record)">采购价</a-button>
            <template #content>
              <a-spin :spinning="purchaseLoadingOf(record)">
                <div class="recent-wrap">
                  <a-empty v-if="purchaseEmpty(record)" description="该商品暂无已提交采购价" />
                  <div v-else-if="purchaseReady(record)" class="recent-row">
                    <div class="recent-head">
                      <span class="recent-price">{{ purchasePriceText(record) }}</span>
                    </div>
                    <div v-if="purchaseUnitMismatch(record)" class="recent-warn">
                      采购单位「{{ purchaseUnitText(record) }}」与当前销售单位「{{ record.saleUnitSnapshot }}」不同，不可直接比较
                    </div>
                    <div class="recent-meta">{{ purchaseMetaText(record) }}</div>
                    <div class="recent-meta">{{ purchaseSupplierText(record) }}</div>
                  </div>
                </div>
              </a-spin>
            </template>
          </a-popover>
        </div>
      </template>
      <template v-else-if="column.dataIndex === 'override'">
        <a-space direction="vertical">
          <a-checkbox v-privilege="'scm:order:price-override'" v-model:checked="record.manualPriceOverride">人工改价 </a-checkbox>
          <template v-if="record.manualPriceOverride">
            <a-input-number string-mode :precision="2" :min="'0'" :step="'0.01'" v-model:value="record.unitPrice" aria-label="人工单价" />
            <a-input v-model:value="record.overrideReason" placeholder="改价原因（必填）" aria-label="改价原因" maxlength="500" />
          </template>
        </a-space>
      </template>
      <template v-else-if="column.dataIndex === 'action'">
        <a-button danger type="link" @click="items.splice(index, 1)">移除</a-button>
      </template>
    </template>
  </a-table>
  <div class="order-total">
    <span
      >合计数量 <strong>{{ sumOrderedQuantity(items) }}</strong></span
    >
  </div>
  <a-button class="add-line" @click="items.push({orderedQuantity: '1.0000', manualPriceOverride: false})">添加商品 </a-button>
</template>
<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {reactive, ref, watch} from 'vue';
import {message, type TableColumnsType} from 'ant-design-vue';
import dayjs from 'dayjs';
import type {SkuOption} from '/@/types/business/scm/pricing';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import {orderApi} from '/@/api/business/scm/order-api';
import {SCM_ORDER_PRICE_SOURCE_ENUM} from '/@/constants/business/scm/order-const';
import type {Id, Item, LastPurchasePrice, RecentPrice} from '../order-types';
import {
  amount,
  createLastPurchasePriceCache,
  createRecentPriceCache,
  hasLastPurchasePrice,
  hasRecentPrices,
  isLoadingLastPurchasePrice,
  isLoadingRecentPrice,
  lastPurchasePrice,
  loadLastPurchasePrice,
  loadRecentPrices,
  recentPriceRows,
  sumOrderedQuantity,
} from '../order-form-model';
import {orderError} from '../order-errors';

const props = defineProps<{items: Item[]; customerId?: Id}>();
const emit = defineEmits<{price: []}>();
const columns: TableColumnsType<Item> = [
  {title: '序号', dataIndex: 'rowNo', width: 60},
  {
    title: '商品 / 商品规格',
    dataIndex: 'skuId',
    width: 300,
  },
  {title: '规格', dataIndex: 'specNameSnapshot', width: 110},
  {title: '单位', dataIndex: 'saleUnitSnapshot', width: 80},
  {title: '下单数量', dataIndex: 'orderedQuantity', width: 150},
  {title: '实数量', dataIndex: 'actualQuantity', width: 100},
  {
    title: '解析单价',
    dataIndex: 'draftUnitPrice',
    width: 220,
  },
  {title: '价格修订', dataIndex: 'override', width: 230},
  {
    title: '操作',
    dataIndex: 'action',
    align: 'center',
    width: 80,
  },
];

/* 历史价只读，点开时按当前客户和规格查询；不回写单价、不参与定价，只在本次抽屉内缓存。 */
const recentCache = reactive(createRecentPriceCache());
/* 最近采购价只读、按商品规格缓存；同样不回写单价，也不进入订单载荷。 */
const purchaseCache = reactive(createLastPurchasePriceCache());

/** 同一时刻只允许一行打开对应浮层。 */
const recentOpenIndex = ref<number | null>(null);
const purchaseOpenIndex = ref<number | null>(null);

// 客户或商品变化后关掉浮层，避免旧价格留在新行旁
watch(
  () => props.customerId,
  () => {
    recentOpenIndex.value = null;
    purchaseOpenIndex.value = null;
  }
);
watch(
  () => props.items.map((item) => item.skuId),
  () => {
    recentOpenIndex.value = null;
    purchaseOpenIndex.value = null;
  }
);

/* 采购价是比价提示，选中规格后自动取一次；失败不打断录单。 */
watch(
  () => props.items.map((item) => item.skuId).join(','),
  () => {
    const skuIds = new Set(props.items.map((item) => item.skuId).filter((skuId) => skuId != null && skuId !== ''));
    for (const skuId of skuIds) {
      void ensurePurchase(skuId as Id);
    }
  },
  {immediate: true}
);

/** 浮层挂在抽屉内，抽屉关闭时一起消失。 */
function recentPopupContainer(trigger: HTMLElement): HTMLElement {
  return (trigger.closest('.ant-drawer-body') ?? document.body) as HTMLElement;
}

function recentReady(record: Item): boolean {
  return hasRecentPrices(recentCache, props.customerId, record.skuId);
}

/** 已查过但没有历史价。 */
function recentEmpty(record: Item): boolean {
  return recentReady(record) && recentRows(record).length === 0;
}

function recentRows(record: Item): RecentPrice[] {
  return recentPriceRows(recentCache, props.customerId, record.skuId);
}

function recentLoadingOf(record: Item): boolean {
  return isLoadingRecentPrice(recentCache, props.customerId, record.skuId);
}

function priceSourceDesc(source: string): string {
  return SCM_ORDER_PRICE_SOURCE_ENUM[source]?.desc ?? source;
}

function purchaseReady(record: Item): boolean {
  return hasLastPurchasePrice(purchaseCache, record.skuId);
}

function purchaseEmpty(record: Item): boolean {
  return purchaseReady(record) && purchaseOf(record) == null;
}

function purchaseOf(record: Item): LastPurchasePrice | null {
  return lastPurchasePrice(purchaseCache, record.skuId);
}

function purchaseLoadingOf(record: Item): boolean {
  return isLoadingLastPurchasePrice(purchaseCache, record.skuId);
}

function purchasePriceText(record: Item): string {
  const last = purchaseOf(record);
  return last == null ? '—' : `${amount(last.purchasePrice)}${last.purchaseUnit ? `/${last.purchaseUnit}` : ''}`;
}

function purchaseUnitText(record: Item): string {
  return purchaseOf(record)?.purchaseUnit ?? '';
}

/** 采购价只有和当前销售单位一致时才可直接比较；不一致只作参考，不做高低提示。 */
function samePurchaseUnit(record: Item): boolean {
  const last = purchaseOf(record);
  return last != null && !!last.purchaseUnit && !!record.saleUnitSnapshot && last.purchaseUnit === record.saleUnitSnapshot;
}

function purchaseUnitMismatch(record: Item): boolean {
  const last = purchaseOf(record);
  return last != null && !!last.purchaseUnit && !!record.saleUnitSnapshot && last.purchaseUnit !== record.saleUnitSnapshot;
}

function purchaseMetaText(record: Item): string {
  const last = purchaseOf(record);
  return last == null ? '' : `${last.purchaseOrderNo} · ${dayjs(last.submittedAt).format('YYYY-MM-DD')}`;
}

function purchaseSupplierText(record: Item): string {
  return purchaseOf(record)?.supplierName ?? '';
}

/** 人工改价优先于解析价：两者都是用户即将成交的单价口径。 */
function effectivePrice(record: Item): string | null {
  return record.manualPriceOverride ? record.unitPrice ?? null : record.draftUnitPrice ?? null;
}

/** 选中规格后立即回填快照，录单区不用等保存就能看到规格和单位。 */
function applySku(record: Item, option?: SkuOption): void {
  if (!option) return;
  record.skuCodeSnapshot = option.skuCode;
  record.productNameSnapshot = option.productName;
  record.specNameSnapshot = option.specName;
  record.saleUnitSnapshot = option.saleUnit;
  record.productTypeSnapshot = option.productType;
}

function belowPurchasePrice(record: Item): boolean {
  const last = purchaseOf(record);
  const current = effectivePrice(record);
  return last != null && samePurchaseUnit(record) && current != null && current !== '' && Number(current) < Number(last.purchasePrice);
}

function purchaseWarning(record: Item): string {
  const last = purchaseOf(record);
  return last == null ? '' : `当前单价低于最近采购价 ${purchasePriceText(record)}（${last.purchaseOrderNo} · ${last.supplierName}）`;
}

async function ensurePurchase(skuId: Id): Promise<void> {
  try {
    await loadLastPurchasePrice(purchaseCache, skuId, async () => {
      const r = await orderApi.lastPurchasePrices([skuId], {suppressGlobalErrorMessage: true});
      return r.data.find((p) => String(p.skuId) === String(skuId)) ?? null;
    });
  } catch {
    // 采购价只是参考，失败时静默，不打断录单
  }
}

async function loadPurchase(record: Item) {
  if (record.skuId == null) return;
  await ensurePurchase(record.skuId as Id);
}

async function loadRecent(record: Item) {
  try {
    await loadRecentPrices(recentCache, props.customerId, record.skuId, async () => {
      const r = await orderApi.recentPrices(props.customerId as Id, record.skuId as Id, 5);
      return r.data;
    });
  } catch (e) {
    message.error(orderError(e));
  }
}

/** 抽屉关闭时由父组件调用，避免下次打开还显示上次的价签。 */
defineExpose({
  closeRecentPopover: () => {
    recentOpenIndex.value = null;
    purchaseOpenIndex.value = null;
  },
});
</script>
<style scoped>
.add-line {
  margin-top: 12px;
}

.row-no,
.actual-quantity {
  font-variant-numeric: tabular-nums;
  color: rgba(0, 0, 0, 0.55);
}

.order-total {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-top: 12px;
  padding: 8px 12px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  font-variant-numeric: tabular-nums;
}

.order-total strong {
  font-weight: 600;
}

.price-cell {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
}

.price-main {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.purchase-warn {
  color: #d46b08;
  font-size: 12px;
  cursor: help;
}

.purchase-line {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.65);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.purchase-line--empty {
  color: rgba(0, 0, 0, 0.35);
}

.purchase-mismatch {
  margin-left: 2px;
  color: #d46b08;
}

.recent-btn {
  padding: 0 4px;
  height: auto;
}

.recent-wrap {
  min-width: 240px;
}

.recent-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 260px;
  overflow: auto;
}

.recent-row .recent-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.recent-price {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.recent-unit {
  margin-left: 2px;
  font-weight: 400;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}

.recent-warn {
  color: #fa8c16;
  font-size: 12px;
}

.recent-meta {
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}
</style>
