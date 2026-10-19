<template>
  <div class="mall-home-editor">
    <a-card :bordered="false" size="small">
      <div class="mall-home-editor__heading">
        <div>
          <h2>商城首页装修</h2>
          <p class="scm-note">按顺序配置轮播、入口、商品推荐、活动专题和公告。保存草稿后再预览与发布。</p>
        </div>
        <a-space wrap>
          <a-tag color="blue">草稿版本 {{ draftVersion }}</a-tag>
          <a-tag :color="currentReleaseId ? 'green' : 'default'">
            {{ currentReleaseId ? `已发布版本 ${currentReleaseId}` : '尚未发布' }}
          </a-tag>
        </a-space>
      </div>
      <a-alert v-if="error" type="error" :message="error" show-icon class="mall-home-editor__alert">
        <template #action><a-button size="small" @click="load">重新加载</a-button></template>
      </a-alert>
      <a-space wrap class="mall-home-editor__toolbar">
        <a-select v-model:value="themeCode" :options="themeOptions" style="width: 140px" @change="dirty = true"
                  aria-label="首页主题"/>
        <a-select v-model:value="newType" :options="blockTypeOptions" style="width: 170px"/>
        <a-button v-privilege="'scm:mall:home:edit'" :disabled="blocks.length >= 30" @click="addBlock">添加组件</a-button>
        <a-button v-privilege="'scm:mall:home:edit'" type="primary" :loading="saving" :disabled="!dirty" @click="saveDraft">保存草稿</a-button>
        <a-button v-privilege="'scm:mall:home:publish'" :loading="publishing" :disabled="dirty || !blocks.length" @click="publish">发布草稿</a-button>
      </a-space>
      <a-table :data-source="blocks" :columns="columns" row-key="key" size="small" bordered
               :loading="loading" :pagination="false" :scroll="{ x: scmColumnsWidth(columns) }"
               :locale="{emptyText: '草稿还没有组件。选择类型后点击“添加组件”。'}">
        <template #bodyCell="{record, column, index}">
          <template v-if="column.dataIndex === 'type'">{{ blockTypeLabel(record.type) }}</template>
          <template v-else-if="column.dataIndex === 'title'">{{ record.title || record.text || '—' }}</template>
          <template v-else-if="column.dataIndex === 'audience'">{{ record.customerIds?.length ? `指定 ${record.customerIds.length} 位客户` : '全部客户' }}</template>
          <template v-else-if="column.dataIndex === 'enabled'"><a-tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? '启用' : '停用' }}</a-tag></template>
          <template v-else-if="column.dataIndex === 'action'">
            <a-space size="small">
              <a-button type="link" v-privilege="'scm:mall:home:edit'" @click="editBlock(record)">编辑</a-button>
              <ScmActionMore v-if="hasPermission('scm:mall:home:edit')" :actions="rowActions(index)"
                             @select="onRowAction($event, record, index)"/>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-row :gutter="16" class="mall-home-editor__lower">
      <a-col :xs="24" :lg="14">
        <a-card title="客户内容预览" :bordered="false" size="small">
          <a-space wrap class="mall-home-editor__toolbar">
            <CustomerSelect v-model:value="previewCustomerId" width="200px"/>
            <WarehouseSelect v-model:value="previewWarehouseId" width="180px"/>
            <a-button :loading="previewLoading" :disabled="dirty" v-privilege="'scm:mall:home:query'" @click="preview">按已保存草稿预览</a-button>
          </a-space>
          <a-alert v-if="dirty" type="info" show-icon message="有未保存修改；预览使用最近一次保存的草稿。"/>
          <a-alert v-if="previewError" type="error" show-icon :message="previewError"/>
          <div v-if="previewPage" class="mall-home-editor__preview">
            <div v-for="block in previewPage.blocks" :key="block.key" class="mall-home-editor__preview-block">
              <img v-if="block.imageUrl" :src="block.imageUrl" :alt="block.title || '首页图片'"/>
              <strong v-if="block.title">{{ block.title }}</strong>
              <p v-if="block.subtitle">{{ block.subtitle }}</p>
              <p v-if="block.text">{{ block.text }}</p>
              <div v-if="block.products?.length" class="mall-home-editor__preview-products">
                <span v-for="product in block.products" :key="String(product.skuId)">{{ product.productName }} · {{ product.specName }}</span>
              </div>
            </div>
            <a-empty v-if="!previewPage.blocks.length" description="该客户当前没有可展示的装修内容"/>
          </div>
          <a-empty v-else description="选择客户与配送仓后预览"/>
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="10">
        <a-card title="发布记录" :bordered="false" size="small">
          <a-list :data-source="releases" :loading="loading" :locale="{emptyText: '尚无发布记录'}">
            <template #renderItem="{item}">
              <a-list-item>
                <template #actions>
                  <a-button v-if="String(item.id) !== String(currentReleaseId)" type="link"
                            :disabled="dirty" v-privilege="'scm:mall:home:publish'" @click="openRollback(item)">回滚到此版</a-button>
                </template>
                <a-list-item-meta :title="`版本 ${item.id} · ${item.publishKind === 'ROLLBACK' ? '回滚发布' : '正式发布'}`"
                                  :description="`${item.publishedAt} · ${item.publishedBy}`"/>
              </a-list-item>
            </template>
          </a-list>
        </a-card>
      </a-col>
    </a-row>

    <a-drawer :open="editorOpen" title="编辑首页组件" :width="scmDrawerWidth('s')" @close="editorOpen=false">
      <a-form v-if="editing" layout="vertical">
        <a-form-item label="组件类型"><a-input :value="blockTypeLabel(editing.type)" disabled/></a-form-item>
        <a-form-item v-if="editing.type !== 'ANNOUNCEMENT'" label="标题">
          <a-input v-model:value="editing.title" :maxlength="80"/>
        </a-form-item>
        <a-form-item v-if="['BANNER','ACTIVITY_TOPIC'].includes(editing.type)" label="副标题">
          <a-input v-model:value="editing.subtitle" :maxlength="160"/>
        </a-form-item>
        <a-form-item v-if="editing.type === 'ANNOUNCEMENT'" label="公告内容" required>
          <a-textarea v-model:value="editing.text" :maxlength="600" :rows="4"/>
        </a-form-item>
        <a-form-item v-if="['BANNER','SHORTCUT','ACTIVITY_TOPIC'].includes(editing.type)" label="展示图片">
          <a-upload accept="image/png,image/jpeg,image/webp,image/gif" :show-upload-list="false" :custom-request="uploadImage">
            <a-button :loading="uploading">上传公开图片</a-button>
          </a-upload>
          <span v-if="editing.imageFileKey" class="scm-note">已选图片，保存后可预览。</span>
        </a-form-item>
        <a-form-item v-if="editing.type === 'RECOMMENDATION'" label="推荐商品规格" required>
          <SkuSelect v-model:value="recommendationSkuIds" mode="multiple" :limit="100"/>
        </a-form-item>
        <a-form-item v-if="editing.type !== 'RECOMMENDATION'" label="点击跳转">
          <a-select v-model:value="editing.linkType" :options="linkOptions"/>
        </a-form-item>
        <a-form-item v-if="editing.linkType === 'PRODUCT'" label="目标商品规格"><SkuSelect v-model:value="productTargetId"/></a-form-item>
        <a-form-item v-if="editing.linkType === 'CATEGORY'" label="目标分类">
          <CategorySelect v-model:value="categoryTargetId" :categories="categories"/>
        </a-form-item>
        <a-form-item v-if="editing.linkType === 'ACTIVITY'" label="目标活动">
          <a-select v-model:value="activityTargetId" show-search option-filter-prop="label" :options="activityOptions"/>
        </a-form-item>
        <a-form-item v-if="editing.linkType === 'EXTERNAL_URL'" label="外部网页地址">
          <a-input v-model:value="editing.linkTarget" placeholder="仅限已授权的 HTTPS 域名"/>
        </a-form-item>
        <a-form-item label="展示客户">
          <a-select v-model:value="editing.customerIds" mode="multiple" show-search option-filter-prop="label"
                    :options="customerOptions" placeholder="留空表示全部客户"/>
        </a-form-item>
        <a-form-item label="展示时间"><a-range-picker v-model:value="period" show-time value-format="YYYY-MM-DD HH:mm:ss"
                                           :allow-empty="[true, true]" style="width:100%"/></a-form-item>
        <a-form-item label="启用"><a-switch v-model:checked="editing.enabled"/></a-form-item>
      </a-form>
      <template #footer>
        <a-space><a-button @click="editorOpen=false">取消</a-button><a-button type="primary" @click="applyEdit">加入草稿</a-button></a-space>
      </template>
    </a-drawer>

    <a-modal :open="rollbackOpen" title="回滚首页发布版本" :confirm-loading="publishing"
             @ok="rollback" @cancel="rollbackOpen=false">
      <p>将目标版本复制为新的发布记录，当前版本仍保留在历史中。</p>
      <a-form-item label="回滚原因" required><a-textarea v-model:value="rollbackReason" :maxlength="500" :rows="3"/></a-form-item>
    </a-modal>
  </div>
