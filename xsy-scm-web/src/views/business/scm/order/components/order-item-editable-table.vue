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
      <template v-else-if="column.dataIndex==='draftUnitPrice'">{{ amount(record.draftUnitPrice, true) }}</template>
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
import type {TableColumnsType} from 'ant-design-vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import type {Item} from '../order-types';
import {amount} from '../order-form-model';

defineProps<{ items: Item[] }>();
const emit = defineEmits<{ price: [] }>();
const columns: TableColumnsType<Item> = [{
  title: '商品 / 规格 / SKU',
  dataIndex: 'skuId',
  width: 300
}, {title: '下单数量', dataIndex: 'orderedQuantity', align: 'right', width: 165}, {
  title: '解析单价',
  dataIndex: 'draftUnitPrice',
  align: 'right',
  width: 140
}, {title: '价格修订', dataIndex: 'override', width: 230}, {
  title: '操作',
  dataIndex: 'action',
  align: 'right',
  width: 80
}];
</script>
<style scoped>.add-line {
  margin-top: 12px;
}</style>
