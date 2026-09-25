<!--
  * 员工下拉选择框
  *
  * `option-filter-prop="label"` + 每个 option 绑 `label`：a-select 默认按 value 过滤，而 value 是
  * employeeId，按姓名搜索会永远「暂无数据」（展示文本带部门后缀，也不能当过滤键）。
  * 与 `warehouse-select` 同一做法。
-->
<template>
  <a-select
      v-model:value="selectValue"
      :style="`width: ${width}`"
      :placeholder="props.placeholder"
      :showSearch="true"
      option-filter-prop="label"
      :allowClear="true"
      :size="size"
      @change="onChange"
  >
    <!-- label 是过滤键，展示仍带部门：只绑 actualName 才能让「按姓名搜」命中带后缀的渲染文本。 -->
    <a-select-option v-for="item in employeeList" :key="item.employeeId" :value="item.employeeId" :label="item.actualName">
      {{ item.actualName }}
      <template v-if="item.departmentName"> （{{ item.departmentName }}）</template>
    </a-select-option>
  </a-select>
</template>

<script setup lang="ts">
import {onMounted, ref, watch} from 'vue';
import {employeeApi} from '/@/api/system/employee-api';
import {smartSentry} from '/@/lib/smart-sentry';

// =========== 属性定义 和 事件方法暴露 =============

const props = defineProps({
  value: [Number, Array],
  placeholder: {
    type: String,
    default: '请选择',
  },
  width: {
    type: String,
    default: '100%',
  },
  size: {
    type: String,
    default: 'default',
  },
  // 角色ID，可为空
  roleId: {
    type: Number,
    default: null,
  },
  // 禁用标识
  disabledFlag: {
    type: Number,
    default: null,
  },
});

const emit = defineEmits(['update:value', 'change']);

// =========== 查询数据 =============

//员工列表数据
// 只声明模板真正消费的字段：接口返回的是未类型化的 any，靠字面量 [] 推导会变成 never[]。
const employeeList = ref<{employeeId: number; actualName: string; departmentName?: string}[]>([]);

async function query() {
  try {
    let params = {};
    if (props.roleId) {
      params = {roleId: props.roleId};
    }
    if (null != props.disabledFlag) {
      params.disabledFlag = props.disabledFlag;
    }
    let resp = await employeeApi.queryAll(params);
    employeeList.value = resp.data;
  } catch (e) {
    smartSentry.captureError(e);
  }
}

onMounted(query);

// =========== 选择 监听、事件 =============

const selectValue = ref(props.value);
watch(
    () => props.value,
    (newValue) => {
      selectValue.value = newValue;
    }
);

function onChange(value) {
  emit('update:value', value);
  emit('change', value);
}
</script>
