<template>
  <a-tabs v-model:activeKey="activeTab" class="mall-decoration-console">
    <a-tab-pane key="home" tab="首页设置">
      <div class="mall-home-editor">
        <a-card :bordered="false" size="small">
          <a-alert v-if="error" type="error" :message="error" show-icon class="mall-home-editor__alert">
            <template #action><a-button size="small" @click="load">重新加载</a-button></template>
          </a-alert>

          <div class="console-head">
            <div class="console-head__intro">
              <h2>商城首页</h2>
              <div class="console-head__state">
                <span class="console-state" :class="dirty ? 'is-dirty' : 'is-saved'">
                  <EditOutlined v-if="dirty" aria-hidden="true" />
                  <CheckCircleFilled v-else aria-hidden="true" />
                  {{ dirty ? '有未保存修改' : '草稿已保存' }}
                </span>
                <span class="console-head__version">草稿 v{{ draftVersion }}</span>
                <span class="console-head__version">{{ currentReleaseId ? `已发布 v${currentReleaseId}` : '尚未发布' }}</span>
              </div>
            </div>
            <a-space>
              <a-button v-privilege="'scm:mall:home:edit'" :loading="saving" :disabled="!dirty" @click="saveDraft">保存草稿</a-button>
              <a-button
                v-privilege="'scm:mall:home:publish'"
                type="primary"
                :loading="publishing"
                :disabled="dirty || !blocks.length"
                @click="publish"
                >发布草稿</a-button
              >
            </a-space>
          </div>

          <div class="console-theme">
            <span class="console-theme__label">店铺配色</span>
            <div class="console-theme__presets" role="group" aria-label="主题预设">
              <button
                v-for="preset in presetSwatches"
                :key="preset.code"
                type="button"
                class="console-swatch"
                :class="{'is-active': !themeColor && themeCode === preset.code}"
                :style="{background: preset.color}"
                :title="preset.label"
                :aria-label="`使用${preset.label}主题`"
                :aria-pressed="!themeColor && themeCode === preset.code"
                @click="selectPreset(preset.code)"
              />
              <span class="console-theme__name">{{ themeName }}</span>
            </div>
            <div class="console-theme__custom">
              <input type="color" class="console-color-input" :value="themeColorText" aria-label="自定义主题主色" @input="onColorInput" />
              <a-input
                v-model:value="themeColorText"
                style="width: 96px"
                aria-label="主题主色色值"
                @blur="applyHexColor"
                @press-enter="applyHexColor"
              />
            </div>
            <a-tag v-if="contrastWarning" color="orange" title="白字放在这个颜色上可能看不清，建议加深">主色偏浅</a-tag>
            <span v-else class="console-theme__contrast">白字对比度 {{ whiteContrastText }}</span>
          </div>

          <div class="console-body">
            <section class="console-stack-panel" aria-label="页面结构">
              <div class="console-panel-head">
                <div class="console-panel-head__title">
                  <strong>页面结构</strong>
                  <span>{{ blocks.length }} 个组件</span>
                </div>
                <div class="console-panel-head__add">
                  <a-select v-model:value="newType" :options="blockTypeOptions" style="width: 132px" aria-label="新组件类型" />
                  <a-button v-privilege="'scm:mall:home:edit'" :disabled="blocks.length >= 30" @click="addBlock">
                    <template #icon><PlusOutlined /></template>
                    添加组件
                  </a-button>
                </div>
              </div>
              <a-spin :spinning="loading">
                <div v-if="blocks.length" class="console-stack">
                  <article v-for="(record, index) in blocks" :key="record.key" class="console-block" :class="{'is-off': !record.enabled}">
                    <span class="console-block__order" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</span>
                    <span class="console-block__glyph" :style="{background: themePalette.light, color: themePalette.ink}">
                      <component :is="blockTypeIcons[record.type]" aria-hidden="true" />
                    </span>
                    <div class="console-block__main">
                      <div class="console-block__heading">
                        <strong class="console-block__name">{{ blockSummary(record) }}</strong>
                        <span class="console-status" :class="record.enabled ? 'is-on' : 'is-off'">{{ record.enabled ? '启用' : '停用' }}</span>
                      </div>
                      <div class="console-block__meta">
                        <span>{{ blockTypeLabel(record.type) }}</span>
                        <span>{{ audienceText(record) }}</span>
                        <span v-if="scheduleText(record)">{{ scheduleText(record) }}</span>
                      </div>
                    </div>
                    <div class="console-block__actions">
                      <a-button type="link" size="small" v-privilege="'scm:mall:home:edit'" @click="editBlock(record)">编辑</a-button>
                      <a-tooltip title="上移">
                        <a-button type="text" size="small" aria-label="上移" :disabled="index === 0" @click="move(index, -1)">
                          <ArrowUpOutlined />
                        </a-button>
                      </a-tooltip>
                      <a-tooltip title="下移">
                        <a-button type="text" size="small" aria-label="下移" :disabled="index === blocks.length - 1" @click="move(index, 1)">
                          <ArrowDownOutlined />
                        </a-button>
                      </a-tooltip>
                      <ScmActionMore v-if="hasPermission('scm:mall:home:edit')" :actions="rowActions()" @select="onRowAction($event, record)" />
                    </div>
                  </article>
                </div>
                <div v-else class="console-empty">
                  <InboxOutlined aria-hidden="true" />
                  <strong>还没有首页组件</strong>
                  <span>选择组件类型后点「添加组件」</span>
                </div>
              </a-spin>
            </section>

            <aside class="console-preview-panel" aria-label="实时预览">
              <div class="console-panel-head">
                <div class="console-panel-head__title"><strong>实时预览</strong></div>
                <a-button type="link" size="small" @click="previewDrawerOpen = true">按客户预览</a-button>
              </div>
              <div class="console-preview-stage">
                <MallPhonePreview ref="phonePreviewRef" :theme-code="themeCode" :theme-color="themeColor" :blocks="blocks" />
              </div>
              <a-collapse ghost class="console-history">
                <a-collapse-panel key="history" :header="`发布记录 ${releases.length}`">
                  <a-list :data-source="releases" :loading="loading" size="small" :locale="{emptyText: '尚无发布记录'}">
                    <template #renderItem="{item}">
                      <a-list-item>
                        <template #actions>
                          <a-button
                            v-if="String(item.id) !== String(currentReleaseId)"
                            type="link"
                            size="small"
                            :disabled="dirty"
                            v-privilege="'scm:mall:home:publish'"
                            @click="openRollback(item)"
                            >回滚</a-button
                          >
                        </template>
                        <a-list-item-meta
                          :title="`版本 ${item.id} · ${item.publishKind === 'ROLLBACK' ? '回滚发布' : '正式发布'}`"
                          :description="`${item.publishedAt} · ${item.publishedBy}`"
                        />
                      </a-list-item>
                    </template>
                  </a-list>
                </a-collapse-panel>
              </a-collapse>
            </aside>
          </div>
        </a-card>

        <a-drawer v-model:open="previewDrawerOpen" title="按客户预览" :width="scmDrawerWidth('s')">
          <div class="console-customer-preview">
            <div class="console-customer-preview__controls">
              <div class="console-field">
                <span>预览客户</span>
                <CustomerSelect v-model:value="previewCustomerId" width="200px" />
              </div>
              <div class="console-field">
                <span>配送仓</span>
                <WarehouseSelect v-model:value="previewWarehouseId" width="180px" />
              </div>
              <a-button :loading="previewLoading" :disabled="dirty" v-privilege="'scm:mall:home:query'" @click="preview">预览</a-button>
            </div>
            <p v-if="dirty" class="console-customer-preview__hint">先保存草稿，再按客户预览。</p>
            <a-alert v-if="previewError" type="error" show-icon :message="previewError" />
            <div v-if="previewPage" class="console-customer-preview__blocks">
              <div v-for="block in previewPage.blocks" :key="block.key" class="console-customer-preview__block">
                <img v-if="block.imageUrl" :src="block.imageUrl" :alt="block.title || '首页图片'" />
                <strong v-if="block.title">{{ block.title }}</strong>
                <p v-if="block.subtitle">{{ block.subtitle }}</p>
                <p v-if="block.text">{{ block.text }}</p>
                <div v-if="block.products?.length" class="console-customer-preview__products">
                  <span v-for="product in block.products" :key="String(product.skuId)">{{ product.productName }} · {{ product.specName }}</span>
                </div>
              </div>
              <a-empty v-if="!previewPage.blocks.length" description="该客户当前没有可展示的装修内容" />
            </div>
            <a-empty v-else class="console-customer-preview__empty" description="选择客户与配送仓后预览" />
          </div>
        </a-drawer>

        <a-drawer :open="editorOpen" title="编辑首页组件" :width="scmDrawerWidth('s')" @close="editorOpen = false">
          <a-form v-if="editing" layout="vertical">
            <a-form-item label="组件类型"><a-input :value="blockTypeLabel(editing.type)" disabled /></a-form-item>
            <a-form-item v-if="editing.type !== 'ANNOUNCEMENT'" label="标题">
              <a-input v-model:value="editing.title" :maxlength="80" />
            </a-form-item>
            <a-form-item v-if="['BANNER', 'ACTIVITY_TOPIC'].includes(editing.type)" label="副标题">
              <a-input v-model:value="editing.subtitle" :maxlength="160" />
            </a-form-item>
            <a-form-item v-if="editing.type === 'ANNOUNCEMENT'" label="公告内容" required>
              <a-textarea v-model:value="editing.text" :maxlength="600" :rows="4" />
            </a-form-item>
            <a-form-item v-if="['BANNER', 'SHORTCUT', 'ACTIVITY_TOPIC'].includes(editing.type)" label="展示图片">
              <a-upload :accept="IMAGE_ACCEPT" :show-upload-list="false" :custom-request="uploadImage">
                <a-button :loading="uploading">上传公开图片</a-button>
              </a-upload>
            </a-form-item>
            <a-form-item v-if="editing.type === 'RECOMMENDATION'" label="推荐商品规格" required>
              <SkuSelect v-model:value="recommendationSkuIds" mode="multiple" :limit="100" />
            </a-form-item>
            <a-form-item v-if="editing.type !== 'RECOMMENDATION'" label="点击跳转">
              <a-select v-model:value="editing.linkType" :options="linkOptions" />
            </a-form-item>
            <a-form-item v-if="editing.linkType === 'PRODUCT'" label="目标商品规格"><SkuSelect v-model:value="productTargetId" /></a-form-item>
            <a-form-item v-if="editing.linkType === 'CATEGORY'" label="目标分类">
              <CategorySelect v-model:value="categoryTargetId" :categories="categories" />
            </a-form-item>
            <a-form-item v-if="editing.linkType === 'ACTIVITY'" label="目标活动">
              <a-select v-model:value="activityTargetId" show-search option-filter-prop="label" :options="activityOptions" />
            </a-form-item>
            <a-form-item v-if="editing.linkType === 'EXTERNAL_URL'" label="外部网页地址">
              <a-input v-model:value="editing.linkTarget" placeholder="仅限已授权的 HTTPS 域名" />
            </a-form-item>
            <a-form-item label="展示客户">
              <a-select
                v-model:value="editing.customerIds"
                mode="multiple"
                show-search
                option-filter-prop="label"
                :options="customerOptions"
                placeholder="留空表示全部客户"
              />
            </a-form-item>
            <a-form-item label="展示时间"
              ><a-range-picker v-model:value="period" show-time value-format="YYYY-MM-DD HH:mm:ss" :allow-empty="[true, true]" style="width: 100%"
            /></a-form-item>
            <a-form-item label="启用"><a-switch v-model:checked="editing.enabled" /></a-form-item>
          </a-form>
          <template #footer>
            <a-space><a-button @click="editorOpen = false">取消</a-button><a-button type="primary" @click="applyEdit">加入草稿</a-button></a-space>
          </template>
        </a-drawer>

        <a-modal :open="rollbackOpen" title="回滚首页发布版本" :confirm-loading="publishing" @ok="rollback" @cancel="rollbackOpen = false">
          <p>将目标版本复制为新的发布记录，当前版本仍保留在历史中。</p>
          <a-form-item label="回滚原因" required><a-textarea v-model:value="rollbackReason" :maxlength="500" :rows="3" /></a-form-item>
        </a-modal>
      </div>
    </a-tab-pane>
    <a-tab-pane key="popup" tab="首页弹窗"><MallPopupSettings /></a-tab-pane>
    <a-tab-pane key="announcement" tab="商城公告"><MallPageSettings page-code="ANNOUNCEMENT" /></a-tab-pane>
    <a-tab-pane key="terms" tab="服务条款"><MallPageSettings page-code="TERMS" /></a-tab-pane>
    <a-tab-pane key="about" tab="关于我们"><MallPageSettings page-code="ABOUT" /></a-tab-pane>
    <a-tab-pane key="after-sale" tab="售后规则"><MallPageSettings page-code="AFTER_SALE" /></a-tab-pane>
    <a-tab-pane key="store" tab="店铺"><MallStoreSettings /></a-tab-pane>
  </a-tabs>
