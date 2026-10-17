<template>
  <section aria-label="商品档案">
    <a-form class="smart-query-form" layout="inline">
      <a-row class="smart-query-form-row">
        <a-form-item label="分类" class="smart-query-form-item"><CategorySelect v-model:value="filters.categoryId" :categories="categories" style="width: 220px" /></a-form-item>
        <a-form-item label="关键字" class="smart-query-form-item"><a-input v-model:value="filters.keyword" allow-clear placeholder="商品名称 / 商品编码 / 条码 / 助记码" style="width: 260px" /></a-form-item>
        <a-form-item label="商品状态" class="smart-query-form-item"><a-select v-model:value="filters.status" allow-clear :options="SHELF_STATUS_ENUM" style="width: 110px" /></a-form-item>
        <a-form-item class="smart-query-form-item"><a-space><a-button type="primary" @click="search">查询</a-button><a-button @click="reset">重置</a-button></a-space><a-button class="smart-margin-left20" @click="toggleAdvanced"><template #icon><UpOutlined v-if="advanced" aria-hidden="true" /><DownOutlined v-else aria-hidden="true" /></template>{{ advanced ? '收起筛选' : '高级筛选' }}</a-button></a-form-item>
      </a-row>
      <a-row v-if="advanced" class="smart-query-form-row">
        <a-form-item label="商品规格状态" class="smart-query-form-item"><a-select v-model:value="filters.skuStatus" allow-clear :options="SHELF_STATUS_ENUM" style="width: 120px" /></a-form-item>
        <a-form-item label="商品类型" class="smart-query-form-item"><a-select v-model:value="filters.productType" allow-clear :options="PRODUCT_TYPE_ENUM" style="width: 120px" /></a-form-item>
        <a-form-item label="主档状态" class="smart-query-form-item"><a-select v-model:value="filters.masterStatus" allow-clear :options="MASTER_STATUS_ENUM" style="width: 120px" /></a-form-item>
        <a-form-item label="储存方式" class="smart-query-form-item"><a-select v-model:value="filters.storageMethod" allow-clear :options="STORAGE_METHOD_ENUM" style="width: 110px" /></a-form-item>
        <a-form-item label="标签" class="smart-query-form-item"><a-select v-model:value="filters.tagIds" mode="multiple" allow-clear :options="tagFilterOptions" option-filter-prop="label" placeholder="命中任一标签" style="width: 220px" /></a-form-item>
        <a-form-item label="主图" class="smart-query-form-item"><a-select v-model:value="filters.hasPrimaryImage" allow-clear :options="YES_NO_ENUM" style="width: 90px" /></a-form-item>
        <a-form-item label="条码" class="smart-query-form-item"><a-select v-model:value="filters.hasBarcode" allow-clear :options="YES_NO_ENUM" style="width: 90px" /></a-form-item>
        <a-form-item label="创建时间" class="smart-query-form-item"><a-range-picker v-model:value="createdRange" show-time value-format="YYYY-MM-DD HH:mm:ss" :allow-empty="[true, true]" /></a-form-item>
      </a-row>
      <ScmAdvancedFilterBuilder v-if="advanced" v-model="advancedFilters" :fields="advancedFilterFields"/>
      <ScmSavedFilterPresets v-if="advanced" view-key="PRODUCT"
                             :conditions="advancedFilters" @apply="applySavedFilters"/>
    </a-form>
    <a-card size="small" :bordered="false">
      <a-row class="smart-table-btn-block" justify="space-between" align="middle">
        <a-space>
          <a-button v-privilege="'scm:product:add'" type="primary" @click="drawer?.open()">新增商品</a-button>
          <a-button v-privilege="'scm:product:batch'" :disabled="!selectedRows.length"
                    @click="batch?.open('STATUS', selectedItems)">批量改状态
          </a-button>
          <a-button v-privilege="'scm:product:batch'" :disabled="!selectedRows.length"
                    @click="batch?.open('CATEGORY', selectedItems)">批量改分类
          </a-button>
          <a-button v-privilege="'scm:product:batch'" :disabled="!selectedRows.length"
                    @click="batch?.open('TAG', selectedItems)">批量打标签
          </a-button>
          <a-button v-privilege="'scm:product:import'" @click="importModal?.show()">导入</a-button>
          <a-select v-privilege="'scm:product:export'" v-model:value="exportColumnKeys" mode="multiple"
                    allow-clear :options="exportColumnOptions" option-filter-prop="label"
                    placeholder="导出列：不选则全部" style="width: 300px"
                    @focus="loadExportColumnOptions"/>
          <a-button v-privilege="'scm:product:export'" :loading="exporting" @click="exportCurrent">导出</a-button>
          <span v-if="selectedRows.length" class="scm-cell-hint">已选 {{ selectedRows.length }} 个</span>
        </a-space>
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_PRODUCT" :refresh="load"/>
      </a-row>
      <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
        <template #action>
          <a-button size="small" @click="load">重新加载</a-button>
        </template>
      </a-alert>
      <div ref="tableHost" class="scm-product-table-host">
      <a-table :data-source="rows" :columns="displayColumns" row-key="spuId" :loading="loading" :pagination="false"
               size="small" bordered table-layout="fixed" :scroll="{ x: scrollX }" :expand-column-width="SCM_COLUMN_WIDTH.expand"
               :row-selection="canBatch ? rowSelection : undefined"
               @change="sortChanged">
        <template #expandedRowRender="{ record }">
          <SkuTable :rows="record.skuList"/>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'name'">
            <a-tooltip :title="record.name"><a-button type="link" class="scm-cell-link" @click="detail(record.spuId)">{{ record.name }}</a-button></a-tooltip>
          </template>
          <template v-else-if="column.dataIndex === 'categoryPath'">
            <a-tooltip :title="record.categoryPath || '—'">
              <span class="scm-product-category">{{ record.categoryPath || '—' }}</span>
            </a-tooltip>
          </template>
          <template v-else-if="column.dataIndex === 'primaryImageUrl'">
            <a-image v-if="record.primaryImageUrl && !failedImageUrls.has(record.primaryImageUrl)"
                     :src="record.primaryImageUrl" :width="40" :height="40"
                     :alt="record.name" @error="imageFailed(record.primaryImageUrl)">
              <template #previewMask>
                <EyeOutlined/>
              </template>
            </a-image>
            <a-tooltip v-else :title="record.primaryImageUrl ? '图片加载失败' : '暂无商品图片'">
              <span class="product-image-placeholder" :aria-label="record.primaryImageUrl ? '图片加载失败' : '暂无商品图片'">
                <PictureOutlined/>
              </span>
            </a-tooltip>
          </template>
          <template v-else-if="column.dataIndex === 'tags'">
            <a-space v-if="record.tags.length" :size="2" class="scm-tags">
              <a-tag v-for="tag in record.tags.slice(0, TAG_PREVIEW_LIMIT)" :key="tag.tagId"
                     :color="tag.status === 'ENABLED' ? 'blue' : 'default'">
                {{ tag.name }}
              </a-tag>
              <a-tooltip v-if="record.tags.length > TAG_PREVIEW_LIMIT" :title="restTagTitle(record.tags)">
                <a-tag class="scm-tags__more">+{{ record.tags.length - TAG_PREVIEW_LIMIT }}</a-tag>
              </a-tooltip>
            </a-space>
            <span v-else>—</span></template>
          <span v-else-if="column.dataIndex === 'saleUnit'">{{ record.defaultSku?.saleUnit || '—' }}</span>
          <a-tooltip v-else-if="column.dataIndex === 'price'"
                     :title="priceRange(record.minMarketPrice, record.maxMarketPrice)">
            <span class="scm-money">{{ priceRange(record.minMarketPrice, record.maxMarketPrice) }}</span>
          </a-tooltip>
          <a-tag v-else-if="column.dataIndex === 'status'" :color="record.status === 'ON_SHELF' ? 'green' : 'default'">
            {{ record.status === 'ON_SHELF' ? '上架' : '下架' }}
          </a-tag>
          <a-tag v-else-if="column.dataIndex === 'masterStatus'" :color="MASTER_STATUS_COLOR[record.masterStatus]">
            {{ enumLabel(MASTER_STATUS_ENUM, record.masterStatus) }}
          </a-tag>
          <a-space v-else-if="column.dataIndex === 'action'" :size="0"
                   class="smart-table-operate scm-table-actions">
            <a-button v-privilege="'scm:product:update'" type="link" size="small" @click="drawer?.open(record.spuId)">
              编辑
            </a-button>
            <a-popconfirm :title="`确认${record.status === 'ON_SHELF' ? '下架' : '上架'}此商品？`"
                          @confirm="toggleStatus(record)">
              <a-button v-privilege="'scm:product:status'" type="link" size="small">
                {{ record.status === 'ON_SHELF' ? '下架' : '上架' }}
              </a-button>
            </a-popconfirm>
            <a-popconfirm title="确认删除商品及其规格和图集？" @confirm="remove(record)">
              <a-button v-privilege="'scm:product:delete'" type="link" danger size="small">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </a-table>
      </div>
      <div class="smart-query-table-page">
        <a-pagination v-model:current="filters.pageNum" v-model:page-size="filters.pageSize" :total="total"
                      show-size-changer :show-total="(n: number) => `共 ${n} 条`" @change="load"/>
      </div>
    </a-card>
    <ProductDrawer ref="drawer" @saved="load"/>
    <ProductBatchModal ref="batch" :categories="categories" :tag-options="batchTagOptions" @done="batchDone"/>
    <ProductImportModal ref="importModal" @imported="load"/>
  </section>
