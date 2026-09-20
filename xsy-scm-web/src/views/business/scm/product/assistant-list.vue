<!-- PCO-1 辅助资料：计量单位与商品标签两个字典，供商品档案与后续采购/库存选择取值。 -->
<template>
  <section aria-label="辅助资料">
    <a-card size="small" :bordered="false">
      <a-tabs v-model:activeKey="tab" destroy-inactive-tab-pane>
        <a-tab-pane key="UOM" tab="计量单位"><UomTable /></a-tab-pane>
        <a-tab-pane key="TAG" tab="商品标签"><TagTable /></a-tab-pane>
      </a-tabs>
    </a-card>
  </section>
</template>
<script setup lang="ts">
import { ref } from 'vue';
import UomTable from './components/product-uom-table.vue';
import TagTable from './components/product-tag-table.vue';
const tab = ref<'UOM' | 'TAG'>('UOM');
</script>
