<template>
  <section aria-label="商品图片中心">
    <a-row :gutter="12">
      <a-col :xs="24" :md="9">
        <a-card size="small" title="选择商品" :bordered="false">
          <a-form layout="inline" class="pick-form">
            <a-form-item><a-input v-model:value="keyword" allow-clear placeholder="商品名称" style="width: 200px" /></a-form-item>
            <a-form-item><a-checkbox v-model:checked="onlyNoPrimary">仅无主图</a-checkbox></a-form-item>
            <a-form-item
              ><a-space><a-button type="primary" @click="search">查询</a-button> <a-button @click="resetPick">重置</a-button></a-space></a-form-item
            >
          </a-form>
          <a-alert v-if="pickError" :message="pickError" type="error" show-icon class="gap" />
          <a-table
            :data-source="products"
            :columns="pickColumns"
            row-key="spuId"
            size="small"
            :loading="pickLoading"
            :pagination="false"
            :scroll="{y: 420}"
            :custom-row="(row: ProductRow) => ({onClick: () => select(row.spuId), style: {cursor: 'pointer'}})"
          >
            <template #bodyCell="{column, record}">
              <template v-if="column.dataIndex === 'name'">{{ record.name || '—' }}</template>
              <template v-else-if="column.dataIndex === 'primary'">
                <a-tag v-if="record.primaryImageUrl" color="green">有主图</a-tag>
                <a-tag v-else>无主图</a-tag>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>

      <a-col :xs="24" :md="15">
        <a-card size="small" :bordered="false" :class="{'pool-target': !!poolDragKey}" @dragover.prevent @drop.prevent="dropFromPool">
          <template #title>
            <span v-if="view">
              {{ view.name }}
              <ScmFieldHelp label="商品图片" text="主图最多一张；详情图可拖动排序" />
            </span>
            <span v-else>图片维护</span>
          </template>
          <template #extra>
            <a-button v-privilege="'scm:product:image:batch'" type="primary" size="small" :disabled="!products.length" @click="openBatch"
              >批量导入图片
            </a-button>
          </template>
          <a-empty v-if="!view" description="请从左侧选择一个商品" />
          <template v-else>
            <a-alert v-if="imageError" :message="imageError" type="error" show-icon class="gap" />
            <a-spin :spinning="imageLoading">
              <div class="images">
                <figure
                  v-for="(image, index) in view.images"
                  :key="String(image.imageId)"
                  class="image-tile"
                  :class="{'image-tile--primary': image.primaryFlag}"
                  :draggable="canWrite"
                  @dragstart="dragIndex = index"
                  @dragover.prevent
                  @drop.prevent="move(dragIndex, index)"
                >
                  <div class="image-tile__frame">
                    <a-image
                      v-if="image.fileUrl && !brokenImageKeys.has(image.fileKey)"
                      :src="image.fileUrl"
                      :width="130"
                      :height="104"
                      :alt="image.fileName || `图 ${index + 1}`"
                      @error="markImageBroken(image.fileKey)"
                    />
                    <div v-else class="image-preview-fallback">图片无法预览</div>
                  </div>
                  <figcaption class="image-tile__meta">
                    <a-tag v-if="image.primaryFlag" color="green">主图</a-tag>
                    <span v-else class="image-tile__order">第 {{ index + 1 }} 张</span>
                  </figcaption>
                  <div v-if="canWrite" class="image-tile__actions">
                    <div class="image-tile__group">
                      <a-tooltip title="设为主图">
                        <a-button size="small" type="text" :disabled="image.primaryFlag || busy" aria-label="设为主图" @click="setPrimary(image)">
                          <StarOutlined />
                        </a-button>
                      </a-tooltip>
                      <a-tooltip title="前移">
                        <a-button
                          size="small"
                          type="text"
                          :disabled="index === 0 || busy"
                          :aria-label="`前移 ${index + 1}`"
                          @click="move(index, index - 1)"
                        >
                          <ArrowLeftOutlined />
                        </a-button>
                      </a-tooltip>
                      <a-tooltip title="后移">
                        <a-button
                          size="small"
                          type="text"
                          :disabled="index === view.images.length - 1 || busy"
                          :aria-label="`后移 ${index + 1}`"
                          @click="move(index, index + 1)"
                        >
                          <ArrowRightOutlined />
                        </a-button>
                      </a-tooltip>
                    </div>
                    <a-popconfirm title="从本商品移除此图片？" @confirm="removeImage(image)">
                      <a-button size="small" type="text" danger :disabled="busy" aria-label="移除">移除</a-button>
                    </a-popconfirm>
                  </div>
                </figure>
                <a-upload v-if="canWrite" :accept="IMAGE_ACCEPT" :file-list="[]" :before-upload="uploadIntoSelected" :show-upload-list="false">
                  <a-button :loading="uploading" type="dashed" class="add-tile">+ 上传图片</a-button>
                </a-upload>
              </div>
            </a-spin>
          </template>
        </a-card>
      </a-col>
    </a-row>

    <a-card size="small" :bordered="false" class="pool-card">
      <template #title>
        <span
          >未绑定图片（{{ pool.length }}）
          <ScmFieldHelp
            label="未绑定图片"
            text="已上传到公开图片目录、但还没挂到任何商品的图片。先选中左侧商品，再把它拖到右边，或点「绑到当前商品」。"
          />
        </span>
      </template>
      <a-alert v-if="poolError" :message="poolError" type="error" show-icon class="gap" />
      <a-spin :spinning="poolLoading">
        <a-empty v-if="!pool.length" description="没有待绑定的图片" />
        <div v-else class="pool">
          <figure v-for="image in pool" :key="image.fileKey" draggable="true" @dragstart="poolDragKey = image.fileKey" @dragend="poolDragKey = ''">
            <a-image
              v-if="image.fileUrl && !brokenImageKeys.has(image.fileKey)"
              :src="image.fileUrl"
              :width="88"
              :height="88"
              :alt="image.fileName || '未绑定图片'"
              @error="markImageBroken(image.fileKey)"
            />
            <div v-else class="image-preview-fallback image-preview-fallback--small">图片无法预览</div>
            <figcaption :title="image.fileName">{{ image.fileName || '—' }}</figcaption>
            <a-button size="small" :disabled="!view || busy" @click="bindFromPool(image)">绑到当前商品</a-button>
          </figure>
        </div>
      </a-spin>
    </a-card>

    <a-modal v-model:open="batchOpen" title="批量导入商品图片" :width="760" :mask-closable="false" @cancel="closeBatch">
      <div class="import-step">
        <div class="import-step__head">
          <span class="import-step__index">1</span>
          <span class="import-step__title">选择图片</span>
          <span class="import-step__desc">文件名不限；系统会按文件名尝试匹配商品，匹配不到可在下一步手动指定</span>
        </div>
        <a-upload-dragger
          class="import-drop"
          :class="{'is-picked': rows.length > 0}"
          :file-list="[]"
          :before-upload="stageFiles"
          :accept="IMAGE_ACCEPT"
          multiple
          :show-upload-list="false"
        >
          <p class="import-drop__icon"><InboxOutlined /></p>
          <p class="import-drop__title">
            {{ staging ? '正在上传…' : rows.length ? `已选择 ${rows.length} 张，点击可继续添加` : '点击或拖拽图片到此处' }}
          </p>
          <p class="import-drop__hint">支持 JPG、PNG、WebP、GIF、BMP，单张不超过 {{ MAX_IMAGE_SIZE_MB }} MB；可一次选多张</p>
        </a-upload-dragger>
      </div>

      <div class="import-step">
        <div class="import-step__head">
          <span class="import-step__index">2</span>
          <span class="import-step__title">指定商品</span>
          <span class="import-step__desc">只有已指定商品的图片会被绑定；绑定后自动按商品命名，多张依次递增</span>
        </div>
        <div class="match-summary">
          <span class="match-stat match-stat--ok"
            ><em>{{ assignedCount }}</em> 已指定</span
          >
          <span class="match-stat match-stat--warn"
            ><em>{{ pendingCount }}</em> 待指定</span
          >
          <a-button v-if="rows.length" type="link" size="small" @click="clearBatchFiles">清空列表</a-button>
        </div>
        <div v-if="rows.length > 1" class="bulk-assign">
          <span class="bulk-assign__label">批量指定</span>
          <a-select
            :value="bulkTargetId"
            class="bulk-assign__select"
            :options="bulkOptions"
            show-search
            allow-clear
            :filter-option="false"
            :loading="targetLoading"
            placeholder="搜索商品名称"
            @search="searchTargets"
            @change="onBulkTargetChange"
          />
          <a-button size="small" :disabled="bulkTargetId == null" @click="applyBulkTarget">应用到全部</a-button>
        </div>
        <a-table
          v-if="rows.length"
          :data-source="rows"
          :columns="importColumns"
          :row-key="importRowKey"
          size="small"
          :pagination="false"
          :scroll="{y: 260}"
        >
          <template #bodyCell="{column, record}">
            <template v-if="column.dataIndex === 'fileName'">
              <span class="import-file" :title="record.file.fileName">{{ record.file.fileName }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'target'">
              <a-select
                :value="record.target?.spuId"
                :options="targetOptionsFor(record)"
                show-search
                allow-clear
                :filter-option="false"
                :loading="targetLoading"
                placeholder="搜索商品名称"
                size="small"
                style="width: 100%"
                @search="searchTargets"
                @change="onRowTargetChange(record)"
              />
            </template>
            <template v-else-if="column.dataIndex === 'action'">
              <a-button type="link" danger size="small" @click="removeBatchFile(record)">移除</a-button>
            </template>
          </template>
        </a-table>
        <div v-else class="match-empty">还没有选择图片，先在上方上传</div>
      </div>

      <template #footer>
        <a-button @click="closeBatch">取消</a-button>
        <a-button type="primary" :disabled="!assignedCount" :loading="binding" @click="confirmBatch">
          {{ assignedCount ? `确认绑定 ${assignedCount} 张` : '请先指定商品' }}
        </a-button>
      </template>
    </a-modal>
  </section>
</template>
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import {message} from 'ant-design-vue';
import type {UploadProps} from 'ant-design-vue';
import {ArrowLeftOutlined, ArrowRightOutlined, InboxOutlined, StarOutlined} from '@ant-design/icons-vue';
import {productApi, productImageApi} from '/@/api/business/scm/product-api';
import {fileApi} from '/@/api/support/file-api';
import {FILE_FOLDER_TYPE_ENUM} from '/@/constants/support/file-const';
import {IMAGE_ACCEPT, MAX_IMAGE_SIZE_MB, imageFileError} from '/@/constants/business/scm/image-const';
import {useUserStore} from '/@/store/modules/system/user';
import type {ImageCenterView, ImageMatchTarget, ProductId, ProductImage, ProductRow, UnboundImage} from '/@/types/business/scm/product';
import type {SpuTarget, UploadedImageFile} from './product-import-model';
import {matchFilesBySpuCode} from './product-import-model';
import {productError} from './product-errors';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';

type UploadedFile = {fileKey: string; fileUrl: string; fileName?: string; fileSize?: number};

const user = useUserStore();
const canWrite = computed(
  () => user.administratorFlag || user.getPointList?.some((p: {webPerms: string}) => p.webPerms === 'scm:product:image:batch')
);

const keyword = ref(''),
  onlyNoPrimary = ref(false),
  products = ref<ProductRow[]>([]),
  pickLoading = ref(false),
  pickError = ref('');
// 左侧选择区三列：编码、名称、主图状态。
const pickColumns = [
  {title: '商品名称', dataIndex: 'name'},
  {title: '主图', dataIndex: 'primary', width: 90},
];

const view = ref<ImageCenterView | null>(null),
  imageLoading = ref(false),
  imageError = ref(''),
  busy = ref(false),
  uploading = ref(false),
  dragIndex = ref(-1);
const brokenImageKeys = ref(new Set<string>());
function markImageBroken(fileKey: string) {
  brokenImageKeys.value = new Set([...brokenImageKeys.value, fileKey]);
}

// 未绑定图片池：公开目录里还没挂到任何商品的图片。
const pool = ref<UnboundImage[]>([]),
  poolLoading = ref(false),
  poolError = ref(''),
  poolDragKey = ref('');

let poolRequestId = 0;
async function loadPool() {
  const current = ++poolRequestId;
  poolLoading.value = true;
  poolError.value = '';
  try {
    const response = await productImageApi.unboundImages(300);
    if (current === poolRequestId) pool.value = response.data;
  } catch (e) {
    if (current === poolRequestId) poolError.value = productError(e);
  } finally {
    if (current === poolRequestId) poolLoading.value = false;
  }
}

/** 绑定到当前商品；商品没有图时才设为主图。 */
async function bindFromPool(image: UnboundImage) {
  if (!view.value) return;
  const target = view.value;
  const primaryFlag = target.images.length === 0;
  await runWrite(
    () =>
      productImageApi.batchBind({
        items: [{spuId: target.spuId, fileKey: image.fileKey, primaryFlag, sortOrder: target.images.length}],
      }),
    '图片已绑定'
  );
  await loadPool();
}

/** 从图片池拖到商品卡片；商品内换序没有 poolDragKey，直接放行。 */
async function dropFromPool() {
  const fileKey = poolDragKey.value;
  poolDragKey.value = '';
  if (!fileKey) return;
  const image = pool.value.find((item) => item.fileKey === fileKey);
  if (image) await bindFromPool(image);
}

onMounted(() => {
  search();
  loadPool();
});

let searchRequestId = 0;
async function search() {
  const current = ++searchRequestId;
  pickLoading.value = true;
  pickError.value = '';
  try {
    const response = await productApi.query({
      pageNum: 1,
      pageSize: 50,
      keyword: keyword.value || undefined,
      hasPrimaryImage: onlyNoPrimary.value ? false : undefined,
    });
    if (current === searchRequestId) products.value = response.data.list;
  } catch (e) {
    if (current === searchRequestId) pickError.value = productError(e);
  } finally {
    if (current === searchRequestId) pickLoading.value = false;
  }
}

function resetPick() {
  keyword.value = '';
  onlyNoPrimary.value = false;
  view.value = null;
  // 重置只清筛选条件，列表重新加载。
  search();
}

let selectRequestId = 0;
async function select(spuId: ProductId) {
  const current = ++selectRequestId;
  imageLoading.value = true;
  imageError.value = '';
  try {
    const response = await productImageApi.query(spuId);
    if (current === selectRequestId) view.value = response.data;
  } catch (e) {
    if (current === selectRequestId) {
      imageError.value = productError(e);
      view.value = null;
    }
  } finally {
    if (current === selectRequestId) imageLoading.value = false;
  }
}

async function setPrimary(image: ProductImage) {
  if (!view.value) return;
  await runWrite(() => productImageApi.setPrimary({spuId: view.value!.spuId, imageId: image.imageId!}), '主图已更新');
}

async function removeImage(image: ProductImage) {
  if (!view.value) return;
  await runWrite(() => productImageApi.batchRemove({spuId: view.value!.spuId, imageIds: [image.imageId!]}), '图片已移除');
}

/** 按整列 imageId 提交顺序；下标越界时不动。 */
async function move(from: number, to: number) {
  if (!view.value) return;
  const images = view.value.images;
  if (from < 0 || to < 0 || to >= images.length || from === to) {
    dragIndex.value = -1;
    return;
  }
  const ordered = [...images];
  ordered.splice(to, 0, ordered.splice(from, 1)[0]);
  dragIndex.value = -1;
  const orderedImageIds = ordered.map((image) => image.imageId!);
  if (orderedImageIds.every((id, i) => id === images[i].imageId)) return;
  await runWrite(() => productImageApi.reorder({spuId: view.value!.spuId, orderedImageIds}), '顺序已更新');
}

const uploadIntoSelected: UploadProps['beforeUpload'] = async (selected) => {
  if (!view.value || !canWrite.value) return false;
  const file = selected as File;
  const invalid = imageFileError(file);
  if (invalid) {
    message.error(invalid);
    return false;
  }
  uploading.value = true;
  const isFirst = view.value.images.length === 0;
  const sortOrder = view.value.images.length;
  const targetSpuId = view.value.spuId;
  try {
    const uploaded = await uploadFile(file);
    await productImageApi.batchBind({
      items: [{spuId: targetSpuId, fileKey: uploaded.fileKey, primaryFlag: isFirst, sortOrder}],
    });
    await select(targetSpuId);
    message.success('图片已上传并添加到商品');
  } catch (e) {
    imageError.value = productError(e);
  } finally {
    uploading.value = false;
  }
  return false;
};

async function runWrite(action: () => Promise<unknown>, successMsg: string) {
  busy.value = true;
  try {
    await action();
    message.success(successMsg);
    // 图片归属变化后让编码缓存失效，下次批量导入重新取目标
    matchCache.clear();
    if (view.value) await select(view.value.spuId);
  } catch (e) {
    imageError.value = productError(e);
  } finally {
    busy.value = false;
  }
}

async function uploadFile(file: File): Promise<{fileKey: string}> {
  const data = new FormData();
  data.append('file', file);
  const response = await fileApi.uploadFile<UploadedFile>(data, FILE_FOLDER_TYPE_ENUM.PUBLIC_IMAGE.value, {
    suppressGlobalErrorMessage: true,
  });
  return {fileKey: response.data.fileKey};
}

// 批量导入：上传后逐张指定商品；文件名只用于自动匹配提示，不再要求等于商品编码
interface ImportRow {
  file: UploadedImageFile;
  target: ImageMatchTarget | null;
  autoMatched: boolean;
}

interface TargetOption {
  value: ProductId;
  label: string;
  spuCode: string;
  name?: string;
}

const batchOpen = ref(false),
  staging = ref(false),
  binding = ref(false),
  rows = ref<ImportRow[]>([]),
  bulkTargetId = ref<ProductId | undefined>(),
  bulkTarget = ref<TargetOption | null>(null);
/** 编码 → 商品；空数组表示已确认未命中，避免重复请求。 */
const matchCache = new Map<string, SpuTarget[]>();
let matchGeneration = 0;
const importRowKey = (row: ImportRow) => row.file.fileKey;
const importColumns = [
  {title: '文件', dataIndex: 'fileName'},
  {title: '目标商品', dataIndex: 'target'},
  {title: '操作', dataIndex: 'action', width: 70, align: 'center'},
];
const assignedCount = computed(() => rows.value.filter((row) => row.target).length);
const pendingCount = computed(() => rows.value.length - assignedCount.value);

// 目标商品下拉：复用商品列表接口做远程搜索；已选中的商品即使不在当前搜索结果里也保留显示
const targetOptions = ref<TargetOption[]>([]),
  targetLoading = ref(false);
let targetRequestId = 0;
let targetTimer: ReturnType<typeof setTimeout> | undefined;

async function loadTargetOptions(keyword = '') {
  const id = ++targetRequestId;
  targetLoading.value = true;
  try {
    const response = await productApi.query({pageNum: 1, pageSize: 20, keyword: keyword || undefined}, {suppressGlobalErrorMessage: true});
    if (id === targetRequestId) {
      targetOptions.value = response.data.list.map((row) => ({
        value: row.spuId,
        label: row.name,
        spuCode: row.spuCode,
        name: row.name,
      }));
    }
  } catch {
    if (id === targetRequestId) targetOptions.value = [];
  } finally {
    if (id === targetRequestId) targetLoading.value = false;
  }
}

function searchTargets(text: string) {
  clearTimeout(targetTimer);
  targetTimer = setTimeout(() => loadTargetOptions(text.trim()), 250);
}

function targetOptionsFor(row: ImportRow): TargetOption[] {
  const list = [...targetOptions.value];
  const target = row.target;
  if (target && !list.some((option) => String(option.value) === String(target.spuId))) {
    list.unshift({
      value: target.spuId,
      label: target.name || '已选商品',
      spuCode: target.spuCode,
      name: target.name,
    });
  }
  return list;
}

function setRowTarget(row: ImportRow, value: ProductId | undefined) {
  // 必须从该行自己的选项里找：搜索会替换全局选项，已选项只保留在 targetOptionsFor 的合并结果里
  const option = targetOptionsFor(row).find((item) => String(item.value) === String(value));
  row.target = option ? {spuId: option.value, spuCode: option.spuCode, name: option.name} : null;
  row.autoMatched = false;
}

/** 把行记录闭包进 change 回调，模板里不必写带类型的箭头函数。 */
function onRowTargetChange(row: ImportRow) {
  return (value: ProductId | undefined) => setRowTarget(row, value);
}

const bulkOptions = computed<TargetOption[]>(() => {
  const list = [...targetOptions.value];
  const selected = bulkTarget.value;
  if (selected && !list.some((option) => String(option.value) === String(selected.value))) {
    list.unshift(selected);
  }
  return list;
});

function onBulkTargetChange(value: ProductId | undefined) {
  bulkTargetId.value = value;
  bulkTarget.value = bulkOptions.value.find((option) => String(option.value) === String(value)) ?? null;
}

function applyBulkTarget() {
  const option = bulkTarget.value;
  if (!option) return;
  rows.value = rows.value.map((row) => ({
    ...row,
    target: {spuId: option.value, spuCode: option.spuCode, name: option.name},
    autoMatched: false,
  }));
}

function openBatch() {
  batchOpen.value = true;
  void loadTargetOptions();
}

function closeBatch() {
  batchOpen.value = false;
  clearBatchFiles();
}

function clearBatchFiles() {
  // 关闭或清空时同时让在途匹配失效，避免旧响应把新列表重新填满
  matchGeneration++;
  matchCache.clear();
  rows.value = [];
  bulkTargetId.value = undefined;
  bulkTarget.value = null;
}

function removeBatchFile(row: ImportRow) {
  rows.value = rows.value.filter((item) => item.file.fileKey !== row.file.fileKey);
}

const fileCode = (file: UploadedImageFile) =>
  file.fileName
    .replace(/\.[^.]+$/, '')
    .trim()
    .toLowerCase();

/**
 * 只按编码反查目标商品，不依赖当前分页列表；负结果也缓存，避免同一编码反复请求。
 * 后端一次最多 500 个编码，超出时按块并发请求。
 */
async function ensureMatchTargets(codes: string[]) {
  const missing = [...new Set(codes)].filter((code) => !matchCache.has(code));
  if (!missing.length) {
    return;
  }
  const chunks: string[][] = [];
  for (let index = 0; index < missing.length; index += 500) {
    chunks.push(missing.slice(index, index + 500));
  }
  const responses = await Promise.all(chunks.map((chunk) => productImageApi.matchTargets(chunk, {suppressGlobalErrorMessage: true})));
  const grouped = new Map<string, SpuTarget[]>();
  for (const target of responses.flatMap((response) => response.data ?? [])) {
    const key = target.spuCode.trim().toLowerCase();
    const list = grouped.get(key);
    if (list) list.push(target);
    else grouped.set(key, [target]);
  }
  for (const code of missing) {
    matchCache.set(code, grouped.get(code) ?? []);
  }
}

// 多选上传会并发触发 beforeUpload；匹配串行排队，保证最后展示的是全部已上传文件。
let matchQueue: Promise<void> = Promise.resolve();
/** 文件名恰好是商品编码时自动预选目标商品；其余情况留给用户手动指定。 */
function refreshAutoMatches() {
  const task = matchQueue.then(async () => {
    const generation = matchGeneration;
    await ensureMatchTargets(rows.value.map((row) => fileCode(row.file)));
    if (generation !== matchGeneration) {
      return;
    }
    const results = matchFilesBySpuCode(
      rows.value.map((row) => row.file),
      [...matchCache.values()].flat()
    );
    rows.value = rows.value.map((row, index) => {
      if (row.target) {
        return row;
      }
      const matched = results[index]?.status === 'matched' ? results[index].candidates[0] : null;
      return matched ? {...row, target: {spuId: matched.spuId, spuCode: matched.spuCode}, autoMatched: true} : row;
    });
  });
  matchQueue = task.catch(() => undefined);
  return task;
}

const stageFiles: UploadProps['beforeUpload'] = async (selected) => {
  const file = selected as File;
  const invalid = imageFileError(file);
  if (invalid) {
    message.error(invalid);
    return false;
  }
  staging.value = true;
  try {
    const uploaded = await uploadFile(file);
    if (!rows.value.some((row) => row.file.fileKey === uploaded.fileKey)) {
      rows.value = [...rows.value, {file: {fileName: file.name, fileKey: uploaded.fileKey}, target: null, autoMatched: false}];
    }
    await refreshAutoMatches();
  } catch (e) {
    message.error(productError(e));
  } finally {
    staging.value = false;
  }
  return false;
};

async function confirmBatch() {
  const items = rows.value
    .filter((row) => row.target)
    .map((row) => ({spuId: row.target!.spuId, fileKey: row.file.fileKey, primaryFlag: false, sortOrder: 0}));
  if (!items.length) return;
  binding.value = true;
  try {
    await productImageApi.batchBind({items});
    const touchedSelected = view.value && items.some((item) => String(item.spuId) === String(view.value!.spuId));
    matchCache.clear();
    message.success(`已绑定 ${items.length} 张图片`);
    closeBatch();
    if (touchedSelected) await select(view.value!.spuId);
    await loadPool();
  } catch (e) {
    message.error(productError(e));
  } finally {
    binding.value = false;
  }
}
</script>
<style scoped>
.gap {
  margin-bottom: 12px;
}

.pick-form {
  margin-bottom: 8px;
}

.images {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 16px;
}

.image-tile {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 148px;
  margin: 0;
  padding: 8px;
  border: 1px solid var(--scm-border, #e5e6eb);
  border-radius: 8px;
  background: var(--scm-bg-container, #fff);
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

.image-tile[draggable='true'] {
  cursor: grab;
}

.image-tile:hover {
  border-color: var(--scm-primary, #1677ff);
  box-shadow: 0 2px 8px rgb(0 0 0 / 8%);
}

.image-tile--primary {
  border-color: var(--scm-primary, #1677ff);
}

.image-tile__frame {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 104px;
  overflow: hidden;
  border-radius: 6px;
  background: var(--scm-fill, #f5f5f5);
}

.image-tile__frame :deep(.ant-image),
.image-tile__frame :deep(.ant-image-img) {
  width: 100%;
  height: 100%;
}

.image-tile__frame :deep(.ant-image-img) {
  object-fit: cover;
}

.image-tile__meta {
  display: flex;
  align-items: center;
  min-height: 22px;
  margin: 0;
}

.image-tile__meta :deep(.ant-tag) {
  margin-inline-end: 0;
}

.image-tile__order {
  color: var(--scm-text-secondary, #888);
  font-size: 12px;
}

.image-tile__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
}

.image-tile__group {
  display: flex;
  align-items: center;
  gap: 2px;
}

.image-tile__actions :deep(.ant-btn) {
  height: 24px;
  padding-inline: 6px;
}

.image-preview-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  padding: 8px;
  border: 1px dashed var(--scm-border, #e5e6eb);
  border-radius: 6px;
  background: var(--scm-fill, #f5f5f5);
  color: var(--scm-text-secondary, #888);
  font-size: 12px;
  text-align: center;
}

.image-preview-fallback--small {
  width: 88px;
  height: 88px;
  flex: none;
}

.add-tile {
  width: 148px;
  height: 184px;
  border-radius: 8px;
}

.import-step {
  margin-bottom: 20px;
}

.import-step__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}

/* 步骤序号，强调「先选图、再核对」的顺序 */
.import-step__index {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background-color: var(--scm-primary, #00b96b);
  color: #ffffff;
  font-size: 11px;
  line-height: 1;
}

.import-step__title {
  color: rgba(0, 0, 0, 0.88);
  font-weight: 500;
}

.import-step__desc {
  color: var(--scm-text-secondary, #888);
  font-size: 12px;
}

.import-drop :deep(.ant-upload-drag) {
  border-radius: 8px;
  transition:
    border-color 0.2s,
    background-color 0.2s;
}

.import-drop.is-picked :deep(.ant-upload-drag) {
  border-color: var(--scm-primary, #00b96b);
  background-color: var(--scm-primary-bg, #e6f4ff);
}

.import-drop__icon {
  margin: 0 0 4px;
  color: #bfbfbf;
  font-size: 26px;
}

.import-drop.is-picked .import-drop__icon {
  color: var(--scm-primary, #00b96b);
}

.import-drop__title {
  margin: 0 0 2px;
  color: rgba(0, 0, 0, 0.88);
  font-size: 14px;
}

.import-drop__hint {
  margin: 0;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}

.match-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  margin-bottom: 10px;
}

.match-stat {
  color: var(--scm-text-secondary, #888);
  font-size: 13px;
}

.match-stat em {
  margin-right: 2px;
  color: rgba(0, 0, 0, 0.88);
  font-style: normal;
  font-weight: 600;
}

.match-stat--ok em {
  color: #389e0d;
}

.match-stat--warn em {
  color: #d46b08;
}

.match-summary :deep(.ant-btn) {
  margin-left: auto;
  padding-inline: 0;
}

.bulk-assign {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.bulk-assign__label {
  flex: none;
  color: var(--scm-text-secondary, #888);
  font-size: 13px;
}

.bulk-assign__select {
  width: 280px;
}

.import-file {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}

.match-empty {
  padding: 28px 0;
  color: var(--scm-text-secondary, #999);
  font-size: 13px;
  text-align: center;
}

.muted {
  color: var(--scm-text-secondary, #bbb);
}

.pool-card {
  margin-top: 12px;
}

.pool {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  max-height: 320px;
  overflow-y: auto;
}

.pool figure {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: 108px;
  margin: 0;
  padding: 8px;
  border: 1px solid var(--scm-border, #e5e6eb);
  border-radius: 8px;
  background: var(--scm-bg-container, #fff);
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

.pool figure:hover {
  border-color: var(--scm-primary, #1677ff);
  box-shadow: 0 2px 8px rgb(0 0 0 / 8%);
}

.pool figcaption {
  width: 100%;
  margin: 0;
  overflow: hidden;
  color: var(--scm-text-secondary, #888);
  font-size: 12px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pool figure :deep(.ant-btn) {
  width: 100%;
  padding-inline: 2px;
}

.pool-target {
  outline: 1px dashed var(--scm-primary, #1677ff);
  outline-offset: -4px;
}
</style>