</template>
<script setup lang="ts">
import {computed, onMounted, ref, watch, type Component} from 'vue';
import {message, Modal, type UploadProps} from 'ant-design-vue';
import {
  AppstoreOutlined,
  ArrowDownOutlined,
  ArrowUpOutlined,
  CheckCircleFilled,
  EditOutlined,
  FlagOutlined,
  InboxOutlined,
  PictureOutlined,
  PlusOutlined,
  ShoppingOutlined,
  SoundOutlined,
} from '@ant-design/icons-vue';
import {fileApi} from '/@/api/support/file-api';
import {FILE_FOLDER_TYPE_ENUM} from '/@/constants/support/file-const';
import {IMAGE_ACCEPT, imageFileError} from '/@/constants/business/scm/image-const';
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
import MallPopupSettings from './components/mall-popup-settings.vue';
import MallPageSettings from './components/mall-page-settings.vue';
import MallStoreSettings from './components/mall-store-settings.vue';
import MallPhonePreview from './components/mall-phone-preview.vue';
import {hasPermission} from '../common/scm-permission';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import {
  MALL_THEME_PRESETS,
  MIN_WHITE_CONTRAST,
  isValidMallThemeColor,
  mallThemeBaseColor,
  mallThemePalette,
  normalizeMallThemeColor,
  whiteContrast,
} from '/@/utils/mall-theme';

