<!--
  * 地区选择框
  *
  *
-->

<template>
  <a-cascader
    :style="`width:${width}`"
    v-model:value="areaValue"
    :show-search="{filter}"
    :options="areaOptionData"
    :placeholder="placeholder"
    :size="size"
    @change="handleChange"
  />
</template>

<script setup lang="ts">
import {PROVINCE_CITY_DISTRICT} from './province-city-district';
import {PROVINCE_CITY} from './province-city';
import {ref, toRaw, watch} from 'vue';

// ============ 组件属性 ============

const TYPE_PROVINCE_CITY_DISTRICT = 'province_city_district';

const props = defineProps({
  type: String,
  value: [Number, Array],
  width: {
    type: String,
    default: '200px',
  },
  placeholder: {
    type: String,
    default: '请选择地区',
  },
  size: {
    type: String,
    default: 'default',
  },
  disabled: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['update:value', 'change']);

// ============ 组件业务 ============
const areaOptionData = props.type === TYPE_PROVINCE_CITY_DISTRICT ? PROVINCE_CITY_DISTRICT : PROVINCE_CITY;

type AreaValue = string | number;
interface AreaOption {
  value?: AreaValue;
  label?: string;
  children?: AreaOption[];
}

// 绑定地区数据
const areaValue = ref<AreaValue[]>([]);
// 监听value变化；首次渲染也要回显已有地区
watch(
  () => props.value,
  (newValue) => {
    const selected = Array.isArray(newValue) ? newValue : [];
    areaValue.value = selected
      .slice(0, 3)
      .map((item) => (item as AreaOption | undefined)?.value)
      .filter((value): value is AreaValue => value !== undefined);
  },
  {immediate: true}
);

function handleChange(value: AreaValue[], selectedOptions: AreaOption[]) {
  emit('update:value', toRaw(selectedOptions));
  emit('change', value, toRaw(selectedOptions));
}

const filter = (inputValue: string, path: Array<{label?: string | number}>): boolean => {
  return path.some(
    (option) =>
      String(option.label ?? '')
        .toLowerCase()
        .indexOf(inputValue.toLowerCase()) > -1
  );
};
</script>
