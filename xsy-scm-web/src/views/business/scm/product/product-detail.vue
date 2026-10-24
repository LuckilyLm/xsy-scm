<!-- 商品详情：页头、基础信息、主档扩展、图集、规格与组合，只读，编辑回列表弹窗。 -->
<template>
  <a-card size="small" :bordered="false" :loading="loading">
    <header class="scm-detail-header">
      <div class="scm-detail-header__main">
        <a-button type="link" class="scm-detail-header__back" @click="router.push('/product/product-list')">
          <ArrowLeftOutlined />
          返回商品列表
        </a-button>
        <div class="scm-detail-header__title-row">
          <h1 class="scm-detail-header__title">{{ product?.name || '商品详情' }}</h1>
        </div>
        <p v-if="product?.categoryPath" class="scm-detail-header__meta">{{ product.categoryPath }}</p>
      </div>
    </header>

    <a-alert v-if="error" :message="error" type="error" show-icon>
      <template #action>
        <a-button size="small" @click="load">重新加载</a-button>
      </template>
    </a-alert>
    <template v-else-if="product">
      <div class="scm-detail-read">
        <div class="scm-detail-grid">
          <section class="scm-detail-card">
            <h3 class="scm-detail-card__title">基础信息</h3>
            <dl class="scm-field-list scm-field-list--2">
              <div class="scm-field">
                <dt class="scm-field__label">分类</dt>
                <dd class="scm-field__value">{{ product.categoryPath || '—' }}</dd>
              </div>
              <div class="scm-field">
                <dt class="scm-field__label">别名</dt>
                <dd class="scm-field__value">{{ product.alias || '—' }}</dd>
              </div>
              <div class="scm-field">
                <dt class="scm-field__label">市场价</dt>
                <dd class="scm-field__value scm-money">{{ priceRange(product.minMarketPrice, product.maxMarketPrice) }}</dd>
              </div>
              <div class="scm-field">
                <dt class="scm-field__label">在售状态</dt>
                <dd class="scm-field__value">{{ enumLabel(SHELF_STATUS_ENUM, product.status) }}</dd>
              </div>
              <div class="scm-field">
                <dt class="scm-field__label">主档状态</dt>
                <dd class="scm-field__value">
                  <ScmStatusTag
                    :tone="MASTER_STATUS_TONE[product.masterStatus] || 'neutral'"
                    :label="enumLabel(MASTER_STATUS_ENUM, product.masterStatus)"
                  />
                </dd>
              </div>
            </dl>
          </section>

          <section class="scm-detail-card">
            <h3 class="scm-detail-card__title">主档扩展</h3>
            <dl class="scm-field-list scm-field-list--2">
              <div class="scm-field">
                <dt class="scm-field__label">品牌</dt>
                <dd class="scm-field__value">{{ product.brandName || '—' }}</dd>
              </div>
              <div class="scm-field">
                <dt class="scm-field__label">产地</dt>
                <dd class="scm-field__value">{{ product.origin || '—' }}</dd>
              </div>
              <div class="scm-field">
                <dt class="scm-field__label">储存方式</dt>
                <dd class="scm-field__value">
                  {{ product.storageMethod ? enumLabel(STORAGE_METHOD_ENUM, product.storageMethod) : '—' }}
                </dd>
              </div>
              <div class="scm-field">
                <dt class="scm-field__label">保质期</dt>
                <dd class="scm-field__value">{{ product.shelfLifeDays == null ? '—' : `${product.shelfLifeDays} 天` }}</dd>
              </div>
              <div class="scm-field">
                <dt class="scm-field__label">损耗率</dt>
                <dd class="scm-field__value">{{ product.lossRate == null ? '—' : `${product.lossRate}%` }}</dd>
              </div>
              <div class="scm-field">
                <dt class="scm-field__label">采购预警</dt>
                <dd class="scm-field__value">
                  {{ product.purchaseWarningDays == null ? '—' : `${product.purchaseWarningDays} 天` }}
                </dd>
              </div>
              <div class="scm-field">
                <dt class="scm-field__label">开票品名</dt>
                <dd class="scm-field__value">{{ product.invoiceName || '—' }}</dd>
              </div>
              <div class="scm-field">
                <dt class="scm-field__label">税收编码</dt>
                <dd class="scm-field__value">{{ product.taxCategoryCode || '—' }}</dd>
              </div>
              <div class="scm-field">
                <dt class="scm-field__label">免税</dt>
                <dd class="scm-field__value">{{ product.taxExempt == null ? '—' : product.taxExempt ? '免税' : '否' }}</dd>
              </div>
              <div class="scm-field">
                <dt class="scm-field__label">税率</dt>
                <dd class="scm-field__value">{{ product.taxRate == null ? '—' : `${product.taxRate}%` }}</dd>
              </div>
            </dl>
          </section>

          <section class="scm-detail-card scm-detail-card--full">
            <h3 class="scm-detail-card__title">商品简介</h3>
            <p class="product-description">{{ product.description || '—' }}</p>
          </section>

          <section class="scm-detail-card scm-detail-card--full">
            <h3 class="scm-detail-card__title">商品标签</h3>
            <a-space v-if="product.tags.length" wrap>
              <ScmStatusTag v-for="tag in product.tags" :key="tag.tagId" :tone="tag.status === 'ENABLED' ? 'success' : 'neutral'" :label="tag.name" />
            </a-space>
            <span v-else>—</span>
          </section>

          <section class="scm-detail-card scm-detail-card--full">
            <h3 class="scm-detail-card__title">商品图集</h3>
            <a-image-preview-group v-if="product.images?.length">
              <a-space wrap>
                <figure v-for="image in product.images ?? []" :key="image.fileKey">
                  <a-image :src="image.fileUrl" :width="120" :height="120" :alt="image.fileName || product.name" />
                  <figcaption>{{ image.primaryFlag ? '主图' : image.fileName }}</figcaption>
                </figure>
              </a-space>
            </a-image-preview-group>
            <a-empty v-else description="暂无商品图片" :image="Empty.PRESENTED_IMAGE_SIMPLE" />
          </section>

          <section class="scm-detail-card scm-detail-card--full">
            <TraceAttachmentPanel
              scope="PRODUCT"
              :target-id="spuId"
              title="商品资质"
              :can-manage="canManageAttachment"
              empty-text="还没有上传资质文件"
            />
          </section>

          <section class="scm-detail-card scm-detail-card--full">
            <h3 class="scm-detail-card__title">商品规格</h3>
            <SkuTable :rows="product.skuList" />
          </section>

          <section class="scm-detail-card scm-detail-card--full">
            <h3 class="scm-detail-card__title">商城库存单位换算</h3>
            <StockConversionEditor v-for="sku in product.skuList" :key="sku.skuId" :sku="sku" />
          </section>

          <section class="scm-detail-card scm-detail-card--full">
            <h3 class="scm-detail-card__title">组合商品组成</h3>
            <BundleEditor v-for="sku in product.skuList" :key="`bundle-${sku.skuId}`" :sku="sku" @saved="load" />
          </section>

          <section class="scm-detail-card scm-detail-card--muted scm-detail-card--full">
            <h3 class="scm-detail-card__title">系统信息</h3>
            <dl class="scm-field-list scm-field-list--3">
              <div class="scm-field">
                <dt class="scm-field__label">创建时间</dt>
                <dd class="scm-field__value">{{ datetime(product.createdAt) }}</dd>
              </div>
              <div class="scm-field">
                <dt class="scm-field__label">更新时间</dt>
                <dd class="scm-field__value">{{ datetime(product.updatedAt) }}</dd>
              </div>
            </dl>
          </section>
        </div>
      </div>
    </template>
  </a-card>
