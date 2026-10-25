<template>
  <a-card :bordered="false" size="small">
    <div class="popup-heading">
      <div>
        <h2>首页弹窗</h2>
        <p class="scm-note">最多 5 张图片，保存后顾客打开首页时展示。</p>
        <p class="popup-versions">
          当前版本 {{ version }}<template v-if="updatedAt"> · 更新于 {{ updatedAt }} · {{ updatedBy || '—' }}</template>
        </p>
      </div>
      <a-space>
        <a-button v-privilege="'scm:mall:popup:query'" :disabled="!items.length" @click="previewOpen = true">预览弹窗</a-button>
        <a-button v-privilege="'scm:mall:popup:edit'" type="primary" :loading="saving" :disabled="!dirty" @click="save">保存配置</a-button>
      </a-space>
    </div>

    <a-alert v-if="error" type="error" show-icon :message="error" class="popup-alert">
      <template #action><a-button size="small" @click="load">重新加载</a-button></template>
    </a-alert>

    <a-spin :spinning="loading">
      <a-form layout="vertical" class="popup-form">
        <a-form-item label="启用首页弹窗">
          <a-switch v-model:checked="enabled" @change="dirty = true" />
          <span class="popup-note">关闭后商城不再弹出，已保存的图片不会丢失。</span>
        </a-form-item>
        <a-form-item label="展示规则">
          <a-radio-group v-model:value="displayRule" @change="dirty = true">
            <a-radio value="DAILY">每日首次展示</a-radio>
            <a-radio value="EVERY_ENTRY">每次进入展示</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="生效时间">
          <a-range-picker
            v-model:value="period"
            show-time
            value-format="YYYY-MM-DD HH:mm:ss"
            :allow-empty="[true, true]"
            style="width: 380px"
            @change="dirty = true"
          />
          <span class="popup-note">留空表示长期生效；结束时间必须晚于开始时间。</span>
        </a-form-item>

        <div class="popup-section-head">
          <strong>轮播弹窗</strong>
          <span>{{ items.length }}/5</span>
        </div>
        <div class="popup-items">
          <div v-for="(item, index) in items" :key="index" class="popup-item">
            <div class="popup-item__image">
              <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.title || `弹窗图片${index + 1}`" />
              <div v-else class="popup-item__placeholder">806×1068</div>
              <a-upload :accept="IMAGE_ACCEPT" :show-upload-list="false" :custom-request="uploaderFor(item)">
                <a-button size="small" :loading="uploadingIndex === index">{{ item.imageFileKey ? '更换图片' : '上传图片' }}</a-button>
              </a-upload>
              <a-button type="link" danger size="small" class="popup-item__remove" @click="removeItem(index)">移除图片</a-button>
            </div>
            <div class="popup-item__fields">
              <a-form-item label="跳转类型">
                <a-select v-model:value="item.linkType" :options="linkOptions" @change="onLinkTypeChange(item)" />
              </a-form-item>
              <a-form-item v-if="item.linkType === 'PRODUCT'" label="目标商品规格">
                <SkuSelect v-model:value="item.productTargetId" />
              </a-form-item>
              <a-form-item v-else-if="item.linkType === 'CATEGORY'" label="目标分类">
                <CategorySelect v-model:value="item.categoryTargetId" :categories="categories" />
              </a-form-item>
              <a-form-item v-else-if="item.linkType === 'ACTIVITY'" label="目标活动">
                <a-select v-model:value="item.activityTargetId" show-search option-filter-prop="label" :options="activityOptions" />
              </a-form-item>
              <a-form-item v-else-if="item.linkType === 'EXTERNAL_URL'" label="外部网页地址">
                <a-input v-model:value="item.externalUrl" placeholder="仅限已授权的 HTTPS 域名" />
              </a-form-item>
              <a-form-item label="图片说明（可选）">
                <a-input v-model:value="item.title" :maxlength="80" placeholder="用于无障碍描述，不显示在弹窗上" />
              </a-form-item>
            </div>
          </div>
          <a-empty v-if="!items.length" description="还没有弹窗图片，添加后保存即可在商城展示" />
        </div>
        <a-button v-privilege="'scm:mall:popup:edit'" type="dashed" block :disabled="items.length >= 5" @click="addItem">+ 新增一张图片</a-button>
      </a-form>
    </a-spin>

    <a-modal v-model:open="previewOpen" title="弹窗预览" :footer="null" :width="420">
      <div class="popup-preview">
        <a-carousel v-if="items.length" :dots="items.length > 1" :autoplay="false">
          <div v-for="(item, index) in items" :key="index" class="popup-preview__slide">
            <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.title || '弹窗预览'" />
            <div v-else class="popup-preview__placeholder">图片 {{ index + 1 }}</div>
          </div>
        </a-carousel>
        <p class="popup-preview__hint">
          实际展示时右上角会有「关闭」按钮；{{ displayRule === 'DAILY' ? '每日只出现一次' : '每次进入首页都会出现' }}。
        </p>
      </div>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue';
