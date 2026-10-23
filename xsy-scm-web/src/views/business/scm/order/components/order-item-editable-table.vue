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
      <template v-else-if="column.dataIndex === 'orderedQuantity'">
        <a-input-number string-mode :precision="4" :min="'0.0001'" v-model:value="record.orderedQuantity" :aria-label="'下单数量 ' + (index + 1)" />
      </template>
      <template v-else-if="column.dataIndex === 'draftUnitPrice'">
        <div class="price-cell">
          <span>{{ amount(record.draftUnitPrice, true) }}</span>
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
        </div>
      </template>
      <template v-else-if="column.dataIndex === 'override'">
        <a-space direction="vertical">
          <a-checkbox v-privilege="'scm:order:price-override'" v-model:checked="record.manualPriceOverride">人工改价 </a-checkbox>
          <template v-if="record.manualPriceOverride">
            <a-input-number string-mode :precision="4" :min="'0'" v-model:value="record.unitPrice" aria-label="人工单价" />
            <a-input v-model:value="record.overrideReason" placeholder="改价原因（必填）" aria-label="改价原因" maxlength="500" />
          </template>
        </a-space>
      </template>
      <template v-else-if="column.dataIndex === 'action'">
        <a-button danger type="link" @click="items.splice(index, 1)">移除</a-button>
      </template>
    </template>
  </a-table>
  <a-button class="add-line" @click="items.push({orderedQuantity: '1.0000', manualPriceOverride: false})">添加商品 </a-button>
</template>
<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {reactive, ref, watch} from 'vue';
import {message, type TableColumnsType} from 'ant-design-vue';
import dayjs from 'dayjs';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import {orderApi} from '/@/api/business/scm/order-api';
import {SCM_ORDER_PRICE_SOURCE_ENUM} from '/@/constants/business/scm/order-const';
import type {Id, Item, RecentPrice} from '../order-types';
import {amount, createRecentPriceCache, hasRecentPrices, isLoadingRecentPrice, loadRecentPrices, recentPriceRows} from '../order-form-model';
import {orderError} from '../order-errors';

const props = defineProps<{items: Item[]; customerId?: Id}>();
const emit = defineEmits<{price: []}>();
const columns: TableColumnsType<Item> = [
  {
    title: '商品 / 商品规格',
    dataIndex: 'skuId',
    width: 300,
  },
  {title: '下单数量', dataIndex: 'orderedQuantity', width: 165},
  {
    title: '解析单价',
    dataIndex: 'draftUnitPrice',
    width: 190,
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

/** 同一时刻只允许一行打开历史价浮层。 */
const recentOpenIndex = ref<number | null>(null);

// 客户或商品变化后关掉浮层，避免旧价格留在新行旁
watch(
  () => props.customerId,
  () => (recentOpenIndex.value = null)
);
watch(
  () => props.items.map((item) => item.skuId),
  () => (recentOpenIndex.value = null)
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
defineExpose({closeRecentPopover: () => (recentOpenIndex.value = null)});
</script>
<style scoped>
.add-line {
  margin-top: 12px;
}

.price-cell {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
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
