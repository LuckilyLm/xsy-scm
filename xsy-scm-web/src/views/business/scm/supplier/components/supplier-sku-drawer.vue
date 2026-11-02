<!-- 关联商品整表替换；已有行带 id 和版本，版本冲突整批回滚。 -->
<template>
  <a-drawer :open="visible" :title="title" :width="scmDrawerWidth('xl')" @close="requestClose">
    <a-alert
      v-if="supplierDisabled"
      type="warning"
      show-icon
      message="该供应商已停用，只能查看关联商品；启用后才能编辑。"
      class="smart-margin-bottom10"
    />
    <a-alert v-else-if="loadState === 'error'" type="error" show-icon :message="error" class="smart-margin-bottom10">
      <template #action>
        <a-button size="small" @click="load">重新加载</a-button>
      </template>
    </a-alert>
    <a-alert v-else-if="error" type="error" show-icon :message="error" class="smart-margin-bottom10" />
    <a-spin :spinning="loading">
      <SupplierSkuEditableTable v-model="drafts" :readonly="!editable" :empty-text="emptyText" />
    </a-spin>
    <template #footer>
      <a-space>
        <a-button @click="requestClose">{{ dirty ? '取消' : '关闭' }}</a-button>
        <a-button v-if="canEdit && !supplierDisabled" type="primary" :loading="saving" :disabled="!editable || !dirty" @click="submit">
          保存<span v-if="dirty">（{{ changeSummary }}）</span>
        </a-button>
      </a-space>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue';
import {message, Modal} from 'ant-design-vue';
import type {ModalFuncProps} from 'ant-design-vue';
import {supplierSkuApi} from '/@/api/business/scm/supplier-sku-api';
import type {ScmId, SupplierRow, SupplierSkuItem} from '/@/types/business/scm/supplier';
import SupplierSkuEditableTable from './supplier-sku-editable-table.vue';
import {fromRows, toReplaceItems, validateSkuDrafts} from '../supplier-form-model';
import type {SkuDraft} from '../supplier-form-model';
import {supplierError} from '../supplier-errors';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import {hasPermission} from '../../common/scm-permission';

const emit = defineEmits<{saved: []}>();

type LoadState = 'loading' | 'ready' | 'error';

const visible = ref(false);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const loadState = ref<LoadState>('loading');
const drafts = ref<SkuDraft[]>([]);
const baseline = ref<SupplierSkuItem[]>([]);
const loadedCount = ref(0);
const supplierId = ref<ScmId>();
const supplierName = ref('');
const supplierDisabled = ref(false);
let requestSeq = 0;

const title = computed(() => (supplierName.value ? `关联商品 · ${supplierName.value}` : '关联商品'));
/** 只有查询权限时以只读方式查看关联商品。 */
const canEdit = computed(() => hasPermission('scm:supplier:sku:update'));
/** 加载完成、供应商启用且具备更新权限时才允许编辑。 */
const editable = computed(() => canEdit.value && !supplierDisabled.value && loadState.value === 'ready');

const currentItems = computed(() => toReplaceItems(drafts.value));

const baselineById = computed(() => {
  const map = new Map<string, string>();
  for (const item of baseline.value) {
    if (item.id != null) map.set(String(item.id), JSON.stringify(item));
  }
  return map;
});

const summary = computed(() => {
  let inserted = 0;
  let updated = 0;
  for (const item of currentItems.value) {
    if (item.id == null) {
      inserted += 1;
    } else if (baselineById.value.get(String(item.id)) !== JSON.stringify(item)) {
      updated += 1;
    }
  }
  const currentIds = new Set(currentItems.value.filter((item) => item.id != null).map((item) => String(item.id)));
  let removed = 0;
  for (const item of baseline.value) {
    if (item.id != null && !currentIds.has(String(item.id))) removed += 1;
  }
  return {inserted, updated, removed};
});

const dirty = computed(() => {
  if (loadState.value !== 'ready') return false;
  return JSON.stringify(currentItems.value) !== JSON.stringify(baseline.value);
});

const changeSummary = computed(() => {
  const {inserted, updated, removed} = summary.value;
  return `新增 ${inserted} · 修改 ${updated} · 移除 ${removed}`;
});

const emptyText = computed(() => {
  if (loadState.value === 'loading') return '正在加载关联商品…';
  if (loadState.value === 'error') return '关联商品加载失败，请先重新加载';
  if (loadedCount.value > 0) return `已移除全部 ${loadedCount.value} 个商品关联，保存后生效`;
  return '尚未关联任何商品；点击“添加商品”开始';
});

/** 打开抽屉并加载已有关联。 */
async function open(row: SupplierRow) {
  visible.value = true;
  error.value = '';
  drafts.value = [];
  baseline.value = [];
  loadedCount.value = 0;
  loadState.value = 'loading';
  supplierDisabled.value = row.status !== 'ENABLED';
  supplierId.value = row.supplierId;
  supplierName.value = row.name;
  await load();
}

/** 只接受最新一次请求，快速切换供应商时旧响应不会覆盖新数据。 */
async function load() {
  if (supplierId.value == null) return;
  const seq = ++requestSeq;
  loading.value = true;
  error.value = '';
  loadState.value = 'loading';
  try {
    const response = await supplierSkuApi.listBySupplierId(supplierId.value, {suppressGlobalErrorMessage: true});
    if (seq !== requestSeq) return;
    drafts.value = fromRows(response.data ?? []);
    loadedCount.value = drafts.value.length;
    baseline.value = toReplaceItems(drafts.value);
    loadState.value = 'ready';
  } catch (e) {
    if (seq !== requestSeq) return;
    loadState.value = 'error';
    error.value = supplierError(e);
  } finally {
    if (seq === requestSeq) loading.value = false;
  }
}

function reset() {
  requestSeq++;
  visible.value = false;
  error.value = '';
  drafts.value = [];
  baseline.value = [];
  loadedCount.value = 0;
  loadState.value = 'loading';
}

/** Modal.confirm 返回的是销毁句柄而不是 Promise，这里包一层拿到用户选择。 */
function confirmDialog(options: ModalFuncProps): Promise<boolean> {
  return new Promise((resolve) => {
    Modal.confirm({
      ...options,
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
}

/** 有未保存修改时先确认，避免点遮罩或 X 静默丢弃。 */
function requestClose() {
  if (saving.value) return;
  if (!dirty.value) {
    reset();
    return;
  }
  void confirmDialog({
    title: '有未保存的修改',
    content: `${changeSummary.value} 尚未保存。关闭后将丢弃这些修改。`,
    okText: '放弃修改',
    cancelText: '继续编辑',
    okButtonProps: {danger: true},
  }).then((confirmed) => {
    if (confirmed) reset();
  });
}

async function submit() {
  if (!editable.value || supplierId.value == null) return;
  const problem = validateSkuDrafts(drafts.value);
  if (problem) {
    error.value = problem;
    return;
  }
  if (summary.value.removed > 0) {
    const confirmed = await confirmDialog({
      title: '确认保存关联商品',
      content: `本次将整表替换该供应商的商品关联：${changeSummary.value}。移除的行保存后无法恢复。`,
      okText: '确认保存',
      cancelText: '继续编辑',
    });
    if (!confirmed) return;
  }
  saving.value = true;
  error.value = '';
  try {
    await supplierSkuApi.replace({supplierId: supplierId.value, items: currentItems.value});
    message.success(`商品关联已保存：${changeSummary.value}`);
    reset();
    emit('saved');
  } catch (e) {
    error.value = supplierError(e);
  } finally {
    saving.value = false;
  }
}

defineExpose({open});
</script>
