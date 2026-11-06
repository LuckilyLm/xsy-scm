<template>
  <a-empty v-if="denied" v-bind="$attrs" description="暂无权限查看此内容" :image="Empty.PRESENTED_IMAGE_SIMPLE" />
</template>

<script setup lang="ts">
import {computed, useSlots, watch} from 'vue';
import {Empty} from 'ant-design-vue';
import {isPermissionDenied, notifyRequestError} from '/@/utils/request-feedback';

/** 请求失败统一弹出提示；无权查看时显示明确的空态，不展示错误横条。 */
const props = defineProps<{message?: string}>();
defineOptions({inheritAttrs: false});
const denied = computed(() => isPermissionDenied(props.message ?? ''));
const slots = useSlots();

watch(
  () => props.message,
  (text) => {
    if (text) notifyRequestError(text, !denied.value && slots.action ? () => slots.action?.({}) : undefined);
  },
  {immediate: true}
);
</script>
