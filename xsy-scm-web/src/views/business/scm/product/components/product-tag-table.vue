<!-- PCO-1 辅助资料「商品标签」Tab：标签被引用后只能停用，历史商品仍保留展示。 -->
<template>
  <a-form class="smart-query-form" layout="inline" @finish="search">
    <a-row class="smart-query-form-row">
      <a-form-item label="关键字" class="smart-query-form-item"><a-input v-model:value="query.keyword" allow-clear placeholder="编码 / 名称" style="width: 200px" /></a-form-item>
      <a-form-item label="状态" class="smart-query-form-item"><a-select v-model:value="query.status" allow-clear :options="ENABLE_STATUS_ENUM" style="width: 110px" /></a-form-item>
      <a-form-item class="smart-query-form-item"><a-space><a-button type="primary" html-type="submit">查询</a-button><a-button @click="reset">重置</a-button></a-space></a-form-item>
    </a-row>
  </a-form>
  <a-row class="smart-table-btn-block" justify="space-between">
    <a-button v-privilege="'scm:product:tag:add'" type="primary" @click="modal?.open()">新增标签</a-button>
    <a-button :loading="loading" @click="load">刷新</a-button>
  </a-row>
  <a-alert v-if="error" type="error" :message="error" show-icon class="smart-margin-bottom10">
    <template #action>
      <a-button size="small" @click="load">重新加载</a-button>
    </template>
  </a-alert>
  <a-table :data-source="rows" :columns="columns" row-key="tagId" :loading="loading" :pagination="false" size="small"
           bordered>
    <template #bodyCell="{ column, record }">
      <a-tag v-if="column.dataIndex === 'status'" :color="record.status === 'ENABLED' ? 'green' : 'default'">
        {{ enumLabel(ENABLE_STATUS_ENUM, record.status) }}
      </a-tag>
      <span v-else-if="column.dataIndex === 'productCount'" class="price">{{ record.productCount }}</span>
      <a-space v-else-if="column.dataIndex === 'action'" :size="0" class="smart-table-operate">
        <a-button v-privilege="'scm:product:tag:update'" type="link" size="small" @click="modal?.open(record)">编辑
        </a-button>
        <a-tooltip v-if="record.productCount > 0"
                   title="仍有商品挂着此标签，只能停用；停用后不再出现在可选标签里，历史绑定保留">
          <a-button v-privilege="'scm:product:tag:delete'" type="link" size="small" disabled>删除</a-button>
        </a-tooltip>
        <a-popconfirm v-else title="确认删除此标签？" @confirm="remove(record)">
          <a-button v-privilege="'scm:product:tag:delete'" type="link" danger size="small">删除</a-button>
        </a-popconfirm>
      </a-space>
    </template>
  </a-table>
  <TagModal ref="modal" @saved="load"/>
</template>
<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {message} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {productTagApi} from '/@/api/business/scm/product-assistant-api';
import type {AssistantQuery, ProductTag} from '/@/types/business/scm/product';
import {ENABLE_STATUS_ENUM, enumLabel} from '/@/constants/business/scm/product-const';
import {productError} from '../product-errors';
import TagModal from './product-tag-modal.vue';

const query = reactive<AssistantQuery>({}), rows = ref<ProductTag[]>([]), loading = ref(false), error = ref('');
const modal = ref<InstanceType<typeof TagModal>>();
const columns: TableColumnsType<ProductTag> = [
  {title: '排序', dataIndex: 'sortOrder', width: 70, align: 'right'}, {
    title: '标签编码',
    dataIndex: 'tagCode',
    width: 170
  },
  {title: '标签名称', dataIndex: 'name', width: 170}, {
    title: '已打标商品',
    dataIndex: 'productCount',
    width: 110,
    align: 'right'
  },
  {title: '状态', dataIndex: 'status', width: 80, align: 'center'}, {
    title: '操作',
    dataIndex: 'action',
    width: 140,
    align: 'right'
  },
];

async function load() {
  loading.value = true;
  error.value = '';
  try {
    rows.value = (await productTagApi.list({...query})).data;
  } catch (e) {
    error.value = productError(e);
  } finally {
    loading.value = false;
  }
}

function search() {
  void load();
}

function reset() {
  query.keyword = undefined;
  query.status = undefined;
  void load();
}

async function remove(row: ProductTag) {
  try {
    await productTagApi.delete(row.tagId, row.version);
    message.success('标签已删除');
    await load();
  } catch (e) {
    error.value = productError(e);
  }
}

onMounted(load);
</script>
<style scoped>.price {
  font-variant-numeric: tabular-nums;
}</style>
