<template>
  <div class="scm-advanced-filter">
    <a-space direction="vertical" style="width: 100%" :size="8">
      <a-space wrap>
        <span>组合筛选</span>
        <a-button size="small" :disabled="modelValue.length >= 8 || modelValue.length >= fields.length" @click="add"> 添加条件 </a-button>
        <span class="scm-advanced-filter__tip">条件同时生效；与上方相同的字段必须取相同值</span>
      </a-space>
      <a-space v-for="(condition, index) in modelValue" :key="index" wrap>
        <a-select
          :value="condition.key"
          style="width: 155px"
          :options="fieldOptions"
          placeholder="选择字段"
          @change="changeField(index, String($event))"
        />
        <a-tag>{{ condition.operator === 'CONTAINS' ? '包含' : '等于' }}</a-tag>
        <a-select
          v-if="field(condition.key)?.valueType === 'BOOLEAN'"
          :value="condition.value"
          style="width: 155px"
          :options="[
            {label: '是', value: 'true'},
            {label: '否', value: 'false'},
          ]"
          @change="updateValue(index, String($event))"
        />
        <CustomerSelect v-else-if="condition.key === 'customerId'" :value="idValue(condition.value)" @update:value="setId(index, $event)" />
        <EmployeeSelect
          v-else-if="condition.key === 'sellerId' || condition.key === 'purchaserId'"
          :value="idValue(condition.value)"
          @update:value="setId(index, $event)"
        />
        <ProductCategoryTreeSelect
          v-else-if="condition.key === 'categoryId'"
          :value="idValue(condition.value)"
          :categories="categories"
          @update:value="setId(index, $event)"
        />
        <SupplierSelect v-else-if="condition.key === 'supplierId'" :value="idValue(condition.value)" @update:value="setId(index, $event)" />
        <WarehouseSelect v-else-if="condition.key === 'warehouseId'" :value="idValue(condition.value)" @update:value="setId(index, $event)" />
        <a-input
          v-else
          :value="condition.value"
          style="width: 200px"
          :maxlength="150"
          placeholder="输入关键字"
          @update:value="updateValue(index, String($event))"
        />
        <a-button size="small" danger @click="remove(index)">移除</a-button>
      </a-space>
    </a-space>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import SupplierSelect from '/@/components/business/scm/supplier-select/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import ProductCategoryTreeSelect from '/@/components/business/scm/product-category-tree-select/index.vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import {productCategoryApi} from '/@/api/business/scm/product-category-api';
import type {ProductCategory} from '/@/types/business/scm/product';
import type {ScmAdvancedFilterCondition, ScmAdvancedFilterField} from '/@/types/business/scm/advanced-filter';

const props = defineProps<{
  modelValue: ScmAdvancedFilterCondition[];
  fields: ScmAdvancedFilterField[];
}>();
const emit = defineEmits<{(e: 'update:modelValue', value: ScmAdvancedFilterCondition[]): void}>();

/** 内部字段仍用 ID 传值，界面只展示名称，不把「编号」暴露给用户。 */
const ID_FIELD_LABELS: Record<string, string> = {
  customerId: '客户',
  sellerId: '销售员',
  categoryId: '商品分类',
  supplierId: '供应商',
  purchaserId: '采购员',
  warehouseId: '仓库',
};
const fieldOptions = computed(() => props.fields.map((f) => ({label: ID_FIELD_LABELS[f.key] ?? f.title, value: f.key})));
const field = (key: string) => props.fields.find((f) => f.key === key);
const categories = ref<ProductCategory[]>([]);

onMounted(async () => {
  try {
    categories.value = (await productCategoryApi.tree({suppressGlobalErrorMessage: true})).data;
  } catch {
    // 分类加载失败时下拉为空，其余筛选条件仍可使用。
  }
});

function idValue(value: string): number | undefined {
  if (value.trim() === '') return undefined;
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : undefined;
}

function setId(index: number, value: unknown): void {
  updateValue(index, value == null ? '' : String(value));
}

function add(): void {
  const available = props.fields.find((f) => !props.modelValue.some((c) => c.key === f.key));
  if (!available) return;
  emit('update:modelValue', [
    ...props.modelValue,
    {
      key: available.key,
      operator: available.operator,
      value: '',
    },
  ]);
}
function changeField(index: number, key: string): void {
  const candidate = field(key);
  if (!candidate || props.modelValue.some((c, i) => i !== index && c.key === key)) return;
  emit(
    'update:modelValue',
    props.modelValue.map((c, i) => (i === index ? {key, operator: candidate.operator, value: ''} : c))
  );
}
function updateValue(index: number, value: string): void {
  emit(
    'update:modelValue',
    props.modelValue.map((c, i) => (i === index ? {...c, value} : c))
  );
}
function remove(index: number): void {
  emit(
    'update:modelValue',
    props.modelValue.filter((_, i) => i !== index)
  );
}
</script>

<style scoped>
.scm-advanced-filter {
  padding: 8px 0 12px;
}
.scm-advanced-filter__tip {
  color: var(--scm-text-secondary);
  font-size: 12px;
}
</style>
