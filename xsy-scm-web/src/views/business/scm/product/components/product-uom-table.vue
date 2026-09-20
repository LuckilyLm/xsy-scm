<!-- PCO-1 辅助资料「计量单位」Tab：字典只作取值来源，编码与名称不可改。 -->
<template>
  <a-form class="smart-query-form" layout="inline" @finish="search">
    <a-form-item label="关键字" class="smart-query-form-item"><a-input v-model:value="query.keyword" allow-clear placeholder="编码 / 名称" style="width: 200px" /></a-form-item>
    <a-form-item label="状态" class="smart-query-form-item"><a-select v-model:value="query.status" allow-clear :options="ENABLE_STATUS_ENUM" style="width: 110px" /></a-form-item>
    <a-form-item class="smart-query-form-item"><a-space><a-button type="primary" html-type="submit">查询</a-button><a-button @click="reset">重置</a-button></a-space></a-form-item>
  </a-form>
  <a-row class="smart-table-btn-block" justify="space-between">
    <a-button v-privilege="'scm:product:uom:add'" type="primary" @click="modal?.open()">新增单位</a-button>
    <a-button :loading="loading" @click="load">刷新</a-button>
  </a-row>
  <a-alert v-if="error" type="error" :message="error" show-icon class="smart-margin-bottom10"><template #action><a-button size="small" @click="load">重新加载</a-button></template></a-alert>
  <a-table :data-source="rows" :columns="columns" row-key="uomId" :loading="loading" :pagination="false" size="small" bordered>
    <template #bodyCell="{ column, record }">
      <span v-if="column.dataIndex === 'category'">{{ enumLabel(UOM_CATEGORY_ENUM, record.category) }}</span>
      <a-tag v-else-if="column.dataIndex === 'status'" :color="record.status === 'ENABLED' ? 'green' : 'default'">{{ enumLabel(ENABLE_STATUS_ENUM, record.status) }}</a-tag>
      <span v-else-if="column.dataIndex === 'referencedCount'" class="price">{{ record.referencedCount }}</span>
      <a-space v-else-if="column.dataIndex === 'action'" :size="0" class="smart-table-operate">
        <a-button v-privilege="'scm:product:uom:update'" type="link" size="small" @click="modal?.open(record)">编辑</a-button>
        <a-tooltip v-if="record.referencedCount > 0" title="已被商品销售单位或供应商采购单位引用，只能停用不能删除">
          <a-button v-privilege="'scm:product:uom:delete'" type="link" size="small" disabled>删除</a-button>
        </a-tooltip>
        <a-popconfirm v-else title="确认删除此计量单位？" @confirm="remove(record)"><a-button v-privilege="'scm:product:uom:delete'" type="link" danger size="small">删除</a-button></a-popconfirm>
      </a-space>
    </template>
  </a-table>
  <UomModal ref="modal" @saved="load" />
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import type { TableColumnsType } from 'ant-design-vue';
import { productUomApi } from '/@/api/business/scm/product-assistant-api';
import type { AssistantQuery, ProductUom } from '/@/types/business/scm/product';
import { ENABLE_STATUS_ENUM, enumLabel, UOM_CATEGORY_ENUM } from '/@/constants/business/scm/product-const';
import { productError } from '../product-errors';
import UomModal from './product-uom-modal.vue';
const query = reactive<AssistantQuery>({}), rows = ref<ProductUom[]>([]), loading = ref(false), error = ref('');
const modal = ref<InstanceType<typeof UomModal>>();
const columns: TableColumnsType<ProductUom> = [
  { title: '排序', dataIndex: 'sortOrder', width: 70, align: 'right' }, { title: '单位编码', dataIndex: 'uomCode', width: 150 },
  { title: '单位名称', dataIndex: 'name', width: 120 }, { title: '量纲', dataIndex: 'category', width: 90 },
  { title: '小数位', dataIndex: 'precisionScale', width: 80, align: 'right' }, { title: '被引用', dataIndex: 'referencedCount', width: 80, align: 'right' },
  { title: '状态', dataIndex: 'status', width: 80, align: 'center' }, { title: '操作', dataIndex: 'action', width: 140, align: 'right' },
];
async function load() {
  loading.value = true; error.value = '';
  try { rows.value = (await productUomApi.list({ ...query })).data; } catch (e) { error.value = productError(e); } finally { loading.value = false; }
}
function search() { void load(); }
function reset() { query.keyword = undefined; query.status = undefined; void load(); }
async function remove(row: ProductUom) {
  try { await productUomApi.delete(row.uomId, row.version); message.success('计量单位已删除'); await load(); } catch (e) { error.value = productError(e); }
}
onMounted(load);
</script>
<style scoped>.price { font-variant-numeric: tabular-nums; }</style>
