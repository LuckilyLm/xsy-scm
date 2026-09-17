<template>
  <a-card size="small" :bordered="false" :loading="loading">
    <a-space class="smart-margin-bottom10"><a-button @click="router.push('/product/product-list')">返回商品列表</a-button><a-button @click="load">刷新详情</a-button></a-space>
    <a-alert v-if="error" :message="error" type="error" show-icon><template #action><a-button size="small" @click="load">重新加载</a-button></template></a-alert>
    <template v-else-if="product">
      <a-descriptions :title="product.name" bordered :column="{ xs: 1, sm: 2, lg: 3 }">
        <a-descriptions-item label="SPU 编码">{{ product.spuCode }}</a-descriptions-item>
        <a-descriptions-item label="分类">{{ product.categoryPath }}</a-descriptions-item>
        <a-descriptions-item label="状态"><a-tag :color="product.status === 'ON_SHELF' ? 'green' : 'default'">{{ product.status === 'ON_SHELF' ? '上架' : '下架' }}</a-tag></a-descriptions-item>
        <a-descriptions-item label="别名">{{ product.alias || '—' }}</a-descriptions-item>
        <a-descriptions-item label="市场价">{{ priceRange(product.minMarketPrice, product.maxMarketPrice) }}</a-descriptions-item>
        <a-descriptions-item label="更新时间">{{ datetime(product.updatedAt) }}</a-descriptions-item>
        <a-descriptions-item label="商品简介" :span="3">{{ product.description || '—' }}</a-descriptions-item>
      </a-descriptions>
      <a-divider orientation="left">商品图集</a-divider>
      <a-image-preview-group v-if="product.images.length"><a-space wrap><figure v-for="image in product.images" :key="image.fileKey"><a-image :src="image.fileUrl" :width="120" :height="120" :alt="image.fileName || product.name" /><figcaption>{{ image.primaryFlag ? '主图' : image.fileName }}</figcaption></figure></a-space></a-image-preview-group>
      <a-empty v-else description="暂无商品图片" :image="Empty.PRESENTED_IMAGE_SIMPLE" />
      <a-divider orientation="left">SKU 规格</a-divider><SkuTable :rows="product.skuList" />
    </template>
  </a-card>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Empty } from 'ant-design-vue';
import { productApi } from '/@/api/business/scm/product-api';
import type { ProductRow } from '/@/types/business/scm/product';
import { priceRange } from '/@/constants/business/scm/product-const';
import SkuTable from './components/product-sku-table.vue';
import { productError } from './product-errors';
import { datetime } from '../common/scm-display';
const route = useRoute(), router = useRouter();
const product = ref<ProductRow>(), loading = ref(false), error = ref('');
let requestId = 0;
async function load() {
  const request = ++requestId; const id = route.query.spuId;
  if (typeof id !== 'string' || !/^\d+$/.test(id)) { product.value = undefined; error.value = '商品链接缺少有效编号'; return; }
  loading.value = true; error.value = ''; product.value = undefined;
  try { const response = await productApi.detail(id); if (request === requestId) product.value = response.data; }
  catch (e) { if (request === requestId) error.value = productError(e); }
  finally { if (request === requestId) loading.value = false; }
}
watch(() => route.query.spuId, load, { immediate: true });
</script>
<style scoped>figure { margin: 0; } figcaption { max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }</style>