import {message, type UploadProps} from 'ant-design-vue';
import {fileApi} from '/@/api/support/file-api';
import {FILE_FOLDER_TYPE_ENUM} from '/@/constants/support/file-const';
import {IMAGE_ACCEPT, imageFileError} from '/@/constants/business/scm/image-const';
import {productCategoryApi} from '/@/api/business/scm/product-category-api';
import {promotionApi} from '/@/api/business/scm/promotion-api';
import type {ProductCategory} from '/@/types/business/scm/product';
import type {ScmId} from '/@/types/business/scm/customer';
import {mallHomeApi, type HomeLinkType, type HomePopupDisplayRule} from '/@/api/business/scm/mall-home-api';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import CategorySelect from '/@/components/business/scm/product-category-tree-select/index.vue';

type UploadOptions = Parameters<NonNullable<UploadProps['customRequest']>>[0];

/** 弹窗编辑态：跳转目标按类型分开保存，提交时再归一成 linkTarget。 */
interface PopupItemDraft {
  imageFileKey: string;
  imageUrl?: string | null;
  linkType: HomeLinkType;
  title?: string | null;
  productTargetId?: ScmId;
  categoryTargetId?: ScmId;
  activityTargetId?: ScmId;
  externalUrl?: string;
}

const linkOptions: {value: HomeLinkType; label: string}[] = [
  {value: 'NONE', label: '不跳转'},
  {value: 'PRODUCT', label: '商品详情'},
  {value: 'CATEGORY', label: '商品分类'},
  {value: 'ACTIVITY', label: '活动列表'},
  {value: 'COUPONS', label: '领券中心'},
  {value: 'SEARCH', label: '商品搜索'},
  {value: 'EXTERNAL_URL', label: '允许的外部网页'},
];

const version = ref(0);
const enabled = ref(false);
const displayRule = ref<HomePopupDisplayRule>('DAILY');
const period = ref<[string, string] | undefined>();
const items = ref<PopupItemDraft[]>([]);
const updatedAt = ref<string | null>(null),
  updatedBy = ref<string | null>(null);
const loading = ref(false),
  saving = ref(false),
  uploadingIndex = ref(-1),
  dirty = ref(false),
  error = ref(''),
  previewOpen = ref(false);
const categories = ref<ProductCategory[]>([]);
const activityOptions = ref<{value: ScmId; label: string}[]>([]);

function targetOf(value?: string | null): ScmId | undefined {
  if (value == null || value === '') return undefined;
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : value;
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    const data = (await mallHomeApi.popup()).data;
    version.value = data.version;
    enabled.value = data.enabled;
    displayRule.value = data.displayRule || 'DAILY';
    period.value = data.startAt && data.endAt ? [data.startAt, data.endAt] : undefined;
    items.value = (data.items || []).map((item) => {
      const linkType = item.linkType || 'NONE';
      return {
        imageFileKey: item.imageFileKey,
        imageUrl: item.imageUrl,
        title: item.title,
        linkType,
        productTargetId: linkType === 'PRODUCT' ? targetOf(item.linkTarget) : undefined,
        categoryTargetId: linkType === 'CATEGORY' ? targetOf(item.linkTarget) : undefined,
        activityTargetId: linkType === 'ACTIVITY' ? targetOf(item.linkTarget) : undefined,
        externalUrl: linkType === 'EXTERNAL_URL' ? item.linkTarget || '' : '',
      };
    });
    updatedAt.value = data.updatedAt ?? null;
    updatedBy.value = data.updatedBy ?? null;
    dirty.value = false;
  } catch {
    error.value = '弹窗配置加载失败';
  } finally {
    loading.value = false;
  }
  const [tree, activities] = await Promise.allSettled([productCategoryApi.tree(), promotionApi.activityQuery({pageNum: 1, pageSize: 50})]);
  categories.value = tree.status === 'fulfilled' ? tree.value.data : [];
  activityOptions.value =
    activities.status === 'fulfilled' ? activities.value.data.list.map((activity) => ({value: activity.id, label: activity.activityName})) : [];
}

function addItem(): void {
  if (items.value.length >= 5) return;
  items.value.push({imageFileKey: '', linkType: 'NONE', title: null, externalUrl: ''});
  dirty.value = true;
}

function removeItem(index: number): void {
  items.value.splice(index, 1);
  dirty.value = true;
}

function onLinkTypeChange(item: PopupItemDraft): void {
  item.productTargetId = undefined;
  item.categoryTargetId = undefined;
  item.activityTargetId = undefined;
  item.externalUrl = '';
  dirty.value = true;
}

function uploaderFor(item: PopupItemDraft): NonNullable<UploadProps['customRequest']> {
  return (options) => upload(options, item);
}

