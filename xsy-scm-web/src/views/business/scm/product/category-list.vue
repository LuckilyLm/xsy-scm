<template>
  <a-card size="small" :bordered="false">
    <a-row data-toolbar-layout="single" class="xsy-table-btn-block">
      <a-button v-privilege="'scm:product:category:add'" type="primary" @click="modal?.open()">新增分类</a-button>
    </a-row>
    <RequestError v-if="error" :message="error" class="xsy-margin-bottom10">
      <template #action>
        <a-button size="small" @click="load">重新加载</a-button>
      </template>
    </RequestError>
    <CategoryTable
      :rows="rows"
      :loading="loading"
      @add="(row) => modal?.open(undefined, row.categoryId)"
      @edit="(row) => modal?.open(row)"
      @remove="remove"
    />
    <CategoryModal ref="modal" :categories="rows" @saved="load" />
  </a-card>
</template>
<script setup lang="ts">
import {onMounted, ref} from 'vue';
import {message} from 'ant-design-vue';
import {productCategoryApi} from '/@/api/business/scm/product-category-api';
import type {ProductCategory} from '/@/types/business/scm/product';
import CategoryTable from './components/category-tree-table.vue';
import CategoryModal from './components/category-form-modal.vue';
import {productError} from './product-errors';

const rows = ref<ProductCategory[]>([]),
  loading = ref(false),
  error = ref('');
const modal = ref<InstanceType<typeof CategoryModal>>();

async function load() {
  loading.value = true;
  error.value = '';
  try {
    // 请求失败交给共享反馈处理，避免重复提示。
    rows.value = (await productCategoryApi.tree({suppressGlobalErrorMessage: true})).data;
  } catch (e) {
    error.value = productError(e);
  } finally {
    loading.value = false;
  }
}

async function remove(row: ProductCategory) {
  try {
    await productCategoryApi.delete(row.categoryId, row.version);
    message.success('分类已删除');
    await load();
  } catch {
    // 删除失败由全局 toast 提示
  }
}

onMounted(load);
</script>