</template>
<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {onMounted, ref} from 'vue';
import {message, Modal, type TableColumnsType, type UploadProps} from 'ant-design-vue';
import {fileApi} from '/@/api/support/file-api';
import {FILE_FOLDER_TYPE_ENUM} from '/@/constants/support/file-const';
import {customerApi} from '/@/api/business/scm/customer-api';
import {productCategoryApi} from '/@/api/business/scm/product-category-api';
import {promotionApi} from '/@/api/business/scm/promotion-api';
import type {ProductCategory} from '/@/types/business/scm/product';
import type {ScmId} from '/@/types/business/scm/customer';
import {mallHomeApi, type HomeBlock, type HomeBlockType, type HomeLinkType, type HomePage, type HomeRelease} from '/@/api/business/scm/mall-home-api';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import CategorySelect from '/@/components/business/scm/product-category-tree-select/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import {hasPermission} from '../common/scm-permission';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';

const blockTypeOptions: {value: HomeBlockType; label: string}[] = [
  {value: 'BANNER', label: '轮播图'}, {value: 'SHORTCUT', label: '快捷入口'},
  {value: 'RECOMMENDATION', label: '推荐商品'}, {value: 'ACTIVITY_TOPIC', label: '活动专题'},
  {value: 'ANNOUNCEMENT', label: '公告'}
];
const linkOptions: {value: HomeLinkType; label: string}[] = [
  {value: 'NONE', label: '不跳转'}, {value: 'PRODUCT', label: '商品详情'},
  {value: 'CATEGORY', label: '商品分类'}, {value: 'ACTIVITY', label: '活动列表'},
  {value: 'COUPONS', label: '领券中心'}, {value: 'SEARCH', label: '商品搜索'},
  {value: 'EXTERNAL_URL', label: '允许的外部网页'}
];
const columns: TableColumnsType<HomeBlock> = [
  {title: '顺序', dataIndex: 'sortOrder', width: 70},
  {title: '组件', dataIndex: 'type', align: 'center', width: 120},
  {title: '内容', dataIndex: 'title', width: 230},
  {title: '展示客户', dataIndex: 'audience', width: 145},
  {title: '状态', dataIndex: 'enabled', width: 90},
  {title: '操作', dataIndex: 'action', align: 'center', width: 150}
];
const newType = ref<HomeBlockType>('BANNER');
const blocks = ref<HomeBlock[]>([]), releases = ref<HomeRelease[]>([]);
const draftVersion = ref(0), pointerVersion = ref(0), currentReleaseId = ref<ScmId | null>(null);
/** 主题只换配色，不换版式；跟着发布版本走，回滚时一起回去。 */
const themeOptions = [
  {value: 'FRESH', label: '生鲜绿'},
  {value: 'WARM', label: '暖橙'},
  {value: 'OCEAN', label: '海蓝'},
];
const themeCode = ref('FRESH');
const loading = ref(false), saving = ref(false), publishing = ref(false), dirty = ref(false), error = ref('');
const editorOpen = ref(false), editing = ref<HomeBlock | null>(null);
const recommendationSkuIds = ref<ScmId | ScmId[]>(), productTargetId = ref<ScmId | ScmId[]>();
const categoryTargetId = ref<ScmId>(), activityTargetId = ref<ScmId>();
const period = ref<[string, string]>();
const categories = ref<ProductCategory[]>([]);
const customerOptions = ref<{value: ScmId; label: string}[]>([]);
const activityOptions = ref<{value: ScmId; label: string}[]>([]);
const uploading = ref(false);
const previewCustomerId = ref<ScmId | ScmId[]>(), previewWarehouseId = ref<ScmId>();
const previewLoading = ref(false), previewPage = ref<HomePage>(), previewError = ref('');
const rollbackOpen = ref(false), rollbackTarget = ref<HomeRelease>(), rollbackReason = ref('');
let requestId = 0;