</template>
<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {Empty} from 'ant-design-vue';
import {ArrowLeftOutlined} from '@ant-design/icons-vue';
import {productApi} from '/@/api/business/scm/product-api';
import type {ProductRow} from '/@/types/business/scm/product';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {enumLabel, MASTER_STATUS_ENUM, priceRange, SHELF_STATUS_ENUM, STORAGE_METHOD_ENUM} from '/@/constants/business/scm/product-const';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import SkuTable from './components/product-sku-table.vue';
import StockConversionEditor from './components/product-stock-conversion-editor.vue';
import BundleEditor from './components/product-bundle-editor.vue';
import TraceAttachmentPanel from '/@/views/business/scm/trace/components/trace-attachment-panel.vue';
import {hasPermission} from '../common/scm-permission';
import {productError} from './product-errors';
import {datetime} from '../common/scm-display';

const canManageAttachment = hasPermission('scm:trace:file:manage');

/** 主档生命周期与在售状态正交，配色沿用 SCM 状态档位。 */
const MASTER_STATUS_TONE: Record<string, ScmStatusTone> = {ENABLED: 'success', DISABLED: 'warning', ARCHIVED: 'neutral'};

const route = useRoute(),
  router = useRouter();
const product = ref<ProductRow>(),
  loading = ref(false),
  error = ref('');
let requestId = 0;

/** 商品编号来自路由，非纯数字视为无效。 */
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
    // 加载失败由页内 Alert 提示，不再弹全局 toast
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
figure {
  margin: 0;
}

figcaption {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-description {
  margin: 0;
  line-height: 22px;
  white-space: pre-wrap;
}
</style>