const blockTypeOptions: {value: HomeBlockType; label: string}[] = [
  {value: 'BANNER', label: '轮播图'},
  {value: 'SHORTCUT', label: '快捷入口'},
  {value: 'RECOMMENDATION', label: '推荐商品'},
  {value: 'ACTIVITY_TOPIC', label: '活动专题'},
  {value: 'ANNOUNCEMENT', label: '公告'},
];
/** 组件类型图标：工作区里用同一套线性图标区分组件，不靠颜色单独表意。 */
const blockTypeIcons: Record<HomeBlockType, Component> = {
  BANNER: PictureOutlined,
  SHORTCUT: AppstoreOutlined,
  RECOMMENDATION: ShoppingOutlined,
  ACTIVITY_TOPIC: FlagOutlined,
  ANNOUNCEMENT: SoundOutlined,
};
const linkOptions: {value: HomeLinkType; label: string}[] = [
  {value: 'NONE', label: '不跳转'},
  {value: 'PRODUCT', label: '商品详情'},
  {value: 'CATEGORY', label: '商品分类'},
  {value: 'ACTIVITY', label: '活动列表'},
  {value: 'COUPONS', label: '领券中心'},
  {value: 'SEARCH', label: '商品搜索'},
  {value: 'EXTERNAL_URL', label: '允许的外部网页'},
];
const newType = ref<HomeBlockType>('BANNER');
const blocks = ref<HomeBlock[]>([]),
  releases = ref<HomeRelease[]>([]);