</template>
<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, reactive, ref} from 'vue';
import {useRouter} from 'vue-router';
import {message} from 'ant-design-vue';
import {EyeOutlined, PictureOutlined} from '@ant-design/icons-vue';
import type {TableColumnsType, TableProps} from 'ant-design-vue';
import {productApi} from '/@/api/business/scm/product-api';
import ScmAdvancedFilterBuilder from '/@/components/business/scm/advanced-filter/index.vue';
import ScmSavedFilterPresets from '/@/components/business/scm/advanced-filter/saved-presets.vue';
import type {ScmAdvancedFilterField, ScmAdvancedFilterCondition} from '/@/types/business/scm/advanced-filter';
import {productCategoryApi} from '/@/api/business/scm/product-category-api';
import {productTagApi} from '/@/api/business/scm/product-assistant-api';
import {useUserStore} from '/@/store/modules/system/user';
import type {
  ProductBatchItem,
  ProductCategory,
  ProductId,
  ProductQuery,
  ProductRow,
  ProductTag,
  ProductTagRef
} from '/@/types/business/scm/product';
import {
  enumLabel,
  MASTER_STATUS_COLOR,
  MASTER_STATUS_ENUM,
  priceRange,
  PRODUCT_TYPE_ENUM,
  SHELF_STATUS_ENUM,
  STORAGE_METHOD_ENUM,
  YES_NO_ENUM
} from '/@/constants/business/scm/product-const';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_COLUMN_WIDTH, scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import CategorySelect from '/@/components/business/scm/product-category-tree-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ProductDrawer from './components/product-form-drawer.vue';
import ProductBatchModal from './components/product-batch-modal.vue';
import ProductImportModal from './components/product-import-modal.vue';
import SkuTable from './components/product-sku-table.vue';
import {productError} from './product-errors';

