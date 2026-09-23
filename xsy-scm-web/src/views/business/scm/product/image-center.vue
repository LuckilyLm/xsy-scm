<template>
  <section aria-label="商品图片中心">
    <a-row :gutter="12">
      <a-col :xs="24" :md="9">
        <a-card size="small" title="选择商品" :bordered="false">
          <a-form layout="inline" class="pick-form">
            <a-form-item><a-input v-model:value="keyword" allow-clear placeholder="SPU 编码 / 名称 / 助记码"
                                   style="width: 200px"/></a-form-item>
            <a-form-item><a-checkbox v-model:checked="onlyNoPrimary">仅无主图</a-checkbox></a-form-item>
            <a-form-item><a-space><a-button type="primary" @click="search">查询</a-button>
              <a-button @click="resetPick">重置</a-button></a-space></a-form-item>
          </a-form>
          <a-alert v-if="pickError" :message="pickError" type="error" show-icon class="gap"/>
          <a-table :data-source="products" :columns="pickColumns" row-key="spuId" size="small"
                   :loading="pickLoading" :pagination="false" :scroll="{ y: 420 }"
                   :custom-row="(row: ProductRow) => ({ onClick: () => select(row.spuId), style: { cursor: 'pointer' } })">
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'primary'">
                <a-tag v-if="record.primaryImageUrl" color="green">有主图</a-tag>
                <a-tag v-else>无主图</a-tag>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>

      <a-col :xs="24" :md="15">
        <a-card size="small" :bordered="false">
          <template #title>
            <span v-if="view">{{ view.name }}（{{ view.spuCode }}）</span>
            <span v-else>图片维护</span>
          </template>
          <template #extra>
            <a-button v-privilege="'scm:product:image:batch'" type="primary" :disabled="!products.length"
                      @click="openBatch">按文件名批量导入
            </a-button>
          </template>
          <a-empty v-if="!view" description="请从左侧选择一个商品"/>
          <template v-else>
            <a-alert v-if="imageError" :message="imageError" type="error" show-icon class="gap"/>
            <p class="hint">单个商品最多一张主图；URL 由后端按 fileKey 现算，只读展示。拖动或用按钮调整详情图顺序。</p>
            <a-spin :spinning="imageLoading">
              <div class="images">
                <figure v-for="(image, index) in view.images" :key="String(image.imageId)" :draggable="canWrite"
                        @dragstart="dragIndex = index" @dragover.prevent @drop.prevent="move(dragIndex, index)">
                  <a-image :src="image.fileUrl" :width="104" :height="104" :alt="image.fileName || `图 ${index + 1}`"/>
                  <figcaption>
                    <a-tag v-if="image.primaryFlag" color="green">主图</a-tag>
                    <span v-else>第 {{ index + 1 }} 张</span>
                  </figcaption>
                  <a-space v-if="canWrite" direction="vertical" :size="2">
                    <a-button size="small" :disabled="image.primaryFlag || busy" @click="setPrimary(image)">设为主图</a-button>
                    <a-space :size="2">
                      <a-button size="small" :disabled="index === 0 || busy" :aria-label="`前移 ${index + 1}`"
                                @click="move(index, index - 1)">←
                      </a-button>
                      <a-button size="small" :disabled="index === view.images.length - 1 || busy"
                                :aria-label="`后移 ${index + 1}`" @click="move(index, index + 1)">→
                      </a-button>
                    </a-space>
                    <a-popconfirm title="从本商品移除此图片？" @confirm="removeImage(image)">
                      <a-button size="small" danger :disabled="busy">移除</a-button>
                    </a-popconfirm>
                  </a-space>
                </figure>
                <a-upload v-if="canWrite" accept="image/png,image/jpeg,image/webp,image/gif" :file-list="[]"
                          :before-upload="uploadIntoSelected" :show-upload-list="false">
                  <a-button :loading="uploading" type="dashed" class="add-tile">+ 上传图片</a-button>
                </a-upload>
              </div>
            </a-spin>
          </template>
        </a-card>
      </a-col>
    </a-row>

    <a-modal v-model:open="batchOpen" title="按文件名批量导入图片" :width="760" :mask-closable="false" @cancel="closeBatch">
      <a-alert type="info" show-icon class="gap"
               message="文件名（去扩展名）需等于目标商品的 SPU 编码。预览确认后才写入；未匹配、歧义的文件不会被静默丢弃。"/>
      <a-upload :file-list="[]" :before-upload="stageFiles" accept="image/*" multiple :show-upload-list="false">
        <a-button :loading="staging">选择多张图片</a-button>
      </a-upload>
      <!-- 计数行始终渲染：一张都没选时也要显性给出「命中 0」，否则「预览确认后才写入」只剩一个禁用按钮 -->
      <div class="match-summary">
        命中 {{ matchedCount }} · 歧义 {{ ambiguousCount }} · 未匹配 {{ unmatchedCount }}
        <a-button v-if="matches.length" size="small" @click="clearMatches">清空</a-button>
      </div>
      <a-table v-if="matches.length" :data-source="matches" :columns="matchColumns" row-key="fileKey" size="small"
               :pagination="false" :scroll="{ y: 300 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'target'">
            <span v-if="record.status === 'matched'">{{ record.candidates[0].spuCode }}</span>
            <span v-else-if="record.status === 'ambiguous'">{{ record.candidates.map((c: SpuTarget) => c.spuCode).join(' / ') }}</span>
            <span v-else class="muted">—</span>
          </template>
        </template>
      </a-table>
      <template #footer>
        <a-button @click="closeBatch">取消</a-button>
        <a-button type="primary" :disabled="!matchedCount" :loading="binding" @click="confirmBatch">
          绑定 {{ matchedCount }} 张
        </a-button>
      </template>
    </a-modal>
  </section>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import { message } from 'ant-design-vue';