const draftVersion = ref(0),
  pointerVersion = ref(0),
  currentReleaseId = ref<ScmId | null>(null);
/** 主题只换配色，不换版式；跟着发布版本走，回滚时一起回去。 */
const themeOptions = [
  {value: 'FRESH', label: '生鲜绿'},
  {value: 'WARM', label: '暖橙'},
  {value: 'OCEAN', label: '海蓝'},
];
const themeCode = ref('FRESH');
/** 自定义主色；为空表示跟随主题预设。 */
const themeColor = ref<string | null>(null);
const themeColorText = ref(MALL_THEME_PRESETS.FRESH);
const presetSwatches = [
  {code: 'FRESH', label: '生鲜绿', color: MALL_THEME_PRESETS.FRESH},
  {code: 'WARM', label: '暖橙', color: MALL_THEME_PRESETS.WARM},
  {code: 'OCEAN', label: '海蓝', color: MALL_THEME_PRESETS.OCEAN},
];
const effectiveThemeColor = computed(() => mallThemeBaseColor(themeCode.value, themeColor.value));
const themeContrast = computed(() => whiteContrast(effectiveThemeColor.value));
const contrastWarning = computed(() => themeContrast.value < MIN_WHITE_CONTRAST);
const whiteContrastText = computed(() => `${themeContrast.value.toFixed(1)}:1`);
const themeName = computed(() => (themeColor.value ? '自定义' : themeOptions.find((option) => option.value === themeCode.value)?.label || '生鲜绿'));
/** 组件图标底色跟随店铺配色，工作区和手机预览保持同一套颜色。 */
const themePalette = computed(() => mallThemePalette(effectiveThemeColor.value));
const activeTab = ref('home');
const phonePreviewRef = ref<InstanceType<typeof MallPhonePreview>>();
// 切回首页时刷新弹窗预览：弹窗在另一个标签页保存，手机预览不应停留在旧版本
watch(activeTab, (tab) => {
  if (tab === 'home') phonePreviewRef.value?.refreshPopup();
});
const loading = ref(false),
  saving = ref(false),
  publishing = ref(false),
  dirty = ref(false),
  error = ref('');