const router = useRouter();
const filters = reactive<ProductQuery>({pageNum: 1, pageSize: 20});
const categories = ref<ProductCategory[]>([]), rows = ref<ProductRow[]>([]), total = ref(0), loading = ref(false),
    error = ref(''), advanced = ref(false);
const tagChoices = ref<ProductTag[]>([]), createdRange = ref<[string, string]>();
const selectedKeys = ref<ProductId[]>([]);
const failedImageUrls = ref(new Set<string>());
const advancedFilterFields = ref<ScmAdvancedFilterField[]>([]);
const advancedFilters = ref<ScmAdvancedFilterCondition[]>([]);
function applySavedFilters(conditions: ScmAdvancedFilterCondition[]): void {
  const keys = new Set(conditions.map(c => c.key));
  if (keys.has('keyword')) filters.keyword = undefined;
  if (keys.has('categoryId')) filters.categoryId = undefined;
  if (keys.has('hasPrimaryImage')) filters.hasPrimaryImage = undefined;
  if (keys.has('hasBarcode')) filters.hasBarcode = undefined;
  advancedFilters.value = conditions;
  advanced.value = true;
  search();
}
async function toggleAdvanced(): Promise<void> {
  advanced.value = !advanced.value;
  if (!advanced.value || advancedFilterFields.value.length) return;
  try {
    advancedFilterFields.value = (await productApi.queryFilterFields()).data;
  } catch {
    void message.error('高级筛选字段加载失败');
  }
}
function imageFailed(url: string) {
  failedImageUrls.value = new Set([...failedImageUrls.value, url]);
}
const drawer = ref<InstanceType<typeof ProductDrawer>>(), batch = ref<InstanceType<typeof ProductBatchModal>>();
const importModal = ref<InstanceType<typeof ProductImportModal>>(), exporting = ref(false);
const exportColumnKeys = ref<string[]>([]);
const exportColumnOptions = ref<{label: string; value: string}[]>([]);
const exportColumnsLoaded = ref(false);
async function loadExportColumnOptions(): Promise<void> {
  if (exportColumnsLoaded.value) return;
  try {
    const response = await productApi.exportColumns();
    exportColumnOptions.value = response.data.map(column => ({label: column.title, value: column.key}));
    exportColumnsLoaded.value = true;
  } catch {
    void message.error('获取商品可导出字段失败');
  }
}
const user = useUserStore();
const canBatch = computed(() => user.administratorFlag || user.getPointList?.some((point: {
  webPerms: string
}) => point.webPerms === 'scm:product:batch'));
// 主列表只放「快速识别 + 状态判断 + 高频操作」用得上的列。
// 商品编码、别名、创建/更新时间等仍在搜索、高级筛选、详情、编辑与导出里，只是不默认摊在列表上。
const columns = ref<TableColumnsType<ProductRow>>([
  {title: '主图', dataIndex: 'primaryImageUrl', width: SCM_COLUMN_WIDTH.image, align: 'center'},
  {title: '商品名称', dataIndex: 'name', width: 200, sorter: true, ellipsis: true},
  {title: '分类', dataIndex: 'categoryPath', width: SCM_COLUMN_WIDTH.name, ellipsis: true},
  {title: '单位', dataIndex: 'saleUnit', width: SCM_COLUMN_WIDTH.unit, align: 'center'},
  {title: '市场价', dataIndex: 'price', width: SCM_COLUMN_WIDTH.moneyRange, align: 'right'},
  {title: '规格数', dataIndex: 'skuCount', width: SCM_COLUMN_WIDTH.count, align: 'right'},
  {title: '在售状态', dataIndex: 'status', width: SCM_COLUMN_WIDTH.status, align: 'center', sorter: true},
  {title: '主档状态', dataIndex: 'masterStatus', width: SCM_COLUMN_WIDTH.status, align: 'center'},
  {title: '标签', dataIndex: 'tags', width: 132},
  {title: '操作', dataIndex: 'action', width: SCM_COLUMN_WIDTH.actionThree, align: 'center', fixed: 'right'},
]);
// 固定长度字段使用系统列宽；宽屏剩余空间只分配给名称和分类，避免金额等列被拉宽。
const tableHost = ref<HTMLElement>();
const tableHostWidth = ref(0);
let tableObserver: ResizeObserver | undefined;
const auxiliaryWidth = computed(() => SCM_COLUMN_WIDTH.expand
    + (canBatch.value ? SCM_COLUMN_WIDTH.selection : 0));
