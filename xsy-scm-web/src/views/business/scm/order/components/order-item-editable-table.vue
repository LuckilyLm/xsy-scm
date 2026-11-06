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
          :selected-options="selectedSkuOptions(record)"
          @select="(option) => applySku(record, option)"
          @update:value="
            (v) => {
              record.skuId = Array.isArray(v) ? v[0] : v;
              emit('price');
            }
          "
          width="100%"
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
        <a-input-number
          string-mode
          :precision="1"
          :min="'0.1'"
          :step="'0.1'"
          placeholder="0.0"
          style="width: 100%"
          v-model:value="record.orderedQuantity"
          :aria-label="'下单数量 ' + (index + 1)"
        />
      </template>
      <template v-else-if="column.dataIndex === 'actualQuantity'">
        <span class="actual-quantity">{{ record.actualQuantity || '' }}</span>
      </template>
      <template v-else-if="column.dataIndex === 'draftUnitPrice'">
        <div class="price-cell">
          <span class="price-value">{{ amount(effectivePrice(record), true) }}</span>
          <a-tag v-if="record.manualPriceOverride" class="price-tag" :bordered="false">人工</a-tag>
          <span v-if="belowPurchasePrice(record)" class="price-flag" :title="purchaseWarning(record)">低于采购价</span>
        </div>
      </template>
      <template v-else-if="column.dataIndex === 'remark'">
        <!-- 行备注随商品打印到分拣单/送货单：切段、去皮、分袋这类要求不能写进整单备注 -->
        <a-input v-model:value="record.remark" :maxlength="200" placeholder="如：切段、去皮" :aria-label="'商品备注 ' + (index + 1)" />
      </template>
      <template v-else-if="column.dataIndex === 'action'">
        <div class="row-actions">
          <a-button type="link" size="small" :disabled="!customerId || !record.skuId" @click="openReference(record, 'recent')">历史售价</a-button>
          <a-button type="link" size="small" :disabled="!record.skuId" @click="openReference(record, 'purchase')">采购价</a-button>
          <a-button type="link" size="small" v-privilege="'scm:order:price-override'" @click="openOverride(record)">改价</a-button>
          <a-button danger type="link" size="small" @click="items.splice(index, 1)">移除</a-button>
        </div>
      </template>
    </template>
  </a-table>
  <a-modal v-model:open="referenceOpen" :title="referenceTitle" :footer="null" :width="420">
    <div v-if="referenceRow" class="price-modal">
      <a-spin :spinning="referenceLoading">
        <template v-if="referenceKind === 'recent'">
          <a-empty v-if="recentEmpty(referenceRow)" description="该客户该商品暂无已确认历史价" />
          <div v-else-if="recentReady(referenceRow)" class="recent-list">
            <div v-for="p in recentRows(referenceRow)" :key="p.itemId" class="recent-row">
              <div class="recent-head">
                <span class="recent-price"
                  >{{ amount(p.unitPrice) }}<span v-if="p.saleUnit" class="recent-unit">/{{ p.saleUnit }}</span></span
                >
                <a-tag v-if="p.priceSource">{{ priceSourceDesc(p.priceSource) }}</a-tag>
              </div>
              <div v-if="p.saleUnit && referenceRow.saleUnitSnapshot && p.saleUnit !== referenceRow.saleUnitSnapshot" class="recent-warn">
                单位「{{ p.saleUnit }}」与当前「{{ referenceRow.saleUnitSnapshot }}」不同，不可直接比较
              </div>
              <div class="recent-meta">{{ p.orderNo }} · {{ p.orderedQuantity }} · {{ dayjs(p.confirmedAt).format('YYYY-MM-DD') }}</div>
            </div>
          </div>
        </template>
        <template v-else>
          <a-empty v-if="purchaseEmpty(referenceRow)" description="该商品暂无已提交采购价" />
          <template v-else-if="purchaseReady(referenceRow)">
            <div class="recent-head">
              <span class="recent-price">{{ purchasePriceText(referenceRow) }}</span>
            </div>
            <div v-if="purchaseUnitMismatch(referenceRow)" class="recent-warn">
              采购单位「{{ purchaseUnitText(referenceRow) }}」与当前销售单位「{{ referenceRow.saleUnitSnapshot }}」不同，不可直接比较
            </div>
            <div class="recent-meta">{{ purchaseMetaText(referenceRow) }}</div>
            <div class="recent-meta">{{ purchaseSupplierText(referenceRow) }}</div>
          </template>
        </template>
      </a-spin>
    </div>
  </a-modal>
  <a-modal v-model:open="overrideOpen" title="人工改价" ok-text="确定" cancel-text="取消" :width="420" @ok="confirmOverride">
    <a-form layout="vertical" class="price-modal">
      <a-form-item label="改价后单价" required>
        <a-input-number
          string-mode
          :precision="2"
          :min="'0'"
          :step="'0.01'"
          placeholder="请输入改价后单价"
          style="width: 100%"
          v-model:value="overridePrice"
          aria-label="改价后单价"
        />
      </a-form-item>
      <a-form-item label="改价原因" required>
        <a-textarea v-model:value="overrideReason" :maxlength="500" :rows="2" show-count placeholder="请填写改价原因，保存后进入审计记录" />
      </a-form-item>
    </a-form>
    <template v-if="overrideRow?.manualPriceOverride" #footer>
      <a-button danger @click="cancelOverride">取消改价</a-button>
      <a-button @click="overrideOpen = false">取消</a-button>
      <a-button type="primary" @click="confirmOverride">确定</a-button>
    </template>
  </a-modal>
  <div class="order-total">
    <span
      >合计数量 <strong>{{ sumOrderedQuantity(items) }}</strong></span
    >
  </div>
  <a-button class="add-line" @click="items.push({orderedQuantity: '1.0', manualPriceOverride: false})">添加商品 </a-button>