const editorOpen = ref(false),
  editing = ref<HomeBlock | null>(null);
const recommendationSkuIds = ref<ScmId | ScmId[]>(),
  productTargetId = ref<ScmId | ScmId[]>();
const categoryTargetId = ref<ScmId>(),
  activityTargetId = ref<ScmId>();
const period = ref<[string, string]>();
const categories = ref<ProductCategory[]>([]);
const customerOptions = ref<{value: ScmId; label: string}[]>([]);
const activityOptions = ref<{value: ScmId; label: string}[]>([]);
const uploading = ref(false);
const previewDrawerOpen = ref(false);
const previewCustomerId = ref<ScmId | ScmId[]>(),
  previewWarehouseId = ref<ScmId>();
const previewLoading = ref(false),
  previewPage = ref<HomePage>(),
  previewError = ref('');
const rollbackOpen = ref(false),
  rollbackTarget = ref<HomeRelease>(),
  rollbackReason = ref('');
let requestId = 0;

function blockTypeLabel(type: HomeBlockType): string {
  return blockTypeOptions.find((option) => option.value === type)?.label || type;
}

function blockSummary(block: HomeBlock): string {
  if (block.title?.trim()) return block.title.trim();
  if (block.type === 'ANNOUNCEMENT') return block.text?.trim() || '未填写公告内容';
  if (block.type === 'RECOMMENDATION') return `推荐商品 ${block.skuIds?.length || 0} 个规格`;
  // 轮播图标题是可选的；没有标题时这是正常状态，不提示必填
  if (block.type === 'BANNER') return block.subtitle?.trim() || '图片轮播';
  return '未填写标题';
}

function audienceText(block: HomeBlock): string {
  return block.customerIds?.length ? `指定 ${block.customerIds.length} 位客户` : '全部客户';
}

/** 时间范围只显示日期，工作区不需要秒级信息。 */
function scheduleText(block: HomeBlock): string {
  const start = block.startAt ? block.startAt.slice(0, 10) : '';
  const end = block.endAt ? block.endAt.slice(0, 10) : '';
  if (start && end) return `${start} 至 ${end}`;
  if (start) return `${start} 起`;
  if (end) return `至 ${end}`;
  return '';
}

/** 主色输入框与色板共用同一个值；主题预设只是主色的快捷方式。 */
function syncColorText(): void {
  themeColorText.value = effectiveThemeColor.value;
}

function applyThemePreset(): void {
  themeColor.value = null;
  syncColorText();
  dirty.value = true;
}

function selectPreset(code: string): void {
  themeCode.value = code;
  applyThemePreset();
}

function onColorInput(event: Event): void {
  const value = normalizeMallThemeColor((event.target as HTMLInputElement).value);
  if (!isValidMallThemeColor(value)) return;
  themeColor.value = value;
  themeColorText.value = value as string;
  dirty.value = true;
}