const displayColumns = computed<TableColumnsType<ProductRow>>(() => {
  const base = columns.value;
  const extra = Math.max(0, tableHostWidth.value - scmColumnsWidth(base, auxiliaryWidth.value));
  const flexible = ['name', 'categoryPath'].filter(key =>
      base.some(column => 'dataIndex' in column && column.dataIndex === key));
  if (!extra || !flexible.length) return base;

  const share = Math.floor(extra / flexible.length);
  const widths = new Map<string, number>(flexible.map((key, index): [string, number] =>
      [key, share + (index === 0 ? extra % flexible.length : 0)]));
  return base.map(column => {
    const addition = widths.get('dataIndex' in column ? String(column.dataIndex) : '');
    if (!addition) return column;
    const baseWidth = typeof column.width === 'number' ? column.width
        : Number.parseFloat(String(column.width ?? '')) || 0;
    return {...column, width: baseWidth + addition};
  });
});
const scrollX = computed(() => scmColumnsWidth(displayColumns.value, auxiliaryWidth.value));

onMounted(() => {
  const host = tableHost.value;
  if (!host) return;
  tableHostWidth.value = Math.floor(host.clientWidth);
  tableObserver = new ResizeObserver(entries => {
    tableHostWidth.value = Math.floor(entries[0]?.contentRect.width ?? 0);
  });
  tableObserver.observe(host);
});
onBeforeUnmount(() => tableObserver?.disconnect());
const tagFilterOptions = computed(() => tagChoices.value.map((tag) => ({value: tag.tagId, label: tag.name})));
// 标签列只展示前 N 个，其余折成 +N；否则标签多的商品会把整行撑宽、撑高。
const TAG_PREVIEW_LIMIT = 2;
const restTagTitle = (tags: ProductTagRef[]) => tags.slice(TAG_PREVIEW_LIMIT)
    .map((tag) => (tag.status === 'ENABLED' ? tag.name : `${tag.name}（已停用）`))
    .join('、');
