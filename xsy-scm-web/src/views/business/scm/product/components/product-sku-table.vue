<template>
  <a-table :columns="columns" :data-source="rows" row-key="skuId" :pagination="false" size="small" :scroll="{ x: 920 }">
    <template #bodyCell="{ column, record }">
      <span v-if="column.dataIndex === 'marketPrice'"
            class="price">{{ record.marketPrice === null ? '未定价' : `¥ ${record.marketPrice}` }}</span>
      <a-tag v-else-if="column.dataIndex === 'status'" :color="record.status === 'ON_SHELF' ? 'green' : 'default'">
        {{ record.status === 'ON_SHELF' ? '上架' : '下架' }}
      </a-tag>
      <a-tag v-else-if="column.dataIndex === 'defaultFlag' && record.defaultFlag" color="green">默认</a-tag>
      <span v-else-if="column.dataIndex === 'productType'">{{
          record.productType === 'STANDARD' ? '标品' : '非标品'
        }}</span>
      <span v-else-if="column.dataIndex === 'specValues'">{{
          Object.entries(record.specValues || {}).map(([key, value]) => `${key}：${value}`).join(' / ') || '—'
        }}</span>
    </template>
  </a-table>
</template>
<script setup lang="ts">
import type {TableColumnsType} from 'ant-design-vue';
import type {ProductSku} from '/@/types/business/scm/product';

defineProps<{ rows: ProductSku[] }>();
const columns: TableColumnsType<ProductSku> = [
  {title: 'SKU 编码', dataIndex: 'skuCode', width: 160}, {title: '规格名称', dataIndex: 'specName', width: 120},
  {title: '规格属性', dataIndex: 'specValues', width: 160}, {title: '条码', dataIndex: 'barcode', width: 130},
  {title: '单位', dataIndex: 'saleUnit', width: 70}, {title: '类型', dataIndex: 'productType', width: 80},
  {title: '市场价', dataIndex: 'marketPrice', width: 130, align: 'right'}, {
    title: '状态',
    dataIndex: 'status',
    width: 80,
    align: 'center'
  },
  {title: '默认', dataIndex: 'defaultFlag', width: 70, align: 'center'},
];
</script>
<style scoped>.price {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}</style>