import type { UploadProps } from 'ant-design-vue';
import { productApi, productImageApi } from '/@/api/business/scm/product-api';
import { fileApi } from '/@/api/support/file-api';
import { FILE_FOLDER_TYPE_ENUM } from '/@/constants/support/file-const';
import { useUserStore } from '/@/store/modules/system/user';
import type { ImageCenterView, ProductId, ProductImage, ProductRow, ScmResponse } from '/@/types/business/scm/product';
import type { FileMatchResult, SpuTarget, UploadedImageFile } from './product-import-model';
import { matchFilesBySpuCode } from './product-import-model';
import { productError } from './product-errors';

type Uploaded = ScmResponse<{ fileKey: string; fileUrl: string; fileName?: string; fileSize?: number }>;

const user = useUserStore();
const canWrite = computed(() => user.administratorFlag
    || user.getPointList?.some((p: { webPerms: string }) => p.webPerms === 'scm:product:image:batch'));

const keyword = ref(''), onlyNoPrimary = ref(false), products = ref<ProductRow[]>([]), pickLoading = ref(false), pickError = ref('');
const pickColumns = [
  { title: 'SPU 编码', dataIndex: 'spuCode', width: 150 },
  { title: '名称', dataIndex: 'name' },
  { title: '主图', dataIndex: 'primary', width: 90 },
];

const view = ref<ImageCenterView | null>(null), imageLoading = ref(false), imageError = ref(''), busy = ref(false),
    uploading = ref(false), dragIndex = ref(-1);

async function search() {
  pickLoading.value = true;
  pickError.value = '';
  try {
    const response = await productApi.query({
      pageNum: 1,
      pageSize: 50,
      keyword: keyword.value || undefined,
      hasPrimaryImage: onlyNoPrimary.value ? false : undefined
    });
    products.value = response.data.list;
  } catch (e) {
    pickError.value = productError(e);
  } finally {
    pickLoading.value = false;
  }
}

function resetPick() {
  keyword.value = '';
  onlyNoPrimary.value = false;
  products.value = [];
  view.value = null;
}

async function select(spuId: ProductId) {
  imageLoading.value = true;
  imageError.value = '';
  try {
    view.value = (await productImageApi.query(spuId)).data;
  } catch (e) {
    imageError.value = productError(e);
    view.value = null;
  } finally {
    imageLoading.value = false;
  }
}

async function setPrimary(image: ProductImage) {
  if (!view.value) return;
  await runWrite(() => productImageApi.setPrimary({ spuId: view.value!.spuId, imageId: image.imageId! }), '主图已更新');
}

async function removeImage(image: ProductImage) {
  if (!view.value) return;
  await runWrite(() => productImageApi.batchRemove({ spuId: view.value!.spuId, imageIds: [image.imageId!] }), '图片已移除');
}

