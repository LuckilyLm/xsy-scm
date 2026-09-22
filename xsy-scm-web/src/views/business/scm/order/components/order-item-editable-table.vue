<!-- 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/order/order-item-list.vue
复制日期：2026-09-16。Copy First + Adapt。
剪枝：履约/支付/裸ID/独立明细写入口/列拖拽。
适配：四状态、API、权限、四位定点、NULL、version、幂等、错误重试。
验收：W4 单测、TS 棘轮与 Playwright。 -->
<template>
  <a-table id="order-item-table" size="small" bordered :data-source="items" :columns="columns" :pagination="false"
           :scroll="{x:1200}" :row-key="(_row:Item,index?:number)=>index??0">
    <template #bodyCell="{record,column,index}">
      <template v-if="column.dataIndex==='skuId'">
        <SkuSelect :value="record.skuId" @update:value="v=>{record.skuId=Array.isArray(v)?v[0]:v;emit('price');}"
                   width="270px" :disabled-statuses="[]"/>
      </template>
      <template v-else-if="column.dataIndex==='orderedQuantity'">
        <a-input-number string-mode :precision="4" :min="'0.0001'" v-model:value="record.orderedQuantity"
                        :aria-label="'下单数量 '+(index+1)"/>
      </template>
      <template v-else-if="column.dataIndex==='draftUnitPrice'">
        <div class="price-cell">
          <span>{{ amount(record.draftUnitPrice, true) }}</span>
          <a-popover v-if="customerId && record.skuId" trigger="click"
                     placement="left" :title="'最近已确认订单价 · '+(record.skuCodeSnapshot||'SKU')">
            <a-button type="link" size="small" class="recent-btn" aria-label="最近已确认订单价"
                      @click="loadRecent(index, record)">历史价</a-button>
            <template #content>
              <a-spin :spinning="recentLoading===index">
                <div class="recent-wrap">
                  <a-empty v-if="recentMap[index]&&!recentMap[index].length" description="该客户该商品暂无已确认历史价"/>
                  <div v-else-if="recentMap[index]" class="recent-list">
                    <div v-for="p in recentMap[index]" :key="p.itemId" class="recent-row">
                      <div class="recent-head">
                        <span class="recent-price">{{ amount(p.unitPrice) }}<span v-if="p.saleUnit"
                                                                                  class="recent-unit">/{{ p.saleUnit }}</span></span>
                        <a-tag v-if="p.priceSource">{{ priceSourceDesc(p.priceSource) }}</a-tag>
                      </div>
                      <div v-if="p.saleUnit && record.saleUnitSnapshot && p.saleUnit!==record.saleUnitSnapshot"
                           class="recent-warn">单位「{{ p.saleUnit }}」与当前「{{ record.saleUnitSnapshot }}」不同，不可直接比较</div>
                      <div class="recent-meta">{{ p.orderNo }} · {{ p.orderedQuantity }} · {{ dayjs(p.confirmedAt).format('YYYY-MM-DD') }}</div>
                    </div>
                  </div>
                </div>
              </a-spin>
            </template>
          </a-popover>
        </div>
      </template>
      <template v-else-if="column.dataIndex==='override'">
        <a-space direction="vertical">
          <a-checkbox v-privilege="'scm:order:price-override'" v-model:checked="record.manualPriceOverride">人工改价
          </a-checkbox>
          <template v-if="record.manualPriceOverride">
            <a-input-number string-mode :precision="4" :min="'0'" v-model:value="record.unitPrice"
                            aria-label="人工单价"/>
            <a-input v-model:value="record.overrideReason" placeholder="改价原因（必填）" aria-label="改价原因"
                     maxlength="500"/>
          </template>
        </a-space>
      </template>
      <template v-else-if="column.dataIndex==='action'">
        <a-button danger type="link" @click="items.splice(index,1)">移除</a-button>
      </template>
    </template>
  </a-table>
  <a-button class="add-line" @click="items.push({orderedQuantity:'1.0000',manualPriceOverride:false})">添加商品
  </a-button>
</template>
<script setup lang="ts">
import {ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import dayjs from 'dayjs';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import {orderApi} from '/@/api/business/scm/order-api';
import {SCM_ORDER_PRICE_SOURCE_ENUM} from '/@/constants/business/scm/order-const';
import type {Id, Item, RecentPrice} from '../order-types';
import {amount} from '../order-form-model';

const props = defineProps<{ items: Item[]; customerId?: Id }>();
const emit = defineEmits<{ price: [] }>();
const columns: TableColumnsType<Item> = [{
  title: '商品 / 规格 / SKU',
  dataIndex: 'skuId',
  width: 300
}, {title: '下单数量', dataIndex: 'orderedQuantity', align: 'right', width: 165}, {
  title: '解析单价',
  dataIndex: 'draftUnitPrice',
  align: 'right',
  width: 190
}, {title: '价格修订', dataIndex: 'override', width: 230}, {
  title: '操作',
  dataIndex: 'action',
  align: 'right',
  width: 80
}];

/*
 * 最近已确认订单价（Wave 3 §7.5）：只读旁证，点开时按「当前客户 + 当前行 SKU」现查现显（后端只取 CONFIRMED）。
 * 不回写解析单价、不参与定价、不换算单位（历史单位不同仅提示不可比较），因此每行独立缓存到本次抽屉生命周期。
 */
const recentMap = ref<Record<number, RecentPrice[]>>({}),
    recentLoading = ref<number | null>(null);

function priceSourceDesc(source: string): string {
  return SCM_ORDER_PRICE_SOURCE_ENUM[source]?.desc ?? source;
}

async function loadRecent(index: number, record: Item) {
  if (recentMap.value[index] || !props.customerId || !record.skuId) return;
  recentLoading.value = index;
  try {
    const r = await orderApi.recentPrices(props.customerId, record.skuId, 5);
    recentMap.value[index] = r.data;
  } finally {
    recentLoading.value = null;
  }
}
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
