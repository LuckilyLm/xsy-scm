<template>
  <a-card size="small" :bordered="false" :loading="loading" class="product-detail">
    <a-button type="link" class="product-detail-back" @click="router.push('/product/product-list')">
      <ArrowLeftOutlined/>返回商品列表
    </a-button>
    <a-alert v-if="error" :message="error" type="error" show-icon>
      <template #action>
        <a-button size="small" @click="load">重新加载</a-button>
      </template>
    </a-alert>
    <template v-else-if="product">
      <header class="product-detail-header">
        <div class="product-detail-identity">
          <h1>{{ product.name }}</h1>
          <a-tag :color="product.status === 'ON_SHELF' ? 'green' : 'default'">
            {{ product.status === 'ON_SHELF' ? '上架' : '下架' }}
          </a-tag>
          <a-tag :color="MASTER_STATUS_COLOR[product.masterStatus]">
            {{ enumLabel(MASTER_STATUS_ENUM, product.masterStatus) }}
          </a-tag>
        </div>
        <a-button v-privilege="'scm:product:update'" type="primary" @click="editor?.open(product.spuId)">
          编辑商品
        </a-button>
      </header>
      <section class="product-overview" aria-label="商品概览">
        <a-image v-if="primaryImage?.fileUrl" :src="primaryImage.fileUrl" :width="104" :height="104"
                 :alt="primaryImage.fileName || product.name" class="product-overview-image"/>
        <div v-else class="product-overview-placeholder"><PictureOutlined/><span>暂无图片</span></div>
        <div class="product-overview-content">
          <dl class="product-facts">
            <div><dt>商品编码</dt><dd class="scm-mono">{{ product.spuCode }}</dd></div>
            <div class="product-fact-wide"><dt>分类</dt><dd>{{ product.categoryPath || '—' }}</dd></div>
            <div><dt>品牌</dt><dd>{{ product.brandName || '—' }}</dd></div>
            <div><dt>产地</dt><dd>{{ product.origin || '—' }}</dd></div>
            <div><dt>储存方式</dt><dd>{{ product.storageMethod ? enumLabel(STORAGE_METHOD_ENUM, product.storageMethod) : '—' }}</dd></div>
            <div><dt>保质期</dt><dd>{{ product.shelfLifeDays == null ? '—' : `${product.shelfLifeDays} 天` }}</dd></div>
          </dl>
          <div class="product-overview-price">
            <span>市场价 <strong class="scm-money">{{ priceRange(product.minMarketPrice, product.maxMarketPrice) }}</strong></span>
            <span>商品规格 <strong class="scm-quantity">{{ product.skuList.length }}</strong> 个</span>
          </div>
        </div>
      </section>
      <section class="product-detail-section">
        <h2>商品规格</h2>
        <SkuTable :rows="product.skuList"/>
      </section>
      <section class="product-detail-section">
        <h2>商品属性与简介</h2>
        <dl class="product-facts">
          <div><dt>别名</dt><dd>{{ product.alias || '—' }}</dd></div>
          <div><dt>助记码</dt><dd>{{ product.mnemonicCode || '—' }}</dd></div>
          <div><dt>损耗率</dt><dd>{{ product.lossRate == null ? '—' : `${product.lossRate}%` }}</dd></div>
          <div class="product-fact-full"><dt>商品标签</dt><dd>
            <a-space v-if="product.tags.length" wrap>
              <a-tag v-for="tag in product.tags" :key="tag.tagId" :color="tag.status === 'ENABLED' ? 'blue' : 'default'">{{ tag.name }}</a-tag>
            </a-space>
            <span v-else>—</span>
          </dd></div>
          <div class="product-fact-full"><dt>商品简介</dt><dd class="product-description">{{ product.description || '—' }}</dd></div>
        </dl>
      </section>
      <a-collapse :bordered="false" class="product-detail-extra">
        <a-collapse-panel key="tax" header="采购与税务">
          <dl class="product-facts">
            <div><dt>采购预警</dt><dd>{{ product.purchaseWarningDays == null ? '—' : `${product.purchaseWarningDays} 天` }}</dd></div>
            <div><dt>开票品名</dt><dd>{{ product.invoiceName || '—' }}</dd></div>
            <div><dt>税收编码</dt><dd>{{ product.taxCategoryCode || '—' }}</dd></div>
            <div><dt>免税</dt><dd>{{ product.taxExempt == null ? '—' : product.taxExempt ? '免税' : '否' }}</dd></div>
            <div><dt>税率</dt><dd>{{ product.taxRate == null ? '—' : `${product.taxRate}%` }}</dd></div>
          </dl>
        </a-collapse-panel>
        <a-collapse-panel key="images" :header="`商品图集（${product.images?.length ?? 0}）`">
          <a-image-preview-group v-if="product.images?.length">
            <div class="product-gallery">
              <figure v-for="image in product.images" :key="image.fileKey">
                <a-image :src="image.fileUrl" :width="104" :height="104" :alt="image.fileName || product.name"/>
                <figcaption :title="image.fileName">{{ image.primaryFlag ? '主图' : image.fileName }}</figcaption>
              </figure>
            </div>
          </a-image-preview-group>
          <span v-else class="product-detail-muted">暂无商品图片</span>
        </a-collapse-panel>
        <a-collapse-panel key="conversion" header="商城库存单位换算">
          <a-alert type="info" show-icon message="库存按配送仓的记账单位维护，销售单位可与记账单位不同；库存换算比例在订单确认时冻结。"/>
          <StockConversionEditor v-for="sku in product.skuList" :key="sku.skuId" :sku="sku"/>
        </a-collapse-panel>
        <a-collapse-panel key="bundle" header="组合商品组成">
          <BundleEditor v-for="sku in product.skuList" :key="`bundle-${sku.skuId}`" :sku="sku" @saved="load"/>
        </a-collapse-panel>
      </a-collapse>
      <footer class="product-detail-meta">
        <span>创建时间 {{ datetime(product.createdAt) }}</span>
        <span>更新时间 {{ datetime(product.updatedAt) }}</span>
      </footer>
    </template>
    <ProductFormDrawer ref="editor" @saved="load"/>
  </a-card>