/** 顺序按「整列目标 imageId」下发，与后端 reorder 的精确集合校验对齐；from/to 越界时不动。 */
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
  const orderedImageIds = ordered.map(image => image.imageId!);
  if (orderedImageIds.every((id, i) => id === images[i].imageId)) return;
  await runWrite(() => productImageApi.reorder({ spuId: view.value!.spuId, orderedImageIds }), '顺序已更新');
}

const uploadIntoSelected: UploadProps['beforeUpload'] = async (selected) => {
  if (!view.value || !canWrite.value) return false;
  uploading.value = true;
  const isFirst = view.value.images.length === 0;
  const targetSpuId = view.value.spuId;
  try {
    const uploaded = await uploadFile(selected as File);
    await productImageApi.batchBind({
      items: [{ spuId: targetSpuId, fileKey: uploaded.fileKey, primaryFlag: isFirst, sortOrder: view.value!.images.length }]
    });
    await select(targetSpuId);
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
    if (view.value) await select(view.value.spuId);
  } catch (e) {
    imageError.value = productError(e);
  } finally {
    busy.value = false;
  }
}

async function uploadFile(file: File): Promise<{ fileKey: string }> {
  const data = new FormData();
  data.append('file', file);
  const response = await fileApi.uploadFile(data, FILE_FOLDER_TYPE_ENUM.PUBLIC_IMAGE.value) as unknown as Uploaded;
  return { fileKey: response.data.fileKey };
}

// ---- 批量按文件名匹配 ----
const batchOpen = ref(false), staging = ref(false), binding = ref(false), matches = ref<FileMatchResult[]>([]);
const stagedFiles = ref<UploadedImageFile[]>([]);
const matchColumns = [
  { title: '文件', dataIndex: 'fileName', width: 220, customRender: ({ record }: { record: FileMatchResult }) => record.file.fileName },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '目标商品', dataIndex: 'target' },
];
const matchedCount = computed(() => matches.value.filter(m => m.status === 'matched').length);
const ambiguousCount = computed(() => matches.value.filter(m => m.status === 'ambiguous').length);
const unmatchedCount = computed(() => matches.value.filter(m => m.status === 'unmatched').length);

function openBatch() {
  batchOpen.value = true;
}

function closeBatch() {
  batchOpen.value = false;
  clearMatches();
}

function clearMatches() {
  matches.value = [];
  stagedFiles.value = [];
}

const stageFiles: UploadProps['beforeUpload'] = async (selected) => {
  staging.value = true;
  try {
    const uploaded = await uploadFile(selected as File);
    const file = { fileName: (selected as File).name, fileKey: uploaded.fileKey };
    stagedFiles.value = [...stagedFiles.value, file];
    const targets: SpuTarget[] = products.value.map(row => ({ spuId: row.spuId, spuCode: row.spuCode }));
    matches.value = matchFilesBySpuCode(stagedFiles.value, targets);
  } catch (e) {
    message.error(productError(e));
  } finally {
    staging.value = false;
  }
  return false;
};

async function confirmBatch() {
  const items = matches.value.filter(m => m.status === 'matched')
      .map(m => ({ spuId: m.candidates[0].spuId, fileKey: m.file.fileKey, primaryFlag: false, sortOrder: 0 }));
  if (!items.length) return;
  binding.value = true;
  try {
    await productImageApi.batchBind({ items });
    const touchedSelected = view.value && items.some(i => String(i.spuId) === String(view.value!.spuId));
    message.success(`已绑定 ${items.length} 张图片`);
    closeBatch();
    if (touchedSelected) await select(view.value!.spuId);
  } catch (e) {
    message.error(productError(e));
  } finally {
    binding.value = false;
  }
}

function statusText(status: string) {
  return status === 'matched' ? '已匹配' : status === 'ambiguous' ? '歧义' : '未匹配';
}

function statusColor(status: string) {
  return status === 'matched' ? 'green' : status === 'ambiguous' ? 'orange' : 'red';
}
</script>
<style scoped>
.gap {
  margin-bottom: 12px;
}

.pick-form {
  margin-bottom: 8px;
}

.hint {
  color: var(--ant-color-text-secondary, #666);
  margin: 0 0 12px;
}

.images {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

figure {
  margin: 0;
  width: 116px;
}

figcaption {
  margin: 6px 0;
}

.add-tile {
  width: 104px;
  height: 104px;
}

.match-summary {
  margin: 12px 0 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.muted {
  color: var(--ant-color-text-tertiary, #bbb);
}
</style>