</template>
<script setup lang="ts">
import {notifyRequestError} from '/@/utils/request-feedback';
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {computed, onBeforeUnmount, onMounted, reactive, ref, watch} from 'vue';
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

const props = defineProps<{items: Item[]; customerId?: Id; orderId?: Id}>();
const emit = defineEmits<{price: []; 'modal-open': [open: boolean]}>();
const columns: TableColumnsType<Item> = [
  {title: '序号', dataIndex: 'rowNo', width: 50},
  {
    title: '商品 / 商品规格',
    dataIndex: 'skuId',
    width: 220,
  },
  {title: '规格', dataIndex: 'specNameSnapshot', width: 80},
  {title: '单位', dataIndex: 'saleUnitSnapshot', width: 50},
  {title: '下单数量', dataIndex: 'orderedQuantity', width: 100},
  {title: '实数量', dataIndex: 'actualQuantity', width: 80},
  {
    title: '报价单价',
    dataIndex: 'draftUnitPrice',
    width: 130,
  },
  {title: '备注', dataIndex: 'remark', width: 140},
  {
    title: '操作',
    dataIndex: 'action',
    align: 'center',
    width: 180,
  },
];

/* 历史价只读，点开时按当前客户和规格查询；不回写单价、不参与定价，只在本次抽屉内缓存。 */
const recentCache = reactive(createRecentPriceCache());
/* 最近采购价只读、按商品规格缓存；同样不回写单价，也不进入订单载荷。 */
const purchaseCache = reactive(createLastPurchasePriceCache());

/** 价格参考与人工改价统一放在操作列，弹窗查看和修改，不占明细单元格。 */
const referenceOpen = ref(false);
const referenceKind = ref<'recent' | 'purchase'>('recent');
const referenceRow = ref<Item | null>(null);
const overrideOpen = ref(false);
const overrideRow = ref<Item | null>(null);
const overridePrice = ref<string | number | null>(null);
const overrideReason = ref('');

// 弹窗打开时让父抽屉停用 Esc 关闭，否则 Esc 会同时触发抽屉的未保存确认。
watch([referenceOpen, overrideOpen], ([reference, override]) => emit('modal-open', reference || override));

/** 焦点可能还留在触发按钮上，Esc 要在捕获阶段先关弹窗，不再传给抽屉。 */
function closeModalOnEscape(event: KeyboardEvent) {
  if (event.key !== 'Escape' || (!referenceOpen.value && !overrideOpen.value)) return;
  event.preventDefault();
  event.stopPropagation();
  referenceOpen.value = false;
  overrideOpen.value = false;
}
onMounted(() => document.addEventListener('keydown', closeModalOnEscape, true));
onBeforeUnmount(() => document.removeEventListener('keydown', closeModalOnEscape, true));

/** 只显示业务名称，不把编码/ID 暴露给录单人员。 */
const referenceTitle = computed(() => (referenceKind.value === 'recent' ? '历史售价' : '采购参考'));
const referenceLoading = computed(() => {
  const record = referenceRow.value;
  if (!record) return false;
  return referenceKind.value === 'recent' ? recentLoadingOf(record) : purchaseLoadingOf(record);
});