function blockTypeLabel(type: HomeBlockType): string {
  return blockTypeOptions.find(option => option.value === type)?.label || type;
}

async function load(): Promise<void> {
  const request = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const [draft, history] = await Promise.all([mallHomeApi.draft(), mallHomeApi.history()]);
    if (request !== requestId) return;
    draftVersion.value = draft.data.version;
    pointerVersion.value = draft.data.pointerVersion;
    currentReleaseId.value = draft.data.currentReleaseId;
    themeCode.value = draft.data.themeCode || 'FRESH';
    blocks.value = draft.data.blocks || [];
    releases.value = history.data || [];
    dirty.value = false;
    const [customers, categoryTree, activities] = await Promise.allSettled([
      customerApi.optionList(), productCategoryApi.tree(),
      promotionApi.activityQuery({pageNum: 1, pageSize: 100})
    ]);
    if (request !== requestId) return;
    customerOptions.value = customers.status === 'fulfilled' ? customers.value.data.map(customer =>
      ({value: customer.customerId, label: customer.name})) : [];
    categories.value = categoryTree.status === 'fulfilled' ? categoryTree.value.data : [];
    activityOptions.value = activities.status === 'fulfilled' ? activities.value.data.list.map(activity =>
      ({value: activity.id, label: activity.activityName})) : [];
  } catch (cause) {
    if (request === requestId) error.value = String((cause as Error)?.message || '首页配置加载失败');
  } finally {
    if (request === requestId) loading.value = false;
  }
}