function applyHexColor(): void {
  const raw = themeColorText.value.trim();
  if (!raw) {
    applyThemePreset();
    return;
  }
  const value = normalizeMallThemeColor(raw.startsWith('#') ? raw : `#${raw}`);
  if (!isValidMallThemeColor(value)) {
    void message.warning('请输入 #RRGGBB 格式的主色');
    syncColorText();
    return;
  }
  themeColor.value = value;
  themeColorText.value = value as string;
  dirty.value = true;
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
    themeColor.value = draft.data.themeColor || null;
    syncColorText();
    blocks.value = draft.data.blocks || [];
    releases.value = history.data || [];
    dirty.value = false;
    const [customers, categoryTree, activities] = await Promise.allSettled([
      customerApi.optionList(),
      productCategoryApi.tree(),
      promotionApi.activityQuery({pageNum: 1, pageSize: 100}),
    ]);
    if (request !== requestId) return;
    customerOptions.value =
      customers.status === 'fulfilled' ? customers.value.data.map((customer) => ({value: customer.customerId, label: customer.name})) : [];
    categories.value = categoryTree.status === 'fulfilled' ? categoryTree.value.data : [];
    activityOptions.value =
      activities.status === 'fulfilled' ? activities.value.data.list.map((activity) => ({value: activity.id, label: activity.activityName})) : [];
  } catch (cause) {
    if (request === requestId) error.value = String((cause as Error)?.message || '首页配置加载失败');
  } finally {
    if (request === requestId) loading.value = false;
  }
}

function normalize(): void {
  blocks.value.forEach((block, index) => {
    block.sortOrder = index;
  });
}
function addBlock(): void {
  const block: HomeBlock = {
    key: crypto.randomUUID(),
    schemaVersion: 1,
    type: newType.value,
    sortOrder: blocks.value.length,
    enabled: true,
    linkType: newType.value === 'ACTIVITY_TOPIC' ? 'ACTIVITY' : 'NONE',
    skuIds: [],
    customerIds: [],
  };
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
  if (
    (block.type === 'BANNER' && !block.imageFileKey) ||
    (block.type === 'RECOMMENDATION' && !block.skuIds?.length) ||
    (block.type === 'RECOMMENDATION' && (block.skuIds?.length || 0) > 20) ||
    (['SHORTCUT', 'RECOMMENDATION', 'ACTIVITY_TOPIC'].includes(block.type) && !block.title?.trim()) ||
    (block.type === 'SHORTCUT' && (!block.linkType || block.linkType === 'NONE')) ||
    (block.type === 'ACTIVITY_TOPIC' && !block.linkTarget) ||
    (block.type === 'ANNOUNCEMENT' && !block.text?.trim())
  ) {
    void message.warning('请补全当前组件的必填内容');
    return;
  }
  const index = blocks.value.findIndex((item) => item.key === block.key);
  if (index < 0) blocks.value.push(block);
  else blocks.value.splice(index, 1, block);
  normalize();
  dirty.value = true;
  editorOpen.value = false;
}
function move(index: number, step: number): void {
  const target = index + step;
  if (target < 0 || target >= blocks.value.length) return;
  [blocks.value[index], blocks.value[target]] = [blocks.value[target], blocks.value[index]];
  normalize();
  dirty.value = true;
}
function removeBlock(key: string): void {
  blocks.value = blocks.value.filter((block) => block.key !== key);
  normalize();
  dirty.value = true;
}
function rowActions() {
  return [{key: 'remove', label: '移除', danger: true}];
}
function onRowAction(action: string, block: HomeBlock): void {
  if (action === 'remove') Modal.confirm({title: '从草稿移除此组件？', onOk: () => removeBlock(block.key)});
}

const uploadImage: UploadProps['customRequest'] = async (options) => {
  if (!editing.value || !(options.file instanceof File)) return;
  const invalid = imageFileError(options.file);
  if (invalid) {
    options.onError?.(new Error(invalid));
    void message.error(invalid);
    return;
  }
  const editingKey = editing.value.key;
  uploading.value = true;
  try {
    const data = new FormData();
    data.append('file', options.file);
    const response = await fileApi.uploadFile<{fileKey: string}>(data, FILE_FOLDER_TYPE_ENUM.PUBLIC_IMAGE.value);
    if (editing.value?.key === editingKey) editing.value.imageFileKey = response.data.fileKey;
    options.onSuccess?.(response.data);
    void message.success('图片已上传');
  } catch (cause) {
    options.onError?.(cause as Error);
    void message.error(String((cause as Error)?.message || '图片上传失败'));
  } finally {
    uploading.value = false;
  }
};

