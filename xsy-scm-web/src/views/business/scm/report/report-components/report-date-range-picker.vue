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

/**
 * 报表中心统一的日期区间选择器。五个页面共用它，为的是两件事只能有一个实现：
 * 快捷区间集合与默认值（由 `report-model.datePresets` 生成，纯函数可单测）；
 * 绑定值恒为 `yyyy-MM-dd` 字符串，与后端 `LocalDate` 的闭区间语义一致。
 *
 * `presets` 的取值必须是 dayjs 对象（面板要按它算月份与格子，传字符串会让日历空白），
 * 因此这里把字符串区间**只为画日历**转成 dayjs，写回 `v-model` 的仍是字符串。
 *
 * `allow-clear` 关掉：报表没有「不设日期看全部」这一档 —— 后端会以 41110 拒掉缺日期的查询。
 */
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
