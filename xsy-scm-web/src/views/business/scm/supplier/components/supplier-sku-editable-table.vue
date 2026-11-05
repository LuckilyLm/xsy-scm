<!-- 关联商品可编辑多行；允许多个优先采购来源，价格用字符串。 -->
<template>
  <div
    ref="tableScroll"
    class="sku-table-scroll"
    tabindex="0"
    role="region"
    aria-label="关联商品表格，左右方向键可横向滚动"
    @keydown="onTableKeydown"
  >
    <a-table
      :data-source="modelValue"
      :columns="columns"
      :pagination="false"
      size="small"
      bordered
      :row-key="(record: SkuDraft, index?: number) => record.id ?? `new-${index}`"
      :scroll="{x: 960}"
    >
      <template #headerCell="{column}">
        <span v-if="column.dataIndex === 'referencePrice'"
          >参考价<ScmFieldHelp label="参考价" text="按该供应商的采购单位计价；采购单位不同时不能直接比较价格。" />
        </span>
        <span v-else-if="column.dataIndex === 'defaultFlag'"
          >优先采购<ScmFieldHelp label="优先采购" text="只影响列表展示和同规格比价排序，不会自动把采购单分给该供应商。" />
        </span>
        <template v-else>{{ column.title }}</template>
      </template>
      <template #bodyCell="{column, record, index}">
        <template v-if="column.dataIndex === 'rowNo'">
          <span class="row-no">{{ index + 1 }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'skuId'">
          <SkuSelect
            :value="record.skuId"
            :disabled="readonly"
            :selected-options="selectedSkuOptions(record)"
            :disabled-values="otherSkuIds(record.skuId)"
            v-aria-label="`第 ${index + 1} 行商品规格`"
            @update:value="(value) => (record.skuId = Array.isArray(value) ? value[0] : value)"
          />
        </template>
        <template v-else-if="column.dataIndex === 'supplierItemCode'">
          <a-input v-model:value="record.supplierItemCode" :maxlength="100" :disabled="readonly" v-aria-label="`第 ${index + 1} 行供应商货号`" />
        </template>
        <template v-else-if="column.dataIndex === 'purchaseUnit'">
          <a-select
            :value="record.purchaseUnit || undefined"
            :options="unitOptions"
            show-search
            option-filter-prop="label"
            placeholder="选择单位"
            :disabled="readonly"
            v-aria-label="`第 ${index + 1} 行采购单位`"
            @update:value="(value: string) => (record.purchaseUnit = value)"
          />
        </template>
        <template v-else-if="column.dataIndex === 'referencePrice'">
          <a-input-number
            v-model:value="record.referencePrice"
            string-mode
            :min="'0'"
            :precision="4"
            :controls="false"
            addon-after="元"
            :disabled="readonly"
            placeholder="例如 3.5000"
            v-aria-label="`第 ${index + 1} 行参考价`"
            style="width: 100%"
          />
        </template>
        <template v-else-if="column.dataIndex === 'defaultFlag'">
          <a-switch
            v-model:checked="record.defaultFlag"
            checked-children="优先"
            un-checked-children="普通"
            size="small"
            :disabled="readonly"
            :aria-label="`第 ${index + 1} 行优先采购`"
          />
        </template>
        <template v-else-if="column.dataIndex === 'purchaserId'">
          <EmployeeSelect
            :value="record.purchaserId ?? undefined"
            :options="employees"
            :auto-load="false"
            width="100%"
            placeholder="请选择"
            :disabled="readonly"
            v-aria-label="`第 ${index + 1} 行采购员`"
            @update:value="(value: number | undefined) => (record.purchaserId = value ?? null)"
          />
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-switch
            :checked="(record.status ?? 'ENABLED') === 'ENABLED'"
            checked-children="启用"
            un-checked-children="停用"
            size="small"
            :disabled="readonly"
            :aria-label="`第 ${index + 1} 行状态`"
            @update:checked="(checked: boolean) => (record.status = checked ? 'ENABLED' : 'DISABLED')"
          />
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <!-- 尚未保存的新行移除可撤销，不弹确认；已有关联删除后才需要二次确认。 -->
          <a-button
            v-if="!readonly && record.id == null"
            type="link"
            danger
            size="small"
            :aria-label="`移除第 ${index + 1} 行`"
            @click="emit('update:modelValue', removeSkuDraft(modelValue, index))"
            >移除</a-button
          >
          <a-popconfirm v-else-if="!readonly" title="确认移除该关联？" @confirm="emit('update:modelValue', removeSkuDraft(modelValue, index))">
            <a-button type="link" danger size="small" :aria-label="`移除第 ${index + 1} 行`">移除</a-button>
          </a-popconfirm>
        </template>
      </template>
      <template #emptyText>
        <span class="empty">{{ emptyText }}</span>
      </template>
    </a-table>
  </div>
  <a-button v-if="!readonly" class="add-sku" @click="emit('update:modelValue', [...modelValue, emptySkuDraft()])">添加商品</a-button>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';
import {productUomApi} from '/@/api/business/scm/product-assistant-api';
import {employeeApi} from '/@/api/system/employee-api';
import type {ProductUom} from '/@/types/business/scm/product';
import type {ScmId} from '/@/types/business/scm/supplier';
import {emptySkuDraft, removeSkuDraft} from '../supplier-form-model';
import type {SkuDraft} from '../supplier-form-model';

const props = defineProps<{modelValue: SkuDraft[]; readonly?: boolean; emptyText?: string}>();
const emit = defineEmits<{'update:modelValue': [rows: SkuDraft[]]}>();

type EmployeeOption = {employeeId: number; actualName: string; departmentName?: string};
type SelectedSkuOption = {skuId: ScmId; productName: string; specName: string};

const columns: TableColumnsType<SkuDraft> = [
  {title: '行号', dataIndex: 'rowNo', width: 48},
  {title: '商品规格 *', dataIndex: 'skuId', width: 200},
  {title: '供应商货号', dataIndex: 'supplierItemCode', width: 100},
  {title: '采购单位 *', dataIndex: 'purchaseUnit', width: 100},
  {title: '参考价', dataIndex: 'referencePrice', width: 150},
  {title: '优先采购', dataIndex: 'defaultFlag', width: 90, align: 'center'},
  {title: '采购员', dataIndex: 'purchaserId', width: 110},
  {title: '状态', dataIndex: 'status', width: 90, align: 'center'},
  {title: '操作', dataIndex: 'action', width: 70, align: 'center', fixed: 'right'},
];

const units = ref<ProductUom[]>([]);
const employees = ref<EmployeeOption[]>([]);
const tableScroll = ref<HTMLElement>();

onMounted(async () => {
  await Promise.allSettled([loadUnits(), loadEmployees()]);
});

async function loadUnits(): Promise<void> {
  try {
    const response = await productUomApi.options({suppressGlobalErrorMessage: true});
    units.value = response.data ?? [];
  } catch {
    // 单位字典读取失败时只保留历史值，不阻断整表编辑；保存仍由后端校验。
  }
}

async function loadEmployees(): Promise<void> {
  try {
    const response = await employeeApi.queryAll();
    employees.value = response.data ?? [];
  } catch {
    // 员工选项失败时仍可保存已有采购员，新行只能留空。
  }
}

/**
 * 采购单位必须从计量单位字典选择；历史手填或已停用的值原样保留，避免编辑旧关联时丢值。
 */
const unitOptions = computed(() => {
  const enabled = units.value.filter((unit) => unit.status !== 'DISABLED');
  const known = new Map(units.value.map((unit) => [unit.name, unit]));
  const current = [...new Set(props.modelValue.map((draft) => draft.purchaseUnit).filter(Boolean))];
  return [
    ...enabled.map((unit) => ({value: unit.name, label: unit.name})),
    ...current
      .filter((name) => !enabled.some((unit) => unit.name === name))
      .map((name) => ({value: name, label: known.has(name) ? `${name}（已停用）` : name})),
  ];
});

const emptyText = computed(() => props.emptyText ?? '尚未关联任何商品；点击“添加商品”开始');

/** 已有关联行用列表保存的数据回显，避免首屏 50 条之外只剩数字 ID。 */
function selectedSkuOptions(record: SkuDraft): SelectedSkuOption[] {
  if (record.skuId == null || !record.skuNameSnapshot) return [];
  const spec = Object.values(record.specValuesSnapshot ?? {}).join('/') || '默认规格';
  return [{skuId: record.skuId, productName: record.skuNameSnapshot, specName: spec}];
}

/** 同一张表里已选过的规格不允许重复添加。 */
function otherSkuIds(current?: ScmId): ScmId[] {
  const key = current == null ? undefined : String(current);
  return props.modelValue.filter((draft) => draft.skuId != null && String(draft.skuId) !== key).map((draft) => draft.skuId as ScmId);
}

/** 聚焦表格容器后，左右方向键横向滚动；在输入框内不拦截方向键。 */
function onTableKeydown(event: KeyboardEvent): void {
  if (event.target !== event.currentTarget) return;
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
  event.preventDefault();
  const scroller = tableScroll.value?.querySelector<HTMLElement>('.ant-table-content, .ant-table-body');
  if (!scroller) return;
  scroller.scrollLeft += event.key === 'ArrowLeft' ? -120 : 120;
}
</script>

<style scoped>
.sku-table-scroll {
  overflow-x: auto;
}

.sku-table-scroll:focus-visible {
  outline: 2px solid var(--scm-primary, #2d8c4d);
  outline-offset: 2px;
}

.ant-select {
  width: 100%;
}

.row-no {
  color: var(--scm-text-secondary, #8c8c8c);
  font-variant-numeric: tabular-nums;
}

.empty {
  color: var(--scm-text-secondary, #8c8c8c);
}

.add-sku {
  margin-top: 12px;
}
</style>