async function upload(options: UploadOptions, item: PopupItemDraft): Promise<void> {
  if (!(options.file instanceof File)) return;
  const invalid = imageFileError(options.file);
  if (invalid) {
    options.onError?.(new Error(invalid));
    void message.error(invalid);
    return;
  }
  uploadingIndex.value = items.value.indexOf(item);
  try {
    const formData = new FormData();
    formData.append('file', options.file);
    const response = await fileApi.uploadFile<{fileKey: string; fileUrl: string}>(formData, FILE_FOLDER_TYPE_ENUM.PUBLIC_IMAGE.value);
    item.imageFileKey = response.data.fileKey;
    item.imageUrl = response.data.fileUrl;
    options.onSuccess?.(response.data);
    dirty.value = true;
    void message.success('图片已上传');
  } catch (cause) {
    options.onError?.(cause as Error);
    void message.error(String((cause as Error)?.message || '图片上传失败'));
  } finally {
    uploadingIndex.value = -1;
  }
}

function linkTargetOf(item: PopupItemDraft): string | null {
  if (item.linkType === 'PRODUCT') return item.productTargetId == null ? null : String(item.productTargetId);
  if (item.linkType === 'CATEGORY') return item.categoryTargetId == null ? null : String(item.categoryTargetId);
  if (item.linkType === 'ACTIVITY') return item.activityTargetId == null ? null : String(item.activityTargetId);
  if (item.linkType === 'EXTERNAL_URL') return item.externalUrl?.trim() || null;
  return null;
}

async function save(): Promise<void> {
  if (enabled.value && !items.value.length) {
    void message.warning('启用弹窗前至少添加一张图片');
    return;
  }
  if (items.value.some((item) => !item.imageFileKey)) {
    void message.warning('有图片还没有上传');
    return;
  }
  if (items.value.some((item) => ['PRODUCT', 'CATEGORY', 'ACTIVITY'].includes(item.linkType) && !linkTargetOf(item))) {
    void message.warning('有弹窗图片缺少跳转目标');
    return;
  }
  if (items.value.some((item) => item.linkType === 'EXTERNAL_URL' && !/^https:\/\//i.test(linkTargetOf(item) || ''))) {
    void message.warning('外部网页地址必须以 https:// 开头');
    return;
  }
  if (period.value && new Date(period.value[1]).getTime() <= new Date(period.value[0]).getTime()) {
    void message.warning('结束时间必须晚于开始时间');
    return;
  }
  saving.value = true;
  try {
    const response = await mallHomeApi.savePopup({
      version: version.value,
      enabled: enabled.value,
      displayRule: displayRule.value,
      startAt: period.value?.[0] ?? null,
      endAt: period.value?.[1] ?? null,
      items: items.value.map((item) => ({
        imageFileKey: item.imageFileKey,
        linkType: item.linkType,
        linkTarget: linkTargetOf(item),
        title: item.title?.trim() || null,
      })),
    });
    version.value = response.data.version;
    dirty.value = false;
    void message.success('弹窗配置已保存');
  } catch (cause) {
    void message.error(String((cause as Error)?.message || '弹窗配置保存失败'));
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.popup-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.popup-heading h2 {
  margin: 0 0 6px;
  font-size: 18px;
}

.popup-versions,
.popup-note {
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}

.popup-note {
  margin-left: 12px;
}

.popup-alert {
  margin-bottom: 12px;
}

.popup-form {
  max-width: 900px;
}

.popup-section-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 4px 0 12px;
  color: var(--scm-ink-soft);
}

.popup-section-head span {
  font-size: 12px;
}

.popup-items {
  display: grid;
  gap: 12px;
  margin-bottom: 12px;
}

.popup-item {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  gap: 16px;
  padding: 14px;
  border: 1px solid var(--scm-line);
  border-radius: 10px;
  background: var(--scm-surface, #fff);
}

.popup-item__image {
  display: grid;
  align-content: start;
  gap: 8px;
}

.popup-item__image img {
  width: 140px;
  height: 186px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid var(--scm-line);
}

.popup-item__placeholder {
  display: grid;
  place-items: center;
  width: 140px;
  height: 186px;
  border: 1px dashed var(--scm-line-strong);
  border-radius: 8px;
  color: var(--scm-text-secondary);
  font-size: 12px;
}

.popup-item__remove {
  justify-self: start;
  height: auto;
  padding: 0;
}

.popup-item__fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.popup-item__fields :deep(.ant-form-item) {
  margin-bottom: 10px;
}

.popup-preview__slide img {
  width: 100%;
  max-height: 520px;
  object-fit: contain;
  border-radius: 10px;
}

.popup-preview__placeholder {
  display: grid;
  place-items: center;
  height: 420px;
  border: 1px dashed var(--scm-line-strong);
  border-radius: 10px;
  color: var(--scm-text-secondary);
}

.popup-preview__hint {
  margin: 12px 0 0;
  color: var(--scm-text-secondary);
  font-size: 12px;
}

@media (max-width: 900px) {
  .popup-item {
    grid-template-columns: minmax(0, 1fr);
  }

  .popup-item__fields {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