function normalize(): void { blocks.value.forEach((block, index) => { block.sortOrder = index; }); }
function addBlock(): void {
  const block: HomeBlock = {key: crypto.randomUUID(), schemaVersion: 1, type: newType.value,
    sortOrder: blocks.value.length, enabled: true, linkType: newType.value === 'ACTIVITY_TOPIC' ? 'ACTIVITY' : 'NONE',
    skuIds: [], customerIds: []};
  editBlock(block);
}
function editBlock(block: HomeBlock): void {
  editing.value = {...block, skuIds: [...(block.skuIds || [])], customerIds: [...(block.customerIds || [])]};
  recommendationSkuIds.value = editing.value.skuIds || [];
  productTargetId.value = editing.value.linkType === 'PRODUCT' ? editing.value.linkTarget || undefined : undefined;
  categoryTargetId.value = editing.value.linkType === 'CATEGORY' ? editing.value.linkTarget || undefined : undefined;
  activityTargetId.value = editing.value.linkType === 'ACTIVITY' ? editing.value.linkTarget || undefined : undefined;
  period.value = [editing.value.startAt || '', editing.value.endAt || ''];
  editorOpen.value = true;
}
function applyEdit(): void {
  if (!editing.value) return;
  const block = editing.value;
  if (block.type === 'RECOMMENDATION') {
    block.skuIds = Array.isArray(recommendationSkuIds.value) ? recommendationSkuIds.value : [];
    block.linkType = 'NONE';
    block.linkTarget = null;
  } else if (block.linkType === 'PRODUCT') block.linkTarget = productTargetId.value == null ? null : String(productTargetId.value);
  else if (block.linkType === 'CATEGORY') block.linkTarget = categoryTargetId.value == null ? null : String(categoryTargetId.value);
  else if (block.linkType === 'ACTIVITY') block.linkTarget = activityTargetId.value == null ? null : String(activityTargetId.value);
  else if (block.linkType !== 'EXTERNAL_URL') block.linkTarget = null;
  block.startAt = period.value?.[0] || null;
  block.endAt = period.value?.[1] || null;
  if ((block.type === 'BANNER' && !block.imageFileKey) ||
      (block.type === 'RECOMMENDATION' && !block.skuIds?.length) ||
      (block.type === 'RECOMMENDATION' && (block.skuIds?.length || 0) > 20) ||
      (['SHORTCUT', 'RECOMMENDATION', 'ACTIVITY_TOPIC'].includes(block.type) && !block.title?.trim()) ||
      (block.type === 'SHORTCUT' && (!block.linkType || block.linkType === 'NONE')) ||
      (block.type === 'ACTIVITY_TOPIC' && !block.linkTarget) ||
      (block.type === 'ANNOUNCEMENT' && !block.text?.trim())) {
    void message.warning('请补全当前组件的必填内容');
    return;
  }
  const index = blocks.value.findIndex(item => item.key === block.key);
  if (index < 0) blocks.value.push(block); else blocks.value.splice(index, 1, block);
  normalize();
  dirty.value = true;
  editorOpen.value = false;
}
function move(index: number, step: number): void {
  const target = index + step;
  if (target < 0 || target >= blocks.value.length) return;
  [blocks.value[index], blocks.value[target]] = [blocks.value[target], blocks.value[index]];
  normalize(); dirty.value = true;
}
function removeBlock(key: string): void { blocks.value = blocks.value.filter(block => block.key !== key); normalize(); dirty.value = true; }
function rowActions(index: number) {
  return [
    {key: 'up', label: '上移', disabled: index === 0},
    {key: 'down', label: '下移', disabled: index === blocks.value.length - 1},
    {key: 'remove', label: '移除', danger: true}
  ];
}
function onRowAction(action: string, block: HomeBlock, index: number): void {
  if (action === 'up') move(index, -1);
  else if (action === 'down') move(index, 1);
  else if (action === 'remove') Modal.confirm({title: '从草稿移除此组件？', onOk: () => removeBlock(block.key)});
}

