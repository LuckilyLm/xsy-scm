<!-- PCO-1 批量维护：上下架 / 主档启停、改分类、打标签三种命令共用一个入口。 -->
<template>
  <a-modal v-model:open="visible" :title="TITLES[command]" :confirm-loading="saving" :width="620" @ok="submit"
           @cancel="visible = false">
    <a-alert v-if="error" type="error" :message="error" show-icon class="smart-margin-bottom10"/>
    <a-alert v-if="failures.length" type="warning" show-icon class="smart-margin-bottom10"
             :message="`本次未做任何修改：${failures.length} 个商品未通过校验`">
      <template #description>
        <ul class="batch-failures">
          <li v-for="row in failures" :key="`${row.spuId}-${row.reasonCode}`">{{
              row.spuCode || row.spuId
            }}：{{ row.reasonMsg }}
          </li>
          <li v-if="hiddenFailures > 0">另有 {{ hiddenFailures }} 行未列出</li>
        </ul>
      </template>
    </a-alert>
    <p class="batch-scope">已选 {{ items.length }} 个商品，整批在一个事务里应用。</p>
    <a-form ref="formRef" :model="form" layout="vertical">
      <template v-if="command === 'STATUS'">
        <a-form-item label="在售状态" name="status">
          <a-select v-model:value="form.status" :options="SHELF_STATUS_ENUM" allow-clear placeholder="不修改"/>
        </a-form-item>
        <a-form-item label="主档状态" name="masterStatus"
                     help="停止引用只影响新单据选不到该商品，已生成的订单与采购不变">
          <a-select v-model:value="form.masterStatus" :options="MASTER_STATUS_ENUM" allow-clear placeholder="不修改"/>
        </a-form-item>
      </template>
      <a-form-item v-else-if="command === 'CATEGORY'" label="目标分类" name="categoryId"
                   :rules="[{ required: true, message: '请选择已启用的三级分类' }]">
        <CategorySelect v-model:value="form.categoryId" :categories="categories" mode="product"/>
      </a-form-item>
      <template v-else>
        <a-form-item label="打标方式" name="mode">
          <a-radio-group v-model:value="form.mode" :options="TAG_MODE_ENUM"/>
        </a-form-item>
        <a-form-item label="商品标签" name="tagIds"
                     :rules="[{ required: form.mode !== 'REPLACE', message: '追加与移除必须至少选择一个标签' }]">
          <a-select v-model:value="form.tagIds" mode="multiple" :options="tagOptions" option-filter-prop="label"
                    :placeholder="form.mode === 'REPLACE' ? '留空即清空标签' : '选择标签'"/>
        </a-form-item>
      </template>
    </a-form>
  </a-modal>
</template>
<script setup lang="ts">
import {reactive, ref} from 'vue';
import {message} from 'ant-design-vue';
import type {FormInstance} from 'ant-design-vue';
import {productApi} from '/@/api/business/scm/product-api';
import {MASTER_STATUS_ENUM, SHELF_STATUS_ENUM, TAG_MODE_ENUM} from '/@/constants/business/scm/product-const';
import type {
  MasterStatus,
  ProductBatchItem,
  ProductBatchResult,
  ProductCategory,
  ProductId,
  ShelfStatus,
  TagMode
} from '/@/types/business/scm/product';
import CategorySelect from '/@/components/business/scm/product-category-tree-select/index.vue';
import {productError} from '../product-errors';

type BatchCommand = 'STATUS' | 'CATEGORY' | 'TAG';
const TITLES: Record<BatchCommand, string> = {STATUS: '批量维护状态', CATEGORY: '批量修改分类', TAG: '批量维护标签'};
defineProps<{ categories: ProductCategory[]; tagOptions: { value: ProductId; label: string }[] }>();
const emit = defineEmits<{ done: [] }>();
const visible = ref(false), saving = ref(false), error = ref(''), command = ref<BatchCommand>('STATUS');
const formRef = ref<FormInstance>();
const items = ref<ProductBatchItem[]>([]), failures = ref<ProductBatchResult['failures']>([]);
const hiddenFailures = ref(0);
const defaults = () => ({
  status: undefined as ShelfStatus | undefined,
  masterStatus: undefined as MasterStatus | undefined,
  categoryId: undefined as ProductId | undefined,
  mode: 'ADD' as TagMode,
  tagIds: [] as ProductId[]
});
const form = reactive(defaults());

function open(next: BatchCommand, rows: ProductBatchItem[]) {
  command.value = next;
  items.value = rows;
  visible.value = true;
  error.value = '';
  failures.value = [];
  hiddenFailures.value = 0;
  Object.assign(form, defaults());
}

async function submit() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  // 两个状态下拉都允许留空（留空＝不改），所以「至少选一个」只能在这里判，不适合写成 rules。
  if (command.value === 'STATUS' && !form.status && !form.masterStatus) {
    error.value = '请至少选择一个要修改的状态';
    return;
  }
  error.value = '';
  saving.value = true;
  try {
    const response = command.value === 'STATUS'
        ? await productApi.batchStatus(items.value, {status: form.status, masterStatus: form.masterStatus})
        : command.value === 'CATEGORY' ? await productApi.batchCategory(items.value, form.categoryId as ProductId) : await productApi.batchTags(items.value, form.tagIds, form.mode);
    const result = response.data;
    // 预校验失败时后端一行都不写，因此这里保持弹窗打开，让用户按行修正后重试。
    if (result.failedCount) {
      failures.value = result.failures;
      hiddenFailures.value = result.failedCount - result.failures.length;
      return;
    }
    message.success(`已更新 ${result.updatedCount} 个商品`);
    visible.value = false;
    emit('done');
  } catch (e) {
    error.value = productError(e);
  } finally {
    saving.value = false;
  }
}

defineExpose({open});
</script>
<style scoped>
.batch-scope {
  margin-bottom: 12px;
  color: var(--ant-color-text-secondary, #4e5969);
}

.batch-failures {
  margin: 0;
  padding-left: 18px;
}

ul.batch-failures {
  max-height: 180px;
  overflow-y: auto;
}
</style>
