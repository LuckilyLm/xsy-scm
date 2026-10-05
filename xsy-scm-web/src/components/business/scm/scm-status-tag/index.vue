<template>
  <a-tag :color="normalizedColor" class="scm-status-tag">
    <slot>{{ label }}</slot>
  </a-tag>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import {scmStatusColor} from '/@/theme/scm/scm-status';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {SCM_STATUS_TONE_COLOR} from '/@/theme/scm/scm-status';

/**
 * 统一状态标签。
 *
 * 既接受业务常量表已有的 antd 色名（`color`），也接受语义档位（`tone`），
 * 便于存量页面渐进接入而不必重写字典。
 */
const props = defineProps<{ color?: string | null; tone?: ScmStatusTone; label?: string }>();

const normalizedColor = computed(() => scmStatusColor(props.tone ? SCM_STATUS_TONE_COLOR[props.tone] : props.color));
</script>