async function saveDraft(): Promise<void> {
  if (contrastWarning.value) {
    void message.warning('主色偏浅，白字可能看不清，请选择更深的颜色');
    return;
  }
  saving.value = true;
  try {
    const response = await mallHomeApi.save(draftVersion.value, themeCode.value, themeColor.value, blocks.value);
    draftVersion.value = response.data.version;
    dirty.value = false;
    void message.success('草稿已保存');
  } catch (cause) {
    void message.error(String((cause as Error)?.message || '草稿保存失败'));
  } finally {
    saving.value = false;
  }
}
function publish(): void {
  Modal.confirm({
    title: '发布商城首页？',
    onOk: async () => {
      publishing.value = true;
      try {
        await mallHomeApi.publish(draftVersion.value, pointerVersion.value);
        await load();
        void message.success('首页已发布');
      } catch (cause) {
        void message.error(String((cause as Error)?.message || '首页发布失败'));
        throw cause;
      } finally {
        publishing.value = false;
      }
    },
  });
}
async function preview(): Promise<void> {
  if (previewCustomerId.value == null || Array.isArray(previewCustomerId.value) || !previewWarehouseId.value) {
    void message.warning('请选择预览客户与配送仓');
    return;
  }
  previewLoading.value = true;
  previewError.value = '';
  try {
    previewPage.value = (await mallHomeApi.preview(previewCustomerId.value, previewWarehouseId.value)).data;
  } catch (cause) {
    previewError.value = String((cause as Error)?.message || '预览失败');
  } finally {
    previewLoading.value = false;
  }
}
function openRollback(release: HomeRelease): void {
  rollbackTarget.value = release;
  rollbackReason.value = '';
  rollbackOpen.value = true;
}
async function rollback(): Promise<void> {
  if (!rollbackTarget.value || !rollbackReason.value.trim()) {
    void message.warning('请填写回滚原因');
    return;
  }
  publishing.value = true;
  try {
    await mallHomeApi.rollback(rollbackTarget.value.id, pointerVersion.value, rollbackReason.value.trim());
    rollbackOpen.value = false;
    await load();
    void message.success('已发布回滚版本');
  } catch (cause) {
    void message.error(String((cause as Error)?.message || '回滚失败'));
  } finally {
    publishing.value = false;
  }
}
onMounted(load);
</script>
<style scoped>
.mall-home-editor {
  display: grid;
  gap: 0;
}
.mall-home-editor__alert {
  margin-bottom: 12px;
}