const selectedRows = computed(() => rows.value.filter((row) => selectedKeys.value.some((key) => String(key) === String(row.spuId))));
/** 批量命令要逐行带乐观锁版本，所以选择只在当前页有效，翻页或刷新后一律清空。 */
const selectedItems = computed<ProductBatchItem[]>(() => selectedRows.value.map((row) => ({
  spuId: row.spuId,
  version: row.version
})));
// REMOVE 停用标签是批量摘标的正当用法：下拉里要连选中商品已挂的停用标签一起给出。
const batchTagOptions = computed(() => {
  const known = new Set(tagChoices.value.map((tag) => String(tag.tagId)));
  const retired = new Map<ProductTagRef['tagId'], string>();
  for (const row of selectedRows.value) for (const tag of row.tags) if (!known.has(String(tag.tagId))) retired.set(String(tag.tagId), tag.name);
  return [...tagFilterOptions.value, ...[...retired].map(([tagId, name]) => ({
    value: tagId,
    label: `${name}（已停用）`
  }))];
});
const rowSelection = computed(() => ({
  columnWidth: SCM_COLUMN_WIDTH.selection,
  selectedRowKeys: selectedKeys.value,
  onChange: (keys: (string | number)[]) => {
    selectedKeys.value = keys;
  },
}));
let requestId = 0;

async function load() {
  const request = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    // 加载失败由页头 Alert 承担，不再让全局 toast 重复说一遍
    const response = await productApi.query({
      ...filters,
      createdFrom: createdRange.value?.[0] || undefined,
      createdTo: createdRange.value?.[1] || undefined,
      advancedFilters: advancedFilters.value.length ? advancedFilters.value : undefined
    }, {suppressGlobalErrorMessage: true});
    if (request === requestId) {
      rows.value = response.data.list;
      total.value = Number(response.data.total);
      selectedKeys.value = [];
    }
  } catch (e) {
    if (request === requestId) error.value = productError(e);
  } finally {
    if (request === requestId) loading.value = false;
  }
}

function search() {
  filters.pageNum = 1;
  void load();
}

function reset() {
  Object.assign(filters, {
    pageNum: 1,
    keyword: undefined,
    categoryId: undefined,
    status: undefined,
    skuStatus: undefined,
    productType: undefined,
    masterStatus: undefined,
    storageMethod: undefined,
    tagIds: undefined,
    hasPrimaryImage: undefined,
    hasBarcode: undefined,
    sortItemList: undefined
  });
  createdRange.value = undefined;
  advancedFilters.value = [];
  void load();
}

const sortChanged: TableProps<ProductRow>['onChange'] = (_page, _filters, sort) => {
  const item = Array.isArray(sort) ? sort[0] : sort;
  const names: Record<string, string> = {spuCode: 'spu_code', name: 'name', status: 'status', updatedAt: 'updated_at'};
  const column = names[String(item.field)];
  filters.sortItemList = item.order && column ? [{column, isAsc: item.order === 'ascend'}] : undefined;
  search();
};

function detail(id: ProductId) {
  void router.push({path: '/product/product-detail', query: {spuId: String(id)}});
}

async function exportCurrent() {
  exporting.value = true;
  try {
    await productApi.exportProducts({
      ...filters,
      createdFrom: createdRange.value?.[0] || undefined,
      createdTo: createdRange.value?.[1] || undefined,
      exportColumnKeys: exportColumnKeys.value.length ? exportColumnKeys.value : undefined,
      advancedFilters: advancedFilters.value.length ? advancedFilters.value : undefined
    });
  } catch {
    // 导出失败只走全局 toast
  } finally {
    exporting.value = false;
  }
}

function batchDone() {
  message.success('批量维护已完成');
  void load();
}

async function toggleStatus(row: ProductRow) {
  try {
    await productApi.status(row.spuId, row.version, row.status === 'ON_SHELF' ? 'OFF_SHELF' : 'ON_SHELF');
    message.success('商品状态已更新');
    await load();
  } catch {
    // 状态流转失败只走全局 toast
  }
}

async function remove(row: ProductRow) {
  try {
    await productApi.delete(row.spuId, row.version);
    message.success('商品已删除');
    await load();
  } catch {
    // 删除失败只走全局 toast
  }
}

onMounted(async () => {
  void load();
  try {
    // 筛选下拉的选项加载失败也由页头 Alert 承担
    const [tree, tags] = await Promise.all([
      productCategoryApi.tree({suppressGlobalErrorMessage: true}),
      productTagApi.options({suppressGlobalErrorMessage: true}),
    ]);
    categories.value = tree.data;
    tagChoices.value = tags.data;
  } catch (e) {
    error.value = productError(e);
  }
});
</script>
<style scoped>
.scm-product-table-host {
  width: 100%;
  min-width: 0;
}

.scm-product-category {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.product-image-placeholder {
  display: inline-flex;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--scm-border, #e5e6eb);
  border-radius: 6px;
  background: var(--scm-fill, #f5f5f5);
  color: var(--scm-text-secondary, #999);
  font-size: 18px;
}
</style>

