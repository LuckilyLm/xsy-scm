<!--
  * 商品-供应商关系 维护抽屉（整表替换）
  *
  * 来源：**新写**（宿主壳）。
  * C 的供货关系维护是 SPU 级的弹窗，行标识与写语义都不同（见 `supplier-sku-editable-table.vue` 说明），
  * 因此外壳新写，但抽屉的「加载 → 编辑 → 保存 → 冲突提示」流程与 C 的 `Modal.confirm` 交互一致。
  *
  * 关键语义（与后端 `SupplierSkuService.replace` 一一对应，绝不能改）：
  * - 唯一写入口是**整表替换**：提交的是「提交后应该存在的完整集合」，
  *   而不是「本次新增 / 修改 / 删除的差量」；
  * - **空数组 = 清空该供应商的全部关联**，不是「无操作」—— 因此 UI 上明确提示，
  *   而不是静默提交空集合；
  * - 已存在的行必须回传 `id` + `version`，任一行版本落后 → 40921 整体回滚；
  * - 同一供应商允许出现多条「默认来源」（legacy 不变量 R12）。
-->
<template>
  <a-drawer v-model:open="visible" :title="title" width="1120" @close="close">
    <a-spin :spinning="loading">
      <a-alert v-if="error" type="error" :message="error" show-icon class="smart-margin-bottom10"/>
      <a-alert
          type="info"
          show-icon
          class="smart-margin-bottom10"
          message="保存时以当前表格内容整体覆盖该供应商的关联关系；清空全部行并保存等于删除所有关联。"
      />
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
import {message} from 'ant-design-vue';
import {supplierSkuApi} from '/@/api/business/scm/supplier-sku-api';
import type {ScmId, SupplierRow} from '/@/types/business/scm/supplier';
import SupplierSkuEditableTable from './supplier-sku-editable-table.vue';
import {fromRows, toReplaceItems, validateSkuDrafts} from '../supplier-form-model';
import type {SkuDraft} from '../supplier-form-model';
import {supplierError} from '../supplier-errors';

const emit = defineEmits<{ saved: [] }>();

const visible = ref(false);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const drafts = ref<SkuDraft[]>([]);
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