/* 工作区标题与草稿状态 */
.console-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px 24px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--scm-line, #e5e7eb);
}
.console-head__intro h2 {
  margin: 0;
  color: var(--scm-ink, #1f2329);
  font-size: 19px;
  font-weight: 600;
  letter-spacing: -0.01em;
}
.console-head__state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 14px;
  margin-top: 6px;
  font-size: 12px;
}
.console-state {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.console-state .anticon {
  font-size: 12px;
}
.console-state.is-saved {
  color: #15803d;
}
.console-state.is-dirty {
  color: #b45309;
}
.console-head__version {
  color: var(--scm-text-secondary, #6b7280);
  font-variant-numeric: tabular-nums;
}

/* 店铺配色：色板即预设，不再叠加一个重复的下拉框 */
.console-theme {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 16px;
  padding: 14px 0;
  border-bottom: 1px solid var(--scm-line, #e5e7eb);
}
.console-theme__label {
  color: var(--scm-text-secondary, #6b7280);
  font-size: 12px;
}
.console-theme__presets {
  display: flex;
  align-items: center;
  gap: 8px;
}
.console-swatch {
  width: 22px;
  height: 22px;
  padding: 0;
  border: 2px solid transparent;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.12);
  cursor: pointer;
}
.console-swatch.is-active {
  border-color: #fff;
  box-shadow: 0 0 0 2px var(--scm-ink, #1f2329);
}
.console-theme__name {
  margin-left: 2px;
  color: var(--scm-ink, #1f2329);
  font-size: 12px;
}
.console-theme__custom {
  display: flex;
  align-items: center;
  gap: 6px;
}
.console-color-input {
  width: 32px;
  height: 32px;
  padding: 2px;
  border: 1px solid var(--scm-line, #e5e7eb);
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
}
.console-theme__contrast {
  color: var(--scm-text-secondary, #6b7280);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

/* 左侧页面结构 + 右侧实时预览 */
.console-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 356px;
  gap: 28px;
  align-items: start;
  padding-top: 18px;
}
.console-stack-panel {
  min-width: 0;
}
.console-preview-panel {
  position: sticky;
  top: 16px;
  min-width: 0;
}
.console-panel-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 32px;
}
.console-panel-head__title {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.console-panel-head__title strong {
  color: var(--scm-ink, #1f2329);
  font-size: 15px;
  font-weight: 600;
}
.console-panel-head__title span {
  color: var(--scm-text-secondary, #6b7280);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.console-panel-head__add {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 组件按真实顺序排列，序号是结构信息而不是装饰 */
.console-stack {
  margin-top: 10px;
  border-top: 1px solid var(--scm-line, #e5e7eb);
}
.console-block {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 4px;
  border-bottom: 1px solid var(--scm-line, #e5e7eb);
}
.console-block.is-off {
  opacity: 0.62;
}
.console-block__order {
  flex: 0 0 24px;
  width: 24px;
  color: #9aa1ab;
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  text-align: right;
}
.console-block__glyph {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 9px;
  font-size: 17px;
}
.console-block__main {
  flex: 1;
  min-width: 0;
}
.console-block__heading {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.console-block__name {
  overflow: hidden;
  color: var(--scm-ink, #1f2329);
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.console-status {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  color: var(--scm-text-secondary, #6b7280);
  font-size: 11px;
}
.console-status::before {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentcolor;
  content: '';
}
.console-status.is-on {
  color: #15803d;
}
.console-block__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-top: 4px;
  color: var(--scm-text-secondary, #6b7280);
  font-size: 12px;
}
.console-block__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 2px;
}
.console-block__actions :deep(.ant-btn) {
  color: var(--scm-text-secondary, #6b7280);
}
.console-block__actions :deep(.ant-btn[disabled]) {
  color: #c9ced6;
}

.console-empty {
  display: grid;
  justify-items: center;
  gap: 6px;
  padding: 64px 16px;
  border-bottom: 1px solid var(--scm-line, #e5e7eb);
  color: var(--scm-text-secondary, #6b7280);
}
.console-empty .anticon {
  color: #c9ced6;
  font-size: 28px;
}
.console-empty strong {
  color: var(--scm-ink, #1f2329);
  font-size: 14px;
  font-weight: 500;
}
.console-empty span {
  font-size: 12px;
}

/* 预览台：手机周围留一圈中性底，不再套一层卡片 */
.console-preview-stage {
  display: grid;
  justify-items: center;
  margin-top: 10px;
  padding: 20px 0;
  border-radius: 12px;
  background: #f3f4f2;
}
.console-history {
  margin-top: 4px;
}
.console-history :deep(.ant-collapse-header) {
  padding: 10px 0 !important;
  color: var(--scm-text-secondary, #6b7280);
  font-size: 12px;
}
.console-history :deep(.ant-collapse-content-box) {
  padding: 0 0 8px !important;
}

/* 按客户预览抽屉 */
.console-customer-preview__controls {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 12px 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--scm-line, #e5e7eb);
}
.console-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--scm-text-secondary, #6b7280);
  font-size: 12px;
}
.console-customer-preview__hint {
  margin: 12px 0 0;
  color: #b45309;
  font-size: 12px;
}
.console-customer-preview__blocks {
  display: grid;
  gap: 12px;
  margin-top: 16px;
}
.console-customer-preview__block {
  padding: 14px;
  border: 1px solid var(--scm-line, #e5e7eb);
  border-radius: 10px;
}
.console-customer-preview__block img {
  display: block;
  width: 100%;
  max-height: 180px;
  margin-bottom: 10px;
  border-radius: 8px;
  object-fit: cover;
}
.console-customer-preview__block p {
  margin: 6px 0 0;
}
.console-customer-preview__products {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.console-customer-preview__products span {
  padding: 4px 8px;
  border-radius: 6px;
  background: var(--scm-fill, #fafafa);
}
.console-customer-preview__empty {
  margin-top: 32px;
}

.mall-decoration-console :deep(.ant-tabs-nav) {
  margin-bottom: 16px;
}

@media (max-width: 1000px) {
  .console-body {
    grid-template-columns: 1fr;
  }
  .console-preview-panel {
    position: static;
  }
}
</style>