</template>
<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {ArrowLeftOutlined, PictureOutlined} from '@ant-design/icons-vue';
import {productApi} from '/@/api/business/scm/product-api';
import type {ProductRow} from '/@/types/business/scm/product';
import {
  enumLabel,
  MASTER_STATUS_COLOR,
  MASTER_STATUS_ENUM,
  priceRange,
  STORAGE_METHOD_ENUM
} from '/@/constants/business/scm/product-const';
import ProductFormDrawer from './components/product-form-drawer.vue';
import SkuTable from './components/product-sku-table.vue';
import StockConversionEditor from './components/product-stock-conversion-editor.vue';
import BundleEditor from './components/product-bundle-editor.vue';
import {productError} from './product-errors';
import {datetime} from '../common/scm-display';

const route = useRoute(), router = useRouter();
const product = ref<ProductRow>(), loading = ref(false), error = ref('');
const editor = ref<InstanceType<typeof ProductFormDrawer>>();
const primaryImage = computed(() => product.value?.images?.find(image => image.primaryFlag) ?? product.value?.images?.[0]);
let requestId = 0;

/** 深链 spuId 是详情页唯一的上下文来源；非纯数字视为无效。 */
const spuId = computed(() => {
  const id = route.query.spuId;
  return typeof id === 'string' && /^\d+$/.test(id) ? id : '';
});

async function load() {
  const request = ++requestId;
  const id = spuId.value;
  if (!id) {
    product.value = undefined;
    error.value = '商品链接缺少有效编号';
    return;
  }
  loading.value = true;
  error.value = '';
  product.value = undefined;
  try {
    // 加载失败由页内 Alert 承担，不再让全局 toast 重复说一遍
    const response = await productApi.detail(id, {suppressGlobalErrorMessage: true});
    if (request === requestId) product.value = response.data;
  } catch (e) {
    if (request === requestId) error.value = productError(e);
  } finally {
    if (request === requestId) loading.value = false;
  }
}

watch(() => route.query.spuId, load, {immediate: true});
</script>
<style scoped>
.product-detail {
  min-width: 0;
  container-type: inline-size;
}

.product-detail-back.ant-btn {
  height: auto;
  padding: 0;
  margin-bottom: 12px;
}

.product-detail-header,
.product-detail-identity {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.product-detail-header {
  justify-content: space-between;
  margin-bottom: 16px;
}

.product-detail-identity {
  min-width: 0;
  flex: 1;
}

.product-detail-identity h1 {
  margin: 0 8px 0 0;
  font-size: 20px;
  line-height: 28px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.product-overview {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  padding: 16px;
  background: var(--scm-fill);
  border-radius: 6px;
}

.product-overview :deep(.ant-image) {
  flex: none;
}

.product-overview :deep(.ant-image-img),
.product-gallery :deep(.ant-image-img) {
  object-fit: contain;
}

.product-overview-placeholder {
  flex: none;
  width: 104px;
  height: 104px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--scm-text-secondary);
  font-size: 13px;
}

.product-overview-placeholder .anticon {
  font-size: 24px;
}

.product-overview-content {
  flex: 1;
  min-width: 0;
}

.product-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px 24px;
  margin: 0;
}

.product-facts > div {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
}

.product-facts dt {
  flex: none;
  width: 64px;
  color: var(--scm-text-secondary);
}

.product-facts dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}

.product-fact-wide {
  grid-column: span 2;
}

.product-fact-full {
  grid-column: 1 / -1;
}

.product-overview-price {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 32px;
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px solid var(--scm-border);
  color: var(--scm-text-secondary);
}

.product-overview-price strong {
  margin-left: 8px;
  font-size: 15px;
  color: var(--scm-text);
}

.product-detail-section {
  padding: 20px 0;
  min-width: 0;
  border-bottom: 1px solid var(--scm-border);
}

.product-detail-section h2 {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
}

.product-description {
  white-space: pre-wrap;
  max-width: 75ch;
}

.product-detail-extra.ant-collapse {
  background: transparent;
}

.product-detail-extra :deep(.ant-collapse-header) {
  padding: 14px 0;
  font-weight: 600;
}

.product-detail-extra :deep(.ant-collapse-content-box) {
  padding: 0 0 16px;
}

.product-gallery {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.product-gallery figure {
  margin: 0;
}

.product-gallery figcaption {
  max-width: 104px;
  margin-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--scm-text-secondary);
  font-size: 13px;
}

.product-detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
  padding-top: 16px;
  color: var(--scm-text-secondary);
  font-size: 13px;
}

.product-detail-muted {
  color: var(--scm-text-secondary);
}

@container (max-width: 900px) {
  .product-facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .product-fact-wide {
    grid-column: 1 / -1;
  }
}

@container (max-width: 560px) {
  .product-overview {
    flex-direction: column;
    gap: 12px;
    padding: 12px;
  }

  .product-facts {
    grid-template-columns: minmax(0, 1fr);
  }

  .product-overview-content {
    width: 100%;
  }
}
</style>