const uploadImage: UploadProps['customRequest'] = async options => {
  if (!editing.value || !(options.file instanceof File)) return;
  const editingKey = editing.value.key;
  uploading.value = true;
  try {
    const data = new FormData(); data.append('file', options.file);
    const response = await fileApi.uploadFile(data, FILE_FOLDER_TYPE_ENUM.PUBLIC_IMAGE.value) as unknown as
      {data: {fileKey: string}};
    if (editing.value?.key === editingKey) editing.value.imageFileKey = response.data.fileKey;
    options.onSuccess?.(response.data);
    void message.success('图片已上传');
  } catch (cause) {
    options.onError?.(cause as Error);
    void message.error(String((cause as Error)?.message || '图片上传失败'));
  } finally { uploading.value = false; }
};

async function saveDraft(): Promise<void> {
  saving.value = true;
  try {
    const response = await mallHomeApi.save(draftVersion.value, themeCode.value, blocks.value);
    draftVersion.value = response.data.version;
    dirty.value = false;
    void message.success('草稿已保存');
  } catch (cause) { void message.error(String((cause as Error)?.message || '草稿保存失败')); }
  finally { saving.value = false; }
}
function publish(): void {
  Modal.confirm({title: '发布商城首页？', content: '客户将在发布完成后看到此版本。',
    onOk: async () => {
      publishing.value = true;
      try { await mallHomeApi.publish(draftVersion.value, pointerVersion.value); await load(); void message.success('首页已发布'); }
      catch (cause) { void message.error(String((cause as Error)?.message || '首页发布失败')); throw cause; }
      finally { publishing.value = false; }
    }});
}
async function preview(): Promise<void> {
  if (previewCustomerId.value == null || Array.isArray(previewCustomerId.value) || !previewWarehouseId.value) {
    void message.warning('请选择预览客户与配送仓'); return;
  }
  previewLoading.value = true; previewError.value = '';
  try { previewPage.value = (await mallHomeApi.preview(previewCustomerId.value, previewWarehouseId.value)).data; }
  catch (cause) { previewError.value = String((cause as Error)?.message || '预览失败'); }
  finally { previewLoading.value = false; }
}
function openRollback(release: HomeRelease): void { rollbackTarget.value = release; rollbackReason.value = ''; rollbackOpen.value = true; }
async function rollback(): Promise<void> {
  if (!rollbackTarget.value || !rollbackReason.value.trim()) { void message.warning('请填写回滚原因'); return; }
  publishing.value = true;
  try { await mallHomeApi.rollback(rollbackTarget.value.id, pointerVersion.value, rollbackReason.value.trim());
    rollbackOpen.value = false; await load(); void message.success('已发布回滚版本'); }
  catch (cause) { void message.error(String((cause as Error)?.message || '回滚失败')); }
  finally { publishing.value = false; }
}
onMounted(load);
</script>
<style scoped>
.mall-home-editor {display: grid; gap: 16px}
.mall-home-editor__heading {display: flex; justify-content: space-between; align-items: flex-start; gap: 16px}
.mall-home-editor__heading h2 {margin: 0 0 6px; font-size: 18px}
.mall-home-editor__toolbar {margin: 16px 0}
.mall-home-editor__alert {margin-top: 12px}
.mall-home-editor__lower {margin: 0}
.mall-home-editor__preview {display: grid; gap: 12px; margin-top: 16px}
.mall-home-editor__preview-block {padding: 12px; border: 1px solid var(--scm-border, #f0f0f0); border-radius: 12px}
.mall-home-editor__preview-block img {display: block; width: min(100%, 460px); max-height: 170px; object-fit: cover; margin-bottom: 8px; border-radius: 8px}
.mall-home-editor__preview-block p {margin: 6px 0 0}
.mall-home-editor__preview-products {display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px}
.mall-home-editor__preview-products span {padding: 4px 8px; background: var(--scm-fill, #fafafa); border-radius: 6px}
@media (max-width: 720px) {.mall-home-editor__heading {display: block}}
</style>
