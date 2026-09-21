<!--
  * 客户下拉选择框
  *
  * 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/components/business/customer-select/index.vue
  * （Copy First + Adapt）
  *
  * 适配：
  * - API 改为 `/@/api/business/scm/customer-api`，`queryAll` → `optionList`；
  * - DTO 字段 `customerId` / `customerName` / `customerNo` → `customerId` / `name` / `customerCode`；
  * - `customerList` 补 DTO 类型（原为 `ref([])`，在 V2 严格模式下是 TS2322）；
  * - `onChange(value)` 补显式类型（原为隐式 any，TS7006）；
  * - 补 `optionFilterProp="label"`：C 只设了 `showSearch` 没设过滤字段，搜索实际不生效；
  * - `value` 增加 `String` 支持（ID 可能是字符串）。
-->
<template>
  <a-select
      v-model:value="selectValue"
      :style="`width: ${width}`"
      :placeholder="props.placeholder"
      :show-search="true"
      :allow-clear="true"
      :size="size"
      option-filter-prop="label"
      @change="onChange"
  >
    <a-select-option
        v-for="item in visibleList"
        :key="item.customerId"
        :value="item.customerId"
        :label="`${item.name}（${item.customerCode}）`"
    >
      {{ item.name }}
      <template v-if="item.customerCode"> （{{ item.customerCode }}）</template>
    </a-select-option>
  </a-select>
</template>

<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue';
import {customerApi} from '/@/api/business/scm/customer-api';
import {smartSentry} from '/@/lib/smart-sentry';
import type {CustomerOption, ScmId} from '/@/types/business/scm/customer';

const props = withDefaults(
    defineProps<{
      value?: ScmId | ScmId[] | null;
      placeholder?: string;
      width?: string;
      size?: string;
      /**
       * 只列出指定类型编码的客户。
       * 「上级集团客户」传 `GROUP` —— 后端只接受集团作为上级，前端收窄可避免必然失败的提交。
       */
      typeCode?: string | null;
      /**
       * 需要排除的客户 ID。
       * 编辑客户时传自己的 `customerId`，否则「上级客户」下拉会包含自己（后端 `validateParent` 会以 40032 拒绝）。
       */
      excludeId?: ScmId | null;
    }>(),
    {placeholder: '请选择客户', width: '100%', size: 'default', typeCode: null, excludeId: null}
);

const emit = defineEmits<{ 'update:value': [value: ScmId | undefined]; change: [value: ScmId | undefined] }>();

const customerList = ref<CustomerOption[]>([]);

const visibleList = computed(() =>
    customerList.value.filter((item) => {
      if (props.typeCode && item.customerTypeCode !== props.typeCode) {
        return false;
      }
      // 用 String() 比较：后端 Long 与前端可能拿到的字符串形式 ID 都要能对上。
      return props.excludeId == null || String(item.customerId) !== String(props.excludeId);
    })
);

async function query() {
  try {
    const resp = await customerApi.optionList();
    customerList.value = resp.data ?? [];
  } catch (e) {
    smartSentry.captureError(e);
  }
}

onMounted(query);

const selectValue = ref<ScmId | ScmId[] | null | undefined>(props.value);
watch(
    () => props.value,
    (newValue) => {
      selectValue.value = newValue;
    }
);

function onChange(value: ScmId | undefined) {
  emit('update:value', value);
  emit('change', value);
}
</script>
