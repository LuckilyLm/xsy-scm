<template>
  <a-range-picker
    v-model:value="model"
    :presets="presetOptions"
    value-format="YYYY-MM-DD"
    :allow-clear="false"
    :placeholder="['开始日期', '结束日期']"
  />
</template>

<script setup lang="ts">
import {computed} from 'vue';
import dayjs from 'dayjs';
import type {DateRange} from '../report-model';
import {datePresets} from '../report-model';

/** 报表日期区间由统一组件处理。查询使用 yyyy-MM-dd 字符串，日历显示时转换为 dayjs；日期不能为空。 */
const props = defineProps<{value?: DateRange | undefined}>();

const emit = defineEmits<{'update:value': [value: DateRange | undefined]}>();

const model = computed({
  get: () => props.value,
  // 组件在 `value-format` 下收发字符串；类型由 ant-design-vue 的宽声明决定，这里收敛成报表口径
  set: (value: unknown) => {
    const pair = value as [string, string] | null | undefined;
    emit('update:value', pair && pair[0] && pair[1] ? [pair[0], pair[1]] : undefined);
  },
});

const presetOptions = datePresets().map((preset) => ({
  label: preset.label,
  value: [dayjs(preset.value[0]), dayjs(preset.value[1])],
}));
</script>
