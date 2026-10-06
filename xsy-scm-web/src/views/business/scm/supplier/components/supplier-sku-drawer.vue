<!--
  商品-供应商关系 维护抽屉（整表替换）。
  - 唯一写入口是<b>整表替换</b>：提交的是「提交后应该存在的完整集合」，不是增删差量；
  - <b>空数组 = 清空该供应商的全部关联</b>，不是「无操作」—— 因此 UI 上明确提示，
    而不是静默提交空集合；
  - 已存在的行必须回传 `id` + `version`，任一行版本落后 → 40921 整体回滚。
-->
<template>
  <a-drawer v-model:open="visible" :title="title" :width="scmDrawerWidth('xl')" @close="close">
    <a-spin :spinning="loading">
      <a-alert v-if="error" type="error" :message="error" show-icon class="smart-margin-bottom10"/>
      <SupplierSkuEditableTable v-model="drafts"/>
    </a-spin>
    <template #footer>
      <a-space>
        <a-button @click="visible = false">取消</a-button>
        <a-button type="primary" :loading="saving" @click="submit">保存</a-button>
      </a-space>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import {computed, nextTick, ref} from 'vue';
import {message, Modal} from 'ant-design-vue';
import {supplierSkuApi} from '/@/api/business/scm/supplier-sku-api';
import type {ScmId, SupplierRow} from '/@/types/business/scm/supplier';
import SupplierSkuEditableTable from './supplier-sku-editable-table.vue';
import {fromRows, toReplaceItems, validateSkuDrafts} from '../supplier-form-model';
import type {SkuDraft} from '../supplier-form-model';
import {supplierError} from '../supplier-errors';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';

const emit = defineEmits<{ saved: [] }>();

const visible = ref(false);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const drafts = ref<SkuDraft[]>([]);
// 打开时已存在的关联行数：用于判断本次保存是否会把全部关联清空。
const loadedCount = ref(0);
const supplierId = ref<ScmId>();
const supplierName = ref('');

const title = computed(() => (supplierName.value ? `关联商品 · ${supplierName.value}` : '关联商品'));

/** 打开抽屉并加载该供应商当前的活动关联行。 */
async function open(row: SupplierRow) {
  visible.value = true;
  error.value = '';
  drafts.value = [];
  supplierId.value = row.supplierId;
  supplierName.value = row.name;
  loading.value = true;
  try {
    const response = await supplierSkuApi.listBySupplierId(row.supplierId);
    drafts.value = fromRows(response.data ?? []);
    loadedCount.value = drafts.value.length;
  } catch (e) {
    error.value = supplierError(e);
  } finally {
    loading.value = false;
    await nextTick();
  }
}

function close() {
  error.value = '';
}

async function submit() {
  if (supplierId.value == null) {
    return;
  }
  const problem = validateSkuDrafts(drafts.value);
  if (problem) {
    error.value = problem;
    return;
  }
  // 清空全部行并保存等于删除所有关联，属于不可逆影响，落在操作时确认。
  if (drafts.value.length === 0 && loadedCount.value > 0) {
    try {
      await Modal.confirm({
        title: '确认清空关联商品',
        content: `保存后将删除该供应商现有的全部 ${loadedCount.value} 条商品关联。`,
        okText: '确认清空并保存',
        cancelText: '取消'
      });
    } catch {
      return;
    }
  }
  saving.value = true;
  error.value = '';
  try {
    await supplierSkuApi.replace({supplierId: supplierId.value, items: toReplaceItems(drafts.value)});
    message.success('商品关联已保存');
    visible.value = false;
    emit('saved');
  } catch (e) {
    error.value = supplierError(e);
  } finally {
    saving.value = false;
  }
}

defineExpose({open});
</script>