function openReference(record: Item, kind: 'recent' | 'purchase') {
  referenceRow.value = record;
  referenceKind.value = kind;
  referenceOpen.value = true;
  void (kind === 'recent' ? loadRecent(record) : loadPurchase(record));
}

function openOverride(record: Item) {
  overrideRow.value = record;
  overridePrice.value = record.manualPriceOverride ? record.unitPrice ?? null : null;
  overrideReason.value = record.manualPriceOverride ? record.overrideReason ?? '' : '';
  overrideOpen.value = true;
}

function confirmOverride() {
  const record = overrideRow.value;
  if (!record) return;
  const price = overridePrice.value;
  if (price == null || price === '' || !Number.isFinite(Number(price)) || Number(price) < 0) {
    message.warning('请填写不小于 0 的改价后单价');
    return;
  }
  if (!overrideReason.value.trim()) {
    message.warning('请填写改价原因');
    return;
  }
  record.unitPrice = String(price);
  record.overrideReason = overrideReason.value.trim();
  record.manualPriceOverride = true;
  overrideOpen.value = false;
}

/** 取消人工改价：恢复按解析价成交。 */
function cancelOverride() {
  const record = overrideRow.value;
  if (!record) return;
  record.manualPriceOverride = false;
  record.unitPrice = null;
  record.overrideReason = null;
  overrideOpen.value = false;
}

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

watch(
  () => [props.customerId, ...props.items.map((item) => item.skuId)],
  () => {
    if (!props.customerId) return;
    const seen = new Set<string>();
    for (const row of props.items) {
      if (row.skuId == null || seen.has(String(row.skuId))) continue;
      seen.add(String(row.skuId));
      void loadRecent(row, true);
    }
  },
  {immediate: true}
);

function recentReady(record: Item): boolean {
  return hasRecentPrices(recentCache, props.customerId, record.skuId);
}

/** 已查过但没有历史价。 */
function recentEmpty(record: Item): boolean {
  return recentReady(record) && recentRows(record).length === 0;
}

function recentRows(record: Item): RecentPrice[] {
  return recentPriceRows(recentCache, props.customerId, record.skuId).filter(
    (row) => props.orderId == null || String(row.orderId) !== String(props.orderId)
  );
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

/** 人工改价优先于解析价：两者都是用户即将成交的单价规则。 */
function effectivePrice(record: Item): string | null {
  return record.manualPriceOverride ? record.unitPrice ?? null : record.draftUnitPrice ?? null;
}

/** 编辑旧订单时用已保存当时的数据回显，避免首屏 50 条之外只显示数字 ID。 */
function selectedSkuOptions(record: Item) {
  if (record.skuId == null || !record.productNameSnapshot) return [];
  return [{skuId: record.skuId, productName: record.productNameSnapshot, specName: record.specNameSnapshot || '默认规格'}];
}

/** 选中规格后立即回填商品资料，录单区不用等保存就能看到规格和单位。 */
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

async function loadRecent(record: Item, silent = false) {
  if (!props.customerId || record.skuId == null) return;
  try {
    await loadRecentPrices(recentCache, props.customerId, record.skuId, async () => {
      const r = await orderApi.recentPrices(props.customerId as Id, record.skuId as Id, 5, {suppressGlobalErrorMessage: true});
      return r.data;
    });
  } catch (e) {
    if (!silent) notifyRequestError(orderError(e));
  }
}

/** 抽屉关闭时由父组件调用；弹窗挂在 body 上，不会随抽屉自动消失。 */
defineExpose({
  closeRecentPopover: () => {
    referenceOpen.value = false;
    overrideOpen.value = false;
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
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 6px;
}

.price-value {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.price-tag {
  margin-inline-end: 0;
  padding-inline: 5px;
  color: #0958d9;
  background: #e6f4ff;
  font-size: 11px;
  line-height: 18px;
}

.price-flag {
  padding: 0 6px;
  color: #ad4e00;
  background: #fff7e6;
  border-radius: 3px;
  font-size: 11px;
  line-height: 18px;
  cursor: help;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 0 2px;
}

.row-actions :deep(.ant-btn) {
  padding-inline: 2px;
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
  margin-top: 4px;
  color: #fa8c16;
  font-size: 12px;
}

.recent-meta {
  margin-top: 4px;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}
</style>
